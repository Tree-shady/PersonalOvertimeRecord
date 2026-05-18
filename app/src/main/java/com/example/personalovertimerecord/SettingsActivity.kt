package com.example.personalovertimerecord

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.personalovertimerecord.data.OvertimeSettings
import com.example.personalovertimerecord.data.SettingsManager
import com.example.personalovertimerecord.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settingsManager: SettingsManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        
        settingsManager = SettingsManager(this)
        loadSettings()
        setupButtons()
        setupShiftGroup()
    }
    
    private fun setupShiftGroup() {
        binding.shiftGroup.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.shiftCustom) {
                binding.customTimeLayout.visibility = android.view.View.VISIBLE
            } else {
                binding.customTimeLayout.visibility = android.view.View.GONE
            }
        }
    }
    
    private fun loadSettings() {
        val currentSettings = settingsManager.getSettings()
        
        binding.shiftGroup.check(R.id.shiftNormal)
        binding.customTimeLayout.visibility = android.view.View.GONE
        
        binding.etWorkStart.setText(currentSettings.workStartTime)
        binding.etWorkEnd.setText(currentSettings.workEndTime)
        binding.etRateNormal.setText(currentSettings.overtimeRateNormal.toString())
        binding.etRateWeekend.setText(currentSettings.overtimeRateWeekend.toString())
        binding.etRateHoliday.setText(currentSettings.overtimeRateHoliday.toString())
        binding.etBaseSalary.setText(currentSettings.baseSalary.toString())
        binding.etPerformancePercent.setText(currentSettings.performancePercent.toString())
        binding.etMonthlyWorkDays.setText(currentSettings.monthlyWorkDays.toString())
        binding.etDailyWorkHours.setText(currentSettings.dailyWorkHours.toString())
    }
    
    private fun setupButtons() {
        binding.btnSave.setOnClickListener {
            saveSettings()
        }
    }
    
    private fun saveSettings() {
        try {
            val workStart = binding.etWorkStart.text?.toString() ?: "08:00"
            val workEnd = binding.etWorkEnd.text?.toString() ?: "17:00"
            
            val rateNormal = binding.etRateNormal.text?.toString()?.toDoubleOrNull() ?: 1.5
            val rateWeekend = binding.etRateWeekend.text?.toString()?.toDoubleOrNull() ?: 2.0
            val rateHoliday = binding.etRateHoliday.text?.toString()?.toDoubleOrNull() ?: 3.0
            val baseSalary = binding.etBaseSalary.text?.toString()?.toDoubleOrNull() ?: 5000.0
            val performancePercent = binding.etPerformancePercent.text?.toString()?.toDoubleOrNull() ?: 0.0
            val monthlyWorkDays = binding.etMonthlyWorkDays.text?.toString()?.toDoubleOrNull() ?: 21.75
            val dailyWorkHours = binding.etDailyWorkHours.text?.toString()?.toDoubleOrNull() ?: 8.0
            
            val settings = OvertimeSettings(
                workStartTime = workStart,
                workEndTime = workEnd,
                overtimeRateNormal = rateNormal,
                overtimeRateWeekend = rateWeekend,
                overtimeRateHoliday = rateHoliday,
                baseSalary = baseSalary,
                performancePercent = performancePercent,
                monthlyWorkDays = monthlyWorkDays,
                dailyWorkHours = dailyWorkHours
            )
            
            settingsManager.saveSettings(settings)
            
            Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "保存失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
