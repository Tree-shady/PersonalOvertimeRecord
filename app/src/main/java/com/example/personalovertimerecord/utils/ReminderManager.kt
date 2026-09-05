package com.example.personalovertimerecord.utils

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.personalovertimerecord.MainActivity
import com.example.personalovertimerecord.R

/**
 * 提醒管理器
 * 负责管理上下班打卡提醒
 */
object ReminderManager {
    
    private const val CHANNEL_ID = "reminder_channel"
    private const val CHANNEL_NAME = "打卡提醒"
    private const val PREFS_NAME = "reminder_prefs"
    private const val KEY_WORK_REMINDER_ENABLED = "work_reminder_enabled"
    private const val KEY_WORK_REMINDER_TIME = "work_reminder_time"
    private const val KEY_OFF_WORK_REMINDER_ENABLED = "off_work_reminder_enabled"
    private const val KEY_OFF_WORK_REMINDER_TIME = "off_work_reminder_time"
    private const val KEY_REMINDER_WORKDAYS_ONLY = "workdays_only"
    
    private const val WORK_REMINDER_ID = 2001
    private const val OFF_WORK_REMINDER_ID = 2002
    
    private lateinit var prefs: android.content.SharedPreferences
    
    /**
     * 初始化提醒管理器
     */
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        createNotificationChannel(context)
    }
    
    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "用于提醒上下班打卡"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }
            
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    // ==================== 上班提醒设置 ====================
    
    /**
     * 检查上班提醒是否启用
     */
    fun isWorkReminderEnabled(): Boolean {
        return prefs.getBoolean(KEY_WORK_REMINDER_ENABLED, false)
    }
    
    /**
     * 设置上班提醒是否启用
     */
    fun setWorkReminderEnabled(context: Context, enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WORK_REMINDER_ENABLED, enabled).apply()
        if (enabled) {
            scheduleWorkReminder(context)
        } else {
            cancelWorkReminder(context)
        }
    }
    
    /**
     * 获取上班提醒时间
     */
    fun getWorkReminderTime(): String {
        return prefs.getString(KEY_WORK_REMINDER_TIME, "08:30") ?: "08:30"
    }
    
    /**
     * 设置上班提醒时间
     */
    fun setWorkReminderTime(context: Context, time: String) {
        prefs.edit().putString(KEY_WORK_REMINDER_TIME, time).apply()
        if (isWorkReminderEnabled()) {
            scheduleWorkReminder(context)
        }
    }
    
    // ==================== 下班提醒设置 ====================
    
    /**
     * 检查下班提醒是否启用
     */
    fun isOffWorkReminderEnabled(): Boolean {
        return prefs.getBoolean(KEY_OFF_WORK_REMINDER_ENABLED, false)
    }
    
    /**
     * 设置下班提醒是否启用
     */
    fun setOffWorkReminderEnabled(context: Context, enabled: Boolean) {
        prefs.edit().putBoolean(KEY_OFF_WORK_REMINDER_ENABLED, enabled).apply()
        if (enabled) {
            scheduleOffWorkReminder(context)
        } else {
            cancelOffWorkReminder(context)
        }
    }
    
    /**
     * 获取下班提醒时间
     */
    fun getOffWorkReminderTime(): String {
        return prefs.getString(KEY_OFF_WORK_REMINDER_TIME, "17:30") ?: "17:30"
    }
    
    /**
     * 设置下班提醒时间
     */
    fun setOffWorkReminderTime(context: Context, time: String) {
        prefs.edit().putString(KEY_OFF_WORK_REMINDER_TIME, time).apply()
        if (isOffWorkReminderEnabled()) {
            scheduleOffWorkReminder(context)
        }
    }
    
    // ==================== 工作日设置 ====================
    
    /**
     * 检查是否仅在工作日提醒
     */
    fun isWorkdaysOnly(): Boolean {
        return prefs.getBoolean(KEY_REMINDER_WORKDAYS_ONLY, true)
    }
    
    /**
     * 设置是否仅在工作日提醒
     */
    fun setWorkdaysOnly(workdaysOnly: Boolean) {
        prefs.edit().putBoolean(KEY_REMINDER_WORKDAYS_ONLY, workdaysOnly).apply()
    }
    
    // ==================== 调度提醒 ====================
    
    /**
     * 调度上班提醒
     */
    fun scheduleWorkReminder(context: Context) {
        scheduleReminder(context, WORK_REMINDER_ID, getWorkReminderTime(), "上班打卡提醒", "该上班打卡了！")
    }
    
    /**
     * 取消上班提醒
     */
    fun cancelWorkReminder(context: Context) {
        cancelReminder(context, WORK_REMINDER_ID)
    }
    
    /**
     * 调度下班提醒
     */
    fun scheduleOffWorkReminder(context: Context) {
        scheduleReminder(context, OFF_WORK_REMINDER_ID, getOffWorkReminderTime(), "下班打卡提醒", "该下班打卡了！")
    }
    
    /**
     * 取消下班提醒
     */
    fun cancelOffWorkReminder(context: Context) {
        cancelReminder(context, OFF_WORK_REMINDER_ID)
    }
    
    /**
     * 调度提醒
     */
    private fun scheduleReminder(context: Context, requestCode: Int, time: String, title: String, message: String) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_REQUEST_CODE, requestCode)
            putExtra(ReminderReceiver.EXTRA_TITLE, title)
            putExtra(ReminderReceiver.EXTRA_MESSAGE, message)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        // 解析时间
        val parts = time.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 8
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        
        // 计算下次提醒时间
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            
            // 如果时间已过，设置为明天
            if (timeInMillis <= System.currentTimeMillis()) {
                add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
        }
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            // 如果没有精确闹钟权限，使用普通闹钟
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
    }
    
    /**
     * 取消提醒
     */
    private fun cancelReminder(context: Context, requestCode: Int) {
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)
    }
    
    /**
     * 显示提醒通知
     */
    fun showNotification(context: Context, title: String, message: String, notificationId: Int) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .build()
        
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.notify(notificationId, notification)
    }
}