package com.example.personalovertimerecord.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.personalovertimerecord.R
import com.example.personalovertimerecord.data.Attendance
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AttendanceAdapter(
    private val onItemClick: (Attendance) -> Unit
) : RecyclerView.Adapter<AttendanceAdapter.AttendanceViewHolder>() {
    
    private var attendanceList: List<Attendance> = emptyList()
    
    fun submitList(list: List<Attendance>) {
        attendanceList = list
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttendanceViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_attendance, parent, false)
        return AttendanceViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: AttendanceViewHolder, position: Int) {
        holder.bind(attendanceList[position])
    }
    
    override fun getItemCount(): Int = attendanceList.size
    
    inner class AttendanceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        private val tvCheckIn: TextView = itemView.findViewById(R.id.tvCheckIn)
        private val tvCheckOut: TextView = itemView.findViewById(R.id.tvCheckOut)
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
            tvCheckIn.text = attendance.checkInTime?.substring(0, 5) ?: "--:--"
            tvCheckOut.text = attendance.checkOutTime?.substring(0, 5) ?: "--:--"
            
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
    }
}
