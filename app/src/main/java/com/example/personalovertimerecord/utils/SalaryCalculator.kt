package com.example.personalovertimerecord.utils

import com.example.personalovertimerecord.data.Attendance
import com.example.personalovertimerecord.data.DayType
import com.example.personalovertimerecord.data.OvertimeSettings
import com.example.personalovertimerecord.data.OvertimeResult
import java.util.Calendar
import java.util.Locale

object SalaryCalculator {
    
    data class SalaryReport(
        val year: Int,
        val month: Int,
        val baseSalary: Double,
        val performanceBonus: Double,
        val normalOvertimePay: Double,
        val weekendOvertimePay: Double,
        val holidayOvertimePay: Double,
        val extraPay: Double,
        val totalOvertimePay: Double,
        val totalSalary: Double,
        val totalOvertimeHours: Double,
        val totalExtraHours: Double,
        val totalHours: Double,
        val overtimeDays: Int,
        val normalDays: Int,
        val weekendDays: Int,
        val holidayDays: Int
    )
    
    data class DailySalary(
        val date: String,
        val dayType: DayType,
        val overtimeHours: Double,
        val extraHours: Double,
        val pay: Double
    )
    
    fun calculateMonthlySalary(
        attendanceList: List<Attendance>,
        settings: OvertimeSettings,
        year: Int,
        month: Int
    ): SalaryReport {
        val monthPrefix = String.format(Locale.getDefault(), "%04d-%02d", year, month)
        
        val monthRecords = attendanceList.filter { it.date.startsWith(monthPrefix) }
        
        var normalOvertime = 0.0
        var weekendOvertime = 0.0
        var holidayOvertime = 0.0
        var extraHours = 0.0
        var normalDays = 0
        var weekendDays = 0
        var holidayDays = 0
        
        monthRecords.forEach { attendance ->
            val dayType = HolidayManager.getDayType(attendance.date)
            
            val overtime = if (attendance.manualOvertimeHours >= 0) attendance.manualOvertimeHours else 0.0
            val extra = if (attendance.manualExtraHours >= 0) attendance.manualExtraHours else 0.0
            
            when (dayType) {
                DayType.WORKDAY -> {
                    normalOvertime += overtime
                    if (overtime > 0) normalDays++
                }
                DayType.WEEKEND -> {
                    weekendOvertime += overtime
                    if (overtime > 0) weekendDays++
                }
                DayType.HOLIDAY -> {
                    holidayOvertime += overtime
                    if (overtime > 0) holidayDays++
                }
            }
            extraHours += extra
        }
        
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
        val extraPayAmount = extraHours * hourlyWage * settings.overtimeRateNormal
        
        val totalOvertimePay = normalPay + weekendPay + holidayPay + extraPayAmount
        val totalSalary = totalMonthlySalary + totalOvertimePay
        
        return SalaryReport(
            year = year,
            month = month,
            baseSalary = settings.baseSalary,
            performanceBonus = performanceBonus,
            normalOvertimePay = normalPay,
            weekendOvertimePay = weekendPay,
            holidayOvertimePay = holidayPay,
            extraPay = extraPayAmount,
            totalOvertimePay = totalOvertimePay,
            totalSalary = totalSalary,
            totalOvertimeHours = normalOvertime + weekendOvertime + holidayOvertime,
            totalExtraHours = extraHours,
            totalHours = normalOvertime + weekendOvertime + holidayOvertime + extraHours,
            overtimeDays = normalDays + weekendDays + holidayDays,
            normalDays = normalDays,
            weekendDays = weekendDays,
            holidayDays = holidayDays
        )
    }
    
