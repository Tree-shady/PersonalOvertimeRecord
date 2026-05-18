package com.example.personalovertimerecord.utils

import android.content.Context
import android.graphics.Color
import com.example.personalovertimerecord.data.Attendance
import com.example.personalovertimerecord.data.OvertimeSettings
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import java.text.SimpleDateFormat
import java.util.*

object ChartHelper {
    
    data class MonthData(
        val month: String,
        val hours: Float,
        val pay: Float
    )
    
    data class OvertimeTypeData(
        val type: String,
        val hours: Float,
        val color: Int
    )
    
    fun setupBarChart(chart: BarChart) {
        chart.apply {
            description.isEnabled = false
            setDrawGridBackground(false)
            setDrawBarShadow(false)
            setDrawValueAboveBar(true)
            setPinchZoom(false)
            setScaleEnabled(false)
            legend.isEnabled = true
            legend.textSize = 12f
            
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                textSize = 10f
            }
            
            axisLeft.apply {
                setDrawGridLines(true)
                axisMinimum = 0f
                textSize = 10f
            }
            
            axisRight.isEnabled = false
        }
    }
    
    fun setupLineChart(chart: LineChart) {
        chart.apply {
            description.isEnabled = false
            setDrawGridBackground(false)
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(false)
            setPinchZoom(false)
            legend.isEnabled = true
            legend.textSize = 12f
            
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                textSize = 10f
            }
            
            axisLeft.apply {
                setDrawGridLines(true)
                axisMinimum = 0f
                textSize = 10f
            }
            
            axisRight.isEnabled = false
        }
    }
    
    fun setupPieChart(chart: PieChart) {
        chart.apply {
            description.isEnabled = false
            setUsePercentValues(true)
            setDrawEntryLabels(false)
            isDrawHoleEnabled = true
            setHoleColor(Color.WHITE)
            holeRadius = 45f
            transparentCircleRadius = 50f
            setCenterTextSize(14f)
            legend.isEnabled = true
            legend.textSize = 11f
        }
    }
    
    fun updateBarChart(chart: BarChart, dataList: List<MonthData>, title: String) {
        val entries = dataList.mapIndexed { index, data ->
            BarEntry(index.toFloat(), data.hours)
        }
        
        val dataSet = BarDataSet(entries, title).apply {
            color = Color.parseColor("#6200EE")
            valueTextSize = 10f
        }
        
        chart.data = BarData(dataSet)
        chart.xAxis.valueFormatter = IndexAxisValueFormatter(dataList.map { it.month })
        chart.invalidate()
    }
    
    fun updateLineChart(chart: LineChart, dataList: List<MonthData>, title: String) {
        val entries = dataList.mapIndexed { index, data ->
            Entry(index.toFloat(), data.pay)
        }
        
        val dataSet = LineDataSet(entries, title).apply {
            color = Color.parseColor("#4CAF50")
            lineWidth = 2f
            setDrawCircles(true)
            circleRadius = 4f
            setCircleColor(Color.parseColor("#4CAF50"))
            setDrawValues(true)
            valueTextSize = 10f
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }
        
        chart.data = LineData(dataSet)
        chart.xAxis.valueFormatter = IndexAxisValueFormatter(dataList.map { it.month })
        chart.invalidate()
    }
    
    fun updatePieChart(chart: PieChart, dataList: List<OvertimeTypeData>) {
        val entries = dataList.map { data ->
            PieEntry(data.hours, data.type)
        }
        
        val colors = dataList.map { it.color }
        
        val dataSet = PieDataSet(entries, "").apply {
            this.colors = colors
            valueTextSize = 12f
            valueTextColor = Color.WHITE
            sliceSpace = 2f
        }
        
        chart.data = PieData(dataSet)
        chart.invalidate()
    }
    
    fun calculateMonthlyData(attendanceList: List<Attendance>, year: Int): List<MonthData> {
        val monthData = mutableListOf<MonthData>()
        val calendar = Calendar.getInstance()
        
        for (month in 1..12) {
            calendar.set(year, month - 1, 1)
            val monthStr = SimpleDateFormat("MM月", Locale.getDefault()).format(calendar.time)
            
            val monthPrefix = String.format(Locale.getDefault(), "%04d-%02d", year, month)
            val monthRecords = attendanceList.filter { it.date.startsWith(monthPrefix) }
            
            var totalHours = 0f
            monthRecords.forEach { record ->
                if (record.manualOvertimeHours >= 0) {
                    totalHours += record.manualOvertimeHours.toFloat()
                }
                if (record.manualExtraHours >= 0) {
                    totalHours += record.manualExtraHours.toFloat()
                }
            }
            
            if (totalHours > 0) {
                monthData.add(MonthData(monthStr, totalHours, totalHours * 50f))
            }
        }
        
        return monthData
    }
    
    fun calculateOvertimeTypeData(attendanceList: List<Attendance>): List<OvertimeTypeData> {
        var normalHours = 0f
        var weekendHours = 0f
        var holidayHours = 0f
        
        attendanceList.forEach { record ->
            val dayType = HolidayManager.getDayType(record.date)
            val hours = record.manualOvertimeHours.toFloat()
            
            when (dayType) {
                com.example.personalovertimerecord.data.DayType.WORKDAY -> normalHours += hours
                com.example.personalovertimerecord.data.DayType.WEEKEND -> weekendHours += hours
                com.example.personalovertimerecord.data.DayType.HOLIDAY -> holidayHours += hours
            }
        }
        
        return listOf(
            OvertimeTypeData("工作日加班", normalHours, Color.parseColor("#2196F3")),
            OvertimeTypeData("周末加班", weekendHours, Color.parseColor("#4CAF50")),
            OvertimeTypeData("法定假日", holidayHours, Color.parseColor("#FF9800"))
        ).filter { it.hours > 0 }
    }
}
