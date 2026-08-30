package com.example.personalovertimerecord.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import com.example.personalovertimerecord.R
import com.example.personalovertimerecord.data.OvertimeRecord
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText

class EditAttendanceDialog(
    private val context: Context,
    private val attendance: OvertimeRecord,
    private val onSave: (OvertimeRecord) -> Unit,
    private val onDelete: (Long) -> Unit
) {
    
    private var dialog: Dialog? = null
    private lateinit var etCheckInTime: TextInputEditText
    private lateinit var etCheckOutTime: TextInputEditText
    private lateinit var etManualOvertime: TextInputEditText
    private lateinit var etManualExtra: TextInputEditText
    private lateinit var etNote: TextInputEditText
    
    fun show() {
        dialog = Dialog(context)
        dialog?.setContentView(R.layout.dialog_edit_attendance)
        dialog?.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        
        initViews()
        setupListeners()
        
        dialog?.show()
    }
    
    private fun initViews() {
        dialog?.let { d ->
            val etDate = d.findViewById<TextInputEditText>(R.id.etDate)
            etDate?.setText(attendance.date)
            
            etCheckInTime = d.findViewById(R.id.etCheckInTime)
            etCheckOutTime = d.findViewById(R.id.etCheckOutTime)
            etManualOvertime = d.findViewById(R.id.etManualOvertime)
            etManualExtra = d.findViewById(R.id.etManualExtra)
            etNote = d.findViewById(R.id.etNote)
            
            etCheckInTime.setText(attendance.checkInTime?.substring(0, 5) ?: "")
            etCheckOutTime.setText(attendance.checkOutTime?.substring(0, 5) ?: "")
            
            if (attendance.overtimeHours >= 0) {
                etManualOvertime.setText(attendance.overtimeHours.toString())
            }
            if (attendance.extraHours >= 0) {
                etManualExtra.setText(attendance.extraHours.toString())
            }
            
            etNote.setText(attendance.note ?: "")
        }
    }
    
    private fun setupListeners() {
        dialog?.let { d ->
            d.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSave)?.setOnClickListener {
                saveAttendance()
            }
            
            d.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDelete)?.setOnClickListener {
                showDeleteConfirmation()
            }
        }
    }
    
    private fun saveAttendance() {
        val checkInTimeStr = etCheckInTime.text?.toString()?.trim()
        val checkOutTimeStr = etCheckOutTime.text?.toString()?.trim()
        val manualOvertimeStr = etManualOvertime.text?.toString()?.trim()
        val manualExtraStr = etManualExtra.text?.toString()?.trim()
        val note = etNote.text?.toString()?.trim()
        
        val checkInTime = if (checkInTimeStr.isNullOrEmpty()) null else "${checkInTimeStr}:00"
        val checkOutTime = if (checkOutTimeStr.isNullOrEmpty()) null else "${checkOutTimeStr}:00"
        
        if (checkInTime == null && checkOutTime == null) {
            Toast.makeText(context, "请至少填写一个打卡时间", Toast.LENGTH_SHORT).show()
            return
        }
        
        val manualOvertimeHours = if (manualOvertimeStr.isNullOrEmpty()) {
            -1.0
        } else {
            manualOvertimeStr.toDoubleOrNull() ?: -1.0
        }
        
        val manualExtraHours = if (manualExtraStr.isNullOrEmpty()) {
            -1.0
        } else {
            manualExtraStr.toDoubleOrNull() ?: -1.0
        }
        
        val updatedAttendance = attendance.copy(
            checkInTime = checkInTime,
            checkOutTime = checkOutTime,
            overtimeHours = manualOvertimeHours,
            extraHours = manualExtraHours,
            note = if (note.isNullOrEmpty()) null else note
        )
        
        onSave(updatedAttendance)
        dialog?.dismiss()
    }
    
    private fun showDeleteConfirmation() {
        MaterialAlertDialogBuilder(context)
            .setTitle("删除确认")
            .setMessage("确定要删除这条打卡记录吗？")
            .setPositiveButton("删除") { _, _ ->
                onDelete(attendance.id)
                dialog?.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
