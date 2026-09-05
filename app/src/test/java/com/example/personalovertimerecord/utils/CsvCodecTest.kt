package com.example.personalovertimerecord.utils

import com.example.personalovertimerecord.data.OvertimeRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CSV 双端契约测试：导出行（buildCsvDataRow）必须能被导入端（parseCsvRecord）无损往返。
 */
class CsvCodecTest {

    private fun row(mutate: OvertimeRecord.() -> OvertimeRecord = { this }): OvertimeRecord {
        val base = OvertimeRecord(
            date = "2026-01-05",
            overtimeHours = 2.5,
            extraHours = 1.0,
            note = "普通备注",
            checkInTime = "09:00",
            checkOutTime = "18:30",
            isLeave = false,
            leaveType = null,
            leaveHours = 0.0,
            dayType = "WEEKEND",
            totalPay = 100.0
        )
        return base.mutate()
    }

    private fun parseStandardRow(record: OvertimeRecord): OvertimeRecord? {
        val line = buildCsvDataRow(record)
        val values = parseCsvLine(line)
        val headers = normalizeHeaders(parseCsvLine(CSV_HEADER))
        return parseCsvRecord(values, headers)
    }

    @Test
    fun standardRow_roundTrip_keepsAllFields() {
        val source = row()
        val parsed = parseStandardRow(source)!!

        assertEquals("2026-01-05", parsed.date)
        assertEquals("09:00", parsed.checkInTime)
        assertEquals("18:30", parsed.checkOutTime)
        assertEquals(2.5, parsed.overtimeHours, 1e-9)
        assertEquals(1.0, parsed.extraHours, 1e-9)
        assertEquals("普通备注", parsed.note)
        assertFalse(parsed.isLeave)
    }

    @Test
    fun leaveRow_roundTrip_keepsLeaveFields() {
        val source = row {
            copy(
                overtimeHours = -1.0,
                extraHours = -1.0,
                isLeave = true,
                leaveType = "ANNUAL_LEAVE",
                leaveHours = 8.0,
                note = "休年假"
            )
        }
        val parsed = parseStandardRow(source)!!

        assertTrue(parsed.isLeave)
        assertEquals("ANNUAL_LEAVE", parsed.leaveType) // 显示名“年假”反解回枚举名
        assertEquals(8.0, parsed.leaveHours, 1e-9)
        assertEquals(-1.0, parsed.overtimeHours, 1e-9)
    }

    @Test
    fun noteWithCommaQuoteNewline_survivesRoundTrip() {
        val source = row { copy(note = "含,逗号 \"引号\"\n以及换行的备注") }
        val line = buildCsvDataRow(source)
        val values = parseCsvLine(line)
        val headers = normalizeHeaders(parseCsvLine(CSV_HEADER))
        val parsed = parseCsvRecord(values, headers)!!

        assertEquals(source.note, parsed.note)
    }

    @Test
    fun numbersAlwaysUseDotDecimalSeparator() {
        // 无论运行地区 locale，输出必须用 '.'（避免逗号小数地区破坏往返）
        val line = buildCsvDataRow(row { copy(overtimeHours = 1.5, extraHours = 0.0, leaveHours = 0.0) })
        assertTrue("应输出 1.5 而非 1,5：$line", line.contains(",1.5,"))
        assertFalse("不应出现逗号小数 1,5：$line", line.contains("1,5"))
    }

    @Test
    fun legacyHeaderWithUnits_isStillParsed() {
        // 旧版导出表头：加班时长(小时)/加班费(元)
        val legacyHeaders = normalizeHeaders(
            parseCsvLine("日期,上班时间,下班时间,加班时长(小时),加班类型,加班费(元),备注")
        )
        val values = parseCsvLine("2026-01-05,09:00,18:00,2.0,周末,100.00,旧文件备注")
        val parsed = parseCsvRecord(values, legacyHeaders)!!
        assertEquals(2.0, parsed.overtimeHours, 1e-9)
        assertEquals("旧文件备注", parsed.note)
    }

    @Test
    fun commaDecimalNumber_isTolerated() {
        assertEquals(1.5, parseNumber("1,5")!!, 1e-9)
        assertEquals(1.5, parseNumber("1.5")!!, 1e-9)
        assertNull(parseNumber("abc"))
    }

    @Test
    fun booleanVariantsAreParsed() {
        assertTrue(parseBoolean("是"))
        assertTrue(parseBoolean("1"))
        assertTrue(parseBoolean("TRUE"))
        assertTrue(parseBoolean("true"))
        assertFalse(parseBoolean("否"))
        assertFalse(parseBoolean("0"))
        assertFalse(parseBoolean(""))
        assertFalse(parseBoolean(null))
    }

