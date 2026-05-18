package com.example.personalovertimerecord

import android.app.Application
import com.example.personalovertimerecord.data.db.AppDatabase
import com.example.personalovertimerecord.repository.AttendanceRepository
import com.example.personalovertimerecord.repository.RoomAttendanceRepository

/**
 * 应用全局Application类
 * 管理数据库单例和仓储单例
 */
class OvertimeApplication : Application() {
    
    // 数据库单例
    val database: AppDatabase by lazy {
        AppDatabase.getDatabase(this)
    }
    
    // 考勤记录仓储单例
    val attendanceRepository: AttendanceRepository by lazy {
        RoomAttendanceRepository(database)
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
    }
    
    companion object {
        private lateinit var instance: OvertimeApplication
        
        fun getInstance(): OvertimeApplication = instance
        
        // 提供便捷访问方法
        fun getDatabase(): AppDatabase = instance.database
        fun getAttendanceRepository(): AttendanceRepository = instance.attendanceRepository
    }
}