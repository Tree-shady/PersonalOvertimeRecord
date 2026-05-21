package com.example.personalovertimerecord.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.personalovertimerecord.data.Attendance
import com.example.personalovertimerecord.data.OvertimeSettings
import com.example.personalovertimerecord.data.db.AttendanceDao
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BackupData(
    val version: Int = 1,
    val exportTime: Long = System.currentTimeMillis(),
    val settings: OvertimeSettings,
    val attendanceRecords: List<AttendanceEntityBackup>
)

data class AttendanceEntityBackup(
    val date: String,
    val checkInTime: String? = null,
    val checkOutTime: String? = null,
    val checkInTimestamp: Long? = null,
    val checkOutTimestamp: Long? = null,
    val note: String? = null,
    val manualOvertimeHours: Double = -1.0,
    val manualExtraHours: Double = -1.0,
    val createdAt: Long = System.currentTimeMillis()
)

class DataExporter(
    private val context: Context,
    private val attendanceDao: AttendanceDao
) {
    private val gson = Gson()
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    suspend fun exportData(
        uri: Uri,
        settings: OvertimeSettings,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                val records = attendanceDao.getAllRecordsSync()
                
                val backupRecords = records.map { entity ->
                    AttendanceEntityBackup(
                        date = entity.date,
                        checkInTime = entity.checkInTime,
                        checkOutTime = entity.checkOutTime,
                        checkInTimestamp = entity.checkInTimestamp,
                        checkOutTimestamp = entity.checkOutTimestamp,
                        note = entity.note,
                        manualOvertimeHours = entity.manualOvertimeHours,
                        manualExtraHours = entity.manualExtraHours,
                        createdAt = entity.createdAt
                    )
                }

                val backupData = BackupData(
                    settings = settings,
                    attendanceRecords = backupRecords
                )

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    OutputStreamWriter(outputStream).use { writer ->
                        gson.toJson(backupData, writer)
                    }
                }

                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e)
                }
            }
        }
    }

    suspend fun importData(
        uri: Uri,
        onSuccess: (BackupData) -> Unit,
        onError: (Exception) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        val type = object : TypeToken<BackupData>() {}.type
                        val backupData: BackupData = gson.fromJson(reader, type)
                        
                        withContext(Dispatchers.Main) {
                            onSuccess(backupData)
                        }
                    }
                } ?: throw Exception("无法打开文件")
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e)
                }
            }
        }
    }

    suspend fun restoreData(backupData: BackupData) {
        withContext(Dispatchers.IO) {
            attendanceDao.deleteAll()
            
            for (record in backupData.attendanceRecords) {
                val entity = com.example.personalovertimerecord.data.db.AttendanceEntity(
                    id = 0L,
                    date = record.date,
                    checkInTime = record.checkInTime,
                    checkOutTime = record.checkOutTime,
                    checkInTimestamp = record.checkInTimestamp,
                    checkOutTimestamp = record.checkOutTimestamp,
                    note = record.note,
                    manualOvertimeHours = record.manualOvertimeHours,
                    manualExtraHours = record.manualExtraHours,
                    createdAt = record.createdAt
                )
                attendanceDao.insert(entity)
            }
        }
    }

    fun createExportFileName(): String {
        val timestamp = dateFormat.format(Date())
        return "overtime_backup_$timestamp.json"
    }
}