    @Test
    fun dateFormatsAreNormalized() {
        assertEquals("2024-02-29", normalizeDate("2024/2/29"))
        assertEquals("2024-02-29", normalizeDate("2024年2月29日"))
        assertEquals("2024-02-29", normalizeDate("20240229"))
    }

    @Test
    fun quotedFieldsAndEscapedQuotesAreParsed() {
        assertEquals(
            listOf("a", "b", "c,d", "e\"f"),
            parseCsvLine("a,b,\"c,d\",\"e\"\"f\"")
        )
    }

    @Test
    fun multilineQuotedField_isSupported() {
        // 备注字段含换行：物理行拼接判定 + 解析保留换行
        val recordText = "\"line1\nline2\",other"
        assertFalse(csvRecordClosed("\"line1"))
        assertTrue(csvRecordClosed(recordText))
        val values = parseCsvLine(recordText)
        assertEquals("line1\nline2", values[0])
        assertEquals("other", values[1])

        // 导出侧：含换行备注 → 行内带换行，但仍能完整解析
        val source = row { copy(note = "a\nb") }
        val parsed = parseStandardRow(source)!!
        assertEquals("a\nb", parsed.note)
    }

    @Test
    fun headerlessDetectionAndParse() {
        assertTrue(isDateLeadingDataRow(listOf("2024-1-5", "09:00")))
        assertFalse(isDateLeadingDataRow(listOf("备注", "x")))
        assertTrue(isValidHeader(normalizeHeaders(parseCsvLine(CSV_HEADER))))

        // 无表头：按 日期,上班时间,下班时间,加班时长,额外时长,备注 顺序解析
        val parsed = parseCsvRecord(
            listOf("2024-1-5", "09:00", "18:00", "2.0", "0.5", "无表头备注"),
            null
        )!!
        assertEquals("2024-01-05", parsed.date)
        assertEquals(2.0, parsed.overtimeHours, 1e-9)
        assertEquals(0.5, parsed.extraHours, 1e-9)
        assertEquals("无表头备注", parsed.note)
    }

    @Test
    fun dayTypeAndLeaveLabelsMapToDisplay() {
        assertEquals("平时", dayTypeLabel("WORKDAY"))
        assertEquals("周末", dayTypeLabel("WEEKEND"))
        assertEquals("法定节假日", dayTypeLabel("HOLIDAY"))
        assertEquals("年假", leaveTypeLabel("ANNUAL_LEAVE"))
        assertEquals("", leaveTypeLabel(null))
    }

    // ---- CSV 公式注入防护 ----

    @Test
    fun formulaStarters_areGuardedOnExport() {
        assertEquals("'=1+1", escapeCsvField("=1+1"))
        assertEquals("'+cmd()", escapeCsvField("+cmd()"))
        assertEquals("'@SUM(A1:A2)", escapeCsvField("@SUM(A1:A2)"))
        assertEquals("'-加班小结", escapeCsvField("-加班小结"))
        // 前导空白后紧跟公式起始符同样防护（Excel 会忽略前导空白）
        assertEquals("' =danger", escapeCsvField(" =danger"))
        // 含逗号的公式字段：先加前缀再整体引号包裹
        assertEquals("\"'=1,2\"", escapeCsvField("=1,2"))
    }

    @Test
    fun normalNumbersAndText_areNotGuarded() {
        // 正常负数/小数保持数值原样，避免导出报表中出现 "'-1.0" 文本
        assertEquals("-1.0", escapeCsvField("-1.0"))
        assertEquals("1.5", escapeCsvField("1.5"))
        assertEquals("09:00", escapeCsvField("09:00"))
        assertEquals("普通备注", escapeCsvField("普通备注"))
        assertEquals("", escapeCsvField(""))
        // 单独的 "-" 会被 Excel 当作公式输入，同样加防护
        assertEquals("'-", escapeCsvField("-"))
    }

    @Test
    fun formulaNote_roundTripsLosslessly() {
        // 导出防护加的单引号前缀在导入端还原，保证往返无损
        val source = row { copy(note = "=HYPERLINK(\"http://x\")") }
        val parsed = parseStandardRow(source)!!
        assertEquals(source.note, parsed.note)
    }

    @Test
    fun guardedNoteWithComma_roundTripsLosslessly() {
        val source = row { copy(note = "@cmd,再补一句") }
        val parsed = parseStandardRow(source)!!
        assertEquals(source.note, parsed.note)
    }
}
