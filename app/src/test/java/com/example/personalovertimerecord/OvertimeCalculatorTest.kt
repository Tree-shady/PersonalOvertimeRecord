package com.example.personalovertimerecord

import org.junit.Test
import org.junit.Assert.*
import com.example.personalovertimerecord.data.OvertimeRecord
import com.example.personalovertimerecord.data.OvertimeSettings
import com.example.personalovertimerecord.utils.Formatter
import com.example.personalovertimerecord.utils.OvertimeCalculator

/**
 * 加班计算器单元测试
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
        val record = OvertimeRecord(
            date = "2024-06-15",
            overtimeHours = 2.0,
            extraHours = 1.0
        )

        val result = OvertimeCalculator.calculateOvertime(record, testSettings)

        assertEquals(2.0, result.overtimeHours, 0.001)
        assertEquals(1.0, result.extraHours, 0.001)
        // 2024-06-15 是周六，周末加班按 2 倍计算
        val hourlyWage = 5000.0 / (21.75 * 8.0)
        val expectedPay = 2.0 * hourlyWage * 2.0 + 1.0 * hourlyWage * 1.5
        assertEquals(expectedPay, result.estimatedPay, 0.01)
    }

    @Test
    fun testCalculateOvertime_NoManualHours() {
        // 未手工填写加班时长（默认 -1）时按 0 计算，不会崩溃
        val record = OvertimeRecord(
            date = "2024-06-15",
            checkInTime = "09:00",
            checkOutTime = "21:00"
        )

        val result = OvertimeCalculator.calculateOvertime(record, testSettings)

        assertEquals(0.0, result.overtimeHours, 0.001)
        assertEquals(0.0, result.extraHours, 0.001)
        assertEquals(0.0, result.estimatedPay, 0.001)
    }

    @Test
    fun testCalculateOvertime_NegativeManualHoursTreatedAsZero() {
        val record = OvertimeRecord(
            date = "2024-06-14",
            overtimeHours = -1.0,
            extraHours = -1.0
        )

        val result = OvertimeCalculator.calculateOvertime(record, testSettings)

        assertEquals(0.0, result.overtimeHours, 0.001)
        assertEquals(0.0, result.estimatedPay, 0.001)
    }

    @Test
    fun testFormatHours() {
        assertEquals("3h30m", Formatter.formatHours(3.5))
        assertEquals("3h", Formatter.formatHours(3.0))
    }

    @Test
    fun testFormatMoney() {
        assertEquals("¥1500.00", Formatter.formatMoney(1500.0))
    }

    // ===== 登记即分类：登记"加点"按工作日、登记"加班"按周末/节假日 =====

    @Test
    fun testEffectiveDayType_OvertimeOnWorkdayIsWeekend() {
        // 2024-06-14 是周五（工作日），但登记了加班 → 应按周末 2 倍计算
        val record = OvertimeRecord(
            date = "2024-06-14",
            overtimeHours = 3.0,
            extraHours = -1.0
        )
        val result = OvertimeCalculator.calculateOvertime(record, testSettings)

        assertEquals(0.0, result.normalOvertime, 0.001)
        assertEquals(3.0, result.weekendOvertime, 0.001)
        val hourlyWage = 5000.0 / (21.75 * 8.0)
        assertEquals(3.0 * hourlyWage * 2.0, result.estimatedPay, 0.01)
    }

    @Test
    fun testEffectiveDayType_OvertimeOnHolidayIsHoliday() {
        // 2024-10-01 是国庆节（日历节假日），登记加班 → 按 3 倍计算
        val record = OvertimeRecord(
            date = "2024-10-01",
            overtimeHours = 4.0,
            extraHours = -1.0
        )
        val result = OvertimeCalculator.calculateOvertime(record, testSettings)

        assertEquals(4.0, result.holidayOvertime, 0.001)
        val hourlyWage = 5000.0 / (21.75 * 8.0)
        assertEquals(4.0 * hourlyWage * 3.0, result.estimatedPay, 0.01)
    }

    @Test
    fun testEffectiveDayType_ExtraOnWeekendIsWorkday() {
        // 2024-06-15 是周六（周末），但只登记了加点 → 应按工作日 1.5 倍计算
        val record = OvertimeRecord(
            date = "2024-06-15",
            overtimeHours = -1.0,
            extraHours = 2.0
        )
        val result = OvertimeCalculator.calculateOvertime(record, testSettings)

        assertEquals(0.0, result.weekendOvertime, 0.001)
        val hourlyWage = 5000.0 / (21.75 * 8.0)
        assertEquals(2.0 * hourlyWage * 1.5, result.estimatedPay, 0.01)
    }

    @Test
    fun testEffectiveDayType_NothingRegisteredUsesCalendar() {
        // 什么都没登记 → 按日历：2024-06-15 周六 → 周末分类，但加班为 0 金额为 0
        val record = OvertimeRecord(
            date = "2024-06-15"
        )
        assertEquals(
            com.example.personalovertimerecord.data.DayType.WEEKEND,
            OvertimeCalculator.effectiveDayType(record.date, 0.0, 0.0)
        )
    }
}
