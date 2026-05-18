package com.example.personalovertimerecord.utils

import java.text.SimpleDateFormat
import java.util.*

object DateUtils {
    
    /**
     * 将 yyyy-MM-dd 格式的日期字符串解析为 Calendar
     */
    fun parseDateString(dateString: String): Calendar? {
        val parts = dateString.split("-")
        if (parts.size != 3) return null
        
        val year = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val day = parts[2].toIntOrNull() ?: return null
        
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
    
    /**
     * 从日期字符串中提取年、月、日
     * 返回 Triple(year, month, day)
     */
    fun extractDateParts(dateString: String): Triple<Int, Int, Int>? {
        val calendar = parseDateString(dateString) ?: return null
        return Triple(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }
    
    /**
     * 创建日期字符串
     */
    fun formatDate(year: Int, month: Int, day: Int): String {
        return String.format(Locale.getDefault(), "%d-%02d-%02d", year, month + 1, day)
    }
    
    /**
     * 格式化日期为中文显示
     */
    fun formatToChineseDate(date: Date): String {
        val format = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault())
        return format.format(date)
    }
    
    /**
     * 获取中文星期几
     */
    fun getChineseWeekday(date: Date): String {
        val format = SimpleDateFormat("EEEE", Locale.CHINA)
        val weekday = format.format(date)
        return if (weekday.startsWith("星期")) weekday else "星期$weekday"
    }
    
    /**
     * 获取当前时间 HH:mm:ss
     */
    fun getCurrentTime(): String {
        val format = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return format.format(Date())
    }
}