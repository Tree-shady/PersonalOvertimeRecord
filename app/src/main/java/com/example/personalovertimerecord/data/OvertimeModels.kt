package com.example.personalovertimerecord.data

data class OvertimeSettings(
    val workStartTime: String = "08:00",
    val workEndTime: String = "17:00",
    val lunchStartTime: String = "12:00",
    val lunchEndTime: String = "13:00",
    val overtimeStartAfter: Int = 60,
    val overtimeRateNormal: Double = 1.5,
    val overtimeRateWeekend: Double = 2.0,
    val overtimeRateHoliday: Double = 3.0,
    val baseSalary: Double = 5000.0,
    val performancePercent: Double = 100.0,
    val monthlyWorkDays: Double = 21.75, // 月工作日数
    val dailyWorkHours: Double = 8.0, // 每日工作小时数
    val shiftType: Int = 0 // 0: 白班8小时，1: 自定义
)

data class OvertimeResult(
    val workHours: Double,
    val overtimeHours: Double,
    val normalOvertime: Double,
    val weekendOvertime: Double,
    val holidayOvertime: Double,
    val estimatedPay: Double,
    val extraHours: Double = 0.0 // 加点时长
)
