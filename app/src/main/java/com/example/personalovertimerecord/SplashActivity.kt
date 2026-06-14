package com.example.personalovertimerecord

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AlphaAnimation
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.personalovertimerecord.utils.PermissionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    // POST 检测项目
    private val postItems = listOf(
        PostItem("Initializing system components...", 400),
        PostItem("Checking database integrity...", 600),
        PostItem("Loading attendance records...", 500),
        PostItem("Verifying settings...", 400),
        PostItem("Connecting to cloud storage...", 800)
    )

    private lateinit var permissionManager: PermissionManager
    private lateinit var tvPostLog: TextView
    private lateinit var tvCursor: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgressPercent: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvReadyMessage: TextView
    private lateinit var scrollView: ScrollView

    private var isAnimationFinished = false
    private var isPermissionChecked = false
    private var currentLogText = ""
    private var cursorJob: Job? = null

    data class PostItem(val message: String, val delayMs: Long)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        initViews()
        
        permissionManager = PermissionManager(this) {
            onPermissionsComplete()
        }

        startPostAnimation()
        permissionManager.checkAndRequestPermissions()
    }

    private fun initViews() {
        tvPostLog = findViewById(R.id.tvPostLog)
        tvCursor = findViewById(R.id.tvCursor)
        progressBar = findViewById(R.id.progressBar)
        tvProgressPercent = findViewById(R.id.tvProgressPercent)
        tvStatus = findViewById(R.id.tvStatus)
        tvReadyMessage = findViewById(R.id.tvReadyMessage)
        scrollView = findViewById(R.id.scrollView)
    }

    private fun startPostAnimation() {
        lifecycleScope.launch {
            // 逐项显示 POST 日志
            for ((index, item) in postItems.withIndex()) {
                // 打字机效果显示消息
                awaitTypewriter(item.message)
                
                // 模拟处理过程
                delay(item.delayMs)
                
                // 显示 OK 标记
                appendToLog(" [OK]\n")
                
                // 更新进度
                val progress = ((index + 1) * 100) / postItems.size
                updateProgress(progress)
                
                // 延迟后显示下一项
                delay(200)
            }
            
            // 所有检查完成
            isAnimationFinished = true
            
            // 显示准备就绪消息
            tvStatus.text = "Complete"
            tvReadyMessage.visibility = View.VISIBLE
            tvCursor.visibility = View.GONE
            
            // 开始闪烁效果
            startBlinkAnimation()
            
            // 等待一下然后跳转
            delay(1000)
            tryNavigateToMain()
        }
    }

    private suspend fun awaitTypewriter(text: String) {
        tvCursor.visibility = View.VISIBLE
        for (char in text) {
            currentLogText += char
            tvPostLog.text = currentLogText
            delay(25) // 打字速度
            scrollToBottom()
        }
    }

    private fun appendToLog(text: String) {
        currentLogText += text
        tvPostLog.text = currentLogText
        scrollToBottom()
    }

    private fun scrollToBottom() {
        scrollView.post {
            scrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun updateProgress(progress: Int) {
        progressBar.progress = progress
        tvProgressPercent.text = "$progress%"
        tvStatus.text = when {
            progress < 30 -> "Loading components..."
            progress < 50 -> "Checking database..."
            progress < 70 -> "Loading data..."
            progress < 90 -> "Verifying..."
            else -> "Finalizing..."
        }
    }

    private fun startBlinkAnimation() {
        cursorJob = lifecycleScope.launch {
            while (true) {
                tvCursor.visibility = if (tvCursor.visibility == View.VISIBLE) {
                    View.INVISIBLE
                } else {
                    View.VISIBLE
                }
                delay(500)
            }
        }
    }

    private fun onPermissionsComplete() {
        permissionManager.markFirstLaunchComplete()
        isPermissionChecked = true
        tryNavigateToMain()
    }

    private fun tryNavigateToMain() {
        if (isAnimationFinished && isPermissionChecked) {
            cursorJob?.cancel()
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cursorJob?.cancel()
    }
}
