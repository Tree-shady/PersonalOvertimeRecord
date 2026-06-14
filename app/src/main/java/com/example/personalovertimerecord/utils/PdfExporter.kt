package com.example.personalovertimerecord.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.example.personalovertimerecord.data.OvertimeRecord
import com.example.personalovertimerecord.data.OvertimeSettings
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * PDF导出工具类
 * 用于生成考勤记录的PDF报告
 */
object PdfExporter {
    
    private const val PAGE_WIDTH = 595 // A4 width in points (72 dpi)
    private const val PAGE_HEIGHT = 842 // A4 height in points
    private const val MARGIN = 40f
    private const val LINE_HEIGHT = 20f
    
    /**
     * 导出考勤记录为PDF文件
     * @param context 上下文
     * @param records 考勤记录列表
     * @param settings 考勤设置
     * @param title 报告标题
     * @return 生成的PDF文件
     */
    fun exportToPdf(
        context: Context,
        records: List<OvertimeRecord>,
        settings: OvertimeSettings,
        title: String = "考勤记录报告"
    ): File {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas
        
        var yPosition = MARGIN
        val pageWidth = PAGE_WIDTH - 2 * MARGIN
        
        // 绘制标题
        yPosition = drawTitle(canvas, title, yPosition, pageWidth)
        
        // 绘制时间信息
        yPosition = drawInfo(canvas, yPosition, pageWidth)
        
        // 绘制表头
        yPosition = drawTableHeader(canvas, yPosition, pageWidth)
        
        // 绘制记录
        var recordCount = 0
        for (record in records) {
            recordCount++
            yPosition = drawTableRow(canvas, record, recordCount, yPosition, pageWidth)
            
            // 如果超出页面，创建新页面
            if (yPosition > PAGE_HEIGHT - MARGIN - 50) {
                document.finishPage(page)
                val newPageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, document.pages.size + 1).create()
                page = document.startPage(newPageInfo)
                canvas = page.canvas
                yPosition = MARGIN
            }
        }
        
        // 绘制汇总信息
        yPosition += LINE_HEIGHT
        yPosition = drawSummary(canvas, records, settings, yPosition, pageWidth)
        
        // 绘制页脚
        drawFooter(canvas)
        
        document.finishPage(page)
        
        // 保存PDF文件
        val fileName = "考勤报告_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.pdf"
        val file = File(context.getExternalFilesDir(null), fileName)
        
        try {
            FileOutputStream(file).use { outputStream ->
                document.writeTo(outputStream)
            }
        } finally {
            document.close()
        }
        