    fun calculateDailySalaries(
        attendanceList: List<Attendance>,
        settings: OvertimeSettings
    ): List<DailySalary> {
        return attendanceList.map { attendance ->
            val dayType = HolidayManager.getDayType(attendance.date)
            val overtime = if (attendance.manualOvertimeHours >= 0) attendance.manualOvertimeHours else 0.0
            val extra = if (attendance.manualExtraHours >= 0) attendance.manualExtraHours else 0.0
            
            val performanceBonus = settings.baseSalary * (settings.performancePercent / 100.0)
            val totalMonthlySalary = settings.baseSalary + performanceBonus
            val monthlyTotalHours = settings.monthlyWorkDays * settings.dailyWorkHours
            val hourlyWage = if (monthlyTotalHours > 0) {
                totalMonthlySalary / monthlyTotalHours
            } else {
                0.0
            }
            
            val rate = when (dayType) {
                DayType.WORKDAY -> settings.overtimeRateNormal
                DayType.WEEKEND -> settings.overtimeRateWeekend
                DayType.HOLIDAY -> settings.overtimeRateHoliday
            }
            
            val overtimePay = overtime * hourlyWage * rate
            val extraPay = extra * hourlyWage * settings.overtimeRateNormal
            val totalPay = overtimePay + extraPay
            
            DailySalary(
                date = attendance.date,
                dayType = dayType,
                overtimeHours = overtime,
                extraHours = extra,
                pay = totalPay
            )
        }
    }
    
    fun calculateYearlySalary(
        attendanceList: List<Attendance>,
        settings: OvertimeSettings,
        year: Int
    ): List<SalaryReport> {
        val reports = mutableListOf<SalaryReport>()
        
        for (month in 1..12) {
            val report = calculateMonthlySalary(attendanceList, settings, year, month)
            if (report.totalOvertimeHours > 0 || report.totalExtraHours > 0) {
                reports.add(report)
            }
        }
        
        return reports
    }
    
    fun formatSalaryReport(report: SalaryReport): String {
        return buildString {
            appendLine("=" .repeat(40))
            appendLine("📊 ${report.year}年${report.month}月工资报表")
            appendLine("=".repeat(40))
            appendLine()
            appendLine("💰 基础工资: ${OvertimeCalculator.formatMoney(report.baseSalary)}")
            appendLine("🎯 绩效奖金: ${OvertimeCalculator.formatMoney(report.performanceBonus)}")
            appendLine()
            appendLine("📈 加班工资明细:")
            appendLine("  工作日加班: ${String.format(Locale.getDefault(), "%.1f", report.normalOvertimePay)}h × 1.5 = ${OvertimeCalculator.formatMoney(report.normalOvertimePay)}")
            appendLine("  周末加班: ${String.format(Locale.getDefault(), "%.1f", report.weekendOvertimePay)}h × 2.0 = ${OvertimeCalculator.formatMoney(report.weekendOvertimePay)}")
            appendLine("  法定假日: ${String.format(Locale.getDefault(), "%.1f", report.holidayOvertimePay)}h × 3.0 = ${OvertimeCalculator.formatMoney(report.holidayOvertimePay)}")
            appendLine("  加点工资: ${String.format(Locale.getDefault(), "%.1f", report.totalExtraHours)}h × 1.5 = ${OvertimeCalculator.formatMoney(report.extraPay)}")
            appendLine()
            appendLine("⏱️ 加班统计:")
            appendLine("  工作日加班: ${report.normalDays}天 (${String.format(Locale.getDefault(), "%.1f", report.normalOvertimePay)}h)")
            appendLine("  周末加班: ${report.weekendDays}天 (${String.format(Locale.getDefault(), "%.1f", report.weekendOvertimePay)}h)")
            appendLine("  法定假日: ${report.holidayDays}天 (${String.format(Locale.getDefault(), "%.1f", report.holidayOvertimePay)}h)")
            appendLine()
            appendLine("📋 总计:")
            appendLine("  总加班时长: ${OvertimeCalculator.formatHours(report.totalOvertimeHours)}")
            appendLine("  总加点时长: ${OvertimeCalculator.formatHours(report.totalExtraHours)}")
            appendLine("  加班总工资: ${OvertimeCalculator.formatMoney(report.totalOvertimePay)}")
            appendLine()
            appendLine("💵 本月总工资: ${OvertimeCalculator.formatMoney(report.totalSalary)}")
            appendLine("=".repeat(40))
        }
    }
}
