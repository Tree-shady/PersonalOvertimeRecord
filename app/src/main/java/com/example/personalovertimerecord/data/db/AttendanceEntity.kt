package com.example.personalovertimerecord.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.personalovertimerecord.data.Attendance

/**
 * Room数据库实体 - 加班记录表
 */
@Entity(tableName = "attendance_records")
data class AttendanceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val date: String,
    val checkInTime: String? = null,
    val checkOutTime: String? = null,
    val checkInTimestamp: Long? = null,
    val checkOutTimestamp: Long? = null,
    val note: String? = null,
    val manualOvertimeHours: Double = -1.0,
    val manualExtraHours: Double = -1.0,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long? = null,  // 用于支持增量同步
    // 请假相关字段
    val isLeave: Boolean = false,
    val leaveType: String? = null,
    val leaveHours: Double = 0.0
) {
    /**
     * 转换为Attendance数据模型
     */
    fun toAttendance(): Attendance {
        return Attendance(
            id = id,
            date = date,
            checkInTime = checkInTime,
            checkOutTime = checkOutTime,
            checkInTimestamp = checkInTimestamp,
            checkOutTimestamp = checkOutTimestamp,
            note = note,
            manualOvertimeHours = manualOvertimeHours,
            manualExtraHours = manualExtraHours,
            isLeave = isLeave,
            leaveType = leaveType,
            leaveHours = leaveHours
        )
    }

    companion object {
        /**
         * 从Attendance数据模型创建实体
         */
        fun fromAttendance(attendance: Attendance): AttendanceEntity {
            val now = System.currentTimeMillis()
            return AttendanceEntity(
                id = attendance.id,
                date = attendance.date,
                checkInTime = attendance.checkInTime,
                checkOutTime = attendance.checkOutTime,
                checkInTimestamp = attendance.checkInTimestamp,
                checkOutTimestamp = attendance.checkOutTimestamp,
                note = attendance.note,
                manualOvertimeHours = attendance.manualOvertimeHours,
                manualExtraHours = attendance.manualExtraHours,
                modifiedAt = now,
                isLeave = attendance.isLeave,
                leaveType = attendance.leaveType,
                leaveHours = attendance.leaveHours
            )
        }
    }
}