        return file
    }
    
    private fun drawTitle(canvas: Canvas, title: String, y: Float, width: Float): Float {
        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 24f
            isFakeBoldText = true
            isAntiAlias = true
        }
        
        val x = (PAGE_WIDTH - titlePaint.measureText(title)) / 2
        canvas.drawText(title, x, y + 24f, titlePaint)
        
        // 绘制分隔线
        val linePaint = Paint().apply {
            color = Color.GRAY
            strokeWidth = 1f
        }
        canvas.drawLine(MARGIN, y + 35f, PAGE_WIDTH - MARGIN, y + 35f, linePaint)
        
        return y + 50f
    }
    
    private fun drawInfo(canvas: Canvas, y: Float, width: Float): Float {
        val infoPaint = Paint().apply {
            color = Color.GRAY
            textSize = 10f
            isAntiAlias = true
        }
        
        val dateFormat = SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss", Locale.getDefault())
        val info = "生成时间: ${dateFormat.format(Date())}"
        canvas.drawText(info, MARGIN, y + 12f, infoPaint)
        
        return y + LINE_HEIGHT + 10f
    }
    
    private fun drawTableHeader(canvas: Canvas, y: Float, width: Float): Float {
        val headerPaint = Paint().apply {
            color = Color.WHITE
            textSize = 10f
            isFakeBoldText = true
            isAntiAlias = true
        }
        
        val bgPaint = Paint().apply {
            color = Color.rgb(98, 0, 238) // Purple
        }
        
        // 绘制表头背景
        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + LINE_HEIGHT, bgPaint)
        
        // 绘制表头文字
        val columns = listOf("日期", "上班", "下班", "加班时长", "加班类型", "加班费")
        val colWidths = listOf(80f, 70f, 70f, 80f, 80f, 80f)
        var x = MARGIN + 5f
        
        for ((index, column) in columns.withIndex()) {
            canvas.drawText(column, x, y + 14f, headerPaint)
            x += colWidths.getOrElse(index) { 70f }
        }
        
        return y + LINE_HEIGHT + 5f
    }
    
    private fun drawTableRow(canvas: Canvas, record: OvertimeRecord, rowNum: Int, y: Float, width: Float): Float {
        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 9f
            isAntiAlias = true
        }
        
        val bgPaint = Paint().apply {
            color = if (rowNum % 2 == 0) Color.rgb(245, 245, 245) else Color.WHITE
        }
        
        // 绘制行背景
        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + LINE_HEIGHT, bgPaint)
        
        val dateFormat = SimpleDateFormat("MM-dd", Locale.getDefault())
        val fullDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        
        val columns = listOf(
            dateFormat.format(fullDateFormat.parse(record.date) ?: Date()),
            record.checkInTime?.let { timeFormat.format(fullDateFormat.parse(record.date + " " + it) ?: Date()) } ?: "--:--",
            record.checkOutTime?.let { timeFormat.format(fullDateFormat.parse(record.date + " " + it) ?: Date()) } ?: "--:--",
            String.format("%.1f小时", record.overtimeHours),
            record.dayType,
            String.format("¥%.2f", record.totalPay)
        )
        
        val colWidths = listOf(80f, 70f, 70f, 80f, 80f, 80f)
        var x = MARGIN + 5f
        
        for ((index, text) in columns.withIndex()) {
            // 加班费列右对齐
            if (index == 5) {
                val textWidth = textPaint.measureText(text)
                canvas.drawText(text, PAGE_WIDTH - MARGIN - textWidth - 5f, y + 14f, textPaint)
            } else {
                canvas.drawText(text, x, y + 14f, textPaint)
            }
            x += colWidths.getOrElse(index) { 70f }
        }
        
        return y + LINE_HEIGHT
    }
    
    private fun drawSummary(canvas: Canvas, records: List<OvertimeRecord>, settings: OvertimeSettings, y: Float, width: Float): Float {
        var currentY = y
        
        // 分隔线
        val linePaint = Paint().apply {
            color = Color.GRAY
            strokeWidth = 1f
        }
        canvas.drawLine(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY, linePaint)
        currentY += LINE_HEIGHT
        
        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 14f
            isFakeBoldText = true
            isAntiAlias = true
        }
        
        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 11f
            isAntiAlias = true
        }
        
        canvas.drawText("汇总统计", MARGIN, currentY + 14f, titlePaint)
        currentY += LINE_HEIGHT + 10f
        
        val totalOvertime = records.sumOf { it.overtimeHours }
        val totalPay = records.sumOf { it.totalPay }
        val normalDays = records.count { it.dayType == "平时" }
        val weekendDays = records.count { it.dayType == "周末" }
        val holidayDays = records.count { it.dayType == "法定节假日" }
        
        val summaryItems = listOf(
            "总加班时长: ${String.format("%.1f", totalOvertime)} 小时",
            "总加班费: ¥${String.format("%.2f", totalPay)}",
            "平时加班: $normalDays 天",
            "周末加班: $weekendDays 天",
            "法定节假日: $holidayDays 天"
        )
        
        for (item in summaryItems) {
            canvas.drawText(item, MARGIN, currentY + 12f, textPaint)
            currentY += LINE_HEIGHT
        }
        
        return currentY
    }
    
    private fun drawFooter(canvas: Canvas) {
        val footerPaint = Paint().apply {
            color = Color.GRAY
            textSize = 8f
            isAntiAlias = true
        }
        
        val footer = "Personal Overtime Record"
        val x = (PAGE_WIDTH - footerPaint.measureText(footer)) / 2
        canvas.drawText(footer, x, PAGE_HEIGHT - 20f, footerPaint)
    }
}