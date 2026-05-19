package com.example.personalovertimerecord.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.TextView
import com.example.personalovertimerecord.R
import com.example.personalovertimerecord.data.Attendance
import com.example.personalovertimerecord.data.OvertimeRecord
import java.util.Calendar
import java.util.Locale

class AddOvertimeDialog(
    context: Context,
    private val year: Int,
    private val month: Int,
    private val day: Int,
    private val existingRecord: OvertimeRecord? = null,
    private val existingAttendance: Attendance? = null,
    private val onSaveOvertimeRecord: ((OvertimeRecord) -> Unit)? = null,
    private val onSaveAttendance: ((Attendance) -> Unit)? = null
) : Dialog(context) {
    
    private lateinit var etOvertimeHours: EditText
    private lateinit var etExtraHours: EditText
    private lateinit var etNote: EditText
    private lateinit var tvTitle: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_add_overtime)
        
        window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        
        initViews()
        setupExistingData()
        setupListeners()
    }
    
    private fun initViews() {
        etOvertimeHours = findViewById(R.id.etOvertimeHours)
        etExtraHours = findViewById(R.id.etExtraHours)
        etNote = findViewById(R.id.etNote)
        tvTitle = findViewById(R.id.tvTitle)
        
        val tvDate = findViewById<TextView>(R.id.tvDate)
        val dateStr = String.format(Locale.getDefault(), "%d-%02d-%02d", year, month + 1, day)
        tvDate.text = dateStr
    }
    
    private fun setupExistingData() {
        if (existingRecord != null) {
            tvTitle.text = "编辑加班记录"
            etOvertimeHours.setText(existingRecord.overtimeHours.toString())
            etExtraHours.setText(existingRecord.extraHours.toString())
            etNote.setText(existingRecord.note ?: "")
        } else if (existingAttendance != null) {
            tvTitle.text = "编辑考勤记录"
            if (existingAttendance.manualOvertimeHours > 0) {
                etOvertimeHours.setText(existingAttendance.manualOvertimeHours.toString())
            }
            if (existingAttendance.manualExtraHours > 0) {
                etExtraHours.setText(existingAttendance.manualExtraHours.toString())
            }
            etNote.setText(existingAttendance.note ?: "")
        } else {
            tvTitle.text = "添加加班记录"
        }
    }
    
    private fun setupListeners() {
        findViewById<View>(R.id.btnCancel).setOnClickListener {
            dismiss()
        }
        
        findViewById<View>(R.id.btnSave).setOnClickListener {
            saveData()
        }
    }
    
    private fun saveData() {
        val overtimeHours = etOvertimeHours.text.toString().toDoubleOrNull() ?: 0.0
        val extraHours = etExtraHours.text.toString().toDoubleOrNull() ?: 0.0
        val note = etNote.text.toString()
        
        if (overtimeHours <= 0 && extraHours <= 0) {
            return
        }
        
        val dateStr = String.format(Locale.getDefault(), "%d-%02d-%02d", year, month + 1, day)
        
        if (existingRecord != null && onSaveOvertimeRecord != null) {
            val updatedRecord = existingRecord.copy(
                overtimeHours = overtimeHours,
                extraHours = extraHours,
                note = if (note.isNullOrEmpty()) null else note
            )
            onSaveOvertimeRecord?.invoke(updatedRecord)
        } else if (existingAttendance != null && onSaveAttendance != null) {
            val updatedAttendance = existingAttendance.copy(
                manualOvertimeHours = if (overtimeHours > 0) overtimeHours else -1.0,
                manualExtraHours = if (extraHours > 0) extraHours else -1.0,
                note = if (note.isNullOrEmpty()) null else note
            )
            onSaveAttendance?.invoke(updatedAttendance)
        } else if (onSaveOvertimeRecord != null) {
            val newRecord = OvertimeRecord(
                date = dateStr,
                overtimeHours = overtimeHours,
                extraHours = extraHours,
                note = if (note.isNullOrEmpty()) null else note
            )
            onSaveOvertimeRecord?.invoke(newRecord)
        } else if (onSaveAttendance != null) {
            val newAttendance = Attendance(
                id = System.currentTimeMillis(),
                date = dateStr,
                manualOvertimeHours = if (overtimeHours > 0) overtimeHours else -1.0,
                manualExtraHours = if (extraHours > 0) extraHours else -1.0,
                note = if (note.isNullOrEmpty()) null else note
            )
            onSaveAttendance?.invoke(newAttendance)
        }
        
        dismiss()
    }
}
