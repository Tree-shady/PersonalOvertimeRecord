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
import com.example.personalovertimerecord.OvertimeApplication
import com.example.personalovertimerecord.R
import com.example.personalovertimerecord.data.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 自动同步管理器
 * 负责管理后台定时同步任务
 */
object AutoSyncManager {
    
    private const val CHANNEL_ID = "auto_sync_channel"
    private const val CHANNEL_NAME = "自动同步"
    private const val NOTIFICATION_ID = 1001
    private const val PREFS_NAME = "auto_sync_prefs"
    private const val KEY_SYNC_ENABLED = "sync_enabled"
    private const val KEY_SYNC_INTERVAL = "sync_interval"
    private const val KEY_LAST_SYNC_TIME = "last_sync_time"
    private const val KEY_AUTO_SYNC_WIFI_ONLY = "wifi_only"
    
    // 同步间隔选项（分钟）
    val SYNC_INTERVALS = mapOf(
        15 to "15分钟",
        30 to "30分钟",
        60 to "1小时",
        120 to "2小时",
        360 to "6小时",
        720 to "12小时",
        1440 to "24小时"
    )
    
    private lateinit var prefs: android.content.SharedPreferences
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    /**
     * 初始化自动同步管理器
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
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "用于显示自动同步状态"
                setShowBadge(false)
            }
            
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * 检查是否启用自动同步
     */
    fun isSyncEnabled(): Boolean {
        return prefs.getBoolean(KEY_SYNC_ENABLED, false)
    }
    
    /**
     * 设置是否启用自动同步
     */
    fun setSyncEnabled(context: Context, enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SYNC_ENABLED, enabled).apply()
        
        if (enabled) {
            scheduleSync(context)
        } else {
            cancelSync(context)
        }
    }
    
    /**
     * 获取同步间隔（分钟）
     */
    fun getSyncInterval(): Int {
        return prefs.getInt(KEY_SYNC_INTERVAL, 60)
    }
    
    /**
     * 设置同步间隔
     */
    fun setSyncInterval(context: Context, intervalMinutes: Int) {
        prefs.edit().putInt(KEY_SYNC_INTERVAL, intervalMinutes).apply()
        
        if (isSyncEnabled()) {
            // 重新调度同步任务
            scheduleSync(context)
        }
    }
    
    /**
     * 检查是否仅在WiFi下同步
     */
    fun isWifiOnly(): Boolean {
        return prefs.getBoolean(KEY_AUTO_SYNC_WIFI_ONLY, true)
    }
    
    /**
     * 设置是否仅在WiFi下同步
     */
    fun setWifiOnly(wifiOnly: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SYNC_WIFI_ONLY, wifiOnly).apply()
    }
    
    /**
     * 获取上次同步时间
     */
    fun getLastSyncTime(): Long {
        return prefs.getLong(KEY_LAST_SYNC_TIME, 0)
    }
    
    /**
     * 获取上次同步时间的可读字符串
     */
    fun getLastSyncTimeString(): String {
        val lastTime = getLastSyncTime()
        if (lastTime == 0L) return "从未同步"
        
        val diff = System.currentTimeMillis() - lastTime
        val minutes = diff / (1000 * 60)
        val hours = minutes / 60
        val days = hours / 24
        
        return when {
            minutes < 1 -> "刚刚"
            minutes < 60 -> "${minutes}分钟前"
            hours < 24 -> "${hours}小时前"
            else -> "${days}天前"
        }
    }
    
    /**
     * 调度自动同步任务
     */
    fun scheduleSync(context: Context) {
        if (!isSyncEnabled()) return
        
        val intervalMinutes = getSyncInterval()
        val intervalMillis = intervalMinutes * 60 * 1000L
        
        val intent = Intent(context, AutoSyncReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + intervalMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + intervalMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            // 如果没有精确闹钟权限，使用普通闹钟
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + intervalMillis,
                pendingIntent
            )
        }
    }
    
    /**
     * 取消自动同步任务
     */
    fun cancelSync(context: Context) {
        val intent = Intent(context, AutoSyncReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)
    }
    
    /**
     * 执行自动同步
     */
    fun performSync(context: Context, onComplete: ((Boolean, String) -> Unit)? = null) {
        scope.launch {
            try {
                val sm = SettingsManager(context)
                val webDAVConfig = sm.getWebDAVConfig()
                
                if (webDAVConfig == null) {
                    with(Dispatchers.Main) {
                        onComplete?.invoke(false, "未配置WebDAV")
                    }
                    return@launch
                }
                
                // 检查WiFi限制
                if (isWifiOnly() && !NetworkUtils.isWifiConnected(context)) {
                    with(Dispatchers.Main) {
                        onComplete?.invoke(false, "仅在WiFi下同步")
                    }
                    return@launch
                }
                
                // 执行同步
                val database = OvertimeApplication.getDatabase()
                val syncManager = SyncManager(context, sm, database.attendanceDao())
                
                val result = syncManager.performSync()
                
                // 更新最后同步时间
                prefs.edit().putLong(KEY_LAST_SYNC_TIME, System.currentTimeMillis()).apply()
                
                val syncSuccess = result == SyncResult.SUCCESS
                
                with(Dispatchers.Main) {
                    onComplete?.invoke(syncSuccess, if (syncSuccess) "同步成功" else "同步失败")
                }
                
                // 显示通知
                showSyncNotification(context, syncSuccess)
                
                // 重新调度下一次同步
                scheduleSync(context)
                
            } catch (e: Exception) {
                with(Dispatchers.Main) {
                    onComplete?.invoke(false, e.message ?: "同步异常")
                }
            }
        }
    }
    
    /**
     * 显示同步通知
     */
    private fun showSyncNotification(context: Context, success: Boolean) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("数据同步")
            .setContentText(if (success) "同步成功" else "同步失败，请检查网络")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}