package com.example.personalovertimerecord.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.example.personalovertimerecord.R
import com.example.personalovertimerecord.data.Attendance
import com.example.personalovertimerecord.data.OvertimeSettings
import com.example.personalovertimerecord.data.SettingsManager
import java.util.Calendar

class CalendarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    interface OnDateClickListener {
        fun onDateClick(year: Int, month: Int, day: Int)
    }
    
    private var listener: OnDateClickListener? = null
    private var currentYear: Int = 0
    private var currentMonth: Int = 0
    
    private var attendanceData: Map<String, Attendance> = emptyMap()
    private var settingsManager: SettingsManager? = null
    
    private val calendar = Calendar.getInstance()
    private var daysInMonth: Int = 0
    private var firstDayOfWeek: Int = 0
    
    private val cellWidth: Float
        get() = width.toFloat() / 7
    
    private val cellHeight: Float
        get() = height.toFloat() / 8
    
    // Paint objects - 一次性创建，避免重复创建
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 36f
        textAlign = Paint.Align.CENTER
        color = ContextCompat.getColor(context, android.R.color.black)
    }
    
    private val headerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 40f
        textAlign = Paint.Align.CENTER
        color = ContextCompat.getColor(context, android.R.color.black)
        isFakeBoldText = true
    }
    
    private val normalDayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 36f
        textAlign = Paint.Align.CENTER
        color = ContextCompat.getColor(context, android.R.color.black)
    }
    
    private val overtimeDayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 36f
        textAlign = Paint.Align.CENTER
        color = ContextCompat.getColor(context, R.color.overtime_color)
    }
    
    private val weekendDayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 36f
        textAlign = Paint.Align.CENTER
        color = ContextCompat.getColor(context, android.R.color.darker_gray)
    }
    
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.overtime_color)
        alpha = 50
    }
    
    private val smallTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 20f
        textAlign = Paint.Align.CENTER
        color = ContextCompat.getColor(context, android.R.color.darker_gray)
    }
    
    private val overtimeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 18f
        textAlign = Paint.Align.CENTER
        color = ContextCompat.getColor(context, R.color.overtime_color)
    }
    
    private val headerRect = RectF()
    private val dayRect = RectF()
    
    private val weekDays = arrayOf("日", "一", "二", "三", "四", "五", "六")
    
    private var lastClickedDay: Int = -1
    private var lastClickTime: Long = 0
    
    init {
        val now = Calendar.getInstance()
        currentYear = now.get(Calendar.YEAR)
        currentMonth = now.get(Calendar.MONTH)
        calculateDays()
    }
    
    fun setMonth(year: Int, month: Int) {
        currentYear = year
        currentMonth = month
        calculateDays()
        invalidate()
    }
    
    fun setOnDateClickListener(listener: OnDateClickListener) {
        this.listener = listener
    }
    
    fun setAttendanceData(data: List<Attendance>, manager: SettingsManager) {
        this.settingsManager = manager
        attendanceData = data.associateBy { it.date }
        invalidate()
    }
    
    private fun calculateDays() {
        calendar.set(currentYear, currentMonth, 1)
        daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        firstDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        drawWeekDayHeaders(canvas)
        drawDays(canvas)
    }
    
    private fun drawWeekDayHeaders(canvas: Canvas) {
        for (i in 0 until 7) {
            val x = cellWidth * i + cellWidth / 2
            val y = cellHeight / 2 + headerTextPaint.textSize / 3
            canvas.drawText(weekDays[i], x, y, headerTextPaint)
        }
    }
    
    private fun drawDays(canvas: Canvas) {
        for (day in 1..daysInMonth) {
            val position = firstDayOfWeek + day - 1
            val row = position / 7
            val col = position % 7
            
            val left = col * cellWidth
            val top = cellHeight + row * cellHeight
            val right = left + cellWidth
            val bottom = top + cellHeight
            
            val centerX = left + cellWidth / 2
            val centerY = top + cellHeight / 2
            
            val dateStr = String.format("%d-%02d-%02d", currentYear, currentMonth + 1, day)
            val attendance = attendanceData[dateStr]
            
            // 绘制背景圆
            if (attendance != null) {
                val radius = minOf(cellWidth, cellHeight) / 2 - 4
                dayRect.set(left + 4, top + 4, right - 4, bottom - 4)
                canvas.drawRoundRect(dayRect, radius, radius, circlePaint)
            }
            
            // 选择绘制颜色
            val paint = when {
                attendance != null -> overtimeDayPaint
                col == 0 || col == 6 -> weekendDayPaint
                else -> normalDayPaint
            }
            
            // 绘制日期文字
            canvas.drawText(
                day.toString(),
                centerX,
                centerY - cellHeight / 6,
                paint
            )
            
            // 绘制加班信息
            if (attendance != null) {
                val overtimeHours = attendance.manualOvertimeHours
                val extraHours = attendance.manualExtraHours
                
                val settings = settingsManager?.getSettings()
                val baseSalary = settings?.baseSalary ?: 0.0
                val monthlyWorkDays = settings?.monthlyWorkDays ?: 21.75
                val dailyWorkHours = settings?.dailyWorkHours ?: 8.0
                val overtimeRateNormal = settings?.overtimeRateNormal ?: 1.5
                
                // 计算时薪
                val hourlyWage = if (monthlyWorkDays > 0 && dailyWorkHours > 0) {
                    baseSalary / (monthlyWorkDays * dailyWorkHours)
                } else {
                    0.0
                }
                
                // 计算费用
                val overtimePay = overtimeHours * hourlyWage * overtimeRateNormal
                val extraPay = extraHours * hourlyWage
                val totalPay = overtimePay + extraPay
                
                // 显示加班时长和加点时长
                var infoY = centerY + cellHeight / 6
                
                if (overtimeHours > 0) {
                    canvas.drawText(
                        "${overtimeHours}h",
                        centerX - cellWidth / 4,
                        infoY,
                        overtimeTextPaint
                    )
                }
                
                if (extraHours > 0) {
                    canvas.drawText(
                        "${extraHours}加",
                        centerX + cellWidth / 4,
                        infoY,
                        overtimeTextPaint
                    )
                }
                
                // 显示费用
                if (totalPay > 0) {
                    infoY += smallTextPaint.textSize + 2
                    canvas.drawText(
                        "¥${String.format("%.0f", totalPay)}",
                        centerX,
                        infoY,
                        smallTextPaint
                    )
                }
            }
        }
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val clickTime = System.currentTimeMillis()
            
            // 防重复点击（300ms内）
            if (clickTime - lastClickTime < 300) {
                return true
            }
            
            val x = event.x
            val y = event.y - cellHeight // 减去标题行
            
            if (y < 0) return true
            
            val col = (x / cellWidth).toInt()
            val row = (y / cellHeight).toInt()
            val position = row * 7 + col
            
            val day = position - firstDayOfWeek + 1
            
            if (day in 1..daysInMonth) {
                lastClickedDay = day
                lastClickTime = clickTime
                listener?.onDateClick(currentYear, currentMonth, day)
                return true
            }
        }
        return true
    }
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val desiredHeight = (cellHeight * 8).toInt() // 7行 + 标题行
        val height = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(heightMeasureSpec)
            MeasureSpec.AT_MOST -> minOf(desiredHeight, MeasureSpec.getSize(heightMeasureSpec))
            else -> desiredHeight
        }
        setMeasuredDimension(width, height)
    }
}
