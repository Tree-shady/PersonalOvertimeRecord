package com.example.personalovertimerecord.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.personalovertimerecord.OvertimeApplication
import com.example.personalovertimerecord.data.Attendance
import com.example.personalovertimerecord.repository.AttendanceRepository
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * 考勤记录ViewModel
 * 使用Repository进行数据访问，支持Flow响应式更新
 */
class AttendanceViewModel(application: Application) : AndroidViewModel(application) {
    
    companion object {
        private const val TAG = "AttendanceViewModel"
    }
    
    // 使用Application获取Repository单例
    private val repository: AttendanceRepository = OvertimeApplication.getAttendanceRepository()
    
    // LiveData版本的数据列表（通过Flow转换）
    private val _allAttendance: LiveData<List<Attendance>>
    val allAttendance: LiveData<List<Attendance>> = _allAttendance
    
    // 错误消息
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage
    
    // 加载状态
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading
    
    init {
        // 将Flow转换为LiveData
        _allAttendance = repository.getAllAttendanceFlow()
            .catch { e ->
                Log.e(TAG, "Error loading attendance data", e)
                _errorMessage.postValue("加载数据失败: ${e.message}")
            }
            .asLiveData()
    }
    
    /**
     * 手动刷新数据
     * 虽然Flow会自动更新，但某些场景需要手动刷新
     */
    fun refreshData() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                // Flow会自动更新，这里可以添加额外逻辑
                Log.d(TAG, "Data refresh triggered")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh data", e)
                _errorMessage.value = "刷新数据失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 更新记录
     */
    fun updateAttendance(attendance: Attendance) {
        viewModelScope.launch {
            try {
                repository.updateAttendance(attendance)
                Log.d(TAG, "Attendance updated: ${attendance.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update attendance", e)
                _errorMessage.value = "更新记录失败: ${e.message}"
            }
        }
    }
    
    /**
     * 插入新记录
     */
    fun insertAttendance(attendance: Attendance) {
        viewModelScope.launch {
            try {
                val id = repository.insertAttendance(attendance)
                Log.d(TAG, "Attendance inserted with ID: $id")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to insert attendance", e)
                _errorMessage.value = "添加记录失败: ${e.message}"
            }
        }
    }
    
    /**
     * 保存记录（插入或更新）
     */
    fun saveAttendance(attendance: Attendance) {
        viewModelScope.launch {
            try {
                val id = repository.saveAttendance(attendance)
                Log.d(TAG, "Attendance saved with ID: $id")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save attendance", e)
                _errorMessage.value = "保存记录失败: ${e.message}"
            }
        }
    }
    
    /**
     * 删除记录
     */
    fun deleteAttendance(id: Long) {
        viewModelScope.launch {
            try {
                repository.deleteAttendance(id)
                Log.d(TAG, "Attendance deleted: $id")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete attendance", e)
                _errorMessage.value = "删除记录失败: ${e.message}"
            }
        }
    }
    
    /**
     * 清除错误消息
     */
    fun clearErrorMessage() {
        _errorMessage.value = null
    }
}