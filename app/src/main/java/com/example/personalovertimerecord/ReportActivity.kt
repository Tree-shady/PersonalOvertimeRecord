package com.example.personalovertimerecord

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.example.personalovertimerecord.data.Attendance
import com.example.personalovertimerecord.data.OvertimeSettings
import com.example.personalovertimerecord.data.SettingsManager
import com.example.personalovertimerecord.utils.OvertimeCalculator
import com.example.personalovertimerecord.utils.SalaryCalculator
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class ReportActivity : AppCompatActivity() {
    
    private lateinit var settingsManager: SettingsManager
    private lateinit var settings: OvertimeSettings
    private var currentYear = Calendar.getInstance().get(Calendar.YEAR)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)
        
        settingsManager = SettingsManager(this)
        settings = settingsManager.getSettings()
        
        setupToolbar()
        loadData()
    }
    
    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
        
        val tvYear = findViewById<TextView>(R.id.tvYear)
        tvYear.text = "${currentYear}年"
    }
    
    private fun loadData() {
        lifecycleScope.launch {
            val attendanceList = withContext(Dispatchers.IO) {
                loadAttendanceData()
            }
            updateReport(attendanceList)
        }
    }
    
    private suspend fun loadAttendanceData(): List<Attendance> {
        return try {
            val storage = com.example.personalovertimerecord.data.AttendanceStorage(this)
            storage.getAllAttendance()
        } catch (e: Exception) {
            emptyList()
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
        val tvTotalDays = findViewById<TextView>(R.id.tvTotalDays)
        val tvTotalHours = findViewById<TextView>(R.id.tvTotalHours)
        val tvTotalPay = findViewById<TextView>(R.id.tvTotalPay)
        
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
        
        tvTotalDays.text = "$uniqueDays 天"
        tvTotalHours.text = OvertimeCalculator.formatHours(totalHours)
        tvTotalPay.text = OvertimeCalculator.formatMoney(totalPay)
    }
    
    private fun updateSalaryOverview(attendanceList: List<Attendance>) {
        val tvBaseSalary = findViewById<TextView>(R.id.tvBaseSalary)
        val tvPerformance = findViewById<TextView>(R.id.tvPerformance)
        val tvOvertimePay = findViewById<TextView>(R.id.tvOvertimePay)
        val tvTotalSalary = findViewById<TextView>(R.id.tvTotalSalary)
        
        val report = SalaryCalculator.calculateMonthlySalary(
            attendanceList,
            settings,
            currentYear,
            Calendar.getInstance().get(Calendar.MONTH) + 1
        )
        
        tvBaseSalary.text = OvertimeCalculator.formatMoney(settings.baseSalary)
        tvPerformance.text = OvertimeCalculator.formatMoney(report.performanceBonus)
        tvOvertimePay.text = OvertimeCalculator.formatMoney(report.totalOvertimePay)
        
        val totalSalary = settings.baseSalary + report.performanceBonus + report.totalOvertimePay
        tvTotalSalary.text = OvertimeCalculator.formatMoney(totalSalary)
    }
    
    private fun setupBarChart(attendanceList: List<Attendance>) {
        val barChart = findViewById<BarChart>(R.id.barChart)
        
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
        
        barChart.apply {
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
        val pieChart = findViewById<PieChart>(R.id.pieChart)
        
        val yearPrefix = "${currentYear}-"
        val yearRecords = attendanceList.filter { it.date.startsWith(yearPrefix) }
        
        var normalHours = 0f
        var weekendHours = 0f
        var holidayHours = 0f
        
        yearRecords.forEach { record ->
            val dayType = com.example.personalovertimerecord.utils.HolidayManager.getDayType(record.date)
            val hours = record.manualOvertimeHours.toFloat()
            
            when (dayType) {
                com.example.personalovertimerecord.data.DayType.WORKDAY -> normalHours += hours
                com.example.personalovertimerecord.data.DayType.WEEKEND -> weekendHours += hours
                com.example.personalovertimerecord.data.DayType.HOLIDAY -> holidayHours += hours
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
            entries.add(PieEntry(holidayHours, "假日"))
            colors.add(android.graphics.Color.parseColor("#FF9800"))
        }
        
        if (entries.isNotEmpty()) {
            val dataSet = PieDataSet(entries, "").apply {
                this.colors = colors
                valueTextSize = 12f
                valueTextColor = android.graphics.Color.WHITE
            }
            
            pieChart.apply {
                data = PieData(dataSet)
                description.isEnabled = false
                legend.isEnabled = true
                invalidate()
            }
        }
    }
    
    private fun setupLineChart(attendanceList: List<Attendance>) {
        val lineChart = findViewById<LineChart>(R.id.lineChart)
        
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
        
        lineChart.apply {
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
