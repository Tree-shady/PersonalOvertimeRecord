package com.example.personalovertimerecord.data

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
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
            apply()
        }
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
            dailyWorkHours = prefs.getString(KEY_DAILY_WORK_HOURS, "8.0")?.toDoubleOrNull() ?: 8.0
        )
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
    }
}
