package com.example.personalovertimerecord.data.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.personalovertimerecord.utils.DatabaseKeyManager
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [AttendanceEntity::class],
    version = 1,
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
                .fallbackToDestructiveMigration()
                .build()
        }
        
        private fun createUnencryptedDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "overtime_database_unencrypted"
            )
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
