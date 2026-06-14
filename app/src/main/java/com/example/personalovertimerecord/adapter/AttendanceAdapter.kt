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
        settings = newSettings
        notifyDataSetChanged()
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
        private val tvExtra: TextView = itemView.findViewById(R.id.tvExtra)
        private val tvOvertimePay: TextView = itemView.findViewById(R.id.tvOvertimePay)
        private val tvNote: TextView = itemView.findViewById(R.id.tvNote)
        
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
            
            val result = OvertimeCalculator.calculateOvertime(attendance, settings)
            
            tvOvertime.text = Formatter.formatHoursShort(result.overtimeHours)
            tvExtra.text = Formatter.formatHoursShort(result.extraHours)
            tvOvertimePay.text = Formatter.formatMoney(result.estimatedPay)
            
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