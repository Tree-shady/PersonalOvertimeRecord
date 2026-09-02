package com.example.personalovertimerecord.data

import android.content.Context
import android.content.SharedPreferences
import com.example.personalovertimerecord.utils.SecurePreferencesManager
import com.example.personalovertimerecord.utils.WebDAVConfig

class SettingsManager(context: Context) {
    
    private val prefs: SharedPreferences = SecurePreferencesManager.getEncryptedPrefs(context)
    
    fun saveSettings(settings: OvertimeSettings) {
        prefs.edit().apply {
            putString(KEY_WORK_START, settings.workStartTime)
            putString(KEY_WORK_END, settings.workEndTime)
            putString(KEY_OVERTIME_RATE_NORMAL, settings.overtimeRateNormal.toString())
            putString(KEY_OVERTIME_RATE_WEEKEND, settings.overtimeRateWeekend.toString())
            putString(KEY_OVERTIME_RATE_HOLIDAY, settings.overtimeRateHoliday.toString())
            putString(KEY_BASE_SALARY, settings.baseSalary.toString())
            putString(KEY_PERFORMANCE_PERCENT, settings.performancePercent.toString())
            putString(KEY_MONTHLY_WORK_DAYS, settings.monthlyWorkDays.toString())
            putString(KEY_DAILY_WORK_HOURS, settings.dailyWorkHours.toString())
            // 保存加密设置
            putBoolean(KEY_EXPORT_ENCRYPTION_ENABLED, settings.exportEncryptionEnabled)
            putString(KEY_EXPORT_PASSWORD, settings.exportPassword)
            putBoolean(KEY_SYNC_ENCRYPTION_ENABLED, settings.syncEncryptionEnabled)
            putString(KEY_SYNC_PASSWORD, settings.syncPassword)
            apply()
        }
    }
    
    /**
     * 保存来自备份文件/云端同步的设置。
     * 加密密码不随备份传输（OvertimeSettings 中为 transient），
     * 恢复时保留本机已保存的密码，避免密码被清空后下次同步变为明文上传。
     */
    fun saveSyncedSettings(settings: OvertimeSettings) {
        val current = getSettings()
        saveSettings(
            settings.copy(
                exportPassword = current.exportPassword,
                syncPassword = current.syncPassword
            )
        )
    }

    fun getSettings(): OvertimeSettings {
        return OvertimeSettings(
            workStartTime = prefs.getString(KEY_WORK_START, "08:00") ?: "08:00",
            workEndTime = prefs.getString(KEY_WORK_END, "17:00") ?: "17:00",
            overtimeRateNormal = prefs.getString(KEY_OVERTIME_RATE_NORMAL, "1.5")?.toDoubleOrNull() ?: 1.5,
            overtimeRateWeekend = prefs.getString(KEY_OVERTIME_RATE_WEEKEND, "2.0")?.toDoubleOrNull() ?: 2.0,
            overtimeRateHoliday = prefs.getString(KEY_OVERTIME_RATE_HOLIDAY, "3.0")?.toDoubleOrNull() ?: 3.0,
            baseSalary = prefs.getString(KEY_BASE_SALARY, "5000.0")?.toDoubleOrNull() ?: 5000.0,
            performancePercent = prefs.getString(KEY_PERFORMANCE_PERCENT, "0.0")?.toDoubleOrNull() ?: 0.0,
            monthlyWorkDays = prefs.getString(KEY_MONTHLY_WORK_DAYS, "21.75")?.toDoubleOrNull() ?: 21.75,
            dailyWorkHours = prefs.getString(KEY_DAILY_WORK_HOURS, "8.0")?.toDoubleOrNull() ?: 8.0,
            // 读取加密设置
            exportEncryptionEnabled = prefs.getBoolean(KEY_EXPORT_ENCRYPTION_ENABLED, false),
            exportPassword = prefs.getString(KEY_EXPORT_PASSWORD, "") ?: "",
            syncEncryptionEnabled = prefs.getBoolean(KEY_SYNC_ENCRYPTION_ENABLED, false),
            syncPassword = prefs.getString(KEY_SYNC_PASSWORD, "") ?: ""
        )
    }

    fun saveWebDAVConfig(config: WebDAVConfig) {
        prefs.edit().apply {
            putString(KEY_WEBDAV_SERVER_URL, config.serverUrl)
            putString(KEY_WEBDAV_USERNAME, config.username)
            putString(KEY_WEBDAV_PASSWORD, config.password)
            putString(KEY_WEBDAV_REMOTE_PATH, config.remotePath)
            apply()
        }
    }

    fun getWebDAVConfig(): WebDAVConfig? {
        val serverUrl = prefs.getString(KEY_WEBDAV_SERVER_URL, null) ?: return null
        val username = prefs.getString(KEY_WEBDAV_USERNAME, null) ?: return null
        val password = prefs.getString(KEY_WEBDAV_PASSWORD, null) ?: return null
        val remotePath = prefs.getString(KEY_WEBDAV_REMOTE_PATH, "/overtime_record/") ?: "/overtime_record/"
        
        return WebDAVConfig(
            serverUrl = serverUrl,
            username = username,
            password = password,
            remotePath = remotePath
        )
    }

    fun saveLastSyncTime(time: Long) {
        prefs.edit().putLong(KEY_LAST_SYNC_TIME, time).apply()
    }

    fun getLastSyncTime(): Long {
        return prefs.getLong(KEY_LAST_SYNC_TIME, 0L)
    }
    
    companion object {
        private const val PREFS_NAME = "overtime_settings"
        private const val KEY_WORK_START = "work_start"
        private const val KEY_WORK_END = "work_end"
        private const val KEY_OVERTIME_RATE_NORMAL = "overtime_rate_normal"
        private const val KEY_OVERTIME_RATE_WEEKEND = "overtime_rate_weekend"
        private const val KEY_OVERTIME_RATE_HOLIDAY = "overtime_rate_holiday"
        private const val KEY_BASE_SALARY = "base_salary"
        private const val KEY_PERFORMANCE_PERCENT = "performance_percent"
        private const val KEY_MONTHLY_WORK_DAYS = "monthly_work_days"
        private const val KEY_DAILY_WORK_HOURS = "daily_work_hours"
        private const val KEY_WEBDAV_SERVER_URL = "webdav_server_url"
        private const val KEY_WEBDAV_USERNAME = "webdav_username"
        private const val KEY_WEBDAV_PASSWORD = "webdav_password"
        private const val KEY_WEBDAV_REMOTE_PATH = "webdav_remote_path"
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"
        // 加密设置键
        private const val KEY_EXPORT_ENCRYPTION_ENABLED = "export_encryption_enabled"
        private const val KEY_EXPORT_PASSWORD = "export_password"
        private const val KEY_SYNC_ENCRYPTION_ENABLED = "sync_encryption_enabled"
        private const val KEY_SYNC_PASSWORD = "sync_password"
    }
}
