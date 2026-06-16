package com.example.personalovertimerecord.utils

import com.example.personalovertimerecord.data.DayType
import java.util.Calendar
import java.util.Locale

/**
 * 节假日管理器
 * 用于识别工作日、周末和法定节假日
 */
object HolidayManager {
    
    // 2024年法定节假日
    private val holidays2024 = mapOf(
        "2024-01-01" to "元旦",
        "2024-02-10" to "春节",
        "2024-02-11" to "春节",
        "2024-02-12" to "春节",
        "2024-02-13" to "春节",
        "2024-02-14" to "春节",
        "2024-02-15" to "春节",
        "2024-02-16" to "春节",
        "2024-02-17" to "春节",
        "2024-04-04" to "清明节",
        "2024-04-05" to "清明节",
        "2024-04-06" to "清明节",
        "2024-05-01" to "劳动节",
        "2024-05-02" to "劳动节",
        "2024-05-03" to "劳动节",
        "2024-05-04" to "劳动节",
        "2024-05-05" to "劳动节",
        "2024-06-10" to "端午节",
        "2024-09-15" to "中秋节",
        "2024-09-16" to "中秋节",
        "2024-09-17" to "中秋节",
        "2024-10-01" to "国庆节",
        "2024-10-02" to "国庆节",
        "2024-10-03" to "国庆节",
        "2024-10-04" to "国庆节",
        "2024-10-05" to "国庆节",
        "2024-10-06" to "国庆节",
        "2024-10-07" to "国庆节"
    )
    
    // 2025年法定节假日
    private val holidays2025 = mapOf(
        "2025-01-01" to "元旦",
        "2025-01-28" to "春节",
        "2025-01-29" to "春节",
        "2025-01-30" to "春节",
        "2025-01-31" to "春节",
        "2025-02-01" to "春节",
        "2025-02-02" to "春节",
        "2025-02-03" to "春节",
        "2025-02-04" to "春节",
        "2025-04-04" to "清明节",
        "2025-04-05" to "清明节",
        "2025-04-06" to "清明节",
        "2025-05-01" to "劳动节",
        "2025-05-02" to "劳动节",
        "2025-05-03" to "劳动节",
        "2025-05-04" to "劳动节",
        "2025-05-05" to "劳动节",
        "2025-05-31" to "端午节",
        "2025-06-01" to "端午节",
        "2025-06-02" to "端午节",
        "2025-09-28" to "中秋节",
        "2025-09-29" to "中秋节",
        "2025-09-30" to "中秋节",
        "2025-10-01" to "国庆节",
        "2025-10-02" to "国庆节",
        "2025-10-03" to "国庆节",
        "2025-10-04" to "国庆节",
        "2025-10-05" to "国庆节",
        "2025-10-06" to "国庆节",
        "2025-10-07" to "国庆节"
    )
    
    // 2026年法定节假日
    private val holidays2026 = mapOf(
        "2026-01-01" to "元旦",
        "2026-01-26" to "春节",
        "2026-01-27" to "春节",
        "2026-01-28" to "春节",
        "2026-01-29" to "春节",
        "2026-01-30" to "春节",
        "2026-01-31" to "春节",
        "2026-02-01" to "春节",
        "2026-02-02" to "春节",
        "2026-04-03" to "清明节",
        "2026-04-04" to "清明节",
        "2026-04-05" to "清明节",
        "2026-04-06" to "清明节",
        "2026-05-01" to "劳动节",
        "2026-05-02" to "劳动节",
        "2026-05-03" to "劳动节",
        "2026-05-04" to "劳动节",
        "2026-05-05" to "劳动节",
        "2026-06-20" to "端午节",
        "2026-06-21" to "端午节",
        "2026-06-22" to "端午节",
        "2026-09-24" to "中秋节",
        "2026-09-25" to "中秋节",
        "2026-09-26" to "中秋节",
        "2026-10-01" to "国庆节",
        "2026-10-02" to "国庆节",
        "2026-10-03" to "国庆节",
        "2026-10-04" to "国庆节",
        "2026-10-05" to "国庆节",
        "2026-10-06" to "国庆节",
        "2026-10-07" to "国庆节",
        "2026-10-08" to "国庆节"
    )
    
    // 2027年法定节假日（预测）
    private val holidays2027 = mapOf(
        "2027-01-01" to "元旦",
        "2027-02-15" to "春节",
        "2027-02-16" to "春节",
        "2027-02-17" to "春节",
        "2027-02-18" to "春节",
        "2027-02-19" to "春节",
        "2027-02-20" to "春节",
        "2027-02-21" to "春节",
        "2027-02-22" to "春节",
        "2027-04-03" to "清明节",
        "2027-04-04" to "清明节",
        "2027-04-05" to "清明节",
        "2027-04-06" to "清明节",
        "2027-05-01" to "劳动节",
        "2027-05-02" to "劳动节",
        "2027-05-03" to "劳动节",
        "2027-05-04" to "劳动节",
        "2027-05-05" to "劳动节",
        "2027-06-10" to "端午节",
        "2027-06-11" to "端午节",
        "2027-06-12" to "端午节",
        "2027-09-15" to "中秋节",
        "2027-09-16" to "中秋节",
        "2027-09-17" to "中秋节",
        "2027-10-01" to "国庆节",
        "2027-10-02" to "国庆节",
        "2027-10-03" to "国庆节",
        "2027-10-04" to "国庆节",
        "2027-10-05" to "国庆节",
        "2027-10-06" to "国庆节",
        "2027-10-07" to "国庆节"
    )
    
