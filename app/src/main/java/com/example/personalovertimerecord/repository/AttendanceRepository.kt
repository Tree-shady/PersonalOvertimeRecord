package com.example.personalovertimerecord.repository

import com.example.personalovertimerecord.data.Attendance
import kotlinx.coroutines.flow.Flow

/**
 * 考勤记录仓储接口
 * 定义数据访问的统一接口，便于切换数据源实现
 */
interface AttendanceRepository {
    
    /**
     * 获取所有记录（Flow版本，支持响应式更新）
     */
    fun getAllAttendanceFlow(): Flow<List<Attendance>>
    
    /**
     * 获取所有记录（同步版本）
     */
    suspend fun getAllAttendance(): List<Attendance>
    
    /**
     * 根据日期获取记录
     */
    suspend fun getAttendanceByDate(date: String): Attendance?
    
    /**
     * 根据ID获取记录
     */
    suspend fun getAttendanceById(id: Long): Attendance?
    
    /**
     * 插入或更新记录
     */
    suspend fun saveAttendance(attendance: Attendance): Long
    
    /**
     * 插入新记录
     */
    suspend fun insertAttendance(attendance: Attendance): Long
    
    /**
     * 更新记录
     */
    suspend fun updateAttendance(attendance: Attendance)
    
    /**
     * 删除记录
     */
    suspend fun deleteAttendance(id: Long)
    
    /**
     * 删除所有记录
     */
    suspend fun deleteAllAttendance()
    
    /**
     * 获取记录数量
     */
    suspend fun getCount(): Int
}