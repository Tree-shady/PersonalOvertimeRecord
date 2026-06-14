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
    
    fun addAttendance(attendance: Attendance) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isLoading.postValue(true)
                repository.insertAttendance(attendance)
                AppLogger.sensitive("Add Attendance", attendance.date, true)
            } catch (e: Exception) {
                AppLogger.e("Error adding attendance", e)
                _errorMessage.postValue("添加记录失败，请稍后重试")
            } finally {
                _isLoading.postValue(false)
            }
        }
    }
    
    fun updateAttendance(attendance: Attendance) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isLoading.postValue(true)
                repository.updateAttendance(attendance)
                AppLogger.sensitive("Update Attendance", attendance.date, true)
            } catch (e: Exception) {
                AppLogger.e("Error updating attendance", e)
                _errorMessage.postValue("更新记录失败，请稍后重试")
            } finally {
                _isLoading.postValue(false)
            }
        }
    }
    
    fun deleteAttendance(attendance: Attendance) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isLoading.postValue(true)
                repository.deleteAttendance(attendance.id)
                AppLogger.sensitive("Delete Attendance", attendance.date, true)
            } catch (e: Exception) {
                AppLogger.e("Error deleting attendance", e)
                _errorMessage.postValue("删除记录失败，请稍后重试")
            } finally {
                _isLoading.postValue(false)
            }
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
    
    fun refreshData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val freshData = repository.getAllAttendance()
                _allAttendanceState.value = freshData
                AppLogger.d("Data refreshed successfully")
            } catch (e: Exception) {
                AppLogger.e("Error refreshing data", e)
                _errorMessage.postValue("刷新数据失败，请稍后重试")
            }
        }
    }
}