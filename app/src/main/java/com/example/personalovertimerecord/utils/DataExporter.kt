package com.example.personalovertimerecord.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.personalovertimerecord.data.Attendance
import com.example.personalovertimerecord.data.OvertimeSettings
import com.example.personalovertimerecord.data.db.AttendanceDao
import com.example.personalovertimerecord.data.db.AttendanceEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BackupData(
    val version: Int = 1,
    val exportTime: Long = System.currentTimeMillis(),
    val settings: OvertimeSettings,
    val attendanceRecords: List<AttendanceEntityBackup>
)

data class AttendanceEntityBackup(
    val date: String,
    val checkInTime: String? = null,
    val checkOutTime: String? = null,
    val checkInTimestamp: Long? = null,
    val checkOutTimestamp: Long? = null,
    val note: String? = null,
    val manualOvertimeHours: Double = -1.0,
    val manualExtraHours: Double = -1.0,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis()
)

class DataExporter(
    private val context: Context,
    private val attendanceDao: AttendanceDao
) {
    private val gson = Gson()
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    suspend fun exportData(
        uri: Uri,
        settings: OvertimeSettings,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                val records = attendanceDao.getAllRecordsSync()
                
                val backupRecords = records.map { entity ->
                    AttendanceEntityBackup(
                        date = entity.date,
                        checkInTime = entity.checkInTime,
                        checkOutTime = entity.checkOutTime,
                        checkInTimestamp = entity.checkInTimestamp,
                        checkOutTimestamp = entity.checkOutTimestamp,
                        note = entity.note,
                        manualOvertimeHours = entity.manualOvertimeHours,
                        manualExtraHours = entity.manualExtraHours,
                        createdAt = entity.createdAt,
                        modifiedAt = entity.modifiedAt ?: entity.createdAt
                    )
                }

                val backupData = BackupData(
                    settings = settings,
                    attendanceRecords = backupRecords
                )

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    OutputStreamWriter(outputStream).use { writer ->
                        gson.toJson(backupData, writer)
                    }
                }

                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e)
                }
            }
        }
    }

    suspend fun importData(
        uri: Uri,
        onSuccess: (BackupData) -> Unit,
        onError: (Exception) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        val type = object : TypeToken<BackupData>() {}.type
                        val backupData: BackupData = gson.fromJson(reader, type)
                        
                        withContext(Dispatchers.Main) {
                            onSuccess(backupData)
                        }
                    }
                } ?: throw Exception("无法打开文件")
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e)
                }
            }
        }
    }

    /**
     * 完全替换模式恢复数据
     */
    suspend fun restoreDataFull(backupData: BackupData) {
        withContext(Dispatchers.IO) {
            attendanceDao.deleteAll()
            
            for (record in backupData.attendanceRecords) {
                val entity = AttendanceEntity(
                    id = 0L,
                    date = record.date,
                    checkInTime = record.checkInTime,
                    checkOutTime = record.checkOutTime,
                    checkInTimestamp = record.checkInTimestamp,
                    checkOutTimestamp = record.checkOutTimestamp,
                    note = record.note,
                    manualOvertimeHours = record.manualOvertimeHours,
                    manualExtraHours = record.manualExtraHours,
                    createdAt = record.createdAt,
                    modifiedAt = record.modifiedAt
                )
                attendanceDao.insert(entity)
            }
        }
    }

    /**
     * 增量合并模式恢复数据
     * @param backupData 备份数据
     * @param conflictStrategy 冲突解决策略
     * @return 合并结果的摘要信息
     */
    suspend fun restoreDataIncremental(
        backupData: BackupData,
        conflictStrategy: ConflictStrategy
    ): SyncSummary = withContext(Dispatchers.IO) {
        var added = 0
        var updated = 0
        var skipped = 0

        // 获取本地现有记录
        val localRecords = attendanceDao.getAllRecordsSync()
        val localMap = localRecords.associateBy { it.date }

        for (record in backupData.attendanceRecords) {
            val localRecord = localMap[record.date]
            
            when {
                // 本地不存在，直接插入
                localRecord == null -> {
                    val entity = AttendanceEntity(
                        id = 0L,
                        date = record.date,
                        checkInTime = record.checkInTime,
                        checkOutTime = record.checkOutTime,
                        checkInTimestamp = record.checkInTimestamp,
                        checkOutTimestamp = record.checkOutTimestamp,
                        note = record.note,
                        manualOvertimeHours = record.manualOvertimeHours,
                        manualExtraHours = record.manualExtraHours,
                        createdAt = record.createdAt,
                        modifiedAt = record.modifiedAt
                    )
                    attendanceDao.insert(entity)
                    added++
                }
                
                // 存在冲突，根据策略处理
                else -> {
                    val shouldUpdate = when (conflictStrategy) {
                        ConflictStrategy.NEWER_WINS -> {
                            record.modifiedAt > (localRecord.modifiedAt ?: 0L)
                        }
                        ConflictStrategy.LOCAL_WINS -> false
                        ConflictStrategy.CLOUD_WINS -> true
                        ConflictStrategy.ASK_USER -> true // 默认使用云端
                    }
                    
                    if (shouldUpdate) {
                        val entity = AttendanceEntity(
                            id = localRecord.id,
                            date = record.date,
                            checkInTime = record.checkInTime,
                            checkOutTime = record.checkOutTime,
                            checkInTimestamp = record.checkInTimestamp,
                            checkOutTimestamp = record.checkOutTimestamp,
                            note = record.note,
                            manualOvertimeHours = record.manualOvertimeHours,
                            manualExtraHours = record.manualExtraHours,
                            createdAt = localRecord.createdAt,
                            modifiedAt = record.modifiedAt
                        )
                        attendanceDao.insertOrUpdate(entity)
                        updated++
                    } else {
                        skipped++
                    }
                }
            }
        }

        SyncSummary(
            totalRecords = backupData.attendanceRecords.size,
            added = added,
            updated = updated,
            skipped = skipped,
            conflicts = skipped
        )
    }

    /**
     * 上传时的增量同步
     * @param cloudRecords 云端记录
     * @param conflictStrategy 冲突解决策略
     * @return 需要上传的记录列表
     */
    suspend fun getRecordsToUpload(
        cloudRecords: List<AttendanceEntityBackup>,
        conflictStrategy: ConflictStrategy
    ): List<AttendanceEntityBackup> = withContext(Dispatchers.IO) {
        val localRecords = attendanceDao.getAllRecordsSync()
        val cloudMap = cloudRecords.associateBy { it.date }
        
        val recordsToUpload = mutableListOf<AttendanceEntityBackup>()

        for (localRecord in localRecords) {
            val cloudRecord = cloudMap[localRecord.date]
            
            when {
                // 云端不存在，需要上传
                cloudRecord == null -> {
                    recordsToUpload.add(localRecord.toBackup())
                }
                
                // 存在冲突，根据策略处理
                else -> {
                    val shouldUpload = when (conflictStrategy) {
                        ConflictStrategy.NEWER_WINS -> {
                            (localRecord.modifiedAt ?: 0L) > cloudRecord.modifiedAt
                        }
                        ConflictStrategy.LOCAL_WINS -> true
                        ConflictStrategy.CLOUD_WINS -> false
                        ConflictStrategy.ASK_USER -> true // 默认使用本地
                    }
                    
                    if (shouldUpload) {
                        recordsToUpload.add(localRecord.toBackup())
                    }
                }
            }
        }

        recordsToUpload
    }

    private fun AttendanceEntity.toBackup(): AttendanceEntityBackup {
        return AttendanceEntityBackup(
            date = this.date,
            checkInTime = this.checkInTime,
            checkOutTime = this.checkOutTime,
            checkInTimestamp = this.checkInTimestamp,
            checkOutTimestamp = this.checkOutTimestamp,
            note = this.note,
            manualOvertimeHours = this.manualOvertimeHours,
            manualExtraHours = this.manualExtraHours,
            createdAt = this.createdAt,
            modifiedAt = this.modifiedAt ?: this.createdAt
        )
    }

    fun createExportFileName(): String {
        val timestamp = dateFormat.format(Date())
        return "overtime_backup_$timestamp.json"
    }
}

/**
 * 同步结果摘要
 */
data class SyncSummary(
    val totalRecords: Int,
    val added: Int,
    val updated: Int,
    val skipped: Int,
    val conflicts: Int
) {
    fun toDisplayString(): String {
        val parts = mutableListOf<String>()
        if (added > 0) parts.add("新增 $added 条")
        if (updated > 0) parts.add("更新 $updated 条")
        if (skipped > 0) parts.add("跳过 $skipped 条")
        return parts.joinToString("，")
    }
}
