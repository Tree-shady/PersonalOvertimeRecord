package com.example.personalovertimerecord.data

import java.util.UUID

data class OvertimeRecord(
    val id: String = UUID.randomUUID().toString(),
    val date: String,
    val overtimeHours: Double = 0.0,
    val extraHours: Double = 0.0,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    // 新增字段
    val checkInTime: String? = null,
    val checkOutTime: String? = null,
    val dayType: String = "平时",
    val totalPay: Double = 0.0
)

data class OvertimeSettings(
    var baseSalary: Double = 5000.0,
    var performancePercent: Double = 0.0,
    var monthlyWorkDays: Double = 21.75,
    var dailyWorkHours: Double = 8.0,
    var overtimeRateNormal: Double = 1.5,
    var overtimeRateWeekend: Double = 2.0,
    var overtimeRateHoliday: Double = 3.0,
    var workStartTime: String = "08:00",
    var workEndTime: String = "17:00",
    // 加密相关设置
    var exportEncryptionEnabled: Boolean = false,
    var exportPassword: String = "",
    var syncEncryptionEnabled: Boolean = false,
    var syncPassword: String = ""
)

data class OvertimeResult(
    val workHours: Double = 0.0,
    val overtimeHours: Double = 0.0,
    val normalOvertime: Double = 0.0,
    val weekendOvertime: Double = 0.0,
    val holidayOvertime: Double = 0.0,
    val estimatedPay: Double = 0.0,
    val extraHours: Double = 0.0
)

enum class DayType { WORKDAY, WEEKEND, HOLIDAY }
