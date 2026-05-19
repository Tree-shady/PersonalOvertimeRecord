package com.example.personalovertimerecord

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.personalovertimerecord.adapter.AttendanceAdapter
import com.example.personalovertimerecord.data.Attendance
import com.example.personalovertimerecord.data.OvertimeSettings
import com.example.personalovertimerecord.data.SettingsManager
import com.example.personalovertimerecord.databinding.ActivityMainBinding
import com.example.personalovertimerecord.dialog.AddOvertimeDialog
import com.example.personalovertimerecord.utils.DateUtils
import com.example.personalovertimerecord.utils.OvertimeCalculator
import com.example.personalovertimerecord.view.CalendarView
import com.example.personalovertimerecord.viewmodel.AttendanceViewModel
import java.util.Calendar
import java.util.Date

class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val TIME_UPDATE_INTERVAL = 1000L  // 1 second
    }
    
    private val viewModel: AttendanceViewModel by viewModels()
    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: AttendanceAdapter
    private lateinit var settingsManager: SettingsManager
    private var currentSettings: OvertimeSettings = OvertimeSettings()
    private val handler = Handler(Looper.getMainLooper())
    private val updateTimeRunnable = object : Runnable {
        override fun run() {
            updateCurrentTime()
            handler.postDelayed(this, TIME_UPDATE_INTERVAL)
        }
    }
    
    private lateinit var calendarView: CalendarView
    private var currentYear: Int = Calendar.getInstance().get(Calendar.YEAR)
    private var currentMonth: Int = Calendar.getInstance().get(Calendar.MONTH)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
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
        calendarView = binding.calendarView
        
        updateMonthYearText()
        
        binding.btnPrevMonth.setOnClickListener {
            if (currentMonth == 0) {
                currentMonth = 11
                currentYear--
            } else {
                currentMonth--
            }
            updateMonthYearText()
            updateCalendarData()
        }
        
        binding.btnNextMonth.setOnClickListener {
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
        val dateStr = DateUtils.formatDate(year, month, day)
        val existingAttendance = viewModel.allAttendance.value?.find { it.date == dateStr }
        
        val dialog = AddOvertimeDialog(
            context = this,
            year = year,
            month = month,
            day = day,
            existingAttendance = existingAttendance,
            onSaveAttendance = { attendance ->
                if (existingAttendance != null) {
                    viewModel.updateAttendance(attendance)
                } else {
                    viewModel.addAttendance(attendance)
                }
                updateCalendarData()
            }
        )
        dialog.show()
    }
    
    private fun updateMonthYearText() {
        binding.tvMonthYear.text = "${currentYear}年${currentMonth + 1}月"
        calendarView.setMonth(currentYear, currentMonth)
    }
    
    private fun updateCalendarData() {
        viewModel.allAttendance.value?.let { attendanceList ->
            calendarView.setAttendanceData(attendanceList, settingsManager)
        }
    }
    
    private fun updateCurrentTime() {
        binding.tvCurrentTime.text = DateUtils.getCurrentTime()
    }
    
    private fun updateCurrentDate() {
        val currentDate = Date()
        binding.tvCurrentDate.text = DateUtils.formatToChineseDate(currentDate)
        binding.tvCurrentWeekday.text = DateUtils.getChineseWeekday(currentDate)
    }
    
    private fun setupRecyclerView() {
        adapter = AttendanceAdapter { attendance ->
            showEditDialog(attendance)
        }
        adapter.setSettingsManager(settingsManager)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
    }
    
    private fun showEditDialog(attendance: Attendance) {
        val dateParts = DateUtils.extractDateParts(attendance.date)
        if (dateParts != null) {
            val (year, month, day) = dateParts
            
            val dialog = AddOvertimeDialog(
                context = this,
                year = year,
                month = month,
                day = day,
                existingAttendance = attendance,
                onSaveAttendance = { updatedAttendance ->
                    viewModel.updateAttendance(updatedAttendance)
                    updateCalendarData()
                }
            )
            dialog.show()
        }
    }
    
    private fun setupButtons() {
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        
        binding.btnReport.setOnClickListener {
            startActivity(Intent(this, ReportActivity::class.java))
        }
        
        // 云同步功能已禁用
        binding.btnCloudSync.visibility = View.GONE
    }
    
    private fun observeViewModel() {
        viewModel.allAttendance.observe(this) { attendanceList ->
            adapter.submitList(attendanceList)
            updateCalendarData()
            updateMonthlyStats(attendanceList)
        }
        
        viewModel.errorMessage.observe(this) { errorMsg ->
            if (!errorMsg.isNullOrEmpty()) {
                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
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
        
        binding.tvTotalOvertime.text = OvertimeCalculator.formatHours(totalHours)
        binding.tvTotalPay.text = OvertimeCalculator.formatMoney(totalPay)
    }
}
