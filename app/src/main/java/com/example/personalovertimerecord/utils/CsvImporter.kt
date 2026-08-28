package com.example.personalovertimerecord.utils

import android.content.Context
import com.example.personalovertimerecord.data.Attendance
import com.example.personalovertimerecord.data.LeaveType
import com.example.personalovertimerecord.data.db.AttendanceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * CSV导入工具类
 * 支持从CSV文件批量导入加班记录
 */
object CsvImporter {
    
    data class ImportResult(
        val success: Boolean,
        val importedCount: Int = 0,
        val failedCount: Int = 0,
        val message: String = "",
        val errors: List<String> = emptyList()
    )
    
    /**
     * 从CSV输入流导入数据
     */
    suspend fun importFromCsv(context: Context, inputStream: InputStream): ImportResult {
        val errors = mutableListOf<String>()
        var importedCount = 0
        var failedCount = 0
        
        try {
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                var lineNumber = 0
                var headers: List<String>? = null
                val entities = mutableListOf<AttendanceEntity>()
                
                var line: String? = reader.readLine()
                while (line != null) {
                    lineNumber++
                    
                    // 第一行作为表头（剥离 UTF-8 BOM，否则"自己导出的文件"表头匹配失败）
                    if (lineNumber == 1) {
                        headers = parseCsvLine(line.removePrefix("\uFEFF"))
                        if (!validateHeaders(headers)) {
                            errors.add("第1行：无效的表头格式")
                        }
                        line = reader.readLine()
                        continue
                    }
                    
                    // 跳过空行
                    if (line.trim().isEmpty()) {
                        line = reader.readLine()
                        continue
                    }
                    
                    // 解析数据行
                    val values = parseCsvLine(line)
                    
                    try {
                        val attendance = parseAttendance(values, headers)
                        if (attendance != null) {
                            // 转换为实体
                            val entity = AttendanceEntity.fromAttendance(attendance)
                            entities.add(entity)
                        } else {
                            failedCount++
                            errors.add("第${lineNumber}行：无法解析数据")
                        }
                    } catch (e: Exception) {
                        failedCount++
                        errors.add("第${lineNumber}行：${e.message}")
                    }
                    
                    line = reader.readLine()
                }
                
                // 批量插入数据库（按日期去重：已存在的日期跳过，
                // 避免插入重复记录破坏"按日期唯一"的同步/查询假设）
                if (entities.isNotEmpty()) {
                    val toInsert = withContext(Dispatchers.IO) {
                        val app = context.applicationContext as com.example.personalovertimerecord.OvertimeApplication
                        val attendanceDao = app.database.attendanceDao()
                        val existingDates = attendanceDao.getAllRecordsSync().map { it.date }.toSet()
                        val seenDates = mutableSetOf<String>()
                        entities.filter { entity ->
                            entity.date !in existingDates && seenDates.add(entity.date)
                        }
                    }
                    if (toInsert.isNotEmpty()) {
                        withContext(Dispatchers.IO) {
                            val app = context.applicationContext as com.example.personalovertimerecord.OvertimeApplication
                            val attendanceDao = app.database.attendanceDao()
                            attendanceDao.insertAll(toInsert)
                        }
                    }
                    importedCount = toInsert.size
                    val skippedCount = entities.size - toInsert.size
                    if (skippedCount > 0) {
                        errors.add("跳过 $skippedCount 条已存在的重复日期记录")
                    }
                }
            }
            
            val success = failedCount == 0
            val message = when {
                success -> "成功导入 $importedCount 条记录"
                importedCount > 0 -> "成功导入 $importedCount 条记录，失败 $failedCount 条"
                else -> "导入失败，没有成功导入任何记录"
            }
            
            return ImportResult(
                success = success,
                importedCount = importedCount,
                failedCount = failedCount,
                message = message,
                errors = errors.take(10) // 最多返回10条错误
            )
            
        } catch (e: Exception) {
            return ImportResult(
                success = false,
                message = "导入失败：${e.message}",
                errors = listOf(e.message ?: "未知错误")
            )
        }
    }
    
    /**
     * 解析CSV行（标准状态机：支持引号包裹字段、"" 转义引号；
     * 之前只翻转 inQuotes 并丢弃引号，导致含引号的备注导出后无法再导入）
     */
    private fun parseCsvLine(line: String): List<String> {
        val values = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        // 转义引号 "" → "
                        current.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> {
                    values.add(current.toString().trim())
                    current.setLength(0)
                }
                else -> current.append(c)
            }
            i++
        }
        values.add(current.toString().trim())
        return values
    }
    
    /**
     * 验证表头
     */
    private fun validateHeaders(headers: List<String>): Boolean {
        val requiredHeaders = setOf("日期", "date", "上班时间", "checkIn", "checkInTime")
        return headers.any { requiredHeaders.contains(it.lowercase()) }
    }
    
    /**
     * 解析考勤记录
     */
    private fun parseAttendance(values: List<String>, headers: List<String>?): Attendance? {
        if (values.isEmpty()) return null
        
        // 使用表头或默认顺序
        var date: String? = null
        var checkInTime: String? = null
        var checkOutTime: String? = null
        var overtimeHours: Double? = null
        var extraHours: Double? = null
        var note: String? = null
        var isLeave: Boolean = false
        var leaveType: String? = null
        var leaveHours: Double = 0.0
        
        if (headers != null) {
            // 使用表头解析
            for ((index, header) in headers.withIndex()) {
                val value = values.getOrNull(index)
                when (header.lowercase()) {
                    "日期", "date" -> date = value
                    "上班时间", "checkin", "checkintime", "starttime" -> checkInTime = value
                    "下班时间", "checkout", "checkouttime", "endtime" -> checkOutTime = value
                    "加班时长", "overtime", "overtimehours", "hours" -> overtimeHours = value?.toDoubleOrNull()
                    "额外时长", "extra", "extrahours" -> extraHours = value?.toDoubleOrNull()
                    "请假", "isleave", "leave" -> isLeave = value?.toBoolean() ?: false
                    "请假类型", "leavetype" -> {
                        val typeStr = value
                        if (!typeStr.isNullOrBlank()) {
                            // 尝试通过显示名称或枚举名称匹配
                            leaveType = LeaveType.entries.find { 
                                it.displayName == typeStr || it.name == typeStr 
                            }?.name
                        }
                    }
                    "请假天数", "leavehours" -> leaveHours = value?.toDoubleOrNull() ?: 0.0
                    "备注", "note", "remark", "comment" -> note = value
                }
            }
        } else {
            // 使用默认顺序：日期, 上班时间, 下班时间, 加班时长, 额外时长, 请假, 请假类型, 请假天数, 备注
            date = values.getOrNull(0)
            checkInTime = values.getOrNull(1)
            checkOutTime = values.getOrNull(2)
            overtimeHours = values.getOrNull(3)?.toDoubleOrNull()
            extraHours = values.getOrNull(4)?.toDoubleOrNull()
            // 索引5-7为请假相关字段
            values.getOrNull(5)?.let { if (it.isNotBlank()) isLeave = it.toBoolean() }
            values.getOrNull(6)?.let { typeStr ->
                leaveType = LeaveType.entries.find { 
                    it.displayName == typeStr || it.name == typeStr 
                }?.name
            }
            leaveHours = values.getOrNull(7)?.toDoubleOrNull() ?: 0.0
            note = values.getOrNull(8)
        }
        
        // 日期是必需的
        if (date.isNullOrBlank()) return null
        
        // 标准化日期格式
        date = normalizeDate(date)
        
        return Attendance(
            id = 0,
            date = date,
            checkInTime = checkInTime,
            checkOutTime = checkOutTime,
            manualOvertimeHours = overtimeHours ?: -1.0,
            manualExtraHours = extraHours ?: -1.0,
            note = note,
            checkInTimestamp = null,
            checkOutTimestamp = null,
            isLeave = isLeave,
            leaveType = leaveType,
            leaveHours = leaveHours
        )
    }
    
    /**
     * 标准化日期格式为 yyyy-MM-dd
     */
    private fun normalizeDate(date: String): String {
        // 尝试多种日期格式
        val formats = listOf(
            Regex("(\\d{4})[-/年](\\d{1,2})[-/月](\\d{1,2})"),
            Regex("(\\d{4})(\\d{2})(\\d{2})")
        )
        
        for (format in formats) {
            val match = format.find(date)
            if (match != null) {
                val year = match.groupValues[1]
                val month = match.groupValues[2].padStart(2, '0')
                val day = match.groupValues[3].padStart(2, '0')
                return "$year-$month-$day"
            }
        }
        
        return date
    }
    
    /**
     * 获取支持的CSV格式说明
     */
    fun getSupportedFormatDescription(): String {
        return """支持的CSV格式：
- 编码：UTF-8
- 分隔符：逗号(,)
- 可选表头：日期, 上班时间, 下班时间, 加班时长, 额外时长, 备注

示例：
日期,上班时间,下班时间,加班时长,额外时长,备注
2024-01-01,09:00,18:00,1.5,0,元旦加班
2024-01-02,08:30,17:30,0,0,正常上班

支持的日期格式：
- yyyy-MM-dd (推荐)
- yyyy/MM/dd
- yyyy年MM月dd日
- yyyyMMdd"""
    }
}