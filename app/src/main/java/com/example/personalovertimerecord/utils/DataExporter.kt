package com.example.personalovertimerecord.utils

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.example.personalovertimerecord.data.OvertimeSettings
import com.example.personalovertimerecord.data.SettingsManager
import com.example.personalovertimerecord.data.db.AppDatabase
import com.example.personalovertimerecord.data.db.AttendanceEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BackupData(
    val version: Int = 1,
    val exportTime: Long = System.currentTimeMillis(),
    val settings: OvertimeSettings,
    val attendanceRecords: List<AttendanceEntityBackup>
) {
    /**
     * Gson 通过 Unsafe/反射构造对象，会绕过 Kotlin 的非空默认值；
     * 当 JSON 缺少某字段时该字段运行时为 null。此方法补齐安全默认值，
     * 防止下游（saveSettings / records.size / 遍历）NPE 崩溃。
     */
    fun sanitized(): BackupData = BackupData(
        version = this.version,
        exportTime = this.exportTime,
        settings = this.settings ?: OvertimeSettings(),
        attendanceRecords = this.attendanceRecords ?: emptyList()
    )
}

data class AttendanceEntityBackup(
    val date: String,
    val checkInTime: String? = null,
    val checkOutTime: String? = null,
    val checkInTimestamp: Long? = null,
    val checkOutTimestamp: Long? = null,
    val note: String? = null,
    val manualOvertimeHours: Double = -1.0,
    val manualExtraHours: Double = -1.0,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    // 请假字段（备份/同步/恢复必须携带，否则请假记录往返后丢失类型与天数）
    val isLeave: Boolean = false,
    val leaveType: String? = null,
    val leaveHours: Double = 0.0,
    // 软删除标记：true 表示该日期记录已被删除，用于跨设备同步删除
    val isDeleted: Boolean = false
)

