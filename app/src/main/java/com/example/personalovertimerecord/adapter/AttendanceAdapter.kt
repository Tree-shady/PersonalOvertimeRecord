package com.example.personalovertimerecord.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.personalovertimerecord.R
import com.example.personalovertimerecord.data.Attendance
import com.example.personalovertimerecord.data.OvertimeSettings
import com.example.personalovertimerecord.data.SettingsManager
import com.example.personalovertimerecord.utils.Formatter
import com.example.personalovertimerecord.utils.OvertimeCalculator
import java.util.Locale

class AttendanceAdapter(
    private val onItemClick: (Attendance) -> Unit
) : RecyclerView.Adapter<AttendanceAdapter.AttendanceViewHolder>() {
    
    private var attendanceList: List<Attendance> = emptyList()
    private lateinit var settingsManager: SettingsManager
    private var settings: OvertimeSettings = OvertimeSettings()
    
    fun submitList(list: List<Attendance>) {
        val sortedList = list.sortedByDescending { it.date }
        val diffCallback = AttendanceDiffCallback(attendanceList, sortedList)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        
        attendanceList = sortedList
        diffResult.dispatchUpdatesTo(this)
    }
    
    fun setSettingsManager(manager: SettingsManager) {
        settingsManager = manager
        settings = manager.getSettings()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttendanceViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_attendance, parent, false)
        if (!::settingsManager.isInitialized) {
            settingsManager = SettingsManager(parent.context)
            settings = settingsManager.getSettings()
        }
        return AttendanceViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: AttendanceViewHolder, position: Int) {
        holder.bind(attendanceList[position])
    }
    
    override fun getItemCount(): Int = attendanceList.size
    
    inner class AttendanceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
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
                    onItemClick(attendanceList[position])
                }
            }
            
            itemView.setOnLongClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(attendanceList[position])
                    true
                } else {
                    false
                }
            }
        }
        
        fun bind(attendance: Attendance) {
            tvDate.text = Formatter.formatDate(attendance.date)
            tvDayType.text = Formatter.getDayTypeString(attendance.date)
            
            val result = OvertimeCalculator.calculateOvertime(attendance, settings)
            
            tvOvertime.text = Formatter.formatHoursShort(result.overtimeHours)
            tvExtra.text = Formatter.formatHoursShort(result.extraHours)
            tvOvertimePay.text = Formatter.formatMoney(result.estimatedPay)
            
            if (!attendance.note.isNullOrEmpty()) {
                tvNote.text = attendance.note
                tvNote.visibility = View.VISIBLE
            } else {
                tvNote.visibility = View.GONE
            }
        }
    }
    
    private class AttendanceDiffCallback(
        private val oldList: List<Attendance>,
        private val newList: List<Attendance>
    ) : DiffUtil.Callback() {
        
        override fun getOldListSize(): Int = oldList.size
        
        override fun getNewListSize(): Int = newList.size
        
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].id == newList[newItemPosition].id
        }
        
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]
            return oldItem.date == newItem.date &&
                   oldItem.manualOvertimeHours == newItem.manualOvertimeHours &&
                   oldItem.manualExtraHours == newItem.manualExtraHours &&
                   oldItem.note == newItem.note
        }
    }
}
