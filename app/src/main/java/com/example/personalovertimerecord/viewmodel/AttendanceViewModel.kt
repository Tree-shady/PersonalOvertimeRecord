package com.example.personalovertimerecord.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.personalovertimerecord.OvertimeApplication
import com.example.personalovertimerecord.data.Attendance
import com.example.personalovertimerecord.repository.AttendanceRepository
import com.example.personalovertimerecord.utils.AppLogger
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 考勤记录ViewModel
 * 使用Repository进行数据访问，支持Flow响应式更新
 */
class AttendanceViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository: AttendanceRepository = OvertimeApplication.getAttendanceRepository()
    
    // 使用 StateFlow 替代 LiveData，更好支持刷新
    private val _allAttendanceState = MutableStateFlow<List<Attendance>>(emptyList())
    val allAttendanceState: StateFlow<List<Attendance>> = _allAttendanceState.asStateFlow()
    
    // 为了保持向后兼容，保留 LiveData
    val allAttendance: LiveData<List<Attendance>> get() = _allAttendanceState.asLiveData()
    
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage
    
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading
    
    init {
        loadAttendanceData()
    }
    
    private fun loadAttendanceData() {
        viewModelScope.launch {
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
        viewModelScope.launch {
            try {
                _isLoading.value = true
                repository.insertAttendance(attendance)
                AppLogger.sensitive("Add Attendance", attendance.date, true)
            } catch (e: Exception) {
                AppLogger.e("Error adding attendance", e)
                _errorMessage.value = "添加记录失败，请稍后重试"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun updateAttendance(attendance: Attendance) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                repository.updateAttendance(attendance)
                AppLogger.sensitive("Update Attendance", attendance.date, true)
            } catch (e: Exception) {
                AppLogger.e("Error updating attendance", e)
                _errorMessage.value = "更新记录失败，请稍后重试"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun deleteAttendance(attendance: Attendance) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                repository.deleteAttendance(attendance.id)
                AppLogger.sensitive("Delete Attendance", attendance.date, true)
            } catch (e: Exception) {
                AppLogger.e("Error deleting attendance", e)
                _errorMessage.value = "删除记录失败，请稍后重试"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 获取指定日期的考勤记录
     * 使用 Flow 异步获取，避免在协程中使用 MutableLiveData
     */
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
    
    /**
     * 刷新数据 - 重新从数据库加载
     * StateFlow 已经响应式更新，此方法主要用于强制刷新或重置状态
     */
    fun refreshData() {
        // 不需要重新创建 Flow，因为数据变化会自动通过 Flow 通知
        // 这里可以添加一些刷新状态提示
        _errorMessage.value = null
        AppLogger.d("Refreshing data...")
    }
}
