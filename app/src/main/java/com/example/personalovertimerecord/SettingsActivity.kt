package com.example.personalovertimerecord

import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.personalovertimerecord.data.OvertimeSettings
import com.example.personalovertimerecord.data.SettingsManager
import com.example.personalovertimerecord.databinding.ActivitySettingsBinding
import com.example.personalovertimerecord.utils.AppLogger
import com.example.personalovertimerecord.utils.NetworkUtils
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
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        
        settingsManager = SettingsManager(this)
        webDAVManager = WebDAVManager(this)
        
        loadSettings()
        loadWebDAVConfig()
        setupButtons()
        setupShiftGroup()
        setupScrollToFocusedView()
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
                dailyWorkHours = dailyWorkHours
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
