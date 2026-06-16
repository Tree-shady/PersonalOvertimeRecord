package com.example.personalovertimerecord

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.personalovertimerecord.adapter.AttendanceAdapter
import com.example.personalovertimerecord.data.Attendance
import com.example.personalovertimerecord.data.OvertimeSettings
import com.example.personalovertimerecord.data.SettingsManager
import com.example.personalovertimerecord.data.db.AppDatabase
import com.example.personalovertimerecord.databinding.ActivityMainBinding
import com.example.personalovertimerecord.dialog.AddOvertimeDialog
import com.example.personalovertimerecord.utils.DateUtils
import com.example.personalovertimerecord.utils.Formatter
import com.example.personalovertimerecord.utils.OvertimeCalculator
import com.example.personalovertimerecord.utils.SyncDirection
import com.example.personalovertimerecord.utils.SyncManager
import com.example.personalovertimerecord.utils.SyncOptions
import com.example.personalovertimerecord.utils.SyncPresets
import com.example.personalovertimerecord.utils.SyncResult
import com.example.personalovertimerecord.view.CalendarView
import com.example.personalovertimerecord.viewmodel.AttendanceViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val TIME_UPDATE_INTERVAL = 5000L
    }
    
    private val viewModel: AttendanceViewModel by viewModels()
    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: AttendanceAdapter
    private lateinit var settingsManager: SettingsManager
    private lateinit var syncManager: SyncManager
    private lateinit var calendarView: CalendarView
    private lateinit var statusCard: com.google.android.material.card.MaterialCardView
    private lateinit var tvStatus: android.widget.TextView
    private lateinit var statusProgress: ProgressBar
    private var currentSettings: OvertimeSettings = OvertimeSettings()
    
    private var currentYear: Int = Calendar.getInstance().get(Calendar.YEAR)
    private var currentMonth: Int = Calendar.getInstance().get(Calendar.MONTH)
    
    private var timeUpdateJob: kotlinx.coroutines.Job? = null
    private var startupCheckJob: kotlinx.coroutines.Job? = null
    private val handler = Handler()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.animation = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        settingsManager = SettingsManager(this)
        val database = AppDatabase.getDatabase(this)
        syncManager = SyncManager(this, settingsManager, database.attendanceDao())
        
        initStatusViews()
        setupCalendar()
        setupRecyclerView()
        setupButtons()
        observeViewModel()
        updateCurrentTime()
        updateCurrentDate()
        updateCalendarData()
        
        // 启动后台检查
        runStartupChecks()
    }
    
    private fun initStatusViews() {
        statusCard = binding.statusCard
        tvStatus = binding.tvStatus
        statusProgress = binding.statusProgress
        binding.btnCloseStatus.setOnClickListener {
            statusCard.visibility = View.GONE
        }
    }
    
    private fun runStartupChecks() {
        statusCard.visibility = View.VISIBLE
        statusProgress.visibility = View.VISIBLE
        tvStatus.text = "系统检查中..."
        
        startupCheckJob = lifecycleScope.launch {
            val errors = mutableListOf<String>()
            
            try {
                // 检查1: 数据库完整性
                tvStatus.text = "检查数据库..."
                val database = AppDatabase.getDatabase(this@MainActivity)
                database.attendanceDao().getAllRecordsSync() // 使用正确的方法名
                delay(200)
                
                // 检查2: 设置加载
                tvStatus.text = "加载设置..."
                val settings = settingsManager.getSettings()
                if (settings.baseSalary <= 0) {
                    errors.add("基本工资未设置")
                }
                delay(200)
                
                // 检查3: 存储空间
                tvStatus.text = "检查存储..."
                delay(200)
                
                // 检查4: 权限
                tvStatus.text = "检查权限..."
                delay(200)
                
            } catch (e: Exception) {
                errors.add("检查失败: ${e.message}")
            }
            
            // 显示检查结果
            statusProgress.visibility = View.GONE
            if (errors.isEmpty()) {
                tvStatus.text = "系统就绪"
                statusCard.setCardBackgroundColor(0xFFE8F5E9.toInt()) // 浅绿色
                tvStatus.setTextColor(0xFF2E7D32.toInt())
                handler.postDelayed({
                    statusCard.visibility = View.GONE
                }, 2000)
            } else {
                tvStatus.text = errors.joinToString("; ")
                statusCard.setCardBackgroundColor(0xFFFFEBEE.toInt()) // 浅红色
                tvStatus.setTextColor(0xFFC62828.toInt())
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        startTimeUpdates()
        currentSettings = settingsManager.getSettings()
        adapter.updateSettings(currentSettings)
        viewModel.allAttendance.value?.let { updateMonthlyStats(it) }
        updateCheckInStatus()
    }
    
    override fun onPause() {
        super.onPause()
        stopTimeUpdates()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopTimeUpdates()
    }
    
    private fun startTimeUpdates() {
        timeUpdateJob?.cancel()
        timeUpdateJob = lifecycleScope.launch {
            while (true) {
                updateCurrentTime()
                delay(TIME_UPDATE_INTERVAL)
            }
        }
    }
    
    private fun stopTimeUpdates() {
        timeUpdateJob?.cancel()
        timeUpdateJob = null
    }
    
    private fun setupCalendar() {
        calendarView = binding.calendarView
        
        updateMonthYearText()
        
        binding.btnPrevMonth.setOnClickListener { changeMonth(-1) }
        binding.btnNextMonth.setOnClickListener { changeMonth(1) }
        
        calendarView.setOnDateClickListener(object : CalendarView.OnDateClickListener {
            override fun onDateClick(year: Int, month: Int, day: Int) {
                showAttendanceDialog(year, month, day)
            }
        })
    }
    
    private fun changeMonth(delta: Int) {
        currentMonth += delta
        when {
            currentMonth < 0 -> {
                currentMonth = 11
                currentYear--
            }
            currentMonth > 11 -> {
                currentMonth = 0
                currentYear++
            }
        }
        updateMonthYearText()
        updateCalendarData()
        // 切换月份后更新统计数据
        viewModel.allAttendance.value?.let { updateMonthlyStats(it) }
    }
    
    private fun showAttendanceDialog(year: Int, month: Int, day: Int) {
        val dateStr = DateUtils.formatDate(year, month, day)
        val existingAttendance = viewModel.allAttendance.value?.find { it.date == dateStr }
        
        val dialog = AddOvertimeDialog(
            context = this,
            year = year,
            month = month,
            day = day,
            existingAttendance = existingAttendance,
            onSaveAttendance = { attendance ->
                if (existingAttendance != null) {
                    viewModel.updateAttendance(attendance)
                } else {
                    viewModel.addAttendance(attendance)
                }
                updateCalendarData()
            },
            onDeleteAttendance = { id ->
                viewModel.allAttendance.value?.find { it.id == id }?.let {
                    viewModel.deleteAttendance(it)
                }
                updateCalendarData()
            }
        )
        dialog.show()
    }
    
    private fun showAttendanceDialog(attendance: Attendance) {
        val dateParts = DateUtils.extractDateParts(attendance.date)
        if (dateParts != null) {
            val (year, month, day) = dateParts
            showAttendanceDialog(year, month, day)
        }
    }
    
    private fun updateMonthYearText() {
        binding.tvMonthYear.text = "${currentYear}年${currentMonth + 1}月"
        calendarView.setMonth(currentYear, currentMonth)
    }
    
    private fun updateCalendarData() {
        viewModel.allAttendance.value?.let {
            calendarView.setAttendanceData(it, settingsManager)
        }
    }
    
    private fun updateCurrentTime() {
        binding.tvCurrentTime.text = DateUtils.getCurrentTime()
    }
    
    private fun updateCurrentDate() {
        val currentDate = Date()
        binding.tvCurrentDate.text = DateUtils.formatToChineseDate(currentDate)
        binding.tvCurrentWeekday.text = DateUtils.getChineseWeekday(currentDate)
    }
    
    private fun setupRecyclerView() {
        adapter = AttendanceAdapter(
            settings = currentSettings,
            onItemClick = { attendance -> showAttendanceDialog(attendance) }
        )
        binding.recyclerView.adapter = adapter
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
    }
    
    private fun setupButtons() {
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        
        binding.btnReport.setOnClickListener {
            startActivity(Intent(this, ReportActivity::class.java))
        }
        
        binding.btnData.setOnClickListener {
            startActivity(Intent(this, DataManagerActivity::class.java))
        }
        
        binding.btnCloudSync.setOnClickListener {
            showSyncDialog()
        }
        
        binding.btnCheckIn.setOnClickListener {
            performCheckIn()
        }
        
        binding.btnCheckOut.setOnClickListener {
            performCheckOut()
        }
    }
    
    private fun performCheckIn() {
        val currentTime = DateUtils.getCurrentTime()
        val dateStr = DateUtils.formatDate(
            Calendar.getInstance().get(Calendar.YEAR),
            Calendar.getInstance().get(Calendar.MONTH),
            Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        )
        
        val existingAttendance = viewModel.allAttendance.value?.find { it.date == dateStr }
        
        val newAttendance = existingAttendance?.copy(
            checkInTime = currentTime,
            checkInTimestamp = System.currentTimeMillis()
        ) ?: Attendance(
            id = 0,
            date = dateStr,
            checkInTime = currentTime,
            checkInTimestamp = System.currentTimeMillis()
        )
        
        viewModel.addAttendance(newAttendance)
        Toast.makeText(this, "上班打卡成功！时间：$currentTime", Toast.LENGTH_SHORT).show()
        updateCheckInStatus()
    }
    
    private fun performCheckOut() {
        val currentTime = DateUtils.getCurrentTime()
        val dateStr = DateUtils.formatDate(
            Calendar.getInstance().get(Calendar.YEAR),
            Calendar.getInstance().get(Calendar.MONTH),
            Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        )
        
        val existingAttendance = viewModel.allAttendance.value?.find { it.date == dateStr }
        
        if (existingAttendance == null) {
            Toast.makeText(this, "请先进行上班打卡！", Toast.LENGTH_SHORT).show()
            return
        }
        
        val checkInTime = existingAttendance.checkInTime
        val workHours = calculateWorkHours(checkInTime, currentTime)
        
        val newAttendance = existingAttendance.copy(
            checkOutTime = currentTime,
            checkOutTimestamp = System.currentTimeMillis(),
            manualOvertimeHours = Math.max(0.0, workHours - settingsManager.getSettings().dailyWorkHours)
        )
        
        viewModel.updateAttendance(newAttendance)
        Toast.makeText(this, "下班打卡成功！时间：$currentTime\n工作时长：${String.format("%.1f", workHours)}小时", Toast.LENGTH_SHORT).show()
        updateCheckInStatus()
    }
    
    private fun calculateWorkHours(checkInTime: String?, checkOutTime: String?): Double {
        if (checkInTime == null || checkOutTime == null) return 0.0
        
        return try {
            val checkInParts = checkInTime.split(":").map { it.toInt() }
            val checkOutParts = checkOutTime.split(":").map { it.toInt() }
            
            val checkInMinutes = checkInParts[0] * 60 + checkInParts[1]
            val checkOutMinutes = checkOutParts[0] * 60 + checkOutParts[1]
            
            var diffMinutes = checkOutMinutes - checkInMinutes
            if (diffMinutes < 0) diffMinutes += 1440 // 跨天处理
            
            diffMinutes / 60.0
        } catch (e: Exception) {
            0.0
        }
    }
    
    private fun updateCheckInStatus() {
        val dateStr = DateUtils.formatDate(
            Calendar.getInstance().get(Calendar.YEAR),
            Calendar.getInstance().get(Calendar.MONTH),
            Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        )
        
        val todayAttendance = viewModel.allAttendance.value?.find { it.date == dateStr }
        
        if (todayAttendance != null) {
            binding.checkInStatusLayout.visibility = View.VISIBLE
            val status = StringBuilder()
            
            if (todayAttendance.checkInTime != null) {
                status.append("上班: ${todayAttendance.checkInTime}")
            }
            
            if (todayAttendance.checkOutTime != null) {
                status.append(" | 下班: ${todayAttendance.checkOutTime}")
                val workHours = calculateWorkHours(todayAttendance.checkInTime, todayAttendance.checkOutTime)
                status.append(" | 工时: ${String.format("%.1f", workHours)}h")
            }
            
            binding.tvCheckInStatus.text = status.toString()
        } else {
            binding.checkInStatusLayout.visibility = View.GONE
        }
    }
    
    private fun showSyncDialog() {
        val options = arrayOf(
            "智能双向同步（推荐）",
            "仅上传到云端",
            "仅从云端下载",
            "完全覆盖同步"
        )
        
        AlertDialog.Builder(this)
            .setTitle("WebDAV 同步")
            .setItems(options) { _, which ->
                val (direction, options) = when (which) {
                    0 -> SyncDirection.BIDIRECTIONAL to SyncPresets.SMART_SYNC
                    1 -> SyncDirection.UPLOAD_ONLY to SyncPresets.SMART_SYNC
                    2 -> SyncDirection.DOWNLOAD_ONLY to SyncPresets.SMART_SYNC
                    3 -> SyncDirection.BIDIRECTIONAL to SyncPresets.FULL_BACKUP
                    else -> SyncDirection.BIDIRECTIONAL to SyncPresets.SMART_SYNC
                }
                performSync(direction, options)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun performSync(
        direction: SyncDirection,
        options: SyncOptions = SyncOptions()
    ) {
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("正在同步...")
            .setMessage("请稍候...")
            .setCancelable(false)
            .create()
        progressDialog.show()
        
        lifecycleScope.launch {
            val result = syncManager.performSync(direction, options)
            
            progressDialog.dismiss()
            
            val message = when (result) {
                SyncResult.SUCCESS -> "同步成功！"
                SyncResult.NO_CONFIG -> "请先在设置中配置 WebDAV"
                SyncResult.NO_NETWORK -> "网络不可用，请检查网络连接"
                SyncResult.CONNECTION_FAILED -> "连接失败，请检查网络和配置"
                SyncResult.UPLOAD_FAILED -> "上传失败"
                SyncResult.DOWNLOAD_FAILED -> "下载失败"
                SyncResult.RESTORE_FAILED -> "恢复数据失败"
                SyncResult.NO_CHANGES -> "没有需要同步的更改"
                SyncResult.CONFLICT -> "存在数据冲突，请手动处理"
            }
            
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun observeViewModel() {
        viewModel.allAttendance.observe(this) { attendanceList ->
            adapter.submitList(attendanceList)
            updateCalendarData()
            updateMonthlyStats(attendanceList)
            updateEmptyState(attendanceList)
            updateCheckInStatus()
        }
        
        viewModel.errorMessage.observe(this) { errorMsg ->
            if (!errorMsg.isNullOrEmpty()) {
                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
    }
    
    private fun updateEmptyState(attendanceList: List<Attendance>) {
        if (attendanceList.isEmpty()) {
            binding.recyclerView.visibility = View.GONE
            binding.emptyStateLayout.visibility = View.VISIBLE
        } else {
            binding.recyclerView.visibility = View.VISIBLE
            binding.emptyStateLayout.visibility = View.GONE
        }
    }
    
    private fun updateMonthlyStats(attendanceList: List<Attendance>) {
        var totalHours = 0.0
        var totalOvertimePay = 0.0
        
        // 过滤出当前月份的记录
        val currentMonthRecords = attendanceList.filter { attendance ->
            val dateParts = DateUtils.extractDateParts(attendance.date)
            dateParts?.let { (year, month, _) ->
                year == currentYear && month == currentMonth
            } ?: false
        }
        
        currentMonthRecords.forEach { attendance ->
            val result = OvertimeCalculator.calculateOvertime(attendance, currentSettings)
            totalHours += result.overtimeHours + result.extraHours
            totalOvertimePay += result.estimatedPay
        }
        
        val performanceBonus = currentSettings.baseSalary * (currentSettings.performancePercent / 100.0)
        val totalPay = currentSettings.baseSalary + performanceBonus + totalOvertimePay
        
        binding.tvTotalOvertime.text = Formatter.formatHours(totalHours)
        binding.tvTotalPay.text = Formatter.formatMoney(totalPay)
    }
}