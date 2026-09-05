package com.example.personalovertimerecord.utils

import com.example.personalovertimerecord.data.OvertimeSettings
import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 备份 JSON（本地导出 + WebDAV 同步共用同一模型）必须携带请假字段，
 * 且读取不含请假字段的历史备份时仍能安全解析（默认值兜底）。
 */
class BackupLeaveFieldsTest {

    private val gson = Gson()

    private fun leaveBackup(): BackupData {
        val record = AttendanceEntityBackup(
            date = "2026-03-10",
            checkInTime = null,
            checkOutTime = null,
            note = "休年假",
            manualOvertimeHours = -1.0,
            manualExtraHours = -1.0,
            createdAt = 1000L,
            modifiedAt = 1000L,
            isLeave = true,
            leaveType = "ANNUAL_LEAVE",
            leaveHours = 8.0,
            isDeleted = false
        )
        return BackupData(
            version = 2,
            exportTime = 2000L,
            settings = OvertimeSettings(),
            attendanceRecords = listOf(record)
        )
    }

    @Test
    fun serializedJson_containsLeaveFields() {
        val json = gson.toJson(leaveBackup())
        val record = JsonParser.parseString(json)
            .asJsonObject
            .getAsJsonArray("attendanceRecords")[0]
            .asJsonObject

        assertTrue("备份 JSON 应包含 isLeave", record.has("isLeave"))
        assertTrue(record.get("isLeave").asBoolean)
        assertTrue("备份 JSON 应包含 leaveType", record.has("leaveType"))
        assertEquals("ANNUAL_LEAVE", record.get("leaveType").asString)
        assertTrue("备份 JSON 应包含 leaveHours", record.has("leaveHours"))
        assertEquals(8.0, record.get("leaveHours").asDouble, 1e-9)
    }

    @Test
    fun jsonRoundTrip_keepsLeaveFields() {
        val backup = leaveBackup()
        val parsed = gson.fromJson(gson.toJson(backup), BackupData::class.java)

        val record = parsed.attendanceRecords.single()
        assertTrue(record.isLeave)
        assertEquals("ANNUAL_LEAVE", record.leaveType)
        assertEquals(8.0, record.leaveHours, 1e-9)
        assertEquals("2026-03-10", record.date)
    }

    @Test
    fun legacyJson_withoutLeaveFields_parsesWithDefaults() {
        // 模拟修复前（v0.1.6 回归/更早版本）导出的备份：无请假字段
        val legacyJson = """
            {
              "version": 2,
              "exportTime": 2000,
              "settings": {},
              "attendanceRecords": [
                {
                  "date": "2026-03-11",
                  "checkInTime": null,
                  "checkOutTime": null,
                  "note": "旧版记录",
                  "manualOvertimeHours": -1.0,
                  "manualExtraHours": -1.0,
                  "createdAt": 1000,
                  "modifiedAt": 1000,
                  "isDeleted": false
                }
              ]
            }
        """.trimIndent()

        val backup = gson.fromJson(legacyJson, BackupData::class.java).sanitized()
        val record = backup.attendanceRecords.single()

        assertEquals("2026-03-11", record.date)
        assertFalse(record.isLeave)
        assertNull(record.leaveType)
        assertEquals(0.0, record.leaveHours, 1e-9)
        assertEquals("旧版记录", record.note)
    }
}
