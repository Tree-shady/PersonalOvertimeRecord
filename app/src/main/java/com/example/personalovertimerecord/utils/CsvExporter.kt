package com.example.personalovertimerecord.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.personalovertimerecord.data.OvertimeRecord
import com.example.personalovertimerecord.data.OvertimeSettings
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CSV导出工具类
 * 用于生成Excel兼容的CSV文件
 */
object CsvExporter {
    
    /**
     * 导出考勤记录为CSV文件
     * @param context 上下文
     * @param records 考勤记录列表
     * @param settings 考勤设置
     * @param password 加密密码（可选，为空则不加密）
     * @return 生成的CSV文件（加密时返回加密文件）
     */
    fun exportToCsv(
        context: Context,
        records: List<OvertimeRecord>,
        settings: OvertimeSettings,
        password: String? = null
    ): File {
        val fileName = "考勤记录_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.csv"
        val file = File(context.getExternalFilesDir(null), fileName)
        
        FileWriter(file).use { writer ->
            // 写入UTF-8 BOM，解决Excel打开CSV中文乱码问题
            writer.write("\uFEFF")
            
            // 写入标题行
            writer.write("日期,上班时间,下班时间,加班时长(小时),加班类型,加班费(元),备注\n")
            
            // 写入数据行
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            
            for (record in records) {
                val row = buildString {
                    // 日期
                    append(escapeCsvField(record.date))
                    append(",")
                    
                    // 上班时间
                    append(escapeCsvField(record.checkInTime ?: "--"))
                    append(",")
                    
                    // 下班时间
                    append(escapeCsvField(record.checkOutTime ?: "--"))
                    append(",")
                    
                    // 加班时长
                    append(String.format("%.1f", record.overtimeHours))
                    append(",")
                    
                    // 加班类型
                    append(escapeCsvField(getDayTypeLabel(record.dayType)))
                    append(",")
                    
                    // 加班费
                    append(String.format("%.2f", record.totalPay))
                    append(",")
                    
                    // 备注
                    append(escapeCsvField(record.note ?: ""))
                    append("\n")
                }
                writer.write(row)
            }
            
            // 写入汇总行
            writer.write("\n")
            writer.write("汇总统计\n")
            
            val totalOvertime = records.sumByDouble { it.overtimeHours }
            val totalPay = records.sumByDouble { it.totalPay }
            val normalDays = records.count { getDayTypeLabel(it.dayType) == "平时" }
            val weekendDays = records.count { getDayTypeLabel(it.dayType) == "周末" }
            val holidayDays = records.count { getDayTypeLabel(it.dayType) == "法定节假日" }
            
            writer.write("总加班时长,${String.format("%.1f", totalOvertime)}小时\n")
            writer.write("总加班费,${String.format("%.2f", totalPay)}元\n")
            writer.write("平时加班天数,$normalDays\n")
            writer.write("周末加班天数,$weekendDays\n")
            writer.write("法定节假日加班天数,$holidayDays\n")
            
            // 写入生成时间
            val generateTime = SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss", Locale.getDefault()).format(Date())
            writer.write("\n生成时间,$generateTime\n")
        }
        
        // 如果设置了密码，加密文件
        if (!password.isNullOrBlank()) {
            val encryptedFile = File(context.getExternalFilesDir(null), "$fileName.enc")
            EncryptionUtils.encryptFile(file.path, encryptedFile.path, password)
            file.delete() // 删除原始文件
            return encryptedFile
        }
        
        return file
    }
    
