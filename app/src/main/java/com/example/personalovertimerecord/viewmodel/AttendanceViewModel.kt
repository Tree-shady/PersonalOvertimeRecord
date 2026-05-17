package com.example.personalovertimerecord.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.example.personalovertimerecord.data.Attendance
import com.example.personalovertimerecord.data.AttendanceStorage
import com.example.personalovertimerecord.repository.AttendanceRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AttendanceViewModel(application: Application) : AndroidViewModel(application) {
    
    private val storage = AttendanceStorage(application)
    private val repository = AttendanceRepository(storage)
    
    private val _allAttendance = MutableLiveData<List<Attendance>>()
    val allAttendance: LiveData<List<Attendance>> = _allAttendance
    
    private val _todayAttendance = MutableLiveData<Attendance?>()
    val todayAttendance: LiveData<Attendance?> = _todayAttendance
    
    private val _isCheckIn = MutableLiveData(false)
    val isCheckIn: LiveData<Boolean> = _isCheckIn
    
    private val _isCheckOut = MutableLiveData(false)
    val isCheckOut: LiveData<Boolean> = _isCheckOut
    
    private val _message = MutableLiveData<String>()
    val message: LiveData<String> = _message
    
    init {
        refreshData()
        loadTodayAttendance()
    }
    
    fun refreshData() {
        _allAttendance.value = repository.getAllAttendance()
    }
    
    fun loadTodayAttendance() {
        viewModelScope.launch {
            val today = repository.getTodayAttendance()
            _todayAttendance.value = today
            _isCheckIn.value = today?.checkInTime != null
            _isCheckOut.value = today?.checkOutTime != null
        }
    }
    
    fun checkIn(note: String? = null) {
        viewModelScope.launch {
            try {
                repository.checkIn(note)
                refreshData()
                loadTodayAttendance()
                val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                _message.value = "上班打卡成功：$time"
            } catch (e: Exception) {
                _message.value = "打卡失败：${e.message}"
            }
        }
    }
    
    fun checkOut(note: String? = null) {
        viewModelScope.launch {
            try {
                val success = repository.checkOut(note)
                if (success) {
                    refreshData()
                    loadTodayAttendance()
                    val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    _message.value = "下班打卡成功：$time"
                } else {
                    _message.value = "请先进行上班打卡"
                }
            } catch (e: Exception) {
                _message.value = "打卡失败：${e.message}"
            }
        }
    }
    
    fun updateAttendance(attendance: Attendance) {
        viewModelScope.launch {
            try {
                repository.updateAttendance(attendance)
                refreshData()
                loadTodayAttendance()
                _message.value = "修改成功"
            } catch (e: Exception) {
                _message.value = "修改失败：${e.message}"
            }
        }
    }
    
    fun deleteAttendance(id: Long) {
        viewModelScope.launch {
            try {
                repository.deleteAttendance(id)
                refreshData()
                loadTodayAttendance()
                _message.value = "删除成功"
            } catch (e: Exception) {
                _message.value = "删除失败：${e.message}"
            }
        }
    }
}
