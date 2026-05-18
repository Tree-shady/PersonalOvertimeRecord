package com.example.personalovertimerecord.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO - 加班记录数据访问对象
 */
@Dao
interface AttendanceDao {

    /**
     * 获取所有记录，按日期降序
     */
    @Query("SELECT * FROM attendance_records ORDER BY date DESC")
    fun getAllRecords(): Flow<List<AttendanceEntity>>

    /**
     * 获取所有记录列表（非Flow）
     */
    @Query("SELECT * FROM attendance_records ORDER BY date DESC")
    suspend fun getAllRecordsSync(): List<AttendanceEntity>

    /**
     * 根据日期获取记录
     */
    @Query("SELECT * FROM attendance_records WHERE date = :date LIMIT 1")
    suspend fun getRecordByDate(date: String): AttendanceEntity?

    /**
     * 根据ID获取记录
     */
    @Query("SELECT * FROM attendance_records WHERE id = :id LIMIT 1")
    suspend fun getRecordById(id: Long): AttendanceEntity?

    /**
     * 插入或更新记录
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(record: AttendanceEntity): Long

    /**
     * 批量插入记录
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<AttendanceEntity>)

    /**
     * 删除记录
     */
    @Delete
    suspend fun delete(record: AttendanceEntity)

    /**
     * 根据ID删除记录
     */
    @Query("DELETE FROM attendance_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * 删除所有记录
     */
    @Query("DELETE FROM attendance_records")
    suspend fun deleteAll()

    /**
     * 获取记录数量
     */
    @Query("SELECT COUNT(*) FROM attendance_records")
    suspend fun getCount(): Int
}