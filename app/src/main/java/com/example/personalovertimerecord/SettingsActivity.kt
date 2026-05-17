package com.example.personalovertimerecord

import android.os.Bundle
import android.view.View
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.example.personalovertimerecord.data.OvertimeSettings
import com.example.personalovertimerecord.data.SettingsManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var settingsManager: SettingsManager
    private lateinit var shiftGroup: RadioGroup
    private lateinit var customTimeLayout: View
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
        
        shiftGroup = findViewById(R.id.shiftGroup)
        customTimeLayout = findViewById(R.id.customTimeLayout)
        
        settingsManager = SettingsManager(this)
        loadSettings()
        setupButtons()
        setupShiftGroup()
    }
    
    private fun setupShiftGroup() {
        shiftGroup.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.shiftCustom) {
                customTimeLayout.visibility = View.VISIBLE
            } else {
                customTimeLayout.visibility = View.GONE
            }
        }
    }
    
    private fun loadSettings() {
        val currentSettings = settingsManager.getSettings()
        
        shiftGroup.check(R.id.shiftNormal)
        customTimeLayout.visibility = View.GONE
        
        findViewById<TextInputEditText>(R.id.etWorkStart).setText(currentSettings.workStartTime)
        findViewById<TextInputEditText>(R.id.etWorkEnd).setText(currentSettings.workEndTime)
        findViewById<TextInputEditText>(R.id.etRateNormal).setText(currentSettings.overtimeRateNormal.toString())
        findViewById<TextInputEditText>(R.id.etRateWeekend).setText(currentSettings.overtimeRateWeekend.toString())
        findViewById<TextInputEditText>(R.id.etRateHoliday).setText(currentSettings.overtimeRateHoliday.toString())
        findViewById<TextInputEditText>(R.id.etBaseSalary).setText(currentSettings.baseSalary.toString())
        findViewById<TextInputEditText>(R.id.etPerformancePercent).setText(currentSettings.performancePercent.toString())
        findViewById<TextInputEditText>(R.id.etMonthlyWorkDays).setText(currentSettings.monthlyWorkDays.toString())
        findViewById<TextInputEditText>(R.id.etDailyWorkHours).setText(currentSettings.dailyWorkHours.toString())
    }
    
    private fun setupButtons() {
        findViewById<MaterialButton>(R.id.btnSave).setOnClickListener {
            saveSettings()
        }
    }
    
    private fun saveSettings() {
        try {
            val workStart = findViewById<TextInputEditText>(R.id.etWorkStart).text?.toString() ?: "08:00"
            val workEnd = findViewById<TextInputEditText>(R.id.etWorkEnd).text?.toString() ?: "17:00"
            
            val rateNormal = findViewById<TextInputEditText>(R.id.etRateNormal).text?.toString()?.toDoubleOrNull() ?: 1.5
            val rateWeekend = findViewById<TextInputEditText>(R.id.etRateWeekend).text?.toString()?.toDoubleOrNull() ?: 2.0
            val rateHoliday = findViewById<TextInputEditText>(R.id.etRateHoliday).text?.toString()?.toDoubleOrNull() ?: 3.0
            val baseSalary = findViewById<TextInputEditText>(R.id.etBaseSalary).text?.toString()?.toDoubleOrNull() ?: 5000.0
            val performancePercent = findViewById<TextInputEditText>(R.id.etPerformancePercent).text?.toString()?.toDoubleOrNull() ?: 0.0
            val monthlyWorkDays = findViewById<TextInputEditText>(R.id.etMonthlyWorkDays).text?.toString()?.toDoubleOrNull() ?: 21.75
            val dailyWorkHours = findViewById<TextInputEditText>(R.id.etDailyWorkHours).text?.toString()?.toDoubleOrNull() ?: 8.0
            
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
