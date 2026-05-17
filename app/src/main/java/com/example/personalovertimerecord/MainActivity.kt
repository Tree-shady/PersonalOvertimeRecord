package com.example.personalovertimerecord

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.personalovertimerecord.adapter.AttendanceAdapter
import com.example.personalovertimerecord.dialog.EditAttendanceDialog
import com.example.personalovertimerecord.view.CalendarView
import com.example.personalovertimerecord.viewmodel.AttendanceViewModel
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    
    private val viewModel: AttendanceViewModel by viewModels()
    private lateinit var adapter: AttendanceAdapter
    private val handler = Handler(Looper.getMainLooper())
    private val updateTimeRunnable = object : Runnable {
        override fun run() {
            updateCurrentTime()
            handler.postDelayed(this, 1000)
        }
    }
    
    private lateinit var calendarView: CalendarView
    private var currentYear: Int = Calendar.getInstance().get(Calendar.YEAR)
    private var currentMonth: Int = Calendar.getInstance().get(Calendar.MONTH)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        setupCalendar()
        setupRecyclerView()
        setupButtons()
        observeViewModel()
        updateCurrentTime()
        updateCurrentDate()
        updateCalendarData()
    }
    
    override fun onResume() {
        super.onResume()
        handler.post(updateTimeRunnable)
    }
    
    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateTimeRunnable)
    }
    
    private fun setupCalendar() {
        calendarView = findViewById(R.id.calendarView)
        val tvMonthYear = findViewById<TextView>(R.id.tvMonthYear)
        val btnPrevMonth = findViewById<ImageButton>(R.id.btnPrevMonth)
        val btnNextMonth = findViewById<ImageButton>(R.id.btnNextMonth)
        
        updateMonthYearText()
        
        btnPrevMonth.setOnClickListener {
            if (currentMonth == 0) {
                currentMonth = 11
                currentYear--
            } else {
                currentMonth--
            }
            updateMonthYearText()
            updateCalendarData()
        }
        
        btnNextMonth.setOnClickListener {
            if (currentMonth == 11) {
                currentMonth = 0
                currentYear++
            } else {
                currentMonth++
            }
            updateMonthYearText()
            updateCalendarData()
        }
    }
    
    private fun updateMonthYearText() {
        val tvMonthYear = findViewById<TextView>(R.id.tvMonthYear)
        tvMonthYear.text = "${currentYear}年${currentMonth + 1}月"
        calendarView.setMonth(currentYear, currentMonth)
    }
    
    private fun updateCalendarData() {
        viewModel.allAttendance.value?.let { attendanceList ->
            val checkInDates = mutableSetOf<Int>()
            val checkOutDates = mutableSetOf<Int>()
            
            attendanceList.forEach { attendance ->
                try {
                    val dateStr = attendance.date
                    val parts = dateStr.split("-")
                    if (parts.size == 3) {
                        val year = parts[0].toInt()
                        val month = parts[1].toInt() - 1
                        val day = parts[2].toInt()
                        
                        if (year == currentYear && month == currentMonth) {
                            if (attendance.checkInTime != null) {
                                checkInDates.add(day)
                            }
                            if (attendance.checkOutTime != null) {
                                checkOutDates.add(day)
                            }
                        }
                    }
                } catch (e: Exception) {
                }
            }
            
            calendarView.setCheckInDates(checkInDates)
            calendarView.setCheckOutDates(checkOutDates)
        }
    }
    
    private fun updateCurrentTime() {
        val timeTextView = findViewById<TextView>(R.id.tvCurrentTime)
        val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        timeTextView.text = currentTime
    }
    
    private fun updateCurrentDate() {
        val dateTextView = findViewById<TextView>(R.id.tvCurrentDate)
        val weekdayTextView = findViewById<TextView>(R.id.tvCurrentWeekday)
        
        val dateFormat = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault())
        val weekdayFormat = SimpleDateFormat("EEEE", Locale.CHINA)
        
        val currentDate = Date()
        dateTextView.text = dateFormat.format(currentDate)
        
        val weekday = weekdayFormat.format(currentDate)
        val weekdayWithPrefix = if (weekday.startsWith("星期")) weekday else "星期${weekday}"
        weekdayTextView.text = weekdayWithPrefix
    }
    
    private fun setupRecyclerView() {
        adapter = AttendanceAdapter { attendance ->
            showEditDialog(attendance)
        }
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)
    }
    
    private fun showEditDialog(attendance: com.example.personalovertimerecord.data.Attendance) {
        val dialog = EditAttendanceDialog(
            context = this,
            attendance = attendance,
            onSave = { updatedAttendance ->
                viewModel.updateAttendance(updatedAttendance)
            },
            onDelete = { id ->
                viewModel.deleteAttendance(id)
            }
        )
        dialog.show()
    }
    
    private fun setupButtons() {
        val btnCheckIn = findViewById<MaterialButton>(R.id.btnCheckIn)
        val btnCheckOut = findViewById<MaterialButton>(R.id.btnCheckOut)
        
        btnCheckIn.setOnClickListener {
            viewModel.checkIn()
        }
        
        btnCheckOut.setOnClickListener {
            viewModel.checkOut()
        }
    }
    
    private fun observeViewModel() {
        viewModel.allAttendance.observe(this) { attendanceList ->
            adapter.submitList(attendanceList)
            updateCalendarData()
        }
        
        viewModel.todayAttendance.observe(this) { attendance ->
            val checkInTimeText = findViewById<TextView>(R.id.tvCheckInTime)
            val checkOutTimeText = findViewById<TextView>(R.id.tvCheckOutTime)
            
            checkInTimeText.text = attendance?.checkInTime?.substring(0, 5) ?: "--:--"
            checkOutTimeText.text = attendance?.checkOutTime?.substring(0, 5) ?: "--:--"
        }
        
        viewModel.isCheckIn.observe(this) { isCheckIn ->
            val btnCheckIn = findViewById<MaterialButton>(R.id.btnCheckIn)
            btnCheckIn.isEnabled = !isCheckIn
            btnCheckIn.alpha = if (isCheckIn) 0.5f else 1f
        }
        
        viewModel.isCheckOut.observe(this) { isCheckOut ->
            val btnCheckOut = findViewById<MaterialButton>(R.id.btnCheckOut)
            btnCheckOut.isEnabled = viewModel.isCheckIn.value == true && !isCheckOut
            btnCheckOut.alpha = if (viewModel.isCheckIn.value == true && !isCheckOut) 1f else 0.5f
        }
        
        viewModel.message.observe(this) { message ->
            if (message.isNotEmpty()) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
