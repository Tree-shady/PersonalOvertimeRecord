package com.example.personalovertimerecord.utils

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.personalovertimerecord.OvertimeApplication
import com.example.personalovertimerecord.data.SettingsManager

/**
 * 自动同步 Worker（由 AutoSyncManager 以周期任务方式调度）。
 *
 * 说明：
 * - 网络条件由 WorkManager 的 Constraints 保证（WiFi-only 使用 UNMETERED）；
 * - 未启用自动同步或未配置 WebDAV 时静默结束（周期任务保留，配置好即自动生效）；
 * - 瞬时失败返回 retry，由 WorkManager 按退避策略稍后重试；
 * - 周期任务持久化，重启后自动恢复，无需 BOOT_COMPLETED 广播；
 * - 仅在失败时发一条低优先级通知（成功不再打扰用户）。
 */
class AutoSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        AutoSyncManager.init(context)

        if (!AutoSyncManager.isSyncEnabled()) return Result.success()

        val settingsManager = SettingsManager(context)
        if (settingsManager.getWebDAVConfig() == null) return Result.success()

        return try {
            val database = OvertimeApplication.getDatabase()
            val syncManager = SyncManager(context, settingsManager, database.attendanceDao())
            when (syncManager.performSync()) {
                SyncResult.SUCCESS,
                SyncResult.NO_CHANGES -> Result.success()
                else -> {
                    AutoSyncManager.notifySyncFailure(context)
                    Result.retry()
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "自动同步异常", e)
            AutoSyncManager.notifySyncFailure(context)
            Result.retry()
        }
    }

    private companion object {
        const val TAG = "AutoSyncWorker"
    }
}