    /**
     * 导出月度汇总CSV
     * @param context 上下文
     * @param year 年份
     * @param month 月份
     * @param records 考勤记录列表
     * @param settings 考勤设置
     * @param password 加密密码（可选，为空则不加密）
     * @return 生成的CSV文件
     */
    fun exportMonthlySummary(
        context: Context,
        year: Int,
        month: Int,
        records: List<OvertimeRecord>,
        settings: OvertimeSettings,
        password: String? = null
    ): File {
        val fileName = "${year}年${month}月考勤汇总_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.csv"
        val file = File(context.getExternalFilesDir(null), fileName)
        
        FileWriter(file).use { writer ->
            // 写入UTF-8 BOM
            writer.write("\uFEFF")
            
            // 写入标题
            writer.write("${year}年${month}月考勤汇总\n\n")
            
            // 基本设置信息
            writer.write("基本设置\n")
            writer.write("上班时间,${settings.workStartTime}\n")
            writer.write("下班时间,${settings.workEndTime}\n")
            writer.write("平时加班倍率,${settings.overtimeRateNormal}\n")
            writer.write("周末加班倍率,${settings.overtimeRateWeekend}\n")
            writer.write("节假日加班倍率,${settings.overtimeRateHoliday}\n")
            writer.write("底薪,${settings.baseSalary}元\n")
            writer.write("月工作日数,${settings.monthlyWorkDays}天\n")
            writer.write("每日工作时长,${settings.dailyWorkHours}小时\n\n")
            
            // 写入明细
            writer.write("考勤明细\n")
            writer.write("日期,星期,上班时间,下班时间,加班时长,加班类型,加班费,备注\n")
            
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val dayOfWeekFormat = SimpleDateFormat("E", Locale.CHINESE)
            
            for (record in records) {
                val date = dateFormat.parse(record.date) ?: Date()
                val dayOfWeek = dayOfWeekFormat.format(date)
                
                val row = buildString {
                    append(escapeCsvField(record.date))
                    append(",")
                    append(escapeCsvField(dayOfWeek))
                    append(",")
                    append(escapeCsvField(record.checkInTime ?: "--"))
                    append(",")
                    append(escapeCsvField(record.checkOutTime ?: "--"))
                    append(",")
                    append(String.format("%.1f", record.overtimeHours))
                    append(",")
                    append(escapeCsvField(record.dayType))
                    append(",")
                    append(String.format("%.2f", record.totalPay))
                    append(",")
                    append(escapeCsvField(record.note ?: ""))
                    append("\n")
                }
                writer.write(row)
            }
            
            // 写入汇总
            writer.write("\n汇总统计\n")
            
            val totalOvertime = records.sumByDouble { it.overtimeHours }
            val totalPay = records.sumByDouble { it.totalPay }
            val normalDays = records.count { getDayTypeLabel(it.dayType) == "平时" }
            val weekendDays = records.count { getDayTypeLabel(it.dayType) == "周末" }
            val holidayDays = records.count { getDayTypeLabel(it.dayType) == "法定节假日" }
            val normalHours = records.filter { getDayTypeLabel(it.dayType) == "平时" }.sumByDouble { it.overtimeHours }
            val weekendHours = records.filter { getDayTypeLabel(it.dayType) == "周末" }.sumByDouble { it.overtimeHours }
            val holidayHours = records.filter { getDayTypeLabel(it.dayType) == "法定节假日" }.sumByDouble { it.overtimeHours }
            
            writer.write("总加班时长,${String.format("%.1f", totalOvertime)}小时\n")
            writer.write("总加班费,${String.format("%.2f", totalPay)}元\n")
            writer.write("平时加班,${normalDays}天,${String.format("%.1f", normalHours)}小时\n")
            writer.write("周末加班,${weekendDays}天,${String.format("%.1f", weekendHours)}小时\n")
            writer.write("节假日加班,${holidayDays}天,${String.format("%.1f", holidayHours)}小时\n")
            writer.write("有效工作日,${normalDays}天\n")
            
            // 写入生成时间
            val generateTime = SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss", Locale.getDefault()).format(Date())
            writer.write("\n生成时间,$generateTime\n")
        }
        
        // 如果设置了密码，加密文件
        if (!password.isNullOrBlank()) {
            val encryptedFile = File(context.getExternalFilesDir(null), "$fileName.enc")
            EncryptionUtils.encryptFile(file.path, encryptedFile.path, password)
            file.delete() // 删除原始文件
            return encryptedFile
        }
        
        return file
    }
    
    /**
     * 将 OvertimeRecord.dayType 统一映射为中文标签。
     * 注意：调用方可能传入枚举名（WORKDAY/WEEKEND/HOLIDAY）或中文（平时/周末/法定节假日），
     * 此前按中文硬比较导致导出汇总天数恒为 0。
     */
    private fun getDayTypeLabel(dayType: String): String {
        return when (dayType) {
            "WORKDAY", "平时" -> "平时"
            "WEEKEND", "周末" -> "周末"
            "HOLIDAY", "法定节假日", "法定假日" -> "法定节假日"
            else -> dayType
        }
    }

    /**
     * 转义CSV字段
     * 如果字段包含逗号、引号或换行符，需要用引号包裹并转义内部引号
     */
    private fun escapeCsvField(field: String): String {
        return if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            "\"${field.replace("\"", "\"\"")}\""
        } else {
            field
        }
    }
    
    /**
     * 获取文件的分享Intent
     */
    fun getShareIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        
        return Intent(Intent.ACTION_SEND).apply {
            type = when {
                file.name.endsWith(".pdf") -> "application/pdf"
                file.name.endsWith(".csv") -> "text/csv"
                else -> "*/*"
            }
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}