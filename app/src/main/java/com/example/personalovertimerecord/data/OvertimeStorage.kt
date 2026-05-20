package com.example.personalovertimerecord.data

import android.content.Context
import android.content.SharedPreferences
import com.example.personalovertimerecord.utils.SecurePreferencesManager
import org.json.JSONArray
import org.json.JSONObject

class OvertimeStorage(context: Context) {
    
    private val prefs: SharedPreferences = SecurePreferencesManager.getEncryptedPrefs(context)
    
    private var cache: List<OvertimeRecord>? = null
    private var isCacheDirty: Boolean = true
    
    fun getAllRecords(): List<OvertimeRecord> {
        if (cache == null || isCacheDirty) {
            cache = loadAllFromStorage()
            isCacheDirty = false
        }
        return cache ?: emptyList()
    }
    
    private fun loadAllFromStorage(): List<OvertimeRecord> {
        val jsonString = prefs.getString(KEY_RECORDS, "[]")
        val jsonArray = JSONArray(jsonString)
        val list = mutableListOf<OvertimeRecord>()
        
        for (i in 0 until jsonArray.length()) {
            list.add(jsonToRecord(jsonArray.getJSONObject(i)))
        }
        return list.sortedByDescending { it.date }
    }
    
    fun getRecordByDate(date: String): OvertimeRecord? {
        return getAllRecords().find { it.date == date }
    }
    
    fun getRecordsByMonth(year: Int, month: Int): List<OvertimeRecord> {
        val prefix = String.format("%04d-%02d", year, month)
        return getAllRecords().filter { it.date.startsWith(prefix) }
    }
    
    fun saveRecord(record: OvertimeRecord) {
        val list = getAllRecords().toMutableList()
        val existingIndex = list.indexOfFirst { it.id == record.id }
        if (existingIndex >= 0) {
            list[existingIndex] = record
        } else {
            list.add(record)
        }
        saveAllRecords(list)
    }
    
    fun deleteRecord(record: OvertimeRecord) {
        val list = getAllRecords().toMutableList()
        list.removeAll { it.id == record.id }
        saveAllRecords(list)
    }
    
    fun deleteRecordById(id: String) {
        val list = getAllRecords().toMutableList()
        list.removeAll { it.id == id }
        saveAllRecords(list)
    }
    
    private fun saveAllRecords(list: List<OvertimeRecord>) {
        val jsonArray = JSONArray()
        list.forEach { jsonArray.put(recordToJson(it)) }
        prefs.edit().putString(KEY_RECORDS, jsonArray.toString()).apply()
        cache = list
        isCacheDirty = false
    }
    
    private fun recordToJson(record: OvertimeRecord): JSONObject {
        return JSONObject().apply {
            put("id", record.id)
            put("date", record.date)
            put("overtimeHours", record.overtimeHours)
            put("extraHours", record.extraHours)
            put("note", record.note)
            put("createdAt", record.createdAt)
        }
    }
    
    private fun jsonToRecord(json: JSONObject): OvertimeRecord {
        return OvertimeRecord(
            id = json.getString("id"),
            date = json.getString("date"),
            overtimeHours = json.optDouble("overtimeHours", 0.0),
            extraHours = json.optDouble("extraHours", 0.0),
            note = json.optString("note", null),
            createdAt = json.optLong("createdAt", System.currentTimeMillis())
        )
    }
    
    fun clearCache() {
        isCacheDirty = true
        cache = null
    }
    
    companion object {
        private const val PREFS_NAME = "overtime_prefs"
        private const val KEY_RECORDS = "overtime_records"
    }
}
