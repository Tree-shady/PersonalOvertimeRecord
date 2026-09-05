package com.example.personalovertimerecord.utils

/**
 * 双向同步「智能合并」的纯算法（无 IO / 无 Android 依赖），便于单元测试与多端复用。
 *
 * 语义约定：
 * - 输入记录视为已归一化的备份记录；时间比较口径与 DataExporter/SyncManager 的
 *   toBackup() 一致：modifiedAt 缺失(<=0)时用 createdAt 兜底；
 * - 以 date 为键合并；本地软删除墓碑（isDeleted=true）与活跃记录同样参与，
 *   保证删除操作能跨设备收敛；
 * - 云端记录按原顺序处理，云端/本地内部同日期重复时只保留第一条（防御脏数据）；
 * - 结果 = 「云端每条记录按策略裁决的胜者」+「本地独有记录（含墓碑）」，
 *   日期不重复、不含空日期记录。
 */
fun mergeAttendanceBackups(
    local: List<AttendanceEntityBackup>,
    cloud: List<AttendanceEntityBackup>,
    strategy: ConflictStrategy
): List<AttendanceEntityBackup> {
    val localByDate = HashMap<String, AttendanceEntityBackup>()
    for (l in local) {
        if (!l.date.isNullOrBlank() && l.date !in localByDate) {
            localByDate[l.date] = l
        }
    }

    val merged = ArrayList<AttendanceEntityBackup>(local.size + cloud.size)
    val handledDates = HashSet<String>()

    // 云端记录按原顺序处理：本地不存在 → 采用云端；都存在 → 按策略裁决
    for (c in cloud) {
        if (c.date.isNullOrBlank()) continue
        if (!handledDates.add(c.date)) continue
        val localRecord = localByDate[c.date]
        merged.add(if (localRecord == null) c else resolveConflict(localRecord, c, strategy))
    }

    // 本地独有记录（含软删除墓碑）追加，让删除操作也能同步到云端
    for (l in local) {
        if (l.date.isNullOrBlank()) continue
        if (handledDates.add(l.date)) {
            merged.add(l)
        }
    }
    return merged
}

/**
 * 冲突裁决（纯函数）：返回胜者记录。
 * NEWER_WINS 语义与旧实现一致：云端时间严格大于本地才取云端，相等/更旧取本地。
 */
fun resolveConflict(
    local: AttendanceEntityBackup,
    cloud: AttendanceEntityBackup,
    strategy: ConflictStrategy
): AttendanceEntityBackup = when (strategy) {
    ConflictStrategy.LOCAL_WINS -> local
    ConflictStrategy.CLOUD_WINS -> cloud
    ConflictStrategy.NEWER_WINS -> {
        val localTime = if (local.modifiedAt > 0) local.modifiedAt else local.createdAt
        val cloudTime = if (cloud.modifiedAt > 0) cloud.modifiedAt else cloud.createdAt
        if (cloudTime > localTime) cloud else local
    }
}
