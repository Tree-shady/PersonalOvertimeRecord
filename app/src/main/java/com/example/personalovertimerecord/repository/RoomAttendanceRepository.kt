package com.example.personalovertimerecord.repository

import com.example.personalovertimerecord.data.OvertimeRecord
import com.example.personalovertimerecord.data.db.AppDatabase
import com.example.personalovertimerecord.data.db.AttendanceEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room数据库实现的考勤记录仓储
 * 使用Room数据库和Flow进行响应式数据访问
 */
class RoomAttendanceRepository(
    private val database: AppDatabase
) : AttendanceRepository {
    
    private val dao = database.attendanceDao()
    
    /**
     * 获取所有记录（Flow版本）
     * 支持响应式更新，当数据库变化时会自动通知观察者
     */
    override fun getAllAttendanceFlow(): Flow<List<OvertimeRecord>> {
        return dao.getAllRecords().map { entities ->
            entities.map { it.toRecord() }
        }
    }
    
    /**
     * 获取所有记录（同步版本）
     */
    override suspend fun getAllAttendance(): List<OvertimeRecord> {
        return dao.getAllRecordsSync().map { it.toRecord() }
    }
    
    /**
     * 根据日期获取记录
     */
    override suspend fun getAttendanceByDate(date: String): OvertimeRecord? {
        return dao.getRecordByDate(date)?.toRecord()
    }
    
    /**
     * 根据ID获取记录
     */
    override suspend fun getAttendanceById(id: Long): OvertimeRecord? {
        return dao.getRecordById(id)?.toRecord()
    }
    
    /**
     * 插入或更新记录
     * 如果记录存在则更新，不存在则插入
     */
    override suspend fun saveAttendance(attendance: OvertimeRecord): Long {
        val entity = AttendanceEntity.fromRecord(attendance)
        return dao.insertOrUpdate(entity)
    }
    
    /**
     * 插入新记录
     * 使用ID=0来确保插入新记录
     */
    override suspend fun insertAttendance(attendance: OvertimeRecord): Long {
        val newAttendance = if (attendance.id == 0L) {
            attendance
        } else {
            attendance.copy(id = 0L) // 强制ID为0，确保插入新记录
        }
        val entity = AttendanceEntity.fromRecord(newAttendance)
        return dao.insertOrUpdate(entity)
    }
    
    /**
     * 更新记录
     */
    override suspend fun updateAttendance(attendance: OvertimeRecord) {
        val entity = AttendanceEntity.fromRecord(attendance)
        dao.insertOrUpdate(entity)
    }
    
    /**
     * 删除记录（软删除，标记 isDeleted=1 便于跨设备同步删除操作）
     */
    override suspend fun deleteAttendance(id: Long) {
        dao.markDeleted(id, System.currentTimeMillis())
    }
    
    /**
     * 删除所有记录
     */
    override suspend fun deleteAllAttendance() {
        dao.deleteAll()
    }
    
    /**
     * 获取记录数量
     */
    override suspend fun getCount(): Int {
        return dao.getCount()
    }
}
