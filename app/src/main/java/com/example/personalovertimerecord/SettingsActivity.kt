package com.example.personalovertimerecord

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.personalovertimerecord.data.OvertimeSettings
import com.example.personalovertimerecord.data.SettingsManager
import com.example.personalovertimerecord.databinding.ActivitySettingsBinding
import com.example.personalovertimerecord.utils.AppLogger
import com.example.personalovertimerecord.utils.AutoSyncManager
import com.example.personalovertimerecord.utils.NetworkUtils
import com.example.personalovertimerecord.utils.BiometricManager
import com.example.personalovertimerecord.utils.ReminderManager
import com.example.personalovertimerecord.utils.ThemeManager
import com.example.personalovertimerecord.utils.ThemeMode
import com.example.personalovertimerecord.utils.WebDAVConfig
import com.example.personalovertimerecord.utils.WebDAVManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settingsManager: SettingsManager
    private lateinit var webDAVManager: WebDAVManager
    private var lastResponseCode: Int = 0
    private var isUpdatingAutoSync = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        
        settingsManager = SettingsManager(this)
        webDAVManager = WebDAVManager(this)
        
        // 初始化管理器
        AutoSyncManager.init(this)
        ReminderManager.init(this)
        
        loadSettings()
        loadWebDAVConfig()
        setupThemeGroup()
        setupReminder()
        setupBiometric()
        setupAutoSync()
        setupEncryption()
        setupButtons()
        setupShiftGroup()
        setupScrollToFocusedView()
    }
    
    private fun setupBiometric() {
        val isSupported = BiometricManager.isBiometricSupported(this)
        
        if (!isSupported) {
            binding.switchBiometric.isEnabled = false
            binding.switchBiometric.isChecked = false
            binding.tvBiometricStatus.text = "设备不支持生物识别功能"
            binding.tvBiometricStatus.setTextColor(android.graphics.Color.GRAY)
            return
        }
        
        binding.switchBiometric.isChecked = BiometricManager.isBiometricEnabled(this)
        binding.tvBiometricStatus.text = "已准备就绪（支持指纹/面容解锁）"
        binding.tvBiometricStatus.setTextColor(android.graphics.Color.GREEN)
        
        binding.switchBiometric.setOnCheckedChangeListener { _, isChecked ->
            BiometricManager.setBiometricEnabled(this, isChecked)
            Toast.makeText(this, if (isChecked) "已启用生物识别保护" else "已禁用生物识别保护", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setupReminder() {
        // 上班提醒开关
        binding.switchWorkReminder.isChecked = ReminderManager.isWorkReminderEnabled()
        updateWorkReminderVisibility()
        
        // 下班提醒开关
        binding.switchOffWorkReminder.isChecked = ReminderManager.isOffWorkReminderEnabled()
        updateOffWorkReminderVisibility()
        
        // 仅工作日开关
        binding.switchWorkdaysOnly.isChecked = ReminderManager.isWorkdaysOnly()
        
        // 设置时间按钮文字
        binding.btnWorkReminderTime.text = ReminderManager.getWorkReminderTime()
        binding.btnOffWorkReminderTime.text = ReminderManager.getOffWorkReminderTime()
        
        // 上班提醒开关监听
        binding.switchWorkReminder.setOnCheckedChangeListener { _, isChecked ->
            ReminderManager.setWorkReminderEnabled(this, isChecked)
            updateWorkReminderVisibility()
            Toast.makeText(this, if (isChecked) "已启用上班提醒" else "已禁用上班提醒", Toast.LENGTH_SHORT).show()
        }
        
        // 下班提醒开关监听
        binding.switchOffWorkReminder.setOnCheckedChangeListener { _, isChecked ->
            ReminderManager.setOffWorkReminderEnabled(this, isChecked)
            updateOffWorkReminderVisibility()
            Toast.makeText(this, if (isChecked) "已启用下班提醒" else "已禁用下班提醒", Toast.LENGTH_SHORT).show()
        }
        
        // 仅工作日开关监听
        binding.switchWorkdaysOnly.setOnCheckedChangeListener { _, isChecked ->
            ReminderManager.setWorkdaysOnly(isChecked)
        }
        
        // 上班提醒时间按钮
        binding.btnWorkReminderTime.setOnClickListener {
            showTimePicker(ReminderManager.getWorkReminderTime()) { time ->
                ReminderManager.setWorkReminderTime(this, time)
                binding.btnWorkReminderTime.text = time
            }
        }
        
        // 下班提醒时间按钮
        binding.btnOffWorkReminderTime.setOnClickListener {
            showTimePicker(ReminderManager.getOffWorkReminderTime()) { time ->
                ReminderManager.setOffWorkReminderTime(this, time)
                binding.btnOffWorkReminderTime.text = time
            }
        }
    }
    
    private fun updateWorkReminderVisibility() {
        binding.workReminderTimeLayout.visibility = if (binding.switchWorkReminder.isChecked) View.VISIBLE else View.GONE
    }
    
    private fun updateOffWorkReminderVisibility() {
        binding.offWorkReminderTimeLayout.visibility = if (binding.switchOffWorkReminder.isChecked) View.VISIBLE else View.GONE
    }
    
    private fun showTimePicker(currentTime: String, onTimeSelected: (String) -> Unit) {
        val parts = currentTime.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 8
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        
        TimePickerDialog(
            this,
            { _, selectedHour, selectedMinute ->
                val time = String.format("%02d:%02d", selectedHour, selectedMinute)
                onTimeSelected(time)
            },
            hour,
            minute,
            true
        ).show()
    }
    
    private fun setupAutoSync() {
        // 设置自动同步开关
        binding.switchAutoSync.isChecked = AutoSyncManager.isSyncEnabled()
        updateAutoSyncSettingsVisibility()
        
        // 设置同步间隔选项
        val intervalOptions = AutoSyncManager.SYNC_INTERVALS.values.toList()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, intervalOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerSyncInterval.adapter = adapter
        
        // 设置当前同步间隔
        val currentInterval = AutoSyncManager.getSyncInterval()
        val intervalIndex = AutoSyncManager.SYNC_INTERVALS.keys.toList().indexOf(currentInterval)
        if (intervalIndex >= 0) {
            binding.spinnerSyncInterval.setSelection(intervalIndex)
        }
        
        // WiFi下同步开关
        binding.switchWifiOnly.isChecked = AutoSyncManager.isWifiOnly()
        
        // 更新上次同步时间
        updateLastSyncTime()
        
        // 自动同步开关监听
        binding.switchAutoSync.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingAutoSync) {
                AutoSyncManager.setSyncEnabled(this, isChecked)
                updateAutoSyncSettingsVisibility()
                Toast.makeText(this, if (isChecked) "已启用自动同步" else "已禁用自动同步", Toast.LENGTH_SHORT).show()
            }
        }
        
        // 同步间隔选择监听
        binding.spinnerSyncInterval.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!isUpdatingAutoSync) {
                    val intervals = AutoSyncManager.SYNC_INTERVALS.keys.toList()
                    if (position < intervals.size) {
                        AutoSyncManager.setSyncInterval(this@SettingsActivity, intervals[position])
                    }
                }
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        // WiFi下同步开关监听
        binding.switchWifiOnly.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingAutoSync) {
                AutoSyncManager.setWifiOnly(isChecked)
            }
        }
        
        // 立即同步按钮
        binding.btnSyncNow.setOnClickListener {
            performManualSync()
        }
    }
    
    private fun updateAutoSyncSettingsVisibility() {
        binding.autoSyncSettingsLayout.visibility = if (binding.switchAutoSync.isChecked) View.VISIBLE else View.GONE
    }
    
    private fun updateLastSyncTime() {
        binding.tvLastSyncTime.text = "上次同步：${AutoSyncManager.getLastSyncTimeString(this@SettingsActivity)}"
    }
    
    private fun performManualSync() {
        if (!NetworkUtils.isNetworkAvailable(this)) {
            Toast.makeText(this, "请先检查网络连接", Toast.LENGTH_SHORT).show()
            return
        }
        
        binding.btnSyncNow.isEnabled = false
        binding.btnSyncNow.text = "同步中..."
        
        AutoSyncManager.performSync(this) { success, message ->
            binding.btnSyncNow.isEnabled = true
            binding.btnSyncNow.text = "立即同步"
            updateLastSyncTime()
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setupThemeGroup() {
        // 根据当前主题模式设置选中的 RadioButton
        val currentTheme = ThemeManager.getThemeMode()
        when (currentTheme) {
            ThemeMode.SYSTEM -> binding.themeSystem.isChecked = true
            ThemeMode.LIGHT -> binding.themeLight.isChecked = true
            ThemeMode.DARK -> binding.themeDark.isChecked = true
        }
        
        binding.themeGroup.setOnCheckedChangeListener { _, checkedId ->
            val newTheme = when (checkedId) {
                R.id.themeSystem -> ThemeMode.SYSTEM
                R.id.themeLight -> ThemeMode.LIGHT
                R.id.themeDark -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            }
            ThemeManager.setThemeMode(newTheme)
            Toast.makeText(this, "主题已切换为${newTheme.displayName}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setupEncryption() {
        val currentSettings = settingsManager.getSettings()
        
        // 导出加密开关
        binding.switchExportEncryption.isChecked = currentSettings.exportEncryptionEnabled
        updateExportPasswordVisibility()
        
        // 同步加密开关
        binding.switchSyncEncryption.isChecked = currentSettings.syncEncryptionEnabled
        updateSyncPasswordVisibility()
        
        // 设置密码
        binding.etExportPassword.setText(currentSettings.exportPassword)
        binding.etSyncPassword.setText(currentSettings.syncPassword)
        
        // 导出加密开关监听
        binding.switchExportEncryption.setOnCheckedChangeListener { _, isChecked ->
            updateExportPasswordVisibility()
            Toast.makeText(this, if (isChecked) "已启用导出加密" else "已禁用导出加密", Toast.LENGTH_SHORT).show()
        }
        
        // 同步加密开关监听
        binding.switchSyncEncryption.setOnCheckedChangeListener { _, isChecked ->
            updateSyncPasswordVisibility()
            Toast.makeText(this, if (isChecked) "已启用同步加密" else "已禁用同步加密", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updateExportPasswordVisibility() {
        binding.exportPasswordLayout.visibility = if (binding.switchExportEncryption.isChecked) View.VISIBLE else View.GONE
    }
    
    private fun updateSyncPasswordVisibility() {
        binding.syncPasswordLayout.visibility = if (binding.switchSyncEncryption.isChecked) View.VISIBLE else View.GONE
    }
    
    private fun setupScrollToFocusedView() {
        val rootView = binding.root
        
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val rect = android.graphics.Rect()
            rootView.getWindowVisibleDisplayFrame(rect)
            val screenHeight = rootView.rootView.height
            val keypadHeight = screenHeight - rect.bottom
            
            if (keypadHeight > screenHeight * 0.15) {
                val focusedView = currentFocus
                if (focusedView != null) {
                    binding.scrollView.post {
                        binding.scrollView.requestChildFocus(focusedView, focusedView)
                    }
                }
            }
        }
    }
    
    private fun setupShiftGroup() {
        binding.shiftGroup.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.shiftCustom) {
                binding.customTimeLayout.visibility = View.VISIBLE
            } else {
                binding.customTimeLayout.visibility = View.GONE
            }
        }
    }
    
    private fun loadSettings() {
        val currentSettings = settingsManager.getSettings()
        
        binding.shiftGroup.check(R.id.shiftNormal)
        binding.customTimeLayout.visibility = View.GONE
        
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
    
    private fun loadWebDAVConfig() {
        val config = settingsManager.getWebDAVConfig()
        config?.let {
            binding.etWebDAVServer.setText(it.serverUrl)
            binding.etWebDAVUsername.setText(it.username)
            binding.etWebDAVPassword.setText(it.password)
            binding.etWebDAVPath.setText(it.remotePath)
        } ?: run {
            binding.etWebDAVPath.setText("/")
        }
    }
    
    private fun setupButtons() {
        binding.btnSave.setOnClickListener {
            saveSettings()
        }
        
        binding.btnTestConnection.setOnClickListener {
            testWebDAVConnection()
        }
        
        binding.btnSaveWebDAV.setOnClickListener {
            saveWebDAVConfig()
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
            
            if (!validateInput(rateNormal, rateWeekend, rateHoliday, baseSalary, monthlyWorkDays, dailyWorkHours)) {
                return
            }
            
            val settings = OvertimeSettings(
                workStartTime = workStart,
                workEndTime = workEnd,
                overtimeRateNormal = rateNormal,
                overtimeRateWeekend = rateWeekend,
                overtimeRateHoliday = rateHoliday,
                baseSalary = baseSalary,
                performancePercent = performancePercent,
                monthlyWorkDays = monthlyWorkDays,
                dailyWorkHours = dailyWorkHours,
                // 加密设置
                exportEncryptionEnabled = binding.switchExportEncryption.isChecked,
                exportPassword = binding.etExportPassword.text?.toString() ?: "",
                syncEncryptionEnabled = binding.switchSyncEncryption.isChecked,
                syncPassword = binding.etSyncPassword.text?.toString() ?: ""
            )
            
            settingsManager.saveSettings(settings)
            
            Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "保存失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun testWebDAVConnection() {
        val config = getCurrentWebDAVConfig() ?: return
        
        // 检查网络连接
        if (!NetworkUtils.isNetworkAvailable(this)) {
            Toast.makeText(this, "请先检查网络连接", Toast.LENGTH_SHORT).show()
            return
        }
        
        binding.btnTestConnection.isEnabled = false
        binding.btnTestConnection.text = "测试中..."
        
        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    val testUrl = buildTestUrl(config)
                    AppLogger.d("开始测试连接: $testUrl")
                    webDAVManager.testConnection(config)
                } catch (e: Exception) {
                    AppLogger.e("测试连接异常", e)
                    false
                }
            }
            
            binding.btnTestConnection.isEnabled = true
            binding.btnTestConnection.text = "测试连接"
            
            if (success) {
                Toast.makeText(this@SettingsActivity, "连接成功！", Toast.LENGTH_SHORT).show()
            } else {
                showWebDAVHelpDialog()
            }
        }
    }
    
    private fun buildTestUrl(config: WebDAVConfig): String {
        val cleanBaseUrl = config.serverUrl.trimEnd('/')
        val cleanPath = config.remotePath.trim('/')
        
        return if (cleanPath.isEmpty()) {
            "$cleanBaseUrl/.test_connection"
        } else {
            "$cleanBaseUrl/$cleanPath/.test_connection"
        }
    }
    
    private fun showWebDAVHelpDialog() {
        val responseCodeInfo = if (WebDAVManager.lastResponseCode != 0) {
            val codeDesc = when (WebDAVManager.lastResponseCode) {
                200 -> "成功"
                201 -> "创建成功"
                204 -> "无内容（成功）"
                401 -> "认证失败，请检查用户名和密码"
                403 -> "禁止访问"
                404 -> "路径不存在"
                405 -> "方法不允许"
                500 -> "服务器内部错误"
                -1 -> "连接异常，请检查网络"
                else -> "未知错误"
            }
            "服务器响应码: ${WebDAVManager.lastResponseCode}\n说明: $codeDesc\n\n"
        } else {
            ""
        }
        
        AlertDialog.Builder(this)
            .setTitle("连接失败")
            .setMessage(
                """
                $responseCodeInfo
                连接失败，请检查以下配置：
                
                • 服务器地址：完整的 WebDAV URL，例如 https://webdav-1833423170.pd1.123pan.cn/webdav
                • 用户名和密码：确保正确填写（使用您的123云盘账号）
                • 网络连接：确保设备可以访问外网
                
                远程路径建议改为：/
                
                常见 WebDAV 配置：
                • 您的123云盘：https://webdav-1833423170.pd1.123pan.cn/webdav
                • 普通123云盘：https://webdav.123pan.cn/webdav
                • 坚果云：https://dav.jianguoyun.com/dav
                
                建议：
                1. 先尝试把远程路径改为 "/"
                2. 确认 123云盘 已开启 WebDAV 功能（在设置中）
                3. 检查账号是否是 123云盘 的主账号
                4. 尝试关闭 VPN 再测试
                """.trimIndent()
            )
            .setPositiveButton("确定", null)
            .show()
    }
    
    private fun saveWebDAVConfig() {
        val config = getCurrentWebDAVConfig() ?: return
        
        settingsManager.saveWebDAVConfig(config)
        Toast.makeText(this, "WebDAV配置已保存", Toast.LENGTH_SHORT).show()
    }
    
    private fun getCurrentWebDAVConfig(): WebDAVConfig? {
        val serverUrl = binding.etWebDAVServer.text?.toString()?.trim()
        val username = binding.etWebDAVUsername.text?.toString()?.trim()
        val password = binding.etWebDAVPassword.text?.toString()
        val remotePath = binding.etWebDAVPath.text?.toString()?.trim() ?: "/"
        
        if (serverUrl.isNullOrEmpty() || username.isNullOrEmpty() || password.isNullOrEmpty()) {
            Toast.makeText(this, "请填写完整的WebDAV配置信息", Toast.LENGTH_SHORT).show()
            return null
        }
        
        // 自动添加 https:// 如果没有协议
        val normalizedServerUrl = if (!serverUrl.startsWith("http://") && !serverUrl.startsWith("https://")) {
            "https://$serverUrl"
        } else {
            serverUrl
        }

        // 明文 HTTP 已禁止（见 network_security_config.xml），提示用户改用 https
        if (normalizedServerUrl.startsWith("http://")) {
            Toast.makeText(
                this,
                "应用已禁止明文 HTTP 流量，请使用 https:// 地址",
                Toast.LENGTH_LONG
            ).show()
        }

        return WebDAVConfig(
            serverUrl = normalizedServerUrl,
            username = username,
            password = password,
            remotePath = remotePath
        )
    }
    
    private fun validateInput(
        rateNormal: Double,
        rateWeekend: Double,
        rateHoliday: Double,
        baseSalary: Double,
        monthlyWorkDays: Double,
        dailyWorkHours: Double
    ): Boolean {
        if (baseSalary <= 0) {
            Toast.makeText(this, "基本工资必须大于0", Toast.LENGTH_SHORT).show()
            return false
        }
        if (monthlyWorkDays <= 0 || monthlyWorkDays > 31) {
            Toast.makeText(this, "每月工作天数应在1-31之间", Toast.LENGTH_SHORT).show()
            return false
        }
        if (dailyWorkHours <= 0 || dailyWorkHours > 24) {
            Toast.makeText(this, "每日工作时长应在0.1-24之间", Toast.LENGTH_SHORT).show()
            return false
        }
        if (rateNormal < 1.0 || rateWeekend < 1.0 || rateHoliday < 1.0) {
            Toast.makeText(this, "加班倍率不能小于1", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }
}
