package com.example.personalovertimerecord.data

@Deprecated("Use OvertimeRecord instead", ReplaceWith("OvertimeRecord"))
data class Attendance(
    val id: Long = 0L,
    val date: String,
    val checkInTime: String? = null,
    val checkOutTime: String? = null,
    val checkInTimestamp: Long? = null,
    val checkOutTimestamp: Long? = null,
    val note: String? = null,
    val manualOvertimeHours: Double = -1.0,
    val manualExtraHours: Double = -1.0,
    // 请假相关字段
    val isLeave: Boolean = false,           // 是否请假
    val leaveType: String? = null,          // 请假类型
    val leaveHours: Double = 0.0           // 请假时长（天）
) {
    fun toOvertimeRecord(): OvertimeRecord {
        val overtime = if (manualOvertimeHours >= 0) manualOvertimeHours else 0.0
        val extra = if (manualExtraHours >= 0) manualExtraHours else 0.0
        return OvertimeRecord(
            id = id.toString(),
            date = date,
            overtimeHours = overtime,
            extraHours = extra,
            note = note
        )
    }
}
