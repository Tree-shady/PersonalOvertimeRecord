package com.example.personalovertimerecord

import org.junit.Test

import org.junit.Assert.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * 日期工具类单元测试示例
 */
class DateUtilsTest {

    @Test
    fun testFormatDate() {
        val year = 2024
        val month = 5 // 0-based (June)
        val day = 15
        
        val result = com.example.personalovertimerecord.utils.DateUtils.formatDate(year, month, day)
        
        assertEquals("2024-06-15", result)
    }

    @Test
    fun testExtractDateParts() {
        val dateStr = "2024-06-15"
        val result = com.example.personalovertimerecord.utils.DateUtils.extractDateParts(dateStr)
        
        assertNotNull(result)
        assertEquals(2024, result?.first)
        assertEquals(5, result?.second) // 0-based
        assertEquals(15, result?.third)
    }

    @Test
    fun testExtractInvalidDate() {
        val result = com.example.personalovertimerecord.utils.DateUtils.extractDateParts("invalid-date")
        assertNull(result)
    }

    @Test
    fun testExtractIncompleteDate() {
        val result = com.example.personalovertimerecord.utils.DateUtils.extractDateParts("2024-06")
        assertNull(result)
    }
}