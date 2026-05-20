package com.example.personalovertimerecord

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import com.example.personalovertimerecord.data.Attendance
import com.example.personalovertimerecord.data.DayType
import com.example.personalovertimerecord.data.OvertimeSettings
import com.example.personalovertimerecord.data.SettingsManager
import com.example.personalovertimerecord.databinding.ActivityReportBinding
import com.example.personalovertimerecord.utils.HolidayManager
import com.example.personalovertimerecord.utils.Formatter
import com.example.personalovertimerecord.utils.OvertimeCalculator
import com.example.personalovertimerecord.utils.SalaryCalculator
import com.example.personalovertimerecord.viewmodel.AttendanceViewModel
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import java.util.Calendar
import java.util.Locale

class ReportActivity : AppCompatActivity() {
    
    private val viewModel: AttendanceViewModel by viewModels()
    
    private lateinit var binding: ActivityReportBinding
    private lateinit var settingsManager: SettingsManager
    private lateinit var settings: OvertimeSettings
    private var currentYear = Calendar.getInstance().get(Calendar.YEAR)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        settingsManager = SettingsManager(this)
        settings = settingsManager.getSettings()
        
        clearOldStorageData()
        
        setupToolbar()
        observeViewModel()
    }
    
    private fun clearOldStorageData() {
        val prefs = getSharedPreferences("attendance_prefs", MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.tvYear.text = "${currentYear}年"
    }
    
    private fun observeViewModel() {
        viewModel.allAttendance.observe(this) { attendanceList ->
            updateReport(attendanceList)
        }
    }
    
    private fun updateReport(attendanceList: List<Attendance>) {
        updateMonthlyOverview(attendanceList)
        updateSalaryOverview(attendanceList)
        setupBarChart(attendanceList)
        setupPieChart(attendanceList)
        setupLineChart(attendanceList)
    }
    
    private fun updateMonthlyOverview(attendanceList: List<Attendance>) {
        val yearPrefix = "${currentYear}-"
        val yearRecords = attendanceList.filter { it.date.startsWith(yearPrefix) }
        
        var totalHours = 0.0
        var totalPay = 0.0
        
        yearRecords.forEach { record ->
            val result = OvertimeCalculator.calculateOvertime(record, settings)
            totalHours += result.overtimeHours + result.extraHours
            totalPay += result.estimatedPay
        }
        
        val uniqueDays = yearRecords.map { it.date }.distinct().size
        
        binding.tvTotalDays.text = "$uniqueDays 天"
        binding.tvTotalHours.text = Formatter.formatHours(totalHours)
        binding.tvTotalPay.text = Formatter.formatMoney(totalPay)
    }
    
    private fun updateSalaryOverview(attendanceList: List<Attendance>) {
        val report = SalaryCalculator.calculateMonthlySalary(
            attendanceList,
            settings,
            currentYear,
            Calendar.getInstance().get(Calendar.MONTH) + 1
        )
        
        binding.tvBaseSalary.text = Formatter.formatMoney(settings.baseSalary)
        binding.tvPerformance.text = Formatter.formatMoney(report.performanceBonus)
        binding.tvOvertimePay.text = Formatter.formatMoney(report.totalOvertimePay)
        
        val totalSalary = settings.baseSalary + report.performanceBonus + report.totalOvertimePay
        binding.tvTotalSalary.text = Formatter.formatMoney(totalSalary)
    }
    
    private fun setupBarChart(attendanceList: List<Attendance>) {
        val yearPrefix = "${currentYear}-"
        val yearRecords = attendanceList.filter { it.date.startsWith(yearPrefix) }
        
        val monthData = mutableListOf<Pair<String, Float>>()
        for (month in 1..12) {
            val monthStr = String.format(Locale.getDefault(), "%02d月", month)
            val monthPrefix = String.format(Locale.getDefault(), "%04d-%02d", currentYear, month)
            val monthRecords = yearRecords.filter { it.date.startsWith(monthPrefix) }
            
            var totalHours = 0f
            monthRecords.forEach { record ->
                if (record.manualOvertimeHours >= 0) {
                    totalHours += record.manualOvertimeHours.toFloat()
                }
                if (record.manualExtraHours >= 0) {
                    totalHours += record.manualExtraHours.toFloat()
                }
            }
            
            monthData.add(Pair(monthStr, totalHours))
        }
        
        val entries = monthData.mapIndexed { index, (_, hours) ->
            BarEntry(index.toFloat(), hours)
        }
        
        val dataSet = BarDataSet(entries, "月度加班时长").apply {
            color = android.graphics.Color.parseColor("#6200EE")
            valueTextSize = 8f
        }
        
        binding.barChart.apply {
            data = BarData(dataSet)
            description.isEnabled = false
            xAxis.valueFormatter = IndexAxisValueFormatter(monthData.map { it.first })
            xAxis.position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
            xAxis.granularity = 1f
            axisLeft.axisMinimum = 0f
            legend.isEnabled = false
            invalidate()
        }
    }
    
    private fun setupPieChart(attendanceList: List<Attendance>) {
        val yearPrefix = "${currentYear}-"
        val yearRecords = attendanceList.filter { it.date.startsWith(yearPrefix) }
        
        var normalHours = 0f
        var weekendHours = 0f
        var holidayHours = 0f
        
        yearRecords.forEach { record ->
            val dayType = HolidayManager.getDayType(record.date)
            // 处理默认值：负数表示未设置，视为0
            val overtimeHours = if (record.manualOvertimeHours >= 0) record.manualOvertimeHours.toFloat() else 0f
            val extraHours = if (record.manualExtraHours >= 0) record.manualExtraHours.toFloat() else 0f
            val totalHours = overtimeHours + extraHours
            
            when (dayType) {
                DayType.WORKDAY -> normalHours += totalHours
                DayType.WEEKEND -> weekendHours += totalHours
                DayType.HOLIDAY -> holidayHours += totalHours
            }
        }
        
        val entries = mutableListOf<PieEntry>()
        val colors = mutableListOf<Int>()
        
        if (normalHours > 0) {
            entries.add(PieEntry(normalHours, "工作日"))
            colors.add(android.graphics.Color.parseColor("#2196F3"))
        }
        if (weekendHours > 0) {
            entries.add(PieEntry(weekendHours, "周末"))
            colors.add(android.graphics.Color.parseColor("#4CAF50"))
        }
        if (holidayHours > 0) {
            entries.add(PieEntry(holidayHours, "节假日"))
            colors.add(android.graphics.Color.parseColor("#FF9800"))
        }
        
        if (entries.isNotEmpty()) {
            val dataSet = PieDataSet(entries, "").apply {
                this.colors = colors
                valueTextSize = 12f
                valueTextColor = android.graphics.Color.WHITE
            }
            
            binding.pieChart.apply {
                data = PieData(dataSet)
                description.isEnabled = false
                legend.isEnabled = true
                invalidate()
            }
        }
    }
    
    private fun setupLineChart(attendanceList: List<Attendance>) {
        val yearPrefix = "${currentYear}-"
        val yearRecords = attendanceList.filter { it.date.startsWith(yearPrefix) }
        
        val monthData = mutableListOf<Pair<String, Float>>()
        for (month in 1..12) {
            val monthStr = String.format(Locale.getDefault(), "%02d月", month)
            val monthPrefix = String.format(Locale.getDefault(), "%04d-%02d", currentYear, month)
            val monthRecords = yearRecords.filter { it.date.startsWith(monthPrefix) }
            
            var totalPay = 0f
            monthRecords.forEach { record ->
                val result = OvertimeCalculator.calculateOvertime(record, settings)
                totalPay += result.estimatedPay.toFloat()
            }
            
            monthData.add(Pair(monthStr, totalPay))
        }
        
        val entries = monthData.mapIndexed { index, (_, pay) ->
            Entry(index.toFloat(), pay)
        }
        
        val dataSet = LineDataSet(entries, "加班工资").apply {
            color = android.graphics.Color.parseColor("#4CAF50")
            lineWidth = 2f
            setDrawCircles(true)
            circleRadius = 4f
            setCircleColor(android.graphics.Color.parseColor("#4CAF50"))
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }
        
        binding.lineChart.apply {
            data = LineData(dataSet)
            description.isEnabled = false
            xAxis.valueFormatter = IndexAxisValueFormatter(monthData.map { it.first })
            xAxis.position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
            xAxis.granularity = 1f
            axisLeft.axisMinimum = 0f
            legend.isEnabled = false
            invalidate()
        }
    }
}
