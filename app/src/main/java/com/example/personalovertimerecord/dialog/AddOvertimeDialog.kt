package com.example.personalovertimerecord.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import com.example.personalovertimerecord.R
import com.example.personalovertimerecord.data.Attendance
import com.example.personalovertimerecord.data.AttendanceStorage
import com.example.personalovertimerecord.data.OvertimeSettings
import com.example.personalovertimerecord.data.SettingsManager
import com.example.personalovertimerecord.utils.OvertimeCalculator
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AddOvertimeDialog(
    private val context: Context,
    private val year: Int,
    private val month: Int,
    private val day: Int,
    private val existingAttendance: Attendance?,
    private val onSave: (Attendance) -> Unit,
    private val onDismiss: () -> Unit
) {
    
    private var dialog: Dialog? = null
    private lateinit var settingsManager: SettingsManager
    private lateinit var attendanceStorage: AttendanceStorage
    
    fun show() {
        settingsManager = SettingsManager(context)
        attendanceStorage = AttendanceStorage(context)
        
        dialog = Dialog(context)
        dialog?.setContentView(R.layout.dialog_add_overtime)
        dialog?.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        
        initViews()
        setupListeners()
        
        dialog?.setOnDismissListener { onDismiss() }
        dialog?.show()
    }
    
    private fun initViews() {
        dialog?.let { d ->
            val tvTitle = d.findViewById<TextView>(R.id.tvTitle)
            val tvDate = d.findViewById<TextView>(R.id.tvDate)
            val tvDayType = d.findViewById<TextView>(R.id.tvDayType)
            val etOvertimeHours = d.findViewById<TextInputEditText>(R.id.etOvertimeHours)
            val etExtraHours = d.findViewById<TextInputEditText>(R.id.etExtraHours)
            val etNote = d.findViewById<TextInputEditText>(R.id.etNote)
            
            if (existingAttendance != null) {
                tvTitle.text = "修改加班记录"
                tvTitle.setTextColor(0xFFFF9800.toInt())
            } else {
                tvTitle.text = "添加加班记录"
                tvTitle.setTextColor(0xFF6200EE.toInt())
            }
            
            val dateStr = String.format(Locale.getDefault(), "%d年%02d月%02d日", year, month + 1, day)
            tvDate.text = dateStr
            
            val fullDateStr = String.format(Locale.getDefault(), "%d-%02d-%02d", year, month + 1, day)
            val dayType = getDayType(fullDateStr)
            tvDayType.text = dayType
            
            existingAttendance?.let { attendance ->
                if (attendance.manualOvertimeHours >= 0) {
                    etOvertimeHours.setText(attendance.manualOvertimeHours.toString())
                }
                if (attendance.manualExtraHours >= 0) {
                    etExtraHours.setText(attendance.manualExtraHours.toString())
                }
                attendance.note?.let {
                    etNote.setText(it)
                }
            }
        }
    }
    
    private fun getDayType(dateStr: String): String {
        val settings = settingsManager.getSettings()
        val isWeekend = isWeekend(dateStr)
        val isHoliday = isHoliday(dateStr)
        
        return when {
            isHoliday -> "法定假日"
            isWeekend -> "周末"
            else -> "工作日"
        }
    }
    
    private fun isWeekend(dateStr: String): Boolean {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = format.parse(dateStr) ?: return false
            val calendar = Calendar.getInstance()
            calendar.time = date
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
        } catch (e: Exception) {
            false
        }
    }
    
    private fun isHoliday(dateStr: String): Boolean {
        val holidays = setOf(
            "01-01", "01-02", "01-03", 
            "02-10", "02-11", "02-12", "02-13", "02-14", "02-15", "02-16", "02-17", 
            "04-04", "04-05", "04-06", 
            "05-01", "05-02", "05-03", "05-04", "05-05", 
            "06-10", 
            "09-15", "09-16", "09-17", 
            "10-01", "10-02", "10-03", "10-04", "10-05", "10-06", "10-07"
        )
        
        return try {
            val monthDay = dateStr.substring(5)
            holidays.contains(monthDay)
        } catch (e: Exception) {
            false
        }
    }
    
    private fun setupListeners() {
        dialog?.let { d ->
            d.findViewById<MaterialButton>(R.id.btnSave)?.setOnClickListener {
                saveOvertime()
            }
            
            d.findViewById<MaterialButton>(R.id.btnCancel)?.setOnClickListener {
                dialog?.dismiss()
            }
        }
    }
    
    private fun saveOvertime() {
        dialog?.let { d ->
            val etOvertimeHours = d.findViewById<TextInputEditText>(R.id.etOvertimeHours)
            val etExtraHours = d.findViewById<TextInputEditText>(R.id.etExtraHours)
            val etNote = d.findViewById<TextInputEditText>(R.id.etNote)
            
            val overtimeHoursStr = etOvertimeHours.text?.toString()?.trim()
            val extraHoursStr = etExtraHours.text?.toString()?.trim()
            val note = etNote.text?.toString()?.trim()
            
            val overtimeHours = if (overtimeHoursStr.isNullOrEmpty()) {
                -1.0
            } else {
                overtimeHoursStr.toDoubleOrNull() ?: -1.0
            }
            
            val extraHours = if (extraHoursStr.isNullOrEmpty()) {
                -1.0
            } else {
                extraHoursStr.toDoubleOrNull() ?: -1.0
            }
            
            if (overtimeHours < 0 && extraHours < 0) {
                Toast.makeText(context, "请至少填写加班时长或加点时长", Toast.LENGTH_SHORT).show()
                return
            }
            
            val dateStr = String.format(Locale.getDefault(), "%d-%02d-%02d", year, month + 1, day)
            
            val attendance = if (existingAttendance != null) {
                existingAttendance.copy(
                    manualOvertimeHours = overtimeHours,
                    manualExtraHours = extraHours,
                    note = if (note.isNullOrEmpty()) null else note
                )
            } else {
                Attendance(
                    id = System.currentTimeMillis(),
                    date = dateStr,
                    manualOvertimeHours = overtimeHours,
                    manualExtraHours = extraHours,
                    note = if (note.isNullOrEmpty()) null else note
                )
            }
            
            onSave(attendance)
            dialog?.dismiss()
        }
    }
}
