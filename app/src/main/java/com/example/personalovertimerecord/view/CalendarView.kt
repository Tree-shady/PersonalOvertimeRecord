package com.example.personalovertimerecord.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.personalovertimerecord.R
import com.example.personalovertimerecord.data.Attendance
import com.example.personalovertimerecord.data.OvertimeSettings
import com.example.personalovertimerecord.data.SettingsManager
import com.example.personalovertimerecord.utils.OvertimeCalculator
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CalendarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    private val calendar = Calendar.getInstance()
    private var selectedDay: Int = calendar.get(Calendar.DAY_OF_MONTH)
    private var currentMonth: Int = calendar.get(Calendar.MONTH)
    private var currentYear: Int = calendar.get(Calendar.YEAR)
    
    private var attendanceData: Map<String, Attendance> = emptyMap()
    private lateinit var settingsManager: SettingsManager
    private var settings: OvertimeSettings = OvertimeSettings()
    
    private var onDateClickListener: OnDateClickListener? = null
    
    private val weekdayLabels = arrayOf("日", "一", "二", "三", "四", "五", "六")
    
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 36f
        textAlign = Paint.Align.CENTER
    }
    
    private val weekdayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 32f
        textAlign = Paint.Align.CENTER
        color = 0xFF666666.toInt()
    }
    
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF6200EE.toInt()
        style = Paint.Style.FILL
    }
    
    private val overtimePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF4CAF50.toInt()
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }
    
    private val extraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF9C27B0.toInt()
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }
    
    private val moneyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF9800.toInt()
        textSize = 20f
        textAlign = Paint.Align.CENTER
    }
    
    private val todayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF6200EE.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    
    interface OnDateClickListener {
        fun onDateClick(year: Int, month: Int, day: Int)
    }
    
    fun setOnDateClickListener(listener: OnDateClickListener) {
        onDateClickListener = listener
    }
    
    fun setMonth(year: Int, month: Int) {
        currentYear = year
        currentMonth = month
        selectedDay = 1
        invalidate()
    }
    
    fun setCheckInDates(dates: Set<Int>) {
        // 这个方法保留用于向后兼容
        invalidate()
    }
    
    fun setCheckOutDates(dates: Set<Int>) {
        // 这个方法保留用于向后兼容
        invalidate()
    }
    
    fun setAttendanceData(data: List<Attendance>, manager: SettingsManager) {
        settingsManager = manager
        settings = settingsManager.getSettings()
        attendanceData = data.associateBy { it.date }
        invalidate()
    }
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val cellSize = width / 7
        val rows = 7
        setMeasuredDimension(width, cellSize * rows)
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val width = width
        val cellWidth = width / 7f
        val cellHeight = height / 7f
        
        // 绘制星期标签
        for (i in weekdayLabels.indices) {
            canvas.drawText(
                weekdayLabels[i],
                cellWidth * i + cellWidth / 2,
                cellHeight / 2 + 10,
                weekdayPaint
            )
        }
        
        val tempCalendar = Calendar.getInstance()
        tempCalendar.set(currentYear, currentMonth, 1)
        val firstDayOfWeek = tempCalendar.get(Calendar.DAY_OF_WEEK) - 1
        val daysInMonth = tempCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        
        for (day in 1..daysInMonth) {
            val position = firstDayOfWeek + day - 1
            val row = position / 7 + 1
            val col = position % 7
            
            val centerX = cellWidth * col + cellWidth / 2
            val centerY = cellHeight * row + cellHeight / 2
            val radius = minOf(cellWidth, cellHeight) * 0.25f
            
            // 日期字符串
            val dateStr = String.format(Locale.getDefault(), "%d-%02d-%02d", currentYear, currentMonth + 1, day)
            val attendance = attendanceData[dateStr]
            
            // 绘制选中背景
            if (day == selectedDay) {
                canvas.drawCircle(centerX, centerY - 10, radius + 5, selectedPaint)
                textPaint.color = 0xFFFFFFFF.toInt()
            } else if (day == calendar.get(Calendar.DAY_OF_MONTH) &&
                       currentMonth == calendar.get(Calendar.MONTH) &&
                       currentYear == calendar.get(Calendar.YEAR)) {
                canvas.drawCircle(centerX, centerY - 10, radius, todayPaint)
                textPaint.color = 0xFF6200EE.toInt()
            } else {
                textPaint.color = 0xFF333333.toInt()
            }
            
            // 绘制日期
            canvas.drawText(
                day.toString(),
                centerX,
                centerY - 10 + 12,
                textPaint
            )
            
            // 如果有加班记录，显示加班信息
            attendance?.let {
                val result = OvertimeCalculator.calculateOvertime(it, settings)
                val totalOvertime = result.overtimeHours
                val totalExtra = result.extraHours
                val totalPay = result.estimatedPay
                
                var textY = centerY + radius + 15
                
                // 显示加班时长
                if (totalOvertime > 0) {
                    val overtimeText = String.format(Locale.getDefault(), "%.1fh", totalOvertime)
                    canvas.drawText(overtimeText, centerX, textY, overtimePaint)
                    textY += 22
                }
                
                // 显示加点时长
                if (totalExtra > 0) {
                    val extraText = String.format(Locale.getDefault(), "%.1fh", totalExtra)
                    canvas.drawText(extraText, centerX, textY, extraPaint)
                    textY += 22
                }
                
                // 显示金额
                if (totalPay > 0) {
                    val moneyText = String.format(Locale.getDefault(), "¥%.0f", totalPay)
                    canvas.drawText(moneyText, centerX, textY, moneyPaint)
                }
            }
        }
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val width = width
            val cellWidth = width / 7f
            val cellHeight = height / 7f
            
            val x = event.x
            val y = event.y
            
            val col = (x / cellWidth).toInt()
            val row = (y / cellHeight).toInt()
            
            if (row == 0) {
                return true
            }
            
            val tempCalendar = Calendar.getInstance()
            tempCalendar.set(currentYear, currentMonth, 1)
            val firstDayOfWeek = tempCalendar.get(Calendar.DAY_OF_WEEK) - 1
            val daysInMonth = tempCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)
            
            val position = (row - 1) * 7 + col
            val day = position - firstDayOfWeek + 1
            
            if (day in 1..daysInMonth) {
                selectedDay = day
                invalidate()
                onDateClickListener?.onDateClick(currentYear, currentMonth, day)
                performClick()
            }
        }
        return true
    }
    
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