    // 2028年法定节假日（预测）
    private val holidays2028 = mapOf(
        "2028-01-01" to "元旦",
        "2028-02-04" to "春节",
        "2028-02-05" to "春节",
        "2028-02-06" to "春节",
        "2028-02-07" to "春节",
        "2028-02-08" to "春节",
        "2028-02-09" to "春节",
        "2028-02-10" to "春节",
        "2028-02-11" to "春节",
        "2028-04-04" to "清明节",
        "2028-04-05" to "清明节",
        "2028-04-06" to "清明节",
        "2028-04-07" to "清明节",
        "2028-05-01" to "劳动节",
        "2028-05-02" to "劳动节",
        "2028-05-03" to "劳动节",
        "2028-05-04" to "劳动节",
        "2028-05-05" to "劳动节",
        "2028-05-29" to "端午节",
        "2028-05-30" to "端午节",
        "2028-05-31" to "端午节",
        "2028-10-01" to "国庆节",
        "2028-10-02" to "国庆节",
        "2028-10-03" to "国庆节",
        "2028-10-04" to "国庆节",
        "2028-10-05" to "国庆节",
        "2028-10-06" to "国庆节",
        "2028-10-07" to "国庆节",
        "2028-10-08" to "国庆节"
    )
    
    // 所有节假日数据
    private val allHolidays = mapOf(
        2024 to holidays2024,
        2025 to holidays2025,
        2026 to holidays2026,
        2027 to holidays2027,
        2028 to holidays2028
    )
    
    /**
     * 获取日期类型
     * @param date 日期字符串，格式：yyyy-MM-dd
     * @return 日期类型
     */
    fun getDayType(date: String): DayType {
        // 首先检查是否为法定节假日
        if (isHoliday(date)) {
            return DayType.HOLIDAY
        }
        
        // 然后检查是否为周末
        if (isWeekend(date)) {
            return DayType.WEEKEND
        }
        
        // 其他情况为工作日
        return DayType.WORKDAY
    }
    
    /**
     * 检查是否为法定节假日
     * @param date 日期字符串，格式：yyyy-MM-dd
     * @return 是否为节假日
     */
    fun isHoliday(date: String): Boolean {
        return allHolidays[date.substring(0, 4).toIntOrNull()]?.containsKey(date) == true
    }
    
    /**
     * 获取节假日名称
     * @param date 日期字符串，格式：yyyy-MM-dd
     * @return 节假日名称，如果不为节假日则返回null
     */
    fun getHolidayName(date: String): String? {
        return allHolidays[date.substring(0, 4).toIntOrNull()]?.get(date)
    }
    
    /**
     * 检查是否为周末
     * @param date 日期字符串，格式：yyyy-MM-dd
     * @return 是否为周末
     */
    fun isWeekend(date: String): Boolean {
        val calendar = DateUtils.parseDateString(date) ?: return false
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        return dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
    }
    
    /**
     * 获取星期几
     * @param date 日期字符串，格式：yyyy-MM-dd
     * @return 星期几（1=周日，2=周一... 7=周六）
     */
    fun getDayOfWeek(date: String): Int {
        val calendar = DateUtils.parseDateString(date) ?: return 0
        return calendar.get(Calendar.DAY_OF_WEEK)
    }
    
    /**
     * 获取星期几的中文名称
     * @param date 日期字符串，格式：yyyy-MM-dd
     * @return 星期几的中文名称
     */
    fun getDayOfWeekName(date: String): String {
        return when (getDayOfWeek(date)) {
            Calendar.SUNDAY -> "周日"
            Calendar.MONDAY -> "周一"
            Calendar.TUESDAY -> "周二"
            Calendar.WEDNESDAY -> "周三"
            Calendar.THURSDAY -> "周四"
            Calendar.FRIDAY -> "周五"
            Calendar.SATURDAY -> "周六"
            else -> ""
        }
    }
    
    /**
     * 获取某月的所有节假日
     * @param year 年份
     * @param month 月份（1-12）
     * @return 节假日列表
     */
    fun getMonthHolidays(year: Int, month: Int): List<Pair<String, String>> {
        val yearHolidays = allHolidays[year] ?: emptyMap()
        val monthPrefix = String.format(Locale.getDefault(), "%04d-%02d", year, month)
        
        return yearHolidays.filter { it.key.startsWith(monthPrefix) }
            .map { it.key to it.value }
            .sortedBy { it.first }
    }
    
    /**
     * 获取某年的所有节假日
     * @param year 年份
     * @return 节假日列表
     */
    fun getYearHolidays(year: Int): List<Pair<String, String>> {
        val yearHolidays = allHolidays[year] ?: emptyMap()
        return yearHolidays.map { it.key to it.value }
            .sortedBy { it.first }
    }
}