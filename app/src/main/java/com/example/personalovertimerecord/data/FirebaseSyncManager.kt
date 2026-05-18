package com.example.personalovertimerecord.data

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirebaseSyncManager private constructor(private val context: Context) {
    
    private var auth: FirebaseAuth? = null
    private var db: FirebaseFirestore? = null
    
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState
    
    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn
    
    private val _currentUser = MutableStateFlow<FirebaseUser?>(null)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser
    
    init {
        try {
            auth = FirebaseAuth.getInstance()
            db = FirebaseFirestore.getInstance()
            
            auth?.addAuthStateListener { firebaseAuth ->
                val user = firebaseAuth?.currentUser
                _currentUser.value = user
                _isLoggedIn.value = user != null
                Log.d(TAG, "Auth state changed: ${user?.uid}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Firebase initialization failed", e)
        }
    }
    
    suspend fun signIn(email: String, password: String): Result<FirebaseUser> = withContext(Dispatchers.IO) {
        try {
            if (auth == null) {
                return@withContext Result.failure(Exception("Firebase 未初始化，请检查配置"))
            }
            
            _syncState.value = SyncState.Loading("正在登录...")
            val result = auth?.signInWithEmailAndPassword(email, password)?.await()
            result?.user?.let {
                _syncState.value = SyncState.Success("登录成功")
                Result.success(it)
            } ?: Result.failure(Exception("登录失败"))
        } catch (e: Exception) {
            _syncState.value = SyncState.Error("登录失败: ${e.message}")
            Result.failure(e)
        }
    }
    
    suspend fun signUp(email: String, password: String): Result<FirebaseUser> = withContext(Dispatchers.IO) {
        try {
            if (auth == null) {
                return@withContext Result.failure(Exception("Firebase 未初始化，请检查配置"))
            }
            
            _syncState.value = SyncState.Loading("正在注册...")
            val result = auth?.createUserWithEmailAndPassword(email, password)?.await()
            result?.user?.let {
                _syncState.value = SyncState.Success("注册成功")
                Result.success(it)
            } ?: Result.failure(Exception("注册失败"))
        } catch (e: Exception) {
            _syncState.value = SyncState.Error("注册失败: ${e.message}")
            Result.failure(e)
        }
    }
    
    fun signOut() {
        try {
            auth?.signOut()
            _syncState.value = SyncState.Idle
            Log.d(TAG, "User signed out")
        } catch (e: Exception) {
            Log.e(TAG, "Sign out failed", e)
        }
    }
    
    suspend fun syncToCloud(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val user = auth?.currentUser ?: return@withContext Result.failure(Exception("未登录"))
            if (db == null) {
                return@withContext Result.failure(Exception("Firebase 未初始化，请检查配置"))
            }
            
            _syncState.value = SyncState.Loading("正在同步到云端...")
            Log.d(TAG, "Starting sync to cloud for user: ${user.uid}")
            
            val localStorage = AttendanceStorage(context)
            val localRecords = localStorage.getAllAttendance()
            val settingsManager = SettingsManager(context)
            val localSettings = settingsManager.getSettings()
            
            val recordsCollection = db?.collection("users")
                ?.document(user.uid)
                ?.collection("records")
            
            val batch = db?.batch()
            localRecords.forEach { attendance ->
                try {
                    val dayType = com.example.personalovertimerecord.utils.HolidayManager.getDayType(attendance.date)
                    val firebaseRecord = FirebaseOvertimeRecord.fromAttendance(attendance, user.uid, dayType)
                    val docRef = recordsCollection?.document(attendance.id.toString())
                    if (docRef != null && batch != null) {
                        batch.set(docRef, firebaseRecord)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to process record ${attendance.id}", e)
                }
            }
            batch?.commit()?.await()
            
            val settingsRef = db?.collection("users")
                ?.document(user.uid)
                ?.collection("settings")
                ?.document("user_settings")
            val firebaseSettings = FirebaseUserSettings.fromOvertimeSettings(user.uid, localSettings)
            settingsRef?.set(firebaseSettings)?.await()
            
            _syncState.value = SyncState.Success("同步成功")
            Log.d(TAG, "Sync completed successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            _syncState.value = SyncState.Error("同步失败: ${e.message}")
            Log.e(TAG, "Sync failed", e)
            Result.failure(e)
        }
    }
    
    suspend fun syncFromCloud(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val user = auth?.currentUser ?: return@withContext Result.failure(Exception("未登录"))
            if (db == null) {
                return@withContext Result.failure(Exception("Firebase 未初始化，请检查配置"))
            }
            
            _syncState.value = SyncState.Loading("正在从云端下载...")
            Log.d(TAG, "Starting sync from cloud for user: ${user.uid}")
            
            val recordsCollection = db?.collection("users")
                ?.document(user.uid)
                ?.collection("records")
                ?.orderBy("date", Query.Direction.DESCENDING)
            
            val snapshot = recordsCollection?.get()?.await()
            val cloudRecords = snapshot?.toObjects(FirebaseOvertimeRecord::class.java) ?: emptyList()
            
            val localStorage = AttendanceStorage(context)
            cloudRecords.forEach { firebaseRecord ->
                try {
                    val attendance = firebaseRecord.toAttendance()
                    val existingRecord = localStorage.getAttendanceByDate(attendance.date)
                    if (existingRecord == null) {
                        localStorage.insertAttendance(attendance)
                    } else {
                        localStorage.updateAttendance(attendance)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to process cloud record", e)
                }
            }
            
            val settingsRef = db?.collection("users")
                ?.document(user.uid)
                ?.collection("settings")
                ?.document("user_settings")
            
            try {
                val settingsDoc = settingsRef?.get()?.await()
                if (settingsDoc?.exists() == true) {
                    val firebaseSettings = settingsDoc.toObject(FirebaseUserSettings::class.java)
                    firebaseSettings?.let {
                        val settingsManager = SettingsManager(context)
                        settingsManager.saveSettings(it.toOvertimeSettings())
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "No cloud settings found, using local settings", e)
            }
            
            _syncState.value = SyncState.Success("下载成功")
            Log.d(TAG, "Download from cloud completed successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            _syncState.value = SyncState.Error("下载失败: ${e.message}")
            Log.e(TAG, "Download from cloud failed", e)
            Result.failure(e)
        }
    }
    
    suspend fun syncAll(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val user = auth?.currentUser ?: return@withContext Result.failure(Exception("未登录"))
            if (db == null) {
                return@withContext Result.failure(Exception("Firebase 未初始化，请检查配置"))
            }
            
            _syncState.value = SyncState.Loading("正在全量同步...")
            Log.d(TAG, "Starting full sync for user: ${user.uid}")
            
            val recordsCollection = db?.collection("users")
                ?.document(user.uid)
                ?.collection("records")
                ?.orderBy("date", Query.Direction.DESCENDING)
            
            val snapshot = recordsCollection?.get()?.await()
            val cloudRecords = snapshot?.toObjects(FirebaseOvertimeRecord::class.java) ?: emptyList()
            val cloudRecordsMap = cloudRecords.associateBy { it.date }
            
            val localStorage = AttendanceStorage(context)
            val localRecords = localStorage.getAllAttendance()
            val localRecordsMap = localRecords.associateBy { it.date }
            
            val allDates = (cloudRecordsMap.keys + localRecordsMap.keys).toSet()
            
            allDates.forEach { date ->
                val cloudRecord = cloudRecordsMap[date]
                val localRecord = localRecordsMap[date]
                
                try {
                    when {
                        cloudRecord != null && localRecord != null -> {
                            // 使用 checkInTimestamp 作为本地时间（如果没有则用当前时间）
                            val cloudTime = cloudRecord.updatedAt?.toDate()?.time ?: 0L
                            val localTime = localRecord.checkInTimestamp ?: System.currentTimeMillis()
                            if (cloudTime > localTime) {
                                localStorage.updateAttendance(cloudRecord.toAttendance())
                            } else {
                                val dayType = com.example.personalovertimerecord.utils.HolidayManager.getDayType(localRecord.date)
                                val updatedCloudRecord = FirebaseOvertimeRecord.fromAttendance(localRecord, user.uid, dayType)
                                db?.collection("users")
                                    ?.document(user.uid)
                                    ?.collection("records")
                                    ?.document(localRecord.id.toString())
                                    ?.set(updatedCloudRecord)
                                    ?.await()
                            }
                        }
                        cloudRecord != null && localRecord == null -> {
                            localStorage.insertAttendance(cloudRecord.toAttendance())
                        }
                        cloudRecord == null && localRecord != null -> {
                            val dayType = com.example.personalovertimerecord.utils.HolidayManager.getDayType(localRecord.date)
                            val newCloudRecord = FirebaseOvertimeRecord.fromAttendance(localRecord, user.uid, dayType)
                            db?.collection("users")
                                ?.document(user.uid)
                                ?.collection("records")
                                ?.document(localRecord.id.toString())
                                ?.set(newCloudRecord)
                                ?.await()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sync date $date", e)
                }
            }
            
            val settingsManager = SettingsManager(context)
            val settingsRef = db?.collection("users")
                ?.document(user.uid)
                ?.collection("settings")
                ?.document("user_settings")
            
            try {
                val settingsDoc = settingsRef?.get()?.await()
                if (settingsDoc?.exists() == true) {
                    val firebaseSettings = settingsDoc.toObject(FirebaseUserSettings::class.java)
                    firebaseSettings?.let {
                        settingsManager.saveSettings(it.toOvertimeSettings())
                    }
                }
            } catch (e: Exception) {
                val localSettings = settingsManager.getSettings()
                val firebaseSettings = FirebaseUserSettings.fromOvertimeSettings(user.uid, localSettings)
                settingsRef?.set(firebaseSettings)?.await()
            }
            
            _syncState.value = SyncState.Success("全量同步成功")
            Log.d(TAG, "Full sync completed successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            _syncState.value = SyncState.Error("全量同步失败: ${e.message}")
            Log.e(TAG, "Full sync failed", e)
            Result.failure(e)
        }
    }
    
    fun addCloudListener(onDataChange: (List<FirebaseOvertimeRecord>) -> Unit) {
        try {
            val user = auth?.currentUser ?: return
            if (db == null) {
                return
            }
            
            db?.collection("users")
                ?.document(user.uid)
                ?.collection("records")
                ?.addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Cloud listener error", error)
                        return@addSnapshotListener
                    }
                    
                    snapshot?.let {
                        val records = it.toObjects(FirebaseOvertimeRecord::class.java)
                        onDataChange(records)
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add cloud listener", e)
        }
    }
    
    companion object {
        private const val TAG = "FirebaseSyncManager"
        
        @Volatile
        private var instance: FirebaseSyncManager? = null
        
        fun getInstance(context: Context): FirebaseSyncManager {
            return instance ?: synchronized(this) {
                instance ?: FirebaseSyncManager(context.applicationContext).also { instance = it }
            }
        }
    }
}

sealed class SyncState {
    object Idle : SyncState()
    data class Loading(val message: String) : SyncState()
    data class Success(val message: String) : SyncState()
    data class Error(val message: String) : SyncState()
}
