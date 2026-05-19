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
    private lateinit var _allAttendance: LiveData<List<Attendance>>
    val allAttendance: LiveData<List<Attendance>> get() = _allAttendance
    
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
     * 添加考勤记录
     */
    fun addAttendance(attendance: Attendance) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                repository.insertAttendance(attendance)
                Log.d(TAG, "Attendance added: ${attendance.date}")
            } catch (e: Exception) {
                Log.e(TAG, "Error adding attendance", e)
                _errorMessage.value = "添加记录失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 更新考勤记录
     */
    fun updateAttendance(attendance: Attendance) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                repository.updateAttendance(attendance)
                Log.d(TAG, "Attendance updated: ${attendance.date}")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating attendance", e)
                _errorMessage.value = "更新记录失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 删除考勤记录
     */
    fun deleteAttendance(attendance: Attendance) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                repository.deleteAttendance(attendance.id)
                Log.d(TAG, "Attendance deleted: ${attendance.date}")
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting attendance", e)
                _errorMessage.value = "删除记录失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 按日期获取考勤记录
     */
    fun getAttendanceByDate(date: String): LiveData<Attendance?> {
        val result = MutableLiveData<Attendance?>()
        viewModelScope.launch {
            try {
                val attendance = repository.getAttendanceByDate(date)
                result.postValue(attendance)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting attendance by date", e)
                result.postValue(null)
            }
        }
        return result
    }
    
    /**
     * 清除错误消息
     */
    fun clearError() {
        _errorMessage.value = null
    }
}
