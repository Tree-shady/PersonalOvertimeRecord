package com.example.personalovertimerecord.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机广播接收器
 * 设备重启后恢复自动同步调度（AlarmManager 定时任务在重启后失效），
 * 并恢复上下班打卡提醒（同样基于一次性闹钟，重启后全部失效）。
 */
class AutoSyncBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            AutoSyncManager.rescheduleAfterBoot(context)

            // 恢复打卡提醒：重启后 ReminderManager 的闹钟全部失效，必须重新调度
            ReminderManager.init(context)
            if (ReminderManager.isWorkReminderEnabled()) {
                ReminderManager.scheduleWorkReminder(context)
            }
            if (ReminderManager.isOffWorkReminderEnabled()) {
                ReminderManager.scheduleOffWorkReminder(context)
            }
        }
    }
}
