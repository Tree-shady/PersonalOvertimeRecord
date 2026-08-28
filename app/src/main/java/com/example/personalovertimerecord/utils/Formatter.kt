package com.example.personalovertimerecord.utils

import com.example.personalovertimerecord.data.DayType
import com.example.personalovertimerecord.data.LeaveType
import java.util.Locale

object Formatter {

    fun formatHours(hours: Double): String {
        val fullHours = hours.toInt()
        val minutes = ((hours - fullHours) * 60).toInt()
        return if (minutes > 0) {
            "${fullHours}h${minutes}m"
        } else {
            "${fullHours}h"
        }
    }

    fun formatMoney(amount: Double): String {
        return String.format(Locale.getDefault(), "¥%.2f", amount)
    }

    fun formatHoursShort(hours: Double): String {
        return String.format(Locale.getDefault(), "%.1fh", hours)
    }

    fun formatDate(dateStr: String): String {
        return try {
            val date = DateUtils.parseDate(dateStr) ?: return dateStr
            val weekday = DateUtils.getChineseWeekday(date)
            val parts = dateStr.split("-")
            if (parts.size == 3) {
                "${parts[1]}月${parts[2]}日 $weekday"
            } else {
                dateStr
            }
        } catch (e: Exception) {
            dateStr
        }
    }

    fun getDayTypeString(dateStr: String): String {
        return when (HolidayManager.getDayType(dateStr)) {
            DayType.HOLIDAY -> "法定假日"
            DayType.WEEKEND -> "周末"
            DayType.WORKDAY -> "工作日"
        }
    }

    /**
     * 按"登记即分类"规则获取日期类型的中文名称：
     * 登记了加点→工作日；登记了加班→周末/法定假日；否则按日历
     */
    fun getEffectiveDayTypeString(dateStr: String, overtimeHours: Double, extraHours: Double): String {
        return when (OvertimeCalculator.effectiveDayType(dateStr, overtimeHours, extraHours)) {
            DayType.HOLIDAY -> "法定假日"
            DayType.WEEKEND -> "周末"
            DayType.WORKDAY -> "工作日"
        }
    }

    /**
     * 获取请假类型的显示名称
     */
    fun getLeaveTypeDisplayName(leaveType: String?): String {
        return LeaveType.fromString(leaveType)?.displayName ?: "请假"
    }

    /**
     * 格式化请假信息
     */
    fun formatLeaveInfo(leaveType: String?, leaveHours: Double): String {
        if (leaveType == null || leaveHours <= 0) return ""
        val typeName = getLeaveTypeDisplayName(leaveType)
        return if (leaveHours >= 1.0) {
            "${typeName}${leaveHours.toInt()}天"
        } else {
            "${typeName}${leaveHours * 8}小时"
        }
    }
}
