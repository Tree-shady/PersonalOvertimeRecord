package com.example.personalovertimerecord.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Formatter {
    
    fun formatHours(hours: Double): String {
        val fullHours = hours.toInt()
        val minutes = ((hours - fullHours) * 60).toInt()
        return if (minutes > 0) {
            "${fullHours}h${minutes}m"
        } else {
            "${fullHours}h"
        }
    }
    
    fun formatMoney(amount: Double): String {
        return String.format(Locale.getDefault(), "¥%.2f", amount)
    }
    
    fun formatHoursShort(hours: Double): String {
        return String.format(Locale.getDefault(), "%.1fh", hours)
    }
    
    fun formatDate(dateStr: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MM月dd日 EEEE", Locale.CHINA)
            val date = inputFormat.parse(dateStr)
            val formatted = outputFormat.format(date ?: Date())
            if (formatted.startsWith("星期")) formatted else "星期$formatted"
        } catch (e: Exception) {
            dateStr
        }
    }
    
    fun getDayTypeString(dateStr: String): String {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = format.parse(dateStr) ?: return "工作日"
            val calendar = Calendar.getInstance()
            calendar.time = date
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            val monthDay = dateStr.substring(5)
            
            val holidays = setOf(
                "01-01", "01-02", "01-03",
                "02-10", "02-11", "02-12", "02-13", "02-14", "02-15", "02-16", "02-17",
                "04-04", "04-05", "04-06",
                "05-01", "05-02", "05-03", "05-04", "05-05",
                "06-10",
                "09-15", "09-16", "09-17",
                "10-01", "10-02", "10-03", "10-04", "10-05", "10-06", "10-07"
            )
            
            when {
                holidays.contains(monthDay) -> "法定假日"
                dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY -> "周末"
                else -> "工作日"
            }
        } catch (e: Exception) {
            "工作日"
        }
    }
}
