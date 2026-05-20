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
import android.widget.Toast
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
    private val onSaveAttendance: ((Attendance) -> Unit)? = null,
    private val onDeleteAttendance: ((Long) -> Unit)? = null
) : Dialog(context) {
    
    private lateinit var etOvertimeHours: EditText
    private lateinit var etExtraHours: EditText
    private lateinit var etNote: EditText
    private lateinit var tvTitle: TextView
    private lateinit var btnDelete: View
    
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
        btnDelete = findViewById(R.id.btnDelete)
        
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
            btnDelete.visibility = View.VISIBLE
        } else {
            tvTitle.text = "添加加班记录"
        }
    }
    
    private fun setupListeners() {
        btnDelete.setOnClickListener {
            if (existingAttendance != null && onDeleteAttendance != null) {
                onDeleteAttendance.invoke(existingAttendance.id)
                dismiss()
            }
        }
        
        findViewById<View>(R.id.btnCancel).setOnClickListener {
            dismiss()
        }
        
        findViewById<View>(R.id.btnSave).setOnClickListener {
            saveData()
        }
    }
    
    private fun saveData() {
        val overtimeHoursStr = etOvertimeHours.text?.toString() ?: ""
        val extraHoursStr = etExtraHours.text?.toString() ?: ""
        val note = etNote.text?.toString() ?: ""
        
        val overtimeHours = overtimeHoursStr.toDoubleOrNull()
        val extraHours = extraHoursStr.toDoubleOrNull()
        
        when {
            overtimeHours == null && extraHours == null -> {
                Toast.makeText(context, "请输入加班时长或加点时长", Toast.LENGTH_SHORT).show()
                return
            }
            overtimeHours != null && overtimeHours < 0 -> {
                Toast.makeText(context, "加班时长不能为负数", Toast.LENGTH_SHORT).show()
                return
            }
            extraHours != null && extraHours < 0 -> {
                Toast.makeText(context, "加点时长不能为负数", Toast.LENGTH_SHORT).show()
                return
            }
            overtimeHours != null && overtimeHours > 24 -> {
                Toast.makeText(context, "单日加班时长不能超过24小时", Toast.LENGTH_SHORT).show()
                return
            }
            extraHours != null && extraHours > 24 -> {
                Toast.makeText(context, "单日加点时长不能超过24小时", Toast.LENGTH_SHORT).show()
                return
            }
            (overtimeHours == 0.0 || overtimeHours == null) && 
            (extraHours == 0.0 || extraHours == null) -> {
                Toast.makeText(context, "请输入有效的加班或加点时长", Toast.LENGTH_SHORT).show()
                return
            }
        }
        
        val finalOvertime = overtimeHours ?: 0.0
        val finalExtra = extraHours ?: 0.0
        val dateStr = String.format(Locale.getDefault(), "%d-%02d-%02d", year, month + 1, day)
        
        if (existingRecord != null && onSaveOvertimeRecord != null) {
            val updatedRecord = existingRecord.copy(
                overtimeHours = finalOvertime,
                extraHours = finalExtra,
                note = if (note.isNullOrEmpty()) null else note
            )
            onSaveOvertimeRecord?.invoke(updatedRecord)
        } else if (existingAttendance != null && onSaveAttendance != null) {
            val updatedAttendance = existingAttendance.copy(
                manualOvertimeHours = if (finalOvertime > 0) finalOvertime else -1.0,
                manualExtraHours = if (finalExtra > 0) finalExtra else -1.0,
                note = if (note.isNullOrEmpty()) null else note
            )
            onSaveAttendance?.invoke(updatedAttendance)
        } else if (onSaveOvertimeRecord != null) {
            val newRecord = OvertimeRecord(
                date = dateStr,
                overtimeHours = finalOvertime,
                extraHours = finalExtra,
                note = if (note.isNullOrEmpty()) null else note
            )
            onSaveOvertimeRecord?.invoke(newRecord)
        } else if (onSaveAttendance != null) {
            val newAttendance = Attendance(
                id = 0, // 让Room数据库自动生成ID
                date = dateStr,
                manualOvertimeHours = if (finalOvertime > 0) finalOvertime else -1.0,
                manualExtraHours = if (finalExtra > 0) finalExtra else -1.0,
                note = if (note.isNullOrEmpty()) null else note
            )
            onSaveAttendance?.invoke(newAttendance)
        }
        
        dismiss()
    }
}
