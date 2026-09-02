package com.example.personalovertimerecord.data.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.personalovertimerecord.utils.DatabaseKeyManager
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [AttendanceEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun attendanceDao(): AttendanceDao

    companion object {
        private const val TAG = "AppDatabase"
        
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: createDatabase(context).also { INSTANCE = it }
            }
        }
        
        private fun createDatabase(context: Context): AppDatabase {
            return try {
                createEncryptedDatabase(context)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create encrypted database, falling back to unencrypted", e)
                createUnencryptedDatabase(context)
            }
        }
        
        private fun createEncryptedDatabase(context: Context): AppDatabase {
            val passphrase = DatabaseKeyManager.getDatabaseKey(context)
            val factory = SupportFactory(passphrase)
            
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "overtime_database"
            )
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build()
        }
        
        private fun createUnencryptedDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "overtime_database_unencrypted"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build()
        }
        
        // 从版本1迁移到版本2，添加 modifiedAt 字段
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 添加 modifiedAt 列，默认值为 NULL
                database.execSQL(
                    "ALTER TABLE attendance_records ADD COLUMN modifiedAt INTEGER"
                )
            }
        }

        // 从版本2迁移到版本3，添加请假相关字段（isLeave / leaveType / leaveHours）
        // 此前缺失该迁移，配合 fallbackToDestructiveMigration 会导致老用户升级时整库被删
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE attendance_records ADD COLUMN isLeave INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "ALTER TABLE attendance_records ADD COLUMN leaveType TEXT"
                )
                database.execSQL(
                    "ALTER TABLE attendance_records ADD COLUMN leaveHours REAL NOT NULL DEFAULT 0"
                )
            }
        }

        // 从版本3迁移到版本4，添加软删除标记字段
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 添加 isDeleted 列，默认值为 0（未删除）
                database.execSQL(
                    "ALTER TABLE attendance_records ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0"
                )
            }
        }
    }
}
