package com.example.personalovertimerecord.utils

import java.text.SimpleDateFormat
import java.util.*

object DateUtils {

    // ThreadLocal 缓存 SimpleDateFormat，避免重复创建实例
    private val dateFormatThreadLocal = ThreadLocal<SimpleDateFormat>()
    private val chineseDateFormatThreadLocal = ThreadLocal<SimpleDateFormat>()
    private val timeFormatThreadLocal = ThreadLocal<SimpleDateFormat>()
    private val weekdayFormatThreadLocal = ThreadLocal<SimpleDateFormat>()

    private fun getDateFormat(): SimpleDateFormat {
        return dateFormatThreadLocal.get() ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).also {
            dateFormatThreadLocal.set(it)
        }
    }

    private fun getChineseDateFormat(): SimpleDateFormat {
        return chineseDateFormatThreadLocal.get() ?: SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault()).also {
            chineseDateFormatThreadLocal.set(it)
        }
    }

    private fun getTimeFormat(): SimpleDateFormat {
        return timeFormatThreadLocal.get() ?: SimpleDateFormat("HH:mm:ss", Locale.getDefault()).also {
            timeFormatThreadLocal.set(it)
        }
    }

    private fun getWeekdayFormat(): SimpleDateFormat {
        return weekdayFormatThreadLocal.get() ?: SimpleDateFormat("EEEE", Locale.CHINA).also {
            weekdayFormatThreadLocal.set(it)
        }
    }

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
        return getChineseDateFormat().format(date)
    }

    /**
     * 获取中文星期几
     */
    fun getChineseWeekday(date: Date): String {
        val weekday = getWeekdayFormat().format(date)
        return if (weekday.startsWith("星期")) weekday else "星期$weekday"
    }

    /**
     * 获取当前时间 HH:mm:ss
     */
    fun getCurrentTime(): String {
        return getTimeFormat().format(Date())
    }

    /**
     * 解析日期字符串为 Date 对象
     */
    fun parseDate(dateString: String): Date? {
        return try {
            getDateFormat().parse(dateString)
        } catch (e: Exception) {
            null
        }
    }
}