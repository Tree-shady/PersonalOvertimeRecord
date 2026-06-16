package com.example.personalovertimerecord.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.personalovertimerecord.R
import com.example.personalovertimerecord.data.Attendance
import com.example.personalovertimerecord.data.OvertimeSettings
import com.example.personalovertimerecord.utils.Formatter
import com.example.personalovertimerecord.utils.OvertimeCalculator

class AttendanceAdapter(
    private var settings: OvertimeSettings = OvertimeSettings(),
    private val onItemClick: (Attendance) -> Unit
) : ListAdapter<Attendance, AttendanceAdapter.AttendanceViewHolder>(AttendanceDiffCallback()) {
    
    fun updateSettings(newSettings: OvertimeSettings) {
        if (settings != newSettings) {
            settings = newSettings
            // 只更新当前列表中的项，比 notifyDataSetChanged 更高效
            notifyItemRangeChanged(0, currentList.size)
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttendanceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_attendance, parent, false)
        return AttendanceViewHolder(view, onItemClick)
    }
    
    override fun onBindViewHolder(holder: AttendanceViewHolder, position: Int) {
        holder.bind(getItem(position), settings)
    }
    
    inner class AttendanceViewHolder(
        itemView: View,
        private val onItemClick: (Attendance) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        private val tvDayType: TextView = itemView.findViewById(R.id.tvDayType)
        private val tvOvertime: TextView = itemView.findViewById(R.id.tvOvertime)
        private val tvOvertimeLabel: TextView = itemView.findViewById(R.id.tvOvertimeLabel)
        private val tvExtra: TextView = itemView.findViewById(R.id.tvExtra)
        private val extraLayout: View = itemView.findViewById(R.id.extraLayout)
        private val tvOvertimePay: TextView = itemView.findViewById(R.id.tvOvertimePay)
        private val tvNote: TextView = itemView.findViewById(R.id.tvNote)
        private val leaveLayout: View = itemView.findViewById(R.id.leaveLayout)
        private val tvLeaveInfo: TextView = itemView.findViewById(R.id.tvLeaveInfo)
        
        init {
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
            
            itemView.setOnLongClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                    true
                } else {
                    false
                }
            }
        }
        
        fun bind(attendance: Attendance, settings: OvertimeSettings) {
            tvDate.text = Formatter.formatDate(attendance.date)
            tvDayType.text = Formatter.getDayTypeString(attendance.date)
            
            // 如果是请假，显示请假信息，隐藏加班相关
            if (attendance.isLeave && attendance.leaveHours > 0) {
                leaveLayout.visibility = View.VISIBLE
                extraLayout.visibility = View.GONE
                tvOvertimeLabel.text = "工作时长"
                tvOvertime.text = "${(attendance.leaveHours * 8).toInt()}h"
                tvOvertime.setTextColor(0xFFFF9800.toInt()) // 橙色
                tvOvertimePay.text = Formatter.formatLeaveInfo(attendance.leaveType, attendance.leaveHours)
            } else {
                leaveLayout.visibility = View.GONE
                extraLayout.visibility = View.VISIBLE
                tvOvertimeLabel.text = "加班时长"
                tvOvertime.setTextColor(0xFF2196F3.toInt()) // 蓝色
                
                val result = OvertimeCalculator.calculateOvertime(attendance, settings)
                tvOvertime.text = Formatter.formatHoursShort(result.overtimeHours)
                tvExtra.text = Formatter.formatHoursShort(result.extraHours)
                tvOvertimePay.text = Formatter.formatMoney(result.estimatedPay)
            }
            
            tvNote.text = attendance.note
            tvNote.visibility = if (!attendance.note.isNullOrEmpty()) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }
    
    private class AttendanceDiffCallback : DiffUtil.ItemCallback<Attendance>() {
        
        override fun areItemsTheSame(oldItem: Attendance, newItem: Attendance): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: Attendance, newItem: Attendance): Boolean {
            return oldItem.date == newItem.date &&
                   oldItem.manualOvertimeHours == newItem.manualOvertimeHours &&
                   oldItem.manualExtraHours == newItem.manualExtraHours &&
                   oldItem.note == newItem.note
        }
    }
}