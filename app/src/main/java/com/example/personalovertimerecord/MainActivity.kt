package com.example.personalovertimerecord

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.personalovertimerecord.adapter.AttendanceAdapter
import com.example.personalovertimerecord.data.Attendance
import com.example.personalovertimerecord.data.OvertimeSettings
import com.example.personalovertimerecord.data.SettingsManager
import com.example.personalovertimerecord.dialog.AddOvertimeDialog
import com.example.personalovertimerecord.utils.OvertimeCalculator
import com.example.personalovertimerecord.view.CalendarView
import com.example.personalovertimerecord.viewmodel.AttendanceViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    
    private val viewModel: AttendanceViewModel by viewModels()
    private lateinit var adapter: AttendanceAdapter
    private lateinit var settingsManager: SettingsManager
    private var currentSettings: OvertimeSettings = OvertimeSettings()
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
        
        settingsManager = SettingsManager(this)
        
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
        currentSettings = settingsManager.getSettings()
        adapter.setSettingsManager(settingsManager)
        viewModel.allAttendance.value?.let { attendanceList ->
            updateMonthlyStats(attendanceList)
        }
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
        
        calendarView.setOnDateClickListener(object : CalendarView.OnDateClickListener {
            override fun onDateClick(year: Int, month: Int, day: Int) {
                showAddOvertimeDialog(year, month, day)
            }
        })
    }
    
    private fun showAddOvertimeDialog(year: Int, month: Int, day: Int) {
        val dateStr = String.format(Locale.getDefault(), "%d-%02d-%02d", year, month + 1, day)
        val existingAttendance = viewModel.allAttendance.value?.find { it.date == dateStr }
        
        val dialog = AddOvertimeDialog(
            context = this,
            year = year,
            month = month,
            day = day,
            existingAttendance = existingAttendance,
            onSave = { attendance ->
                if (existingAttendance != null) {
                    viewModel.updateAttendance(attendance)
                } else {
                    viewModel.insertAttendance(attendance)
                }
            },
            onDismiss = {
                updateCalendarData()
            }
        )
        dialog.show()
    }
    
    private fun updateMonthYearText() {
        val tvMonthYear = findViewById<TextView>(R.id.tvMonthYear)
        tvMonthYear.text = "${currentYear}年${currentMonth + 1}月"
        calendarView.setMonth(currentYear, currentMonth)
    }
    
    private fun updateCalendarData() {
        viewModel.allAttendance.value?.let { attendanceList ->
            calendarView.setAttendanceData(attendanceList, settingsManager)
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
        adapter.setSettingsManager(settingsManager)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)
    }
    
    private fun showEditDialog(attendance: Attendance) {
        val dialog = AddOvertimeDialog(
            context = this,
            year = attendance.date.substring(0, 4).toInt(),
            month = attendance.date.substring(5, 7).toInt() - 1,
            day = attendance.date.substring(8, 10).toInt(),
            existingAttendance = attendance,
            onSave = { updatedAttendance ->
                viewModel.updateAttendance(updatedAttendance)
            },
            onDismiss = {
                updateCalendarData()
            }
        )
        dialog.show()
    }
    
    private fun setupButtons() {
        val btnSettings = findViewById<ImageButton>(R.id.btnSettings)
        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }
    
    private fun observeViewModel() {
        viewModel.allAttendance.observe(this) { attendanceList ->
            adapter.submitList(attendanceList)
            updateCalendarData()
            updateMonthlyStats(attendanceList)
        }
    }
    
    private fun updateMonthlyStats(attendanceList: List<Attendance>) {
        var totalHours = 0.0
        var totalOvertimePay = 0.0
        
        attendanceList.forEach { attendance ->
            val result = OvertimeCalculator.calculateOvertime(attendance, currentSettings)
            totalHours += result.overtimeHours + result.extraHours
            totalOvertimePay += result.estimatedPay
        }
        
        val performanceBonus = currentSettings.baseSalary * (currentSettings.performancePercent / 100.0)
        val totalPay = currentSettings.baseSalary + performanceBonus + totalOvertimePay
        
        val tvTotalOvertime = findViewById<TextView>(R.id.tvTotalOvertime)
        val tvTotalPay = findViewById<TextView>(R.id.tvTotalPay)
        
        tvTotalOvertime.text = OvertimeCalculator.formatHours(totalHours)
        tvTotalPay.text = OvertimeCalculator.formatMoney(totalPay)
    }
}
