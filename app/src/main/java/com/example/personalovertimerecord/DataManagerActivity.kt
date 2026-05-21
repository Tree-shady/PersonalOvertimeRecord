package com.example.personalovertimerecord

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.personalovertimerecord.data.db.AppDatabase
import com.example.personalovertimerecord.data.SettingsManager
import com.example.personalovertimerecord.databinding.ActivityDataManagerBinding
import com.example.personalovertimerecord.utils.DataExporter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DataManagerActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityDataManagerBinding
    private lateinit var dataExporter: DataExporter
    private lateinit var settingsManager: SettingsManager
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    
    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let {
            exportData(it)
        }
    }
    
    private val importLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            importData(it)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDataManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        
        val db = AppDatabase.getDatabase(this)
        dataExporter = DataExporter(this, db.attendanceDao())
        settingsManager = SettingsManager(this)
        
        setupButtons()
        updateStats()
    }
    
    private fun setupButtons() {
        binding.btnExport.setOnClickListener {
            val fileName = dataExporter.createExportFileName()
            exportLauncher.launch(fileName)
        }
        
        binding.btnImport.setOnClickListener {
            showImportConfirmDialog()
        }
    }
    
    private fun exportData(uri: android.net.Uri) {
        lifecycleScope.launch {
            val settings = settingsManager.getSettings()
            
            dataExporter.exportData(
                uri = uri,
                settings = settings,
                onSuccess = {
                    saveLastExportTime()
                    Toast.makeText(this@DataManagerActivity, "数据导出成功！", Toast.LENGTH_LONG).show()
                    updateStats()
                },
                onError = { e ->
                    Toast.makeText(this@DataManagerActivity, "导出失败：${e.message}", Toast.LENGTH_LONG).show()
                }
            )
        }
    }
    
    private fun importData(uri: android.net.Uri) {
        lifecycleScope.launch {
            dataExporter.importData(
                uri = uri,
                onSuccess = { backupData ->
                    showRestoreConfirmDialog(backupData)
                },
                onError = { e ->
                    Toast.makeText(this@DataManagerActivity, "导入失败：${e.message}", Toast.LENGTH_LONG).show()
                }
            )
        }
    }
    
    private fun showImportConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("导入数据")
            .setMessage("请选择要导入的 JSON 文件")
            .setPositiveButton("选择文件") { _, _ ->
                importLauncher.launch("application/json")
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showRestoreConfirmDialog(backupData: com.example.personalovertimerecord.utils.BackupData) {
        val exportDate = dateFormat.format(Date(backupData.exportTime))
        val recordCount = backupData.attendanceRecords.size
        
        AlertDialog.Builder(this)
            .setTitle("确认恢复")
            .setMessage(
                "文件创建时间：$exportDate\n" +
                "包含 $recordCount 条记录\n\n" +
                "恢复将覆盖当前所有数据，确定要继续吗？"
            )
            .setPositiveButton("恢复") { _, _ ->
                restoreData(backupData)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun restoreData(backupData: com.example.personalovertimerecord.utils.BackupData) {
        lifecycleScope.launch {
            try {
                settingsManager.saveSettings(backupData.settings)
                dataExporter.restoreData(backupData)
                
                Toast.makeText(this@DataManagerActivity, "数据恢复成功！", Toast.LENGTH_LONG).show()
                updateStats()
            } catch (e: Exception) {
                Toast.makeText(this@DataManagerActivity, "恢复失败：${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun updateStats() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@DataManagerActivity)
            val count = db.attendanceDao().getCount()
            binding.tvRecordCount.text = "加班记录数：$count"
            
            val lastExport = getLastExportTime()
            binding.tvLastExport.text = if (lastExport > 0) {
                "最后导出：${dateFormat.format(Date(lastExport))}"
            } else {
                "最后导出：未导出过"
            }
        }
    }
    
    private fun saveLastExportTime() {
        val prefs = getSharedPreferences("data_manager", MODE_PRIVATE)
        prefs.edit().putLong("last_export_time", System.currentTimeMillis()).apply()
    }
    
    private fun getLastExportTime(): Long {
        val prefs = getSharedPreferences("data_manager", MODE_PRIVATE)
        return prefs.getLong("last_export_time", 0L)
    }
}

