package com.example.personalovertimerecord.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.personalovertimerecord.R
import com.example.personalovertimerecord.data.Attendance
import com.example.personalovertimerecord.data.OvertimeSettings
import com.example.personalovertimerecord.data.SettingsManager
import com.example.personalovertimerecord.utils.OvertimeCalculator
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AttendanceAdapter(
    private val onItemClick: (Attendance) -> Unit
) : RecyclerView.Adapter<AttendanceAdapter.AttendanceViewHolder>() {
    
    private var attendanceList: List<Attendance> = emptyList()
    private lateinit var settingsManager: SettingsManager
    private var settings: OvertimeSettings = OvertimeSettings()
    
    fun submitList(list: List<Attendance>) {
        attendanceList = list.sortedByDescending { it.date }
        notifyDataSetChanged()
    }
    
    fun setSettingsManager(manager: SettingsManager) {
        settingsManager = manager
        settings = manager.getSettings()
        notifyDataSetChanged()
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
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(attendanceList[position])
                }
            }
            
            itemView.setOnLongClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(attendanceList[position])
                    true
                } else {
                    false
                }
            }
        }
        
        fun bind(attendance: Attendance) {
            tvDate.text = formatDate(attendance.date)
            tvDayType.text = getDayType(attendance.date)
            
            val result = OvertimeCalculator.calculateOvertime(attendance, settings)
            
            tvOvertime.text = String.format(Locale.getDefault(), "%.1fh", result.overtimeHours)
            tvExtra.text = String.format(Locale.getDefault(), "%.1fh", result.extraHours)
            tvOvertimePay.text = OvertimeCalculator.formatMoney(result.estimatedPay)
            
            if (!attendance.note.isNullOrEmpty()) {
                tvNote.text = attendance.note
                tvNote.visibility = View.VISIBLE
            } else {
                tvNote.visibility = View.GONE
            }
        }
        
        private fun formatDate(dateStr: String): String {
            return try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val outputFormat = SimpleDateFormat("MM月dd日 EEEE", Locale.CHINA)
                val date = inputFormat.parse(dateStr)
                val formatted = outputFormat.format(date ?: Date())
                if (formatted.startsWith("星期")) formatted else "星期${formatted}"
            } catch (e: Exception) {
                dateStr
            }
        }
        
        private fun getDayType(dateStr: String): String {
            return try {
                val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val date = format.parse(dateStr) ?: return "工作日"
                val calendar = Calendar.getInstance()
                calendar.time = date
                val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                val monthDay = dateStr.substring(5)
                
                val holidays = setOf(
                    "01-01", "01-02", "01-03", 
                    "02-10", "02-11", "02-12", "02-13", "02-14", "02-15", "02-16", "02-17", 
                    "04-04", "04-05", "04-06", 
                    "05-01", "05-02", "05-03", "05-04", "05-05", 
                    "06-10", 
                    "09-15", "09-16", "09-17", 
                    "10-01", "10-02", "10-03", "10-04", "10-05", "10-06", "10-07"
                )
                
                when {
                    holidays.contains(monthDay) -> "法定假日"
                    dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY -> "周末"
                    else -> "工作日"
                }
            } catch (e: Exception) {
                "工作日"
            }
        }
    }
}
