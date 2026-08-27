package com.example.personalovertimerecord.utils

/**
 * 同步模式枚举
 */
enum class SyncMode {
    FULL_REPLACE,           // 完全替换：本地数据全部替换为云端数据（或反之）
    INCREMENTAL_MERGE,      // 增量合并：只同步新增和修改的记录
    LOCAL_PRIORITY,        // 本地优先：云端只用于备份和恢复
    CLOUD_PRIORITY         // 云端优先：本地数据只作为缓存
}

/**
 * 冲突解决策略枚举
 */
enum class ConflictStrategy {
    NEWER_WINS,            // 较新者获胜：根据时间戳自动选择
    LOCAL_WINS,            // 本地优先：保留本地数据
    CLOUD_WINS             // 云端优先：使用云端数据
}

/**
 * 同步选项
 */
data class SyncOptions(
    val mode: SyncMode = SyncMode.INCREMENTAL_MERGE,
    val conflictStrategy: ConflictStrategy = ConflictStrategy.NEWER_WINS,
    val syncSettings: Boolean = true,      // 是否同步设置
    val syncAttendance: Boolean = true,    // 是否同步考勤记录
    val autoBackup: Boolean = false,       // 是否自动备份
    val backupOnWifiOnly: Boolean = true  // 仅在WiFi下自动备份
)
