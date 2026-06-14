package com.example.personalovertimerecord.utils

/**
 * 同步方向枚举
 */
enum class SyncDirection {
    UPLOAD_ONLY,      // 仅上传到云端
    DOWNLOAD_ONLY,    // 仅从云端下载
    BIDIRECTIONAL     // 双向同步
}

/**
 * 预定义的同步配置
 */
object SyncPresets {
    val SMART_SYNC = SyncOptions(
        mode = SyncMode.INCREMENTAL_MERGE,
        conflictStrategy = ConflictStrategy.NEWER_WINS
    )
    
    val FULL_BACKUP = SyncOptions(
        mode = SyncMode.FULL_REPLACE,
        conflictStrategy = ConflictStrategy.CLOUD_WINS
    )
    
    val LOCAL_PRIORITY = SyncOptions(
        mode = SyncMode.LOCAL_PRIORITY,
        conflictStrategy = ConflictStrategy.LOCAL_WINS
    )
    
    val CLOUD_PRIORITY = SyncOptions(
        mode = SyncMode.CLOUD_PRIORITY,
        conflictStrategy = ConflictStrategy.CLOUD_WINS
    )
}
