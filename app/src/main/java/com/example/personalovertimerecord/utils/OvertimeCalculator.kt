package com.example.personalovertimerecord.utils

import com.example.personalovertimerecord.data.Attendance
import com.example.personalovertimerecord.data.OvertimeRecord
import com.example.personalovertimerecord.data.OvertimeResult
import com.example.personalovertimerecord.data.OvertimeSettings

object OvertimeCalculator {
    
    fun calculateOvertime(
        record: OvertimeRecord,
        settings: OvertimeSettings
    ): OvertimeResult {
        val dayType = HolidayManager.getDayType(record.date)
        val overtimeHours = record.overtimeHours
        val extraHours = record.extraHours
        
        var normalOvertime = 0.0
        var weekendOvertime = 0.0
        var holidayOvertime = 0.0
        
        when (dayType) {
            com.example.personalovertimerecord.data.DayType.HOLIDAY -> {
                holidayOvertime = overtimeHours
            }
            com.example.personalovertimerecord.data.DayType.WEEKEND -> {
                weekendOvertime = overtimeHours
            }
            com.example.personalovertimerecord.data.DayType.WORKDAY -> {
                normalOvertime = overtimeHours
            }
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
    
    fun calculateOvertime(
        attendance: Attendance,
        settings: OvertimeSettings
    ): OvertimeResult {
        val overtimeHours = if (attendance.manualOvertimeHours >= 0) attendance.manualOvertimeHours else 0.0
        val extraHours = if (attendance.manualExtraHours >= 0) attendance.manualExtraHours else 0.0
        
        val dayType = HolidayManager.getDayType(attendance.date)
        
        var normalOvertime = 0.0
        var weekendOvertime = 0.0
        var holidayOvertime = 0.0
        
        when (dayType) {
            com.example.personalovertimerecord.data.DayType.HOLIDAY -> {
                holidayOvertime = overtimeHours
            }
            com.example.personalovertimerecord.data.DayType.WEEKEND -> {
                weekendOvertime = overtimeHours
            }
            com.example.personalovertimerecord.data.DayType.WORKDAY -> {
                normalOvertime = overtimeHours
            }
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
}
