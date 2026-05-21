package com.example.personalovertimerecord.utils

import android.content.Context
import com.example.personalovertimerecord.data.SettingsManager
import com.example.personalovertimerecord.data.db.AttendanceDao
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 同步结果枚举
 */
enum class SyncResult {
    SUCCESS,
    NO_CONFIG,
    NO_NETWORK,
    CONNECTION_FAILED,
    UPLOAD_FAILED,
    DOWNLOAD_FAILED,
    RESTORE_FAILED,
    NO_CHANGES,
    CONFLICT
}

/**
 * 同步方向枚举
 */
enum class SyncDirection {
    UPLOAD_ONLY,
    DOWNLOAD_ONLY,
    BIDIRECTIONAL
}

/**
 * 同步管理器
 * 负责 WebDAV 数据同步的核心逻辑
 */
class SyncManager(
    private val context: Context,
    private val settingsManager: SettingsManager,
    private val attendanceDao: AttendanceDao
) {

    companion object {
        private const val SYNC_DATA_VERSION = 1
    }

    private val webDAVManager = WebDAVManager(context)
    private val dataExporter = DataExporter(context, attendanceDao)
    private val gson = Gson()

    /**
     * 执行同步操作
     */
    suspend fun performSync(direction: SyncDirection = SyncDirection.BIDIRECTIONAL): SyncResult = withContext(Dispatchers.IO) {
        // 检查配置
        val config = settingsManager.getWebDAVConfig() ?: return@withContext SyncResult.NO_CONFIG
        
        // 检查网络
        if (!NetworkUtils.isNetworkAvailable(context)) {
            return@withContext SyncResult.NO_NETWORK
        }
        
        // 测试连接
        val connectionOk = webDAVManager.testConnection(config)
        if (!connectionOk) {
            return@withContext SyncResult.CONNECTION_FAILED
        }

        // 根据同步方向执行相应操作
        when (direction) {
            SyncDirection.UPLOAD_ONLY -> uploadBackup(config)
            SyncDirection.DOWNLOAD_ONLY -> downloadAndRestore(config)
            SyncDirection.BIDIRECTIONAL -> performBidirectionalSync(config)
        }
    }

    /**
     * 上传备份到 WebDAV
     */
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
                version = SYNC_DATA_VERSION,
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

    /**
     * 从 WebDAV 下载并恢复数据
     */
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

    /**
     * 执行双向同步
     */
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

    /**
     * 测试 WebDAV 连接
     */
    suspend fun testConnection(): Boolean {
        val config = settingsManager.getWebDAVConfig() ?: return false
        
        if (!NetworkUtils.isNetworkAvailable(context)) {
            return false
        }
        
        return webDAVManager.testConnection(config)
    }
}
