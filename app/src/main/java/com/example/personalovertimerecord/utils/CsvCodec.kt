package com.example.personalovertimerecord.utils

import com.example.personalovertimerecord.data.LeaveType
import com.example.personalovertimerecord.data.OvertimeRecord
import java.util.Locale

/**
 * CSV 导出/导入双端共用的纯编解码层（无 Android / IO 依赖，可直接 JVM 单测）。
 *
 * 修复历史契约断裂：
 * - 旧导出表头为「加班时长(小时)/加班费(元)」等带单位/括号的列名，导入端只认裸列名，
 *   导致自己导出的文件再导入时长/金额静默丢失；请假字段（请假/请假类型/请假天数）导出时缺失，
 *   请假记录往返必丢。现统一由 [CSV_HEADER] 定义标准列头，导入端对表头做归一化兼容新旧格式。
 * - 数字一律以 '.' 为小数分隔符输出，避免逗号小数地区（导出 "1,5"）往返解析失败；
 *   导入端同时兼容 ',' 小数写法。
 * - 引号包裹字段支持 "" 转义与字段内换行；引号包裹的内容按原样保留（不再 trim 破坏备注）。
 */
const val CSV_HEADER = "日期,上班时间,下班时间,加班时长,加班类型,加班费,额外时长,请假,请假类型,请假天数,备注"

/** 无表头（headerless）文件使用此简单顺序，与帮助文案保持一致 */
private val HEADERLESS_ORDER = listOf("日期", "上班时间", "下班时间", "加班时长", "额外时长", "备注")

private val normalizedHeaderCells: List<String> =
    CSV_HEADER.split(",").map { normalizeHeaderCell(it) }

/** Excel 会把以 = + - @ 开头（前导空白忽略）的单元格解析为公式，需加前缀防护 */
private val FORMULA_GUARD_CHARS = "=+-@"

/**
 * 判断是否需要加防公式注入前缀。
 * 规则：去除前导空白后首个字符为 = + @ 时一律加防护；
 * '-' 仅在其后不是数字/小数点时防护（"-1"、"-1.5" 等正常负数保持数值原样）。
 */
internal fun needsFormulaGuard(field: String): Boolean {
    val trimmed = field.trimStart(' ', '\t', '\r', '\n')
    if (trimmed.isEmpty()) return false
    val first = trimmed[0]
    if (first == '=' || first == '+' || first == '@') return true
    if (first == '-') {
        if (trimmed.length == 1) return true
        val second = trimmed[1]
        return !second.isDigit() && second != '.'
    }
    return false
}

/**
 * 去除导出端防公式注入时添加的前导单引号（仅当其后紧跟 = + - @），
 * 使“导出→导入”可无损往返；其它以单引号正常开头的内容保持不变。
 */
internal fun stripFormulaGuard(value: String?): String? {
    if (value == null || value.length < 2) return value
    return if (value[0] == '\'' && value[1] in FORMULA_GUARD_CHARS) value.substring(1) else value
}

/**
 * Excel 兼容转义：字段含逗号/引号/换行时用双引号包裹，内部引号翻倍；
 * 以 = + - @（Excel 公式起始符）开头的字段追加单引号前缀，
 * 避免导出报表在 Excel/WPS 中被当作公式/DDE 执行（CSV 注入防护）。
 */
fun escapeCsvField(field: String): String {
    val guarded = if (needsFormulaGuard(field)) "'$field" else field
    return if (guarded.contains(",") || guarded.contains("\"") || guarded.contains("\n") || guarded.contains("\r")) {
        "\"${guarded.replace("\"", "\"\"")}\""
    } else {
        guarded
    }
}

/**
 * 解析一条 CSV 逻辑记录（可能跨物理行）。
 * 支持：引号包裹（含引号内逗号/换行）、"" 转义；引号包裹的字段内容原样返回，
 * 未包裹字段去除首尾空白。
 */
