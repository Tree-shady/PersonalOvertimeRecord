package com.example.personalovertimerecord.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.UUID

data class FirebaseOvertimeRecord(
    @DocumentId
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "",
    val date: String = "",
    val manualOvertimeHours: Double = 0.0,
    val manualExtraHours: Double = 0.0,
    val note: String? = null,
    val dayType: String = "WORKDAY",
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    @ServerTimestamp
    val updatedAt: Timestamp? = null
) {
    fun toAttendance(): Attendance {
        // 转换 String ID 为 Long（从字符串解析为 Long，失败则使用哈希值
        val longId = try {
            id.toLong()
        } catch (e: NumberFormatException) {
            id.hashCode().toLong()
        }
        return Attendance(
            id = longId,
            date = this.date,
            manualOvertimeHours = this.manualOvertimeHours,
            manualExtraHours = this.manualExtraHours,
            note = this.note
        )
    }
    
    companion object {
        fun fromAttendance(attendance: Attendance, userId: String, dayType: DayType): FirebaseOvertimeRecord {
            return FirebaseOvertimeRecord(
                id = attendance.id.toString(),
                userId = userId,
                date = attendance.date,
                manualOvertimeHours = attendance.manualOvertimeHours,
                manualExtraHours = attendance.manualExtraHours,
                note = attendance.note,
                dayType = dayType.name
            )
        }
    }
}

data class FirebaseUserSettings(
    @DocumentId
    val userId: String = "",
    val baseSalary: Double = 5000.0,
    val performancePercent: Double = 0.0,
    val monthlyWorkDays: Double = 21.75,
    val dailyWorkHours: Double = 8.0,
    val overtimeRateNormal: Double = 1.5,
    val overtimeRateWeekend: Double = 2.0,
    val overtimeRateHoliday: Double = 3.0,
    val workStartTime: String = "08:00",
    val workEndTime: String = "17:00",
    @ServerTimestamp
    val updatedAt: Timestamp? = null
) {
    fun toOvertimeSettings(): OvertimeSettings {
        return OvertimeSettings(
            baseSalary = this.baseSalary,
            performancePercent = this.performancePercent,
            monthlyWorkDays = this.monthlyWorkDays,
            dailyWorkHours = this.dailyWorkHours,
            overtimeRateNormal = this.overtimeRateNormal,
            overtimeRateWeekend = this.overtimeRateWeekend,
            overtimeRateHoliday = this.overtimeRateHoliday,
            workStartTime = this.workStartTime,
            workEndTime = this.workEndTime
        )
    }
    
    companion object {
        fun fromOvertimeSettings(userId: String, settings: OvertimeSettings): FirebaseUserSettings {
            return FirebaseUserSettings(
                userId = userId,
                baseSalary = settings.baseSalary,
                performancePercent = settings.performancePercent,
                monthlyWorkDays = settings.monthlyWorkDays,
                dailyWorkHours = settings.dailyWorkHours,
                overtimeRateNormal = settings.overtimeRateNormal,
                overtimeRateWeekend = settings.overtimeRateWeekend,
                overtimeRateHoliday = settings.overtimeRateHoliday,
                workStartTime = settings.workStartTime,
                workEndTime = settings.workEndTime
            )
        }
    }
}
