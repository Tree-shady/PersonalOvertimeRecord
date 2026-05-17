package com.example.personalovertimerecord.repository

import com.example.personalovertimerecord.data.Attendance
import com.example.personalovertimerecord.data.AttendanceStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AttendanceRepository(private val storage: AttendanceStorage) {
    
    fun getAllAttendance(): List<Attendance> = storage.getAllAttendance()
    
    suspend fun getTodayAttendance(): Attendance? = withContext(Dispatchers.IO) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        storage.getAttendanceByDate(today)
    }
    
    suspend fun checkIn(note: String? = null): Long = withContext(Dispatchers.IO) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val timestamp = System.currentTimeMillis()
        
        val existingAttendance = storage.getAttendanceByDate(today)
        
        if (existingAttendance != null && existingAttendance.checkInTime == null) {
            val updatedAttendance = existingAttendance.copy(
                checkInTime = time,
                checkInTimestamp = timestamp,
                note = note
            )
            storage.saveAttendance(updatedAttendance)
            existingAttendance.id
        } else if (existingAttendance == null) {
            val newAttendance = Attendance(
                id = 0L,
                date = today,
                checkInTime = time,
                checkInTimestamp = timestamp,
                note = note
            )
            storage.insertAttendance(newAttendance)
        } else {
            existingAttendance.id
        }
    }
    
    suspend fun checkOut(note: String? = null): Boolean = withContext(Dispatchers.IO) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val existingAttendance = storage.getAttendanceByDate(today)
        
        if (existingAttendance != null && existingAttendance.checkOutTime == null) {
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val timestamp = System.currentTimeMillis()
            val updatedAttendance = existingAttendance.copy(
                checkOutTime = time,
                checkOutTimestamp = timestamp,
                note = note ?: existingAttendance.note
            )
            storage.saveAttendance(updatedAttendance)
            true
        } else {
            false
        }
    }
    
    suspend fun updateAttendance(attendance: Attendance) = withContext(Dispatchers.IO) {
        storage.updateAttendance(attendance)
    }
    
    suspend fun deleteAttendance(id: Long) = withContext(Dispatchers.IO) {
        storage.deleteAttendance(id)
    }
}
