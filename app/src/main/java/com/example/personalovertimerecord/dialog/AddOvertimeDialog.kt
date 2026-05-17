package com.example.personalovertimerecord.dialog

import android.app.Dialog
import android.content.Context
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import com.example.personalovertimerecord.R
import com.example.personalovertimerecord.data.Attendance
import com.example.personalovertimerecord.data.OvertimeRecord
import com.example.personalovertimerecord.data.SettingsManager
import com.example.personalovertimerecord.utils.HolidayManager
import com.example.personalovertimerecord.utils.InputValidator
import com.example.personalovertimerecord.utils.ValidationResult
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import java.util.Locale

class AddOvertimeDialog(
    private val context: Context,
    private val year: Int,
    private val month: Int,
    private val day: Int,
    private val existingRecord: OvertimeRecord? = null,
    private val existingAttendance: Attendance? = null,
    private val onSaveOvertimeRecord: ((OvertimeRecord) -> Unit)? = null,
    private val onSaveAttendance: ((Attendance) -> Unit)? = null,
    private val onDismiss: () -> Unit
) {
    
    private var dialog: Dialog? = null
    private lateinit var settingsManager: SettingsManager
    
    fun show() {
        settingsManager = SettingsManager(context)
        
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
            
            val hasExisting = existingRecord != null || existingAttendance != null
            
            if (hasExisting) {
                tvTitle.text = "修改加班记录"
                tvTitle.setTextColor(0xFFFF9800.toInt())
            } else {
                tvTitle.text = "添加加班记录"
                tvTitle.setTextColor(0xFF6200EE.toInt())
            }
            
            val dateStr = String.format(Locale.getDefault(), "%d年%02d月%02d日", year, month + 1, day)
            tvDate.text = dateStr
            
            val fullDateStr = String.format(Locale.getDefault(), "%d-%02d-%02d", year, month + 1, day)
            val dayType = HolidayManager.getDayType(fullDateStr)
            tvDayType.text = HolidayManager.getDayTypeName(dayType)
            
            existingRecord?.let { record ->
                if (record.overtimeHours > 0) {
                    etOvertimeHours.setText(record.overtimeHours.toString())
                }
                if (record.extraHours > 0) {
                    etExtraHours.setText(record.extraHours.toString())
                }
                record.note?.let {
                    etNote.setText(it)
                }
            }
            
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
            
            val overtimeValidation = InputValidator.validateOvertimeHours(overtimeHoursStr)
            if (overtimeValidation is ValidationResult.Error) {
                Toast.makeText(context, overtimeValidation.message, Toast.LENGTH_SHORT).show()
                return
            }
            
            val extraValidation = InputValidator.validateExtraHours(extraHoursStr)
            if (extraValidation is ValidationResult.Error) {
                Toast.makeText(context, extraValidation.message, Toast.LENGTH_SHORT).show()
                return
            }
            
            val noteValidation = InputValidator.validateNote(note)
            if (noteValidation is ValidationResult.Error) {
                Toast.makeText(context, noteValidation.message, Toast.LENGTH_SHORT).show()
                return
            }
            
            val overtimeHours = if (overtimeHoursStr.isNullOrEmpty()) {
                0.0
            } else {
                overtimeHoursStr.toDoubleOrNull() ?: 0.0
            }
            
            val extraHours = if (extraHoursStr.isNullOrEmpty()) {
                0.0
            } else {
                extraHoursStr.toDoubleOrNull() ?: 0.0
            }
            
            if (overtimeHours <= 0 && extraHours <= 0) {
                Toast.makeText(context, "请至少填写加班时长或加点时长", Toast.LENGTH_SHORT).show()
                return
            }
            
            val dateStr = String.format(Locale.getDefault(), "%d-%02d-%02d", year, month + 1, day)
            
            if (existingRecord != null && onSaveOvertimeRecord != null) {
                val updatedRecord = existingRecord.copy(
                    overtimeHours = overtimeHours,
                    extraHours = extraHours,
                    note = if (note.isNullOrEmpty()) null else note
                )
                onSaveOvertimeRecord(updatedRecord)
            } else if (existingAttendance != null && onSaveAttendance != null) {
                val updatedAttendance = existingAttendance.copy(
                    manualOvertimeHours = if (overtimeHours > 0) overtimeHours else -1.0,
                    manualExtraHours = if (extraHours > 0) extraHours else -1.0,
                    note = if (note.isNullOrEmpty()) null else note
                )
                onSaveAttendance(updatedAttendance)
            } else if (onSaveOvertimeRecord != null) {
                val newRecord = OvertimeRecord(
                    date = dateStr,
                    overtimeHours = overtimeHours,
                    extraHours = extraHours,
                    note = if (note.isNullOrEmpty()) null else note
                )
                onSaveOvertimeRecord(newRecord)
            } else if (onSaveAttendance != null) {
                val newAttendance = Attendance(
                    id = System.currentTimeMillis(),
                    date = dateStr,
                    manualOvertimeHours = if (overtimeHours > 0) overtimeHours else -1.0,
                    manualExtraHours = if (extraHours > 0) extraHours else -1.0,
                    note = if (note.isNullOrEmpty()) null else note
                )
                onSaveAttendance(newAttendance)
            }
            
            dialog?.dismiss()
        }
    }
}
