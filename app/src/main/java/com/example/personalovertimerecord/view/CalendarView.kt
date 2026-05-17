package com.example.personalovertimerecord.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.example.personalovertimerecord.R
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
    
    private val datesWithAttendance = mutableSetOf<Int>()
    private var datesWithCheckIn = mutableSetOf<Int>()
    private var datesWithCheckOut = mutableSetOf<Int>()
    
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
    
    private val checkInPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF4CAF50.toInt()
        style = Paint.Style.FILL
    }
    
    private val checkOutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF44336.toInt()
        style = Paint.Style.FILL
    }
    
    private val todayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF6200EE.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    
    fun setMonth(year: Int, month: Int) {
        currentYear = year
        currentMonth = month
        selectedDay = 1
        invalidate()
    }
    
    fun setAttendanceDates(dates: Set<Int>) {
        datesWithAttendance.clear()
        datesWithAttendance.addAll(dates)
        invalidate()
    }
    
    fun setCheckInDates(dates: Set<Int>) {
        datesWithCheckIn.clear()
        datesWithCheckIn.addAll(dates)
        invalidate()
    }
    
    fun setCheckOutDates(dates: Set<Int>) {
        datesWithCheckOut.clear()
        datesWithCheckOut.addAll(dates)
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
            
            val radius = minOf(cellWidth, cellHeight) * 0.35f
            
            if (day == selectedDay) {
                canvas.drawCircle(centerX, centerY, radius, selectedPaint)
                textPaint.color = 0xFFFFFFFF.toInt()
            } else if (day == calendar.get(Calendar.DAY_OF_MONTH) &&
                       currentMonth == calendar.get(Calendar.MONTH) &&
                       currentYear == calendar.get(Calendar.YEAR)) {
                canvas.drawCircle(centerX, centerY, radius, todayPaint)
                textPaint.color = 0xFF6200EE.toInt()
            } else {
                textPaint.color = 0xFF333333.toInt()
            }
            
            canvas.drawText(
                day.toString(),
                centerX,
                centerY + 12,
                textPaint
            )
            
            if (datesWithCheckOut.contains(day)) {
                canvas.drawCircle(centerX + radius * 0.6f, centerY - radius * 0.6f, 6f, checkOutPaint)
            } else if (datesWithCheckIn.contains(day)) {
                canvas.drawCircle(centerX + radius * 0.6f, centerY - radius * 0.6f, 6f, checkInPaint)
            }
        }
    }
}
