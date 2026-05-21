package com.example.personalovertimerecord.utils

import android.content.Context
import com.example.personalovertimerecord.data.SettingsManager
import com.example.personalovertimerecord.data.db.AttendanceDao
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class SyncResult {
    SUCCESS,
    NO_CONFIG,
    CONNECTION_FAILED,
    UPLOAD_FAILED,
    DOWNLOAD_FAILED,
    RESTORE_FAILED,
    NO_CHANGES,
    CONFLICT
}

enum class SyncDirection {
    UPLOAD_ONLY,
    DOWNLOAD_ONLY,
    BIDIRECTIONAL
}

class SyncManager(
    private val context: Context,
    private val settingsManager: SettingsManager,
    private val attendanceDao: AttendanceDao
) {

    private val webDAVManager = WebDAVManager(context)
    private val dataExporter = DataExporter(context, attendanceDao)
    private val gson = Gson()

    suspend fun performSync(direction: SyncDirection = SyncDirection.BIDIRECTIONAL): SyncResult = withContext(Dispatchers.IO) {
        val config = settingsManager.getWebDAVConfig() ?: return@withContext SyncResult.NO_CONFIG

        // 测试连接
        val connectionOk = webDAVManager.testConnection(config)
        if (!connectionOk) {
            return@withContext SyncResult.CONNECTION_FAILED
        }

        when (direction) {
            SyncDirection.UPLOAD_ONLY -> uploadBackup(config)
            SyncDirection.DOWNLOAD_ONLY -> downloadAndRestore(config)
            SyncDirection.BIDIRECTIONAL -> performBidirectionalSync(config)
        }
    }

    private suspend fun uploadBackup(config: WebDAVConfig): SyncResult {
        return try {
            val settings = settingsManager.getSettings()
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
                    createdAt = entity.createdAt
                )
            }

            val backupData = BackupData(
                version = 1,
                exportTime = System.currentTimeMillis(),
                settings = settings,
                attendanceRecords = backupRecords
            )

            val content = gson.toJson(backupData)
            val success = webDAVManager.uploadFile(config, content)
            
            if (success) {
                settingsManager.saveLastSyncTime(System.currentTimeMillis())
                SyncResult.SUCCESS
            } else {
                SyncResult.UPLOAD_FAILED
            }
        } catch (e: Exception) {
            AppLogger.e("上传备份失败", e)
            SyncResult.UPLOAD_FAILED
        }
    }

    private suspend fun downloadAndRestore(config: WebDAVConfig): SyncResult {
        return try {
            val content = webDAVManager.downloadFile(config) ?: return SyncResult.DOWNLOAD_FAILED
            
            val type = object : com.google.gson.reflect.TypeToken<BackupData>() {}.type
            val backupData: BackupData = gson.fromJson(content, type)
            
            dataExporter.restoreData(backupData)
            settingsManager.saveSettings(backupData.settings)
            settingsManager.saveLastSyncTime(System.currentTimeMillis())
            
            SyncResult.SUCCESS
        } catch (e: Exception) {
            AppLogger.e("下载并恢复失败", e)
            SyncResult.RESTORE_FAILED
        }
    }

    private suspend fun performBidirectionalSync(config: WebDAVConfig): SyncResult {
        val lastSyncTime = settingsManager.getLastSyncTime()
        val remoteModifiedTime = webDAVManager.getFileModifiedTime(config)
        
        // 简单的冲突解决策略：比较时间戳
        return when {
            remoteModifiedTime == null -> {
                // 远程没有文件，直接上传
                uploadBackup(config)
            }
            lastSyncTime == 0L -> {
                // 本地没有同步过，直接下载
                downloadAndRestore(config)
            }
            remoteModifiedTime > lastSyncTime -> {
                // 远程更新，下载
                downloadAndRestore(config)
            }
            else -> {
                // 本地更新或相同，上传
                uploadBackup(config)
            }
        }
    }

    suspend fun testConnection(): Boolean {
        val config = settingsManager.getWebDAVConfig() ?: return false
        return webDAVManager.testConnection(config)
    }
}
