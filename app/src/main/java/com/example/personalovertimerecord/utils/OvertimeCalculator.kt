package com.example.personalovertimerecord.utils

import com.example.personalovertimerecord.data.Attendance
import com.example.personalovertimerecord.data.OvertimeResult
import com.example.personalovertimerecord.data.OvertimeSettings
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object OvertimeCalculator {
    
    fun calculateOvertime(
        attendance: Attendance,
        settings: OvertimeSettings
    ): OvertimeResult {
        val isWeekend = isWeekend(attendance.date)
        val isHoliday = isHoliday(attendance.date)
        val isWeekendOrHoliday = isWeekend || isHoliday
        
        if (attendance.manualOvertimeHours >= 0 || attendance.manualExtraHours >= 0) {
            return calculateManualOvertime(
                attendance, settings, isWeekend, isHoliday, isWeekendOrHoliday
            )
        }
        
        if (isWeekendOrHoliday) {
            return calculateWeekendHolidayOvertime(attendance, settings, isHoliday)
        }
        
        return calculateNormalDayOvertime(attendance, settings)
    }
    
    private fun calculateManualOvertime(
        attendance: Attendance,
        settings: OvertimeSettings,
        isWeekend: Boolean,
        isHoliday: Boolean,
        isWeekendOrHoliday: Boolean
    ): OvertimeResult {
        val overtimeHours = if (attendance.manualOvertimeHours >= 0) attendance.manualOvertimeHours else 0.0
        val extraHours = if (attendance.manualExtraHours >= 0) attendance.manualExtraHours else 0.0
        
        var normalOvertime = 0.0
        var weekendOvertime = 0.0
        var holidayOvertime = 0.0
        
        if (isHoliday) {
            holidayOvertime = overtimeHours
        } else if (isWeekend) {
            weekendOvertime = overtimeHours
        } else {
            normalOvertime = overtimeHours
        }
        
        val estimatedPay = calculateOvertimePay(
            normalOvertime, weekendOvertime, holidayOvertime, extraHours, settings
        )
        
        return OvertimeResult(
            workHours = 0.0,
            overtimeHours = overtimeHours,
            normalOvertime = normalOvertime,
            weekendOvertime = weekendOvertime,
            holidayOvertime = holidayOvertime,
            estimatedPay = estimatedPay,
            extraHours = extraHours
        )
    }
    
    private fun calculateWeekendHolidayOvertime(
        attendance: Attendance,
        settings: OvertimeSettings,
        isHoliday: Boolean
    ): OvertimeResult {
        val checkInTime = attendance.checkInTime ?: return createZeroResult()
        val checkOutTime = attendance.checkOutTime ?: return createZeroResult()
        
        val checkIn = parseTime(checkInTime) ?: return createZeroResult()
        val checkOut = parseTime(checkOutTime) ?: return createZeroResult()
        
        val checkInMinutes = getMinutes(checkIn)
        val checkOutMinutes = getMinutes(checkOut)
        
        val totalWorkingMinutes = calculateWeekendWorkingMinutes(checkInMinutes, checkOutMinutes)
        val overtimeHours = totalWorkingMinutes / 60.0
        
        var normalOvertime = 0.0
        var weekendOvertime = 0.0
        var holidayOvertime = 0.0
        
        if (isHoliday) {
            holidayOvertime = overtimeHours
        } else {
            weekendOvertime = overtimeHours
        }
        
        val estimatedPay = calculateOvertimePay(
            normalOvertime, weekendOvertime, holidayOvertime, 0.0, settings
        )
        
        return OvertimeResult(
            workHours = overtimeHours,
            overtimeHours = overtimeHours,
            normalOvertime = normalOvertime,
            weekendOvertime = weekendOvertime,
            holidayOvertime = holidayOvertime,
            estimatedPay = estimatedPay,
            extraHours = 0.0
        )
    }
    
    private fun calculateNormalDayOvertime(
        attendance: Attendance,
        settings: OvertimeSettings
    ): OvertimeResult {
        val checkInTime = attendance.checkInTime ?: return createZeroResult()
        val checkOutTime = attendance.checkOutTime ?: return createZeroResult()
        
        val checkIn = parseTime(checkInTime) ?: return createZeroResult()
        val checkOut = parseTime(checkOutTime) ?: return createZeroResult()
        
        val checkInMinutes = getMinutes(checkIn)
        val checkOutMinutes = getMinutes(checkOut)
        
        val workStartMinutes = parseTimeToMinutes(settings.workStartTime) ?: 480
        val workEndMinutes = parseTimeToMinutes(settings.workEndTime) ?: 1020
        val lunchStartMinutes = parseTimeToMinutes(settings.lunchStartTime) ?: 720
        val lunchEndMinutes = parseTimeToMinutes(settings.lunchEndTime) ?: 780
        val overtimeStartMinutes = workEndMinutes + settings.overtimeStartAfter
        
        val workMinutes = calculateNormalWorkMinutes(
            checkInMinutes, checkOutMinutes, workStartMinutes, workEndMinutes,
            lunchStartMinutes, lunchEndMinutes
        )
        
        val overtimeMinutes = if (checkOutMinutes > overtimeStartMinutes) {
            checkOutMinutes - overtimeStartMinutes
        } else {
            0
        }
        
        val overtimeHours = overtimeMinutes / 60.0
        
        val estimatedPay = calculateOvertimePay(
            overtimeHours, 0.0, 0.0, 0.0, settings
        )
        
        return OvertimeResult(
            workHours = workMinutes / 60.0,
            overtimeHours = overtimeHours,
            normalOvertime = overtimeHours,
            weekendOvertime = 0.0,
            holidayOvertime = 0.0,
            estimatedPay = estimatedPay,
            extraHours = 0.0
        )
    }
    
    private fun calculateWeekendWorkingMinutes(
        checkInMinutes: Int,
        checkOutMinutes: Int
    ): Int {
        if (checkOutMinutes <= checkInMinutes) return 0
        
        var total = checkOutMinutes - checkInMinutes
        
        if (checkInMinutes < 720 && checkOutMinutes > 720) {
            val endOfLunch1 = minOf(checkOutMinutes, 780)
            total -= (endOfLunch1 - maxOf(checkInMinutes, 720))
        }
        
        if (checkInMinutes < 1020 && checkOutMinutes > 1020) {
            val endOfRest = minOf(checkOutMinutes, 1080)
            total -= (endOfRest - maxOf(checkInMinutes, 1020))
        }
        
        return maxOf(0, total)
    }
    
    private fun calculateNormalWorkMinutes(
        checkInMinutes: Int,
        checkOutMinutes: Int,
        workStartMinutes: Int,
        workEndMinutes: Int,
        lunchStartMinutes: Int,
        lunchEndMinutes: Int
    ): Int {
        val actualStart = maxOf(checkInMinutes, workStartMinutes)
        val actualEnd = minOf(checkOutMinutes, workEndMinutes)
        
        if (actualEnd <= actualStart) return 0
        
        var total = actualEnd - actualStart
        
        if (actualStart < lunchEndMinutes && actualEnd > lunchStartMinutes) {
            val lunchOverlapStart = maxOf(actualStart, lunchStartMinutes)
            val lunchOverlapEnd = minOf(actualEnd, lunchEndMinutes)
            total -= (lunchOverlapEnd - lunchOverlapStart)
        }
        
        return maxOf(0, total)
    }
    
    private fun createZeroResult(): OvertimeResult {
        return OvertimeResult(
            workHours = 0.0,
            overtimeHours = 0.0,
            normalOvertime = 0.0,
            weekendOvertime = 0.0,
            holidayOvertime = 0.0,
            estimatedPay = 0.0,
            extraHours = 0.0
        )
    }
    
    private fun parseTime(timeStr: String): Calendar? {
        return try {
            val format = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            val date = format.parse(timeStr) ?: return null
            val calendar = Calendar.getInstance()
            calendar.time = date
            calendar
        } catch (e: Exception) {
            try {
                val format = SimpleDateFormat("HH:mm", Locale.getDefault())
                val date = format.parse(timeStr) ?: return null
                val calendar = Calendar.getInstance()
                calendar.time = date
                calendar
            } catch (e: Exception) {
                null
            }
        }
    }
    
    private fun parseTimeToMinutes(timeStr: String): Int? {
        return try {
            val parts = timeStr.split(":")
            if (parts.size >= 2) {
                parts[0].toInt() * 60 + parts[1].toInt()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun getMinutes(calendar: Calendar): Int {
        return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    }
    
    private fun isWeekend(dateStr: String): Boolean {
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
    
    private fun isHoliday(dateStr: String): Boolean {
        val holidays = setOf(
            "01-01", "01-02", "01-03", 
            "02-10", "02-11", "02-12", "02-13", "02-14", "02-15", "02-16", "02-17", 
            "04-04", "04-05", "04-06", 
            "05-01", "05-02", "05-03", "05-04", "05-05", 
            "06-10", 
            "09-15", "09-16", "09-17", 
            "10-01", "10-02", "10-03", "10-04", "10-05", "10-06", "10-07"
        )
        
        return try {
            val monthDay = dateStr.substring(5)
            holidays.contains(monthDay)
        } catch (e: Exception) {
            false
        }
    }
    
    private fun calculateOvertimePay(
        normalOvertime: Double,
        weekendOvertime: Double,
        holidayOvertime: Double,
        extraHours: Double,
        settings: OvertimeSettings
    ): Double {
        val performanceBonus = settings.baseSalary * (settings.performancePercent / 100.0)
        val totalMonthlySalary = settings.baseSalary + performanceBonus
        val monthlyTotalHours = settings.monthlyWorkDays * settings.dailyWorkHours
        val hourlyWage = if (monthlyTotalHours > 0) {
            totalMonthlySalary / monthlyTotalHours
        } else {
            0.0
        }
        
        val normalPay = normalOvertime * hourlyWage * settings.overtimeRateNormal
        val weekendPay = weekendOvertime * hourlyWage * settings.overtimeRateWeekend
        val holidayPay = holidayOvertime * hourlyWage * settings.overtimeRateHoliday
        val extraPay = extraHours * hourlyWage * settings.overtimeRateNormal
        
        return normalPay + weekendPay + holidayPay + extraPay
    }
    
    fun formatHours(hours: Double): String {
        val fullHours = hours.toInt()
        val minutes = ((hours - fullHours) * 60).toInt()
        if (minutes > 0) {
            return "${fullHours}小时${minutes}分钟"
        }
        return "${fullHours}小时"
    }
    
    fun formatMoney(amount: Double): String {
        return String.format(Locale.getDefault(), "¥%.2f", amount)
    }
}
