package com.example.personalovertimerecord.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.personalovertimerecord.data.OvertimeRecord
import com.example.personalovertimerecord.data.OvertimeSettings
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CSV导出工具类
 * 用于生成Excel兼容的CSV文件
 *
 * 安全说明：
 * - 加密导出时先在内存中完成 AES 加密再直接写入 ".enc" 文件，
 *   不再“先写明文文件再加密删除”，杜绝中途崩溃导致的明文残留；
 * - 非加密导出固定以 UTF-8（含 BOM）写盘，保证 Excel 打开中文不乱码。
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
        val content = buildString {
            // UTF-8 BOM，解决Excel打开CSV中文乱码问题
            append("\uFEFF")

            // 标题行与数据行统一由 CsvCodec 标准契约构造，保证"导出→再导入"不丢字段
            append(CSV_HEADER)
            append('\n')
            for (record in records) {
                append(buildCsvDataRow(record))
            }

            // 写入汇总行
            append('\n')
            append("汇总统计\n")

            val totalOvertime = records.sumByDouble { it.overtimeHours }
            val totalPay = records.sumByDouble { it.totalPay }
            val normalDays = records.count { dayTypeLabel(it.dayType) == "平时" }
            val weekendDays = records.count { dayTypeLabel(it.dayType) == "周末" }
            val holidayDays = records.count { dayTypeLabel(it.dayType) == "法定节假日" }

            append("总加班时长,").append(String.format("%.1f", totalOvertime)).append("小时\n")
            append("总加班费,").append(String.format("%.2f", totalPay)).append("元\n")
            append("平时加班天数,").append(normalDays).append('\n')
            append("周末加班天数,").append(weekendDays).append('\n')
            append("法定节假日加班天数,").append(holidayDays).append('\n')

            // 写入生成时间
            val generateTime = SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss", Locale.getDefault()).format(Date())
            append('\n')
            append("生成时间,").append(generateTime).append('\n')
        }

        return writeExportFile(context, fileName, content, password)
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
        val content = buildString {
            // UTF-8 BOM
            append("\uFEFF")

            // 写入标题
            append(year).append("年").append(month).append("月考勤汇总\n\n")

            // 基本设置信息
            append("基本设置\n")
            append("上班时间,").append(settings.workStartTime).append('\n')
            append("下班时间,").append(settings.workEndTime).append('\n')
            append("平时加班倍率,").append(settings.overtimeRateNormal).append('\n')
            append("周末加班倍率,").append(settings.overtimeRateWeekend).append('\n')
            append("节假日加班倍率,").append(settings.overtimeRateHoliday).append('\n')
            append("底薪,").append(settings.baseSalary).append("元\n")
            append("月工作日数,").append(settings.monthlyWorkDays).append("天\n")
            append("每日工作时长,").append(settings.dailyWorkHours).append("小时\n\n")

            // 写入明细
            append("考勤明细\n")
            append("日期,星期,上班时间,下班时间,加班时长,加班类型,加班费,备注\n")

            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val dayOfWeekFormat = SimpleDateFormat("E", Locale.CHINESE)

            for (record in records) {
                val date = dateFormat.parse(record.date) ?: Date()
                val dayOfWeek = dayOfWeekFormat.format(date)

                append(escapeCsvField(record.date)).append(',')
                append(escapeCsvField(dayOfWeek)).append(',')
                append(escapeCsvField(record.checkInTime ?: "--")).append(',')
                append(escapeCsvField(record.checkOutTime ?: "--")).append(',')
                append(String.format("%.1f", record.overtimeHours)).append(',')
                append(escapeCsvField(dayTypeLabel(record.dayType))).append(',')
                append(String.format("%.2f", record.totalPay)).append(',')
                append(escapeCsvField(record.note ?: "")).append('\n')
            }

            // 写入汇总
            append('\n')
            append("汇总统计\n")

            val totalOvertime = records.sumByDouble { it.overtimeHours }
            val totalPay = records.sumByDouble { it.totalPay }
            val normalDays = records.count { dayTypeLabel(it.dayType) == "平时" }
            val weekendDays = records.count { dayTypeLabel(it.dayType) == "周末" }
            val holidayDays = records.count { dayTypeLabel(it.dayType) == "法定节假日" }
            val normalHours = records.filter { dayTypeLabel(it.dayType) == "平时" }.sumByDouble { it.overtimeHours }
            val weekendHours = records.filter { dayTypeLabel(it.dayType) == "周末" }.sumByDouble { it.overtimeHours }
            val holidayHours = records.filter { dayTypeLabel(it.dayType) == "法定节假日" }.sumByDouble { it.overtimeHours }

            append("总加班时长,").append(String.format("%.1f", totalOvertime)).append("小时\n")
            append("总加班费,").append(String.format("%.2f", totalPay)).append("元\n")
            append("平时加班,").append(normalDays).append("天,").append(String.format("%.1f", normalHours)).append("小时\n")
            append("周末加班,").append(weekendDays).append("天,").append(String.format("%.1f", weekendHours)).append("小时\n")
            append("节假日加班,").append(holidayDays).append("天,").append(String.format("%.1f", holidayHours)).append("小时\n")
            append("有效工作日,").append(normalDays).append("天\n")

            // 写入生成时间
            val generateTime = SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss", Locale.getDefault()).format(Date())
            append('\n')
            append("生成时间,").append(generateTime).append('\n')
        }

        return writeExportFile(context, fileName, content, password)
    }

    /**
     * 统一落盘：
     * - 设置密码时直接在内存中加密后写入 "$fileName.enc"，全程不落明文；
     * - 未设置密码时以 UTF-8（含 BOM）写入明文文件。
     */
    private fun writeExportFile(
        context: Context,
        fileName: String,
        content: String,
        password: String?
    ): File {
        val dir = context.getExternalFilesDir(null)
        return if (!password.isNullOrBlank()) {
            val encryptedBytes = EncryptionUtils.encrypt(content.toByteArray(Charsets.UTF_8), password)
            val encryptedFile = File(dir, "$fileName.enc")
            encryptedFile.writeBytes(encryptedBytes)
            encryptedFile
        } else {
            val file = File(dir, fileName)
            file.writeBytes(content.toByteArray(Charsets.UTF_8))
            file
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