fun parseCsvLine(line: String): List<String> {
    val values = mutableListOf<String>()
    val current = StringBuilder()
    var inQuotes = false
    var fieldQuoted = false
    // 引号字段关闭后、遇到下一个逗号之前的残留字符（如行尾 \n/\r、多余空白）应忽略，
    // 否则会被拼进最后一个字段导致备注尾随换行
    var quotedClosed = false
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
                    if (inQuotes) {
                        if (current.isEmpty()) fieldQuoted = true
                    } else {
                        quotedClosed = true
                    }
                }
            }

            c == ',' && !inQuotes -> {
                values.add(if (fieldQuoted) current.toString() else current.toString().trim())
                current.setLength(0)
                fieldQuoted = false
                quotedClosed = false
            }

            inQuotes || !quotedClosed -> current.append(c)
            // else：跳过引号关闭后的残留字符
        }
        i++
    }
    values.add(if (fieldQuoted) current.toString() else current.toString().trim())
    return values
}

/**
 * 判断一段原始记录文本的引号是否已闭合（用于跨物理行累积时判定记录是否读完）。
 */
fun csvRecordClosed(rawRecord: String): Boolean {
    var inQuotes = false
    var i = 0
    while (i < rawRecord.length) {
        val c = rawRecord[i]
        if (c == '"') {
            if (inQuotes && i + 1 < rawRecord.length && rawRecord[i + 1] == '"') {
                i++
            } else {
                inQuotes = !inQuotes
            }
        }
        i++
    }
    return !inQuotes
}

/**
 * 表头单元格归一化：小写、去首尾空白、去掉（中文/半角括号及其内容）、去掉单位词。
 * 例如「加班时长(小时)」「加班费（元）」→「加班时长」「加班费」，兼容新旧导出格式。
 */
fun normalizeHeaderCell(raw: String): String {
    var s = raw.trim().lowercase(Locale.ROOT)
    s = s.replace(Regex("[（(][^（()）]*[）)]"), "")
    s = s.replace("小时", "").replace("元", "")
    return s.trim()
}

/**
 * 数字解析（对小数分隔符宽容）：优先标准 '.'；遇 ',' 小数（旧版本地区化导出）先替换再解析。
 */
fun parseNumber(value: String?): Double? {
    if (value.isNullOrBlank()) return null
    val s = value.trim().replace("，", ",")
    val normalized = if (s.count { it == ',' } == 1 && !s.contains('.')) s.replace(',', '.') else s
    return normalized.toDoubleOrNull()
}

/**
 * 布尔解析（宽容）：true/false（忽略大小写）、1/0、是/否、y/n、yes/no。
 */
fun parseBoolean(value: String?): Boolean {
    if (value.isNullOrBlank()) return false
    return when (value.trim().lowercase(Locale.ROOT)) {
        "true", "1", "是", "yes", "y" -> true
        else -> false
    }
}

/**
 * 标准化日期为 yyyy-MM-dd（支持 yyyy/MM/dd、yyyy年MM月dd日、yyyyMMdd）。
 */
