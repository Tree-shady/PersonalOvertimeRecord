package com.example.personalovertimerecord.data

import android.content.Context
import android.content.SharedPreferences
import com.example.personalovertimerecord.utils.SecurePreferencesManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * 旧的 JSON 存储实现（历史遗留，当前未被使用；保留仅为兼容编译）。
 * 数据访问请使用 Room（AttendanceRepository）。
 */
class AttendanceStorage(context: Context) {
    
    private val prefs: SharedPreferences = SecurePreferencesManager.getEncryptedPrefs(context)
    private var nextId: Long = 1L
    
    // 内存缓存，提升性能
    private var cachedData: List<OvertimeRecord>? = null
    private var isCacheDirty: Boolean = true
    
    init {
        val allAttendance = getAllAttendance()
        if (allAttendance.isNotEmpty()) {
            nextId = allAttendance.maxOf { it.id } + 1
        }
    }
    
    fun getAllAttendance(): List<OvertimeRecord> {
        if (!isCacheDirty && cachedData != null) {
            return cachedData!!
        }
        
        val jsonString = prefs.getString(KEY_ATTENDANCE, "[]")
        val jsonArray = JSONArray(jsonString)
        val list = mutableListOf<OvertimeRecord>()
        
        for (i in 0 until jsonArray.length()) {
            list.add(jsonToRecord(jsonArray.getJSONObject(i)))
        }
        
        cachedData = list.sortedByDescending { it.date }
        isCacheDirty = false
        return cachedData!!
    }
    
    fun getAttendanceByDate(date: String): OvertimeRecord? {
        return getAllAttendance().find { it.date == date }
    }
    
    fun getAttendanceById(id: Long): OvertimeRecord? {
        return getAllAttendance().find { it.id == id }
    }
    
    fun saveAttendance(attendance: OvertimeRecord) {
        val list = getAllAttendance().toMutableList()
        val existingIndex = list.indexOfFirst { it.id == attendance.id }
        if (existingIndex >= 0) {
            list[existingIndex] = attendance
        } else {
            list.add(attendance)
        }
        saveAllAttendance(list)
    }
    
    fun insertAttendance(attendance: OvertimeRecord): Long {
        val newAttendance = if (attendance.id == 0L) {
            attendance.copy(id = nextId++)
        } else {
            attendance
        }
        saveAttendance(newAttendance)
        return newAttendance.id
    }
    
    fun updateAttendance(attendance: OvertimeRecord) {
        saveAttendance(attendance)
    }
    
    fun deleteAttendance(id: Long) {
        val list = getAllAttendance().toMutableList()
        list.removeAll { it.id == id }
        saveAllAttendance(list)
    }
    
    private fun saveAllAttendance(list: List<OvertimeRecord>) {
        val jsonArray = JSONArray()
        list.forEach { jsonArray.put(attendanceToJson(it)) }
        prefs.edit().putString(KEY_ATTENDANCE, jsonArray.toString()).apply()
        
        // 数据已变更，标记缓存为脏
        invalidateCache()
    }
    
    private fun invalidateCache() {
        isCacheDirty = true
        cachedData = null
    }
    
    private fun attendanceToJson(attendance: OvertimeRecord): JSONObject {
        return JSONObject().apply {
            put("id", attendance.id)
            put("date", attendance.date)
            put("checkInTime", attendance.checkInTime)
            put("checkOutTime", attendance.checkOutTime)
            put("checkInTimestamp", attendance.checkInTimestamp)
            put("checkOutTimestamp", attendance.checkOutTimestamp)
            put("note", attendance.note)
            put("manualOvertimeHours", attendance.overtimeHours)
            put("manualExtraHours", attendance.extraHours)
        }
    }
    
    private fun jsonToRecord(json: JSONObject): OvertimeRecord {
        return OvertimeRecord(
            id = json.getLong("id"),
            date = json.getString("date"),
            checkInTime = json.optString("checkInTime", null),
            checkOutTime = json.optString("checkOutTime", null),
            checkInTimestamp = json.optLong("checkInTimestamp", 0L).takeIf { it != 0L },
            checkOutTimestamp = json.optLong("checkOutTimestamp", 0L).takeIf { it != 0L },
            note = json.optString("note", null),
            overtimeHours = json.optDouble("manualOvertimeHours", -1.0),
            extraHours = json.optDouble("manualExtraHours", -1.0)
        )
    }
    
    companion object {
        private const val PREFS_NAME = "attendance_prefs"
        private const val KEY_ATTENDANCE = "attendance_list"
    }
}
