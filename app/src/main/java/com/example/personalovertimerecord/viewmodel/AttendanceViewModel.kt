package com.example.personalovertimerecord.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.personalovertimerecord.OvertimeApplication
import com.example.personalovertimerecord.data.Attendance
import com.example.personalovertimerecord.repository.AttendanceRepository
import com.example.personalovertimerecord.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class AttendanceViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository: AttendanceRepository = OvertimeApplication.getAttendanceRepository()
    
    private val _allAttendanceState = MutableStateFlow<List<Attendance>>(emptyList())
    val allAttendanceState: StateFlow<List<Attendance>> = _allAttendanceState.asStateFlow()
    
    val allAttendance: LiveData<List<Attendance>> get() = _allAttendanceState.asLiveData()
    
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage
    
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading
    
    init {
        loadAttendanceData()
    }
    
    private fun loadAttendanceData() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.getAllAttendanceFlow()
                .catch { e ->
                    AppLogger.e("Error loading attendance data", e)
                    _errorMessage.postValue("加载数据失败，请稍后重试")
                }
                .collect { attendanceList ->
                    _allAttendanceState.value = attendanceList
                }
        }
    }
    
    /**
     * 封装带加载状态的数据库操作
     */
    private inline fun executeWithLoading(
        operation: String,
        crossinline block: suspend () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isLoading.postValue(true)
                block()
            } catch (e: Exception) {
                AppLogger.e("Error in $operation", e)
                _errorMessage.postValue("${operation}失败，请稍后重试")
            } finally {
                _isLoading.postValue(false)
            }
        }
    }
    
    fun addAttendance(attendance: Attendance) {
        executeWithLoading("添加记录") {
            repository.insertAttendance(attendance)
            AppLogger.sensitive("Add Attendance", attendance.date, true)
        }
    }
    
    fun updateAttendance(attendance: Attendance) {
        executeWithLoading("更新记录") {
            repository.updateAttendance(attendance)
            AppLogger.sensitive("Update Attendance", attendance.date, true)
        }
    }
    
    fun deleteAttendance(attendance: Attendance) {
        executeWithLoading("删除记录") {
            repository.deleteAttendance(attendance.id)
            AppLogger.sensitive("Delete Attendance", attendance.date, true)
        }
    }
    
    suspend fun getAttendanceByDate(date: String): Attendance? {
        return try {
            repository.getAttendanceByDate(date)
        } catch (e: Exception) {
            AppLogger.e("Error getting attendance by date", e)
            _errorMessage.postValue("获取记录失败，请稍后重试")
            null
        }
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
}