class DataExporter(
    private val context: Context,
    private val database: AppDatabase
) {
    private val attendanceDao = database.attendanceDao()
    private val gson = Gson()
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    companion object {
        /** 当前导出加密前缀（后接 Base64( AES-256-GCM 密文 )） */
        private const val ENC_PREFIX = "PORE_ENC2:"
        /** 历史版本前缀（后接 Base64( salt + iv + AES-CBC 密文 )），仅读取端兼容 */
        private const val ENC_PREFIX_LEGACY = "PORE_ENC1:"

        /** 导入/恢复文件大小上限：防止超大/异常文件整读进内存导致 OOM */
        private const val MAX_IMPORT_FILE_BYTES = 64L * 1024 * 1024
    }

    suspend fun exportData(
        uri: Uri,
        settings: OvertimeSettings,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                val records = attendanceDao.getAllRecordsSync()

                val backupRecords = records.map { it.toBackup() }

                // 备份设置剥离密码字段，避免明文备份文件泄露导出/同步加密密码
                val backupData = BackupData(
                    settings = settings.copy(exportPassword = "", syncPassword = ""),
                    attendanceRecords = backupRecords
                )

                val json = gson.toJson(backupData)
                // 按"导出加密"开关对备份内容加密（带前缀标记，恢复时可自动识别）
                val content = if (settings.exportEncryptionEnabled && settings.exportPassword.isNotBlank()) {
                    ENC_PREFIX + EncryptionUtils.encryptString(json, settings.exportPassword)
                } else {
                    json
                }

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(content.toByteArray(Charsets.UTF_8))
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
                    // 限制大小读取，避免超大/恶意备份文件整体读入内存导致 OOM
                    val raw = inputStream.readCappedUtf8(MAX_IMPORT_FILE_BYTES, "备份文件")
                    val json = if (raw.startsWith(ENC_PREFIX) || raw.startsWith(ENC_PREFIX_LEGACY)) {
                        // 加密备份：使用设置的导出加密密码解密（EncryptionUtils 自动识别 GCM/CBC 格式）
                        val settings = SettingsManager(context).getSettings()
                        val password = settings.exportPassword
                        if (password.isBlank()) {
                            throw Exception("备份文件已加密，请在设置中配置导出加密密码后重试")
                        }
                        try {
                            val encryptedPart = when {
                                raw.startsWith(ENC_PREFIX) -> raw.removePrefix(ENC_PREFIX)
                                raw.startsWith(ENC_PREFIX_LEGACY) -> raw.removePrefix(ENC_PREFIX_LEGACY)
                                else -> raw
                            }
                            EncryptionUtils.decryptString(encryptedPart, password)
                        } catch (e: Exception) {
                            throw Exception("备份解密失败：密码错误或文件已损坏")
                        }
                    } else {
                        raw
                    }

                    val type = object : TypeToken<BackupData>() {}.type
                    val backupData: BackupData? = gson.fromJson(json, type)
                    if (backupData == null) {
                        throw Exception("备份文件格式无效")
                    }
                    // Gson 反序列化可能产生 null 字段，补齐安全默认值防止 NPE
                    val safeBackup = backupData.sanitized()

                    withContext(Dispatchers.Main) {
                        onSuccess(safeBackup)
                    }
                } ?: throw Exception("无法打开文件")
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e)
                }
            }
        }
    }

    /**
     * 完全替换模式恢复数据
     *
     * 恢复前先自校验备份内容（日期非空、无重复），再把「清库 + 逐条插入」整体放进
     * 数据库事务：任何一步失败都会整体回滚，避免出现"本地已被清空、插入到一半失败"的整库丢失。
     */
    suspend fun restoreDataFull(backupData: BackupData) {
        val records = backupData.attendanceRecords.map { it.normalized() }
        validateForFullRestore(records)

        database.withTransaction {
            attendanceDao.deleteAll()

            for (record in records) {
                val entity = toEntity(
                    record = record,
                    id = 0L,
                    createdAt = record.createdAt,
                    isDeleted = record.isDeleted
                )
                attendanceDao.insert(entity)
            }
        }
    }

    /**
     * 恢复前自校验：日期非空且在本文件内唯一（数据库 date 列为唯一索引）。
     * 在删除本地数据之前执行，校验失败直接抛异常，本地数据不受影响。
     */
    private fun validateForFullRestore(records: List<AttendanceEntityBackup>) {
        val seenDates = HashSet<String>(records.size)
        for (record in records) {
            if (record.date.isNullOrBlank()) {
                throw IllegalArgumentException("备份包含缺失日期的记录，已中止恢复以保护现有数据")
            }
            if (!seenDates.add(record.date)) {
                throw IllegalArgumentException("备份包含重复日期：${record.date}，已中止恢复以保护现有数据")
            }
        }
    }

    /**
     * Gson 通过 Unsafe/反射构造对象时会绕过 Kotlin 非空默认值：
     * JSON 缺失字段时该字段在运行时可能为 null 或 0（原始类型）。
     * 这里为逐条记录补齐安全默认值，避免下游 NPE / 时间戳为 0 / 空日期乱插。
     */
    private fun AttendanceEntityBackup.normalized(): AttendanceEntityBackup {
        val created = if (createdAt > 0) createdAt else System.currentTimeMillis()
        val modified = if (modifiedAt > 0) modifiedAt else created
        return copy(
            date = date ?: "",
            createdAt = created,
            modifiedAt = modified,
            manualOvertimeHours = if (manualOvertimeHours.isNaN()) -1.0 else manualOvertimeHours,
            manualExtraHours = if (manualExtraHours.isNaN()) -1.0 else manualExtraHours,
            leaveHours = if (leaveHours.isNaN()) 0.0 else leaveHours
        )
    }

    /**
     * 增量合并模式恢复数据
     *
     * 除新增/更新外，还处理删除同步：
     * - 云端标记删除的记录，按冲突策略决定是否把本地对应记录软删除；
     * - 云端活跃记录可恢复本地已软删除的同日期记录。
     *
     * @param backupData 备份数据
     * @param conflictStrategy 冲突解决策略
     * @return 合并结果的摘要信息
     */
    suspend fun restoreDataIncremental(
        backupData: BackupData,
        conflictStrategy: ConflictStrategy
    ): SyncSummary = database.withTransaction {
        val records = backupData.attendanceRecords.map { it.normalized() }
        var added = 0
        var updated = 0
        var deleted = 0
        var skipped = 0

        // 获取本地现有记录（含软删除记录，避免删除标记被误判为"本地不存在"）
        val localRecords = attendanceDao.getAllRecordsIncludingDeletedSync()
        val localMap = localRecords.associateBy { it.date }

        for (record in records) {
            // 跳过日期缺失的记录，避免空日期写入唯一索引列
            if (record.date.isBlank()) {
                skipped++
                continue
            }

            val localRecord = localMap[record.date]

            when {
                // 本地不存在：直接插入（携带云端删除/活跃状态）
                localRecord == null -> {
                    val entity = toEntity(
                        record = record,
                        id = 0L,
                        createdAt = record.createdAt,
                        isDeleted = record.isDeleted
                    )
                    attendanceDao.insert(entity)
                    if (record.isDeleted) deleted++ else added++
                }

                // 本地存在：按云端状态与策略处理
                else -> {
                    // 云端已删除
                    if (record.isDeleted) {
                        val shouldDelete = when (conflictStrategy) {
                            ConflictStrategy.NEWER_WINS ->
                                record.modifiedAt >= (localRecord.modifiedAt ?: localRecord.createdAt)
                            ConflictStrategy.LOCAL_WINS -> false
                            ConflictStrategy.CLOUD_WINS -> true
                        }
                        if (shouldDelete && !localRecord.isDeleted) {
                            attendanceDao.markDeleted(localRecord.id, record.modifiedAt)
                            deleted++
                        } else {
                            skipped++
                        }
                    } else {
                        // 云端活跃记录
                        val shouldUpdate = when (conflictStrategy) {
                            ConflictStrategy.NEWER_WINS ->
                                record.modifiedAt > (localRecord.modifiedAt ?: localRecord.createdAt)
                            ConflictStrategy.LOCAL_WINS -> false
                            ConflictStrategy.CLOUD_WINS -> true
                        }
                        if (shouldUpdate) {
                            val entity = toEntity(
                                record = record,
                                id = localRecord.id,
                                createdAt = localRecord.createdAt,
                                isDeleted = false
                            )
                            attendanceDao.insertOrUpdate(entity)
                            if (localRecord.isDeleted) added++ else updated++
                        } else {
                            skipped++
                        }
                    }
                }
            }
        }

        SyncSummary(
            totalRecords = records.size,
            added = added,
            updated = updated,
            deleted = deleted,
            skipped = skipped,
            conflicts = deleted
        )
    }

    /**
     * 上传时的增量同步
     * 本地所有记录（含软删除记录）都参与对比，确保删除操作也能同步到云端。
     *
     * @param cloudRecords 云端记录
     * @param conflictStrategy 冲突解决策略
     * @return 需要上传的记录列表
     */
    suspend fun getRecordsToUpload(
        cloudRecords: List<AttendanceEntityBackup>,
        conflictStrategy: ConflictStrategy
    ): List<AttendanceEntityBackup> = withContext(Dispatchers.IO) {
        val localRecords = attendanceDao.getAllRecordsIncludingDeletedSync()
        val cloudMap = cloudRecords.associateBy { it.date }

        val recordsToUpload = mutableListOf<AttendanceEntityBackup>()

        for (localRecord in localRecords) {
            val cloudRecord = cloudMap[localRecord.date]

            when {
                // 云端不存在：需要上传（包括删除标记，让云端同步删除）
                cloudRecord == null -> {
                    recordsToUpload.add(localRecord.toBackup())
                }

                // 存在冲突，根据策略处理
                else -> {
                    // 与 toBackup() 一致：旧记录 modifiedAt 可能为 NULL，用 createdAt 兜底，
                    // 避免老数据永远判定为"云端较新"而丢失本地修改
                    val localTime = localRecord.modifiedAt ?: localRecord.createdAt
                    val shouldUpload = when (conflictStrategy) {
                        // 本地较新则上传（内容或删除状态可能已变化）
                        ConflictStrategy.NEWER_WINS -> localTime > cloudRecord.modifiedAt
                        ConflictStrategy.LOCAL_WINS -> true
                        ConflictStrategy.CLOUD_WINS -> false
                    }

                    if (shouldUpload) {
                        recordsToUpload.add(localRecord.toBackup())
                    }
                }
            }
        }

        recordsToUpload
    }

    /**
     * 从备份记录构造数据库实体
     */
    private fun toEntity(
        record: AttendanceEntityBackup,
        id: Long,
        createdAt: Long,
        isDeleted: Boolean
    ): AttendanceEntity {
        return AttendanceEntity(
            id = id,
            date = record.date,
            checkInTime = record.checkInTime,
            checkOutTime = record.checkOutTime,
            checkInTimestamp = record.checkInTimestamp,
            checkOutTimestamp = record.checkOutTimestamp,
            note = record.note,
            manualOvertimeHours = record.manualOvertimeHours,
            manualExtraHours = record.manualExtraHours,
            createdAt = createdAt,
            modifiedAt = record.modifiedAt,
            isLeave = record.isLeave,
            leaveType = record.leaveType,
            leaveHours = record.leaveHours,
            isDeleted = isDeleted
        )
    }

    private fun AttendanceEntity.toBackup(): AttendanceEntityBackup {
        return AttendanceEntityBackup(
            date = this.date,
            checkInTime = this.checkInTime,
            checkOutTime = this.checkOutTime,
            checkInTimestamp = this.checkInTimestamp,
            checkOutTimestamp = this.checkOutTimestamp,
            note = this.note,
            manualOvertimeHours = this.manualOvertimeHours,
            manualExtraHours = this.manualExtraHours,
            createdAt = this.createdAt,
            modifiedAt = this.modifiedAt ?: this.createdAt,
            isLeave = this.isLeave,
            leaveType = this.leaveType,
            leaveHours = this.leaveHours,
            isDeleted = this.isDeleted
        )
    }

    fun createExportFileName(): String {
        val timestamp = dateFormat.format(Date())
        return "overtime_backup_$timestamp.json"
    }
}

/** 限制大小地读取 UTF-8 文本；超过上限抛异常，避免超大文件整读导致 OOM */
private fun java.io.InputStream.readCappedUtf8(maxBytes: Long, what: String): String {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0L
    while (true) {
        val read = read(buffer)
        if (read == -1) break
        total += read
        if (total > maxBytes) {
            throw IllegalArgumentException(
                "$what 超过大小上限（${maxBytes / (1024 * 1024)} MB），已中止读取"
            )
        }
        out.write(buffer, 0, read)
    }
    return String(out.toByteArray(), Charsets.UTF_8)
}

/**
 * 同步结果摘要
 */
data class SyncSummary(
    val totalRecords: Int,
    val added: Int,
    val updated: Int,
    val deleted: Int = 0,
    val skipped: Int,
    val conflicts: Int
) {
    fun toDisplayString(): String {
        val parts = mutableListOf<String>()
        if (added > 0) parts.add("新增 $added 条")
        if (updated > 0) parts.add("更新 $updated 条")
        if (deleted > 0) parts.add("删除 $deleted 条")
        if (skipped > 0) parts.add("跳过 $skipped 条")
        return parts.joinToString("，")
    }
}
