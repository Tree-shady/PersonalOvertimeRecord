package com.example.personalovertimerecord.utils

import com.example.personalovertimerecord.data.DayType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object HolidayManager {
    
    private var holidayDates: Set<String> = getDefaultHolidays()
    
    fun getDefaultHolidays(): Set<String> {
        return setOf(
            "01-01", "01-02", "01-03", 
            "02-10", "02-11", "02-12", "02-13", "02-14", "02-15", "02-16", "02-17", 
            "04-04", "04-05", "04-06", 
            "05-01", "05-02", "05-03", "05-04", "05-05", 
            "06-10", 
            "09-15", "09-16", "09-17", 
            "10-01", "10-02", "10-03", "10-04", "10-05", "10-06", "10-07"
        )
    }
    
    fun setHolidays(holidays: List<String>) {
        holidayDates = holidays.toSet()
    }
    
    fun getDayType(dateStr: String): DayType {
        return when {
            isHoliday(dateStr) -> DayType.HOLIDAY
            isWeekend(dateStr) -> DayType.WEEKEND
            else -> DayType.WORKDAY
        }
    }
    
    fun isHoliday(dateStr: String): Boolean {
        return try {
            val monthDay = dateStr.substring(5)
            holidayDates.contains(monthDay)
        } catch (e: Exception) {
            false
        }
    }
    
    fun isWeekend(dateStr: String): Boolean {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = format.parse(dateStr) ?: return false
            val calendar = Calendar.getInstance()
            calendar.time = date
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
        } catch (e: Exception) {
            false
        }
    }
    
    fun isWorkday(dateStr: String): Boolean {
        return getDayType(dateStr) == DayType.WORKDAY
    }
    
    fun getDayTypeName(dayType: DayType): String {
        return when (dayType) {
            DayType.WORKDAY -> "工作日"
            DayType.WEEKEND -> "周末"
            DayType.HOLIDAY -> "法定假日"
        }
    }
}
