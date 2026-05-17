package com.example.personalovertimerecord.data

data class Attendance(
    val id: Long,
    val date: String,
    val checkInTime: String? = null,
    val checkOutTime: String? = null,
    val checkInTimestamp: Long? = null,
    val checkOutTimestamp: Long? = null,
    val note: String? = null,
    val manualOvertimeHours: Double = -1.0, // 手动加班时长，-1表示自动计算
    val manualExtraHours: Double = -1.0 // 手动加点时长，-1表示自动计算
)
