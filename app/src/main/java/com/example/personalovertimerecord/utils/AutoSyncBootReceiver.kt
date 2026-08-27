package com.example.personalovertimerecord.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机广播接收器
 * 设备重启后恢复自动同步调度（AlarmManager 定时任务在重启后失效）
 */
class AutoSyncBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            AutoSyncManager.rescheduleAfterBoot(context)
        }
    }
}
