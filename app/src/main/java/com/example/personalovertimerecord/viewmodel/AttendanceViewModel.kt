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

class AttendanceViewModel(application: Application) : AndroidViewModel(application) {
    
    private val storage = AttendanceStorage(application)
    private val repository = AttendanceRepository(storage)
    
    private val _allAttendance = MutableLiveData<List<Attendance>>()
    val allAttendance: LiveData<List<Attendance>> = _allAttendance
    
    init {
        refreshData()
    }
    
    fun refreshData() {
        viewModelScope.launch {
            _allAttendance.value = repository.getAllAttendance()
        }
    }
    
    fun updateAttendance(attendance: Attendance) {
        viewModelScope.launch {
            try {
                repository.updateAttendance(attendance)
                refreshData()
            } catch (e: Exception) {
            }
        }
    }
    
    fun insertAttendance(attendance: Attendance) {
        viewModelScope.launch {
            try {
                repository.insertAttendance(attendance)
                refreshData()
            } catch (e: Exception) {
            }
        }
    }
    
    fun deleteAttendance(id: Long) {
        viewModelScope.launch {
            try {
                repository.deleteAttendance(id)
                refreshData()
            } catch (e: Exception) {
            }
        }
    }
}
