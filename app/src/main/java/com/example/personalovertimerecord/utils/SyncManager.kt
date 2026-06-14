package com.example.personalovertimerecord.utils

import android.content.Context
import com.example.personalovertimerecord.data.SettingsManager
import com.example.personalovertimerecord.data.db.AttendanceDao
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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
 * 同步管理器
 * 负责 WebDAV 数据同步的核心逻辑，支持增量同步和多种冲突解决策略
 */
class SyncManager(
    private val context: Context,
    private val settingsManager: SettingsManager,
    private val attendanceDao: AttendanceDao
) {

    companion object {
        private const val SYNC_DATA_VERSION = 2
    }

    private val webDAVManager = WebDAVManager(context)
    private val dataExporter = DataExporter(context, attendanceDao)
    private val gson = Gson()

    /**
     * 执行同步操作
     */
    suspend fun performSync(
        direction: SyncDirection = SyncDirection.BIDIRECTIONAL,
        options: SyncOptions = SyncOptions()
    ): SyncResult = withContext(Dispatchers.IO) {
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
            SyncDirection.UPLOAD_ONLY -> uploadBackup(config, options)
            SyncDirection.DOWNLOAD_ONLY -> downloadAndRestore(config, options)
            SyncDirection.BIDIRECTIONAL -> performBidirectionalSync(config, options)
        }
    }

    /**
     * 上传备份到 WebDAV
     */
    private suspend fun uploadBackup(config: WebDAVConfig, options: SyncOptions): SyncResult {
        return try {
            // 获取加密设置
            val settings = settingsManager.getSettings()
            val encryptPassword = if (settings.syncEncryptionEnabled && settings.syncPassword.isNotBlank()) {
                settings.syncPassword
            } else {
                null
            }
            
            // 检查云端是否有现有数据
            val cloudContent = webDAVManager.downloadFile(config, encryptPassword)
            
            var recordsToUpload: List<AttendanceEntityBackup>
            var cloudRecords: List<AttendanceEntityBackup> = emptyList()
            
            // 如果云端有数据，进行增量对比
            if (cloudContent != null) {
                try {
                    val type = object : TypeToken<BackupData>() {}.type
                    val cloudBackup: BackupData = gson.fromJson(cloudContent, type)
                    cloudRecords = cloudBackup.attendanceRecords
                } catch (e: Exception) {
                    AppLogger.d("解析云端数据失败，将进行全量上传: ${e.message}")
                }
            }
            
            // 根据策略获取需要上传的记录
            if (cloudRecords.isNotEmpty() && options.mode == SyncMode.INCREMENTAL_MERGE) {
                // 增量模式：只上传有变化的记录
                recordsToUpload = dataExporter.getRecordsToUpload(cloudRecords, options.conflictStrategy)
                
                if (recordsToUpload.isEmpty()) {
                    AppLogger.d("没有需要上传的记录")
                    settingsManager.saveLastSyncTime(System.currentTimeMillis())
                    return SyncResult.NO_CHANGES
                }
            } else {
                // 全量模式：上传所有本地记录
                val localRecords = attendanceDao.getAllRecordsSync()
                recordsToUpload = localRecords.map { entity ->
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
            }
            
            val syncSettings = if (options.syncSettings) {
                settings
            } else {
                null
            }
            
            val backupData = BackupData(
                version = SYNC_DATA_VERSION,
                exportTime = System.currentTimeMillis(),
                settings = syncSettings ?: settings,
                attendanceRecords = recordsToUpload
            )

            val content = gson.toJson(backupData)
            val success = webDAVManager.uploadFile(config, content, encryptPassword)
            
            if (success) {
                settingsManager.saveLastSyncTime(System.currentTimeMillis())
                AppLogger.d("上传成功，共 ${recordsToUpload.size} 条记录" + if (encryptPassword != null) " (已加密)" else " (未加密)")
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
     * 兼容处理：如果启用了加密但下载的是旧版本未加密数据，自动尝试不使用密码
     */
    private suspend fun downloadAndRestore(config: WebDAVConfig, options: SyncOptions): SyncResult {
        return try {
            // 获取加密设置
            val settings = settingsManager.getSettings()
            val decryptPassword = if (settings.syncEncryptionEnabled && settings.syncPassword.isNotBlank()) {
                settings.syncPassword
            } else {
                null
            }
            
            // 首先尝试用密码下载并解密
            var content = webDAVManager.downloadFile(config, decryptPassword)
            
            // 如果解密失败（可能云端是旧版本未加密数据），尝试不用密码下载
            if (content == null && decryptPassword != null) {
                AppLogger.d("使用密码下载失败，尝试不使用密码下载（旧版本兼容）")
                content = webDAVManager.downloadFile(config, null)
            }
            
            if (content == null) {
                return SyncResult.DOWNLOAD_FAILED
            }
            
            val type = object : TypeToken<BackupData>() {}.type
            val backupData: BackupData = gson.fromJson(content, type)
            
            when (options.mode) {
                SyncMode.FULL_REPLACE, SyncMode.CLOUD_PRIORITY -> {
                    // 全量替换模式
                    dataExporter.restoreDataFull(backupData)
                }
                SyncMode.INCREMENTAL_MERGE, SyncMode.LOCAL_PRIORITY -> {
                    // 增量合并模式
                    dataExporter.restoreDataIncremental(backupData, options.conflictStrategy)
                }
            }
            
            if (options.syncSettings) {
                settingsManager.saveSettings(backupData.settings)
            }
            
            settingsManager.saveLastSyncTime(System.currentTimeMillis())
            AppLogger.d("下载恢复成功，共 ${backupData.attendanceRecords.size} 条记录" + if (decryptPassword != null && content != null) " (已解密)" else " (未加密)")
            SyncResult.SUCCESS
        } catch (e: Exception) {
            AppLogger.e("下载并恢复失败", e)
            SyncResult.RESTORE_FAILED
        }
    }

    /**
     * 执行双向同步
     * 策略：先获取云端数据，然后进行智能合并
     */
    private suspend fun performBidirectionalSync(config: WebDAVConfig, options: SyncOptions): SyncResult {
        // 获取加密设置
        val settings = settingsManager.getSettings()
        val encryptPassword = if (settings.syncEncryptionEnabled && settings.syncPassword.isNotBlank()) {
            settings.syncPassword
        } else {
            null
        }
        
        val lastSyncTime = settingsManager.getLastSyncTime()
        val remoteModifiedTime = webDAVManager.getFileModifiedTime(config)
        
        AppLogger.d("双向同步开始 - 本地最后同步时间: $lastSyncTime, 云端修改时间: $remoteModifiedTime")
        
        // 获取云端数据
        var cloudContent = webDAVManager.downloadFile(config, encryptPassword)
        
        // 如果解密失败，尝试不使用密码下载（兼容旧版本）
        if (cloudContent == null && encryptPassword != null) {
            AppLogger.d("使用密码下载失败，尝试不使用密码下载（旧版本兼容）")
            cloudContent = webDAVManager.downloadFile(config, null)
        }
        
        return when {
            // 情况1：云端没有数据，直接上传本地数据
            cloudContent == null -> {
                AppLogger.d("云端无数据，执行上传")
                uploadBackup(config, options)
            }
            
            // 情况2：本地从未同步过，下载云端数据
            lastSyncTime == 0L -> {
                AppLogger.d("本地从未同步过，执行下载")
                downloadAndRestore(config, options)
            }
            
            // 情况3：云端在本地上次同步之后没有更新，直接上传
            remoteModifiedTime != null && remoteModifiedTime <= lastSyncTime -> {
                AppLogger.d("云端没有更新，执行上传")
                uploadBackup(config, options)
            }
            
            // 情况4：云端在本地上次同步之后有更新，需要合并
            else -> {
                AppLogger.d("云端有更新，执行智能合并")
                performSmartMerge(config, options)
            }
        }
    }
    
    /**
     * 执行智能合并
     * 1. 下载云端数据
     * 2. 与本地数据按策略合并
     * 3. 上传合并结果
     */
    private suspend fun performSmartMerge(config: WebDAVConfig, options: SyncOptions): SyncResult {
        return try {
            // 获取加密设置
            val settings = settingsManager.getSettings()
            val encryptPassword = if (settings.syncEncryptionEnabled && settings.syncPassword.isNotBlank()) {
                settings.syncPassword
            } else {
                null
            }
            
            // 下载云端数据
            var cloudContent = webDAVManager.downloadFile(config, encryptPassword)
            
            // 如果解密失败，尝试不使用密码下载（兼容旧版本）
            if (cloudContent == null && encryptPassword != null) {
                AppLogger.d("使用密码下载失败，尝试不使用密码下载（旧版本兼容）")
                cloudContent = webDAVManager.downloadFile(config, null)
            }
            
            if (cloudContent == null) {
                return SyncResult.DOWNLOAD_FAILED
            }
            
            val type = object : TypeToken<BackupData>() {}.type
            val cloudBackup: BackupData = gson.fromJson(cloudContent, type)
            
            // 获取本地记录
            val localRecords = attendanceDao.getAllRecordsSync()
            val localMap = localRecords.associateBy { it.date }
            
            // 合并结果
            val mergedRecords = mutableListOf<AttendanceEntityBackup>()
            
            // 处理云端记录
            for (cloudRecord in cloudBackup.attendanceRecords) {
                val localRecord = localMap[cloudRecord.date]
                
                when {
                    // 本地不存在，使用云端
                    localRecord == null -> {
                        mergedRecords.add(cloudRecord)
                    }
                    
                    // 都存在，根据策略决定
                    else -> {
                        val winner = when (options.conflictStrategy) {
                            ConflictStrategy.NEWER_WINS -> {
                                val cloudTime = cloudRecord.modifiedAt
                                val localTime = localRecord.modifiedAt ?: 0L
                                if (cloudTime > localTime) cloudRecord else localRecord.toBackup()
                            }
                            ConflictStrategy.LOCAL_WINS -> localRecord.toBackup()
                            ConflictStrategy.CLOUD_WINS -> cloudRecord
                            ConflictStrategy.ASK_USER -> cloudRecord // 默认云端
                        }
                        mergedRecords.add(winner)
                    }
                }
            }
            
            // 处理本地独有的记录（云端没有的）
            val cloudDates = cloudBackup.attendanceRecords.map { it.date }.toSet()
            for (localRecord in localRecords) {
                if (localRecord.date !in cloudDates) {
                    mergedRecords.add(localRecord.toBackup())
                }
            }
            
            // 上传合并结果（使用加密）
            val mergedBackup = BackupData(
                version = SYNC_DATA_VERSION,
                exportTime = System.currentTimeMillis(),
                settings = cloudBackup.settings,
                attendanceRecords = mergedRecords
            )
            
            val content = gson.toJson(mergedBackup)
            val success = webDAVManager.uploadFile(config, content, encryptPassword)
            
            if (success) {
                // 如果设置了本地优先，重新应用本地数据到数据库
                if (options.conflictStrategy == ConflictStrategy.LOCAL_WINS || 
                    options.mode == SyncMode.LOCAL_PRIORITY) {
                    dataExporter.restoreDataIncremental(cloudBackup, ConflictStrategy.LOCAL_WINS)
                }
                
                settingsManager.saveLastSyncTime(System.currentTimeMillis())
                AppLogger.d("智能合并成功，共 ${mergedRecords.size} 条记录" + if (encryptPassword != null) " (已加密)" else " (未加密)")
                SyncResult.SUCCESS
            } else {
                SyncResult.UPLOAD_FAILED
            }
        } catch (e: Exception) {
            AppLogger.e("智能合并失败", e)
            SyncResult.UPLOAD_FAILED
        }
    }
    
    private fun com.example.personalovertimerecord.data.db.AttendanceEntity.toBackup(): AttendanceEntityBackup {
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
