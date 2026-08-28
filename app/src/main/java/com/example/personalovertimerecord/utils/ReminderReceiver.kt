package com.example.personalovertimerecord.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar

/**
 * 提醒广播接收器
 * 用于接收定时器触发并显示打卡提醒
 */
class ReminderReceiver : BroadcastReceiver() {
    
    companion object {
        const val EXTRA_REQUEST_CODE = "request_code"
        const val EXTRA_TITLE = "title"
        const val EXTRA_MESSAGE = "message"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        // 初始化提醒管理器
        ReminderManager.init(context)
        
        val requestCode = intent.getIntExtra(EXTRA_REQUEST_CODE, 0)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "打卡提醒"
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "该打卡了！"
        
        // 检查是否仅在工作日提醒
        if (ReminderManager.isWorkdaysOnly()) {
            val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
            // 周六和周日不提醒
            if (today == Calendar.SATURDAY || today == Calendar.SUNDAY) {
                // 重新调度明天的提醒
                when (requestCode) {
                    2001 -> ReminderManager.scheduleWorkReminder(context)
                    2002 -> ReminderManager.scheduleOffWorkReminder(context)
                    else -> {
                        ReminderManager.scheduleWorkReminder(context)
                        ReminderManager.scheduleOffWorkReminder(context)
                    }
                }
                return
            }
        }
        
        // 显示通知
        ReminderManager.showNotification(context, title, message, requestCode)
        
        // 重新调度下一天的提醒（缺省分支兜底：即使 extras 被系统剥离也要保持提醒链不断）
        when (requestCode) {
            2001 -> ReminderManager.scheduleWorkReminder(context)
            2002 -> ReminderManager.scheduleOffWorkReminder(context)
            else -> {
                ReminderManager.scheduleWorkReminder(context)
                ReminderManager.scheduleOffWorkReminder(context)
            }
        }
    }
}