fun normalizeDate(date: String): String {
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

private val normalizedDatePattern = Regex("\\d{4}-\\d{2}-\\d{2}")

/** 是否为可入库的标准日期 */
internal fun isValidDate(date: String): Boolean = normalizedDatePattern.matches(date)

/**
 * 将 OvertimeRecord.dayType 统一映射为中文标签（兼容枚举名 WORKDAY/WEEKEND/HOLIDAY 与中文）。
 */
fun dayTypeLabel(dayType: String): String {
    return when (dayType) {
        "WORKDAY", "平时" -> "平时"
        "WEEKEND", "周末" -> "周末"
        "HOLIDAY", "法定节假日", "法定假日" -> "法定节假日"
        else -> dayType
    }
}

/** 请假类型：枚举名 → 中文显示名（无法识别时原样返回） */
fun leaveTypeLabel(leaveType: String?): String {
    if (leaveType.isNullOrBlank()) return ""
    return LeaveType.entries.find { it.name == leaveType }?.displayName ?: leaveType
}

/**
 * 构造标准数据行（与 [CSV_HEADER] 列序一致）。
 * 数字一律用 '.' 小数分隔符；时间为空时输出空字段（不再输出 "--" 占位，保证可往返）。
 */
fun buildCsvDataRow(record: OvertimeRecord): String {
    val fields = listOf(
        record.date,
        record.checkInTime ?: "",
        record.checkOutTime ?: "",
        String.format(Locale.US, "%.1f", record.overtimeHours),
        dayTypeLabel(record.dayType),
        String.format(Locale.US, "%.2f", record.totalPay),
        String.format(Locale.US, "%.1f", record.extraHours),
        if (record.isLeave) "是" else "否",
        leaveTypeLabel(record.leaveType),
        String.format(Locale.US, "%.1f", record.leaveHours),
        record.note ?: ""
    )
    return buildString {
        fields.forEachIndexed { index, field ->
            if (index > 0) append(',')
            append(escapeCsvField(field))
        }
        append('\n')
    }
}

/**
 * 解析一行数据为记录模型。
 *
 * @param values 已解析字段
 * @param normalizedHeaders 归一化后的表头；为 null 表示无表头文件，
 *                          按 [HEADERLESS_ORDER]（日期,上班时间,下班时间,加班时长,额外时长,备注）位置解析。
 * @return 解析成功返回记录；日期缺失/非法返回 null
 */
fun parseCsvRecord(values: List<String>, normalizedHeaders: List<String>?): OvertimeRecord? {
    if (values.isEmpty()) return null

    var date: String? = null
    var checkInTime: String? = null
    var checkOutTime: String? = null
    var overtimeHours: Double? = null
    var extraHours: Double? = null
    var note: String? = null
    var isLeave = false
    var leaveType: String? = null
    var leaveHours = 0.0

    if (normalizedHeaders != null) {
        for ((index, header) in normalizedHeaders.withIndex()) {
            val value = values.getOrNull(index)
            when (header) {
                "日期", "date" -> date = value
                "上班时间", "checkin", "checkintime", "starttime" -> checkInTime = stripFormulaGuard(value)
                "下班时间", "checkout", "checkouttime", "endtime" -> checkOutTime = stripFormulaGuard(value)
                "加班时长", "overtime", "overtimehours", "hours" -> overtimeHours = parseNumber(value)
                "额外时长", "extra", "extrahours" -> extraHours = parseNumber(value)
                "请假", "isleave", "leave" -> isLeave = parseBoolean(value)
                "请假类型", "leavetype" -> {
                    if (!value.isNullOrBlank()) {
                        val v = value.trim()
                        leaveType = LeaveType.entries.find { it.displayName == v || it.name.equals(v, true) }?.name
                    }
                }
                "请假天数", "leavehours" -> leaveHours = parseNumber(value) ?: 0.0
                "备注", "note", "remark", "comment" -> note = stripFormulaGuard(value)
                // "加班类型"/"加班费" 为展示字段，不落库（加班费由薪资设置实时计算），忽略
            }
        }
    } else {
        // 无表头：按简单顺序解析
        date = values.getOrNull(0)
        checkInTime = stripFormulaGuard(values.getOrNull(1))
        checkOutTime = stripFormulaGuard(values.getOrNull(2))
        overtimeHours = values.getOrNull(3)?.let { parseNumber(it) }
        extraHours = values.getOrNull(4)?.let { parseNumber(it) }
        note = stripFormulaGuard(values.getOrNull(5))
    }

    if (date.isNullOrBlank()) return null
    val normalizedDate = normalizeDate(date)
    if (!isValidDate(normalizedDate)) return null

    return OvertimeRecord(
        id = 0L,
        date = normalizedDate,
        checkInTime = checkInTime?.takeIf { it.isNotBlank() },
        checkOutTime = checkOutTime?.takeIf { it.isNotBlank() },
        overtimeHours = overtimeHours ?: -1.0,
        extraHours = extraHours ?: -1.0,
        note = note?.takeIf { it.isNotBlank() },
        checkInTimestamp = null,
        checkOutTimestamp = null,
        isLeave = isLeave,
        leaveType = leaveType,
        leaveHours = leaveHours
    )
}

/**
 * 判断一行是否为「日期开头的数据行」（用于无表头文件的探测）。
 */
internal fun isDateLeadingDataRow(values: List<String>): Boolean {
    val first = values.firstOrNull() ?: return false
    return isValidDate(normalizeDate(first))
}

internal fun normalizeHeaders(headers: List<String>): List<String> {
    return headers.map { normalizeHeaderCell(it) }
}

/** 标准表头校验：必须包含「日期/date」列 */
internal fun isValidHeader(normalizedHeaders: List<String>): Boolean {
    return normalizedHeaders.any { it == "日期" || it == "date" }
}

/** 标准表头（归一化），导出与导入断言一致时使用 */
internal fun canonicalNormalizedHeader(): List<String> = normalizedHeaderCells
