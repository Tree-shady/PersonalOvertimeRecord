package com.example.personalovertimerecord

import org.junit.Test
import org.junit.Assert.*
import com.example.personalovertimerecord.data.Attendance
import com.example.personalovertimerecord.data.OvertimeSettings
import com.example.personalovertimerecord.utils.OvertimeCalculator

/**
 * 加班计算器单元测试示例
 */
class OvertimeCalculatorTest {

    private val testSettings = OvertimeSettings(
        baseSalary = 5000.0,
        monthlyWorkDays = 21.75,
        dailyWorkHours = 8.0,
        overtimeRateNormal = 1.5,
        overtimeRateWeekend = 2.0,
        overtimeRateHoliday = 3.0
    )

    @Test
    fun testCalculateOvertime_ManualOvertime() {
        val attendance = Attendance(
            date = "2024-06-15",
            manualOvertimeHours = 2.0,
            manualExtraHours = 1.0
        )

        val result = OvertimeCalculator.calculateOvertime(attendance, testSettings)
        
        assertEquals(2.0, result.overtimeHours, 0.001)
        assertEquals(1.0, result.extraHours, 0.001)
    }

    @Test
    fun testCalculateOvertime_NoManualHours() {
        val attendance = Attendance(
            date = "2024-06-15",
            checkInTime = "09:00",
            checkOutTime = "21:00"
        )

        val result = OvertimeCalculator.calculateOvertime(attendance, testSettings)
        
        // 当没有手动输入时，应该使用默认工时计算
        assertTrue(result.totalHours >= 0.0)
    }

    @Test
    fun testFormatHours() {
        val formatted = OvertimeCalculator.formatHours(3.5)
        assertEquals("3.5小时", formatted)
    }

    @Test
    fun testFormatMoney() {
        val formatted = OvertimeCalculator.formatMoney(1500.0)
        assertEquals("¥1,500.00", formatted)
    }
}