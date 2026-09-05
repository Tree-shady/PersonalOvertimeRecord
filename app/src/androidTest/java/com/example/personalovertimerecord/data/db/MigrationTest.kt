package com.example.personalovertimerecord.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room 迁移测试（需在设备/模拟器上执行：connectedDebugAndroidTest）。
 *
 * 背景：v5 时代曾出现「同一版本号但 date 索引唯一/非唯一两种 schema」的漂移，
 * 导致旧库升级时触发 identity hash 校验崩溃。这里直接用手工构造的历史库文件
 * 走 AppDatabase 真实迁移路径，验证 MIGRATION_4_5/MIGRATION_5_6：
 * 1) 健康 v5（唯一索引）→ v6：数据完整保留、索引保持唯一、identity 与全新 v6 一致；
 * 2) 回归场景 v5（非唯一索引 + 同日期重复，即 7f6c3140… 那类库）→ v6：去重、唯一索引、可正常打开；
 * 3) v4（无索引 + 重复）→ 4→5→6 迁移链：同上。
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val opened = mutableListOf<AppDatabase>()
    private val openedNames = mutableListOf<String>()

    private val v5TableSql = """
        CREATE TABLE attendance_records (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            date TEXT NOT NULL,
            checkInTime TEXT,
            checkOutTime TEXT,
            checkInTimestamp INTEGER,
            checkOutTimestamp INTEGER,
            note TEXT,
            manualOvertimeHours REAL NOT NULL,
            manualExtraHours REAL NOT NULL,
            createdAt INTEGER NOT NULL,
            modifiedAt INTEGER,
            isLeave INTEGER NOT NULL,
            leaveType TEXT,
            leaveHours REAL NOT NULL,
            isDeleted INTEGER NOT NULL
        )
    """.trimIndent()

    private val v5UniqueIndexSql =
        "CREATE UNIQUE INDEX IF NOT EXISTS index_attendance_records_date ON attendance_records(date)"
    private val v5PlainIndexSql =
        "CREATE INDEX IF NOT EXISTS index_attendance_records_date ON attendance_records(date)"

    @After
    fun tearDown() {
        opened.forEach { it.close() }
        opened.clear()
        openedNames.forEach { context.deleteDatabase(it) }
        openedNames.clear()
    }

    private fun seed(db: android.database.sqlite.SQLiteDatabase, date: String, createdAt: Long, isDeleted: Int) {
        db.execSQL(
            "INSERT INTO attendance_records " +
                "(date, manualOvertimeHours, manualExtraHours, createdAt, isLeave, leaveHours, isDeleted) " +
                "VALUES ('$date', -1.0, -1.0, $createdAt, 0, 0.0, $isDeleted)"
        )
    }

    /**
     * 用纯 SQL 手工构造一个"历史版本"数据库文件（不经 Room 建库），
     * 从而能模拟 Room 已发布版本的任意历史形态（含 schema 漂移态）。
     */
    private fun createRawDatabase(
        name: String,
        userVersion: Int,
        indexSql: String?,
        seedAction: (android.database.sqlite.SQLiteDatabase) -> Unit
    ) {
        openedNames.add(name)
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL(v5TableSql)
            indexSql?.let { db.execSQL(it) }
            seedAction(db)
            db.version = userVersion
        }
    }

    private fun openAppDatabase(name: String): AppDatabase {
        openedNames.add(name)
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        opened.add(db)
        return db
    }

    private fun readIdentityHash(db: AppDatabase): String? {
        db.openHelper.readableDatabase.query(
            "SELECT identity_hash FROM room_master_table WHERE id = 42"
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    private fun isDateIndexUnique(db: AppDatabase): Boolean {
        db.openHelper.readableDatabase.query("PRAGMA index_list('attendance_records')").use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                val unique = cursor.getInt(cursor.getColumnIndexOrThrow("unique"))
                if (name == "index_attendance_records_date" && unique == 1) return true
            }
        }
        return false
    }

    @Test
    fun migrate5to6_preservesHealthyData() {
        createRawDatabase("migration_5_6_healthy", userVersion = 5, indexSql = v5UniqueIndexSql) { db ->
            seed(db, "2026-01-01", createdAt = 100, isDeleted = 0)
            seed(db, "2026-01-02", createdAt = 200, isDeleted = 0)
        }

        val db = openAppDatabase("migration_5_6_healthy")
        val rows = runBlocking { db.attendanceDao().getAllRecordsIncludingDeletedSync() }

        assertEquals(2, rows.size)
        assertEquals(setOf("2026-01-01", "2026-01-02"), rows.map { it.date }.toSet())
        assertTrue(isDateIndexUnique(db))
        assertNotNull(readIdentityHash(db))

        // 迁移后的 identity hash 应与全新 v6 库完全一致
        val fresh = openAppDatabase("migration_fresh_v6")
        runBlocking { fresh.attendanceDao().getCount() }
        assertEquals(readIdentityHash(fresh), readIdentityHash(db))
    }

    @Test
    fun migrate5to6_healsNonUniqueIndexAndDedupes() {
        // 复现线上崩溃场景：v5 库的 date 索引是非唯一（identity 7f6c3140… 那类），
        // 且同日期存在重复行（一条活跃、一条软删除）
        createRawDatabase("migration_5_6_drift", userVersion = 5, indexSql = v5PlainIndexSql) { db ->
            seed(db, "2026-01-01", createdAt = 100, isDeleted = 0)
            seed(db, "2026-01-01", createdAt = 200, isDeleted = 1) // 墓碑
            seed(db, "2026-01-02", createdAt = 300, isDeleted = 0)
        }

        // 迁移后应能正常打开（不再抛 identity 校验异常）
        val db = openAppDatabase("migration_5_6_drift")
        val rows = runBlocking { db.attendanceDao().getAllRecordsIncludingDeletedSync() }

        // 去重：同日期只保留一条（保留活跃记录 id 最大者）
        assertEquals(2, rows.size)
        assertEquals(setOf("2026-01-01", "2026-01-02"), rows.map { it.date }.toSet())
        assertTrue(rows.filter { it.date == "2026-01-01" }.all { !it.isDeleted })
        assertTrue(isDateIndexUnique(db))

        val fresh = openAppDatabase("migration_fresh_v6_b")
        runBlocking { fresh.attendanceDao().getCount() }
        assertEquals(readIdentityHash(fresh), readIdentityHash(db))
    }

    @Test
    fun migrate4to6_chainCreatesUniqueIndexAndDedupes() {
        // v4：15 列齐全但完全没有 date 索引，且同日期存在重复
        createRawDatabase("migration_4_6_chain", userVersion = 4, indexSql = null) { db ->
            seed(db, "2026-01-01", createdAt = 100, isDeleted = 0)
            seed(db, "2026-01-01", createdAt = 200, isDeleted = 1) // 墓碑
            seed(db, "2026-01-02", createdAt = 300, isDeleted = 0)
        }

        val db = openAppDatabase("migration_4_6_chain")
        val rows = runBlocking { db.attendanceDao().getAllRecordsIncludingDeletedSync() }

        assertEquals(2, rows.size)
        assertEquals(setOf("2026-01-01", "2026-01-02"), rows.map { it.date }.toSet())
        assertTrue(isDateIndexUnique(db))

        val fresh = openAppDatabase("migration_fresh_v6_c")
        runBlocking { fresh.attendanceDao().getCount() }
        assertEquals(readIdentityHash(fresh), readIdentityHash(db))
    }
}
