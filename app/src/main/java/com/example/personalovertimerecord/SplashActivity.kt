package com.example.personalovertimerecord

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.TextView
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import com.example.personalovertimerecord.utils.BiometricManager
import com.example.personalovertimerecord.utils.PermissionManager
import kotlin.random.Random

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    companion object {
        private const val TYPEWRITER_DELAY = 10L
        private const val ITEM_DELAY = 120L
        private const val FINAL_DELAY = 500L
        private const val PROGRESS_DURATION = 200L
        private const val FINAL_PROGRESS_DURATION = 350L
        private const val TOTAL_ITEMS = 10
    }

    private lateinit var permissionManager: PermissionManager
    private lateinit var tvPostLog: TextView
    private lateinit var tvCursor: TextView
    private lateinit var progressBar: View
    private lateinit var tvProgressPercent: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvReadyMessage: TextView
    private lateinit var scrollView: ScrollView

    private var isAnimationFinished = false
    private var isPermissionChecked = false
    private var currentLogText = ""
    private var currentProgress = 0
    private var glitchAnimator: ObjectAnimator? = null
    private val handler = Handler()

    // 增强的检查项目列表
    private val postItems = listOf(
        PostItem("[QUANTUM CORE]", "Initializing quantum processing unit...", 280),
        PostItem("[MEMORY ARBITER]", "Allocating neural memory buffers...", 320),
        PostItem("[GPU ENGINE]", "Calibrating holographic renderer...", 350),
        PostItem("[DATA VAULT]", "Verifying encrypted storage integrity...", 400),
        PostItem("[NET LATENCY]", "Measuring network pathway... %latency%ms", 280),
        PostItem("[SECURITY MATRIX]", "Scanning for temporal anomalies...", 450),
        PostItem("[SYNC NODE]", "Connecting to cloud backup array...", 380),
        PostItem("[TIME SYNC]", "Synchronizing timeline coordinates...", 220),
        PostItem("[PERMISSION]", "Granting access tokens...", 180),
        PostItem("[FINAL CHECK]", "Running system diagnostics...", 300)
    )

    data class PostItem(
        val prefix: String,
        val message: String,
        val delayMs: Long
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )
        
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
        
        progressBar.layoutParams.width = 0
        progressBar.requestLayout()
        
        // 启动随机闪烁效果
        startGlitchEffect()
    }

    private fun startGlitchEffect() {
        glitchAnimator = ObjectAnimator.ofFloat(tvPostLog, "alpha", 1f, 0.97f, 1f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                // 随机轻微颜色变化
                if (Random.nextFloat() < 0.05f) {
                    val green = 255 - Random.nextInt(30)
                    tvPostLog.setTextColor(Color.rgb(0, green, Random.nextInt(50)))
                }
            }
        }
        glitchAnimator?.start()
    }

    private fun startPostAnimation() {
        var currentIndex = 0
        
        fun processNextItem() {
            if (currentIndex >= postItems.size) {
                onAnimationComplete()
                return
            }
            
            val item = postItems[currentIndex]
            val targetProgress = ((currentIndex + 1) * 100) / postItems.size
            
            // 格式化消息，替换占位符
            val formattedMessage = item.message.replace("%latency%", 
                Random.nextInt(15, 65).toString())
            
            animateTypewriter(item.prefix + " ") {
                animateTypewriterColored(formattedMessage) {
                    animateProgressTo(targetProgress) {
                        // 显示OK标记（有时显示警告符号）
                        val okText = if (Random.nextFloat() < 0.1f && currentIndex > 2) {
                            getString(R.string.post_warn)
                        } else {
                            getString(R.string.post_ok)
                        }
                        appendLog(okText)
                        
                        // 更新状态
                        updateStatus(targetProgress)
                        
                        handler.postDelayed({
                            currentIndex++
                            processNextItem()
                        }, ITEM_DELAY + item.delayMs)
                    }
                }
            }
        }
        
        processNextItem()
    }

    private fun animateTypewriter(text: String, onComplete: () -> Unit) {
        tvCursor.visibility = View.VISIBLE
        var charIndex = 0
        
        ValueAnimator.ofInt(0, text.length).apply {
            duration = text.length * TYPEWRITER_DELAY
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val newIndex = anim.animatedValue as Int
                while (charIndex < newIndex) {
                    currentLogText += text[charIndex]
                    charIndex++
                }
                tvPostLog.text = currentLogText
                
                if (charIndex % 8 == 0 || charIndex == text.length) {
                    scrollView.post {
                        scrollView.fullScroll(View.FOCUS_DOWN)
                    }
                }
            }
            addListener(object : android.animation.Animator.AnimatorListener {
                override fun onAnimationEnd(animation: android.animation.Animator) = onComplete()
                override fun onAnimationStart(animation: android.animation.Animator) {}
                override fun onAnimationCancel(animation: android.animation.Animator) {}
                override fun onAnimationRepeat(animation: android.animation.Animator) {}
            })
        }.start()
    }

    private fun animateTypewriterColored(text: String, onComplete: () -> Unit) {
        var charIndex = 0
        val originalColor = tvPostLog.currentTextColor
        
        ValueAnimator.ofInt(0, text.length).apply {
            duration = text.length * TYPEWRITER_DELAY
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val newIndex = anim.animatedValue as Int
                while (charIndex < newIndex) {
                    currentLogText += text[charIndex]
                    charIndex++
                }
                tvPostLog.text = currentLogText
                
                // 打字时显示青色高亮
                if (charIndex <= text.length) {
                    try {
                        tvPostLog.setTextColor(Color.rgb(0, 200, 255))
                    } catch (e: Exception) {}
                }
            }
            addListener(object : android.animation.Animator.AnimatorListener {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    // 恢复原始颜色
                    tvPostLog.setTextColor(originalColor)
                    onComplete()
                }
                override fun onAnimationStart(animation: android.animation.Animator) {}
                override fun onAnimationCancel(animation: android.animation.Animator) {}
                override fun onAnimationRepeat(animation: android.animation.Animator) {}
            })
        }.start()
    }

    private fun animateProgressTo(targetPercent: Int, onComplete: () -> Unit) {
        val targetWidth = (scrollView.width * targetPercent / 100).toInt()
        val startWidth = progressBar.layoutParams.width
        
        ValueAnimator.ofInt(startWidth, targetWidth).apply {
            duration = PROGRESS_DURATION
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener { anim ->
                progressBar.layoutParams.width = anim.animatedValue as Int
                progressBar.requestLayout()
                tvProgressPercent.text = "$targetPercent%"
                
                // 进度条脉动效果
                if (Random.nextFloat() < 0.1f) {
                    val alpha = 0.7f + Random.nextFloat() * 0.3f
                    progressBar.alpha = alpha
                }
            }
            addListener(object : android.animation.Animator.AnimatorListener {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    currentProgress = targetPercent
                    onComplete()
                }
                override fun onAnimationStart(animation: android.animation.Animator) {}
                override fun onAnimationCancel(animation: android.animation.Animator) {}
                override fun onAnimationRepeat(animation: android.animation.Animator) {}
            })
        }.start()
    }

    private fun onAnimationComplete() {
        isAnimationFinished = true
        
        tvStatus.text = getString(R.string.status_complete)
        tvReadyMessage.visibility = View.VISIBLE
        tvCursor.visibility = View.GONE
        
        // 停止闪烁效果
        glitchAnimator?.cancel()
        
        // 最终进度 + 绿色闪烁
        val finalWidth = scrollView.width
        val finalAnimator = ValueAnimator.ofInt(progressBar.layoutParams.width, finalWidth).apply {
            duration = FINAL_PROGRESS_DURATION
            interpolator = DecelerateInterpolator(2f)
            addUpdateListener { anim ->
                progressBar.layoutParams.width = anim.animatedValue as Int
                progressBar.requestLayout()
                val percent = ((anim.animatedValue as Int).toFloat() / finalWidth.toFloat() * 100).toInt()
                tvProgressPercent.text = "$percent%"
                
                // 进度条颜色变化
                if (anim.animatedValue as Int > finalWidth * 0.9) {
                    progressBar.setBackgroundResource(R.drawable.progress_complete)
                }
            }
        }
        
        // 准备消息淡入 + 放大效果
        tvReadyMessage.scaleX = 0.8f
        tvReadyMessage.scaleY = 0.8f
        
        val fadeInAnimator = ObjectAnimator.ofFloat(tvReadyMessage, "alpha", 0f, 1f).apply {
            duration = 400
            interpolator = DecelerateInterpolator()
        }
        
        val scaleXAnimator = ObjectAnimator.ofFloat(tvReadyMessage, "scaleX", 0.8f, 1f).apply {
            duration = 400
            interpolator = DecelerateInterpolator()
        }
        
        val scaleYAnimator = ObjectAnimator.ofFloat(tvReadyMessage, "scaleY", 0.8f, 1f).apply {
            duration = 400
            interpolator = DecelerateInterpolator()
        }
        
        AnimatorSet().apply {
            play(finalAnimator).with(fadeInAnimator).with(scaleXAnimator).with(scaleYAnimator)
            addListener(object : android.animation.Animator.AnimatorListener {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    handler.postDelayed({
                        tryNavigateToMain()
                    }, FINAL_DELAY)
                }
                override fun onAnimationStart(animation: android.animation.Animator) {}
                override fun onAnimationCancel(animation: android.animation.Animator) {}
                override fun onAnimationRepeat(animation: android.animation.Animator) {}
            })
        }.start()
    }

    private fun appendLog(text: String) {
        currentLogText += text
        tvPostLog.text = currentLogText
        scrollView.post {
            scrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun updateStatus(progress: Int) {
        tvStatus.text = when {
            progress < 10 -> getString(R.string.status_quantum)
            progress < 20 -> getString(R.string.status_memory)
            progress < 30 -> getString(R.string.status_gpu)
            progress < 40 -> getString(R.string.status_data)
            progress < 50 -> getString(R.string.status_network)
            progress < 60 -> getString(R.string.status_security)
            progress < 70 -> getString(R.string.status_sync)
            progress < 80 -> getString(R.string.status_time)
            progress < 90 -> getString(R.string.status_permission)
            else -> getString(R.string.status_finalizing)
        }
    }

    private fun onPermissionsComplete() {
        permissionManager.markFirstLaunchComplete()
        isPermissionChecked = true
        tryNavigateToMain()
    }

    private fun tryNavigateToMain() {
        if (isAnimationFinished && isPermissionChecked) {
            glitchAnimator?.cancel()
            val intent = if (BiometricManager.needsAuthentication(this)) {
                Intent(this, BiometricActivity::class.java)
            } else {
                Intent(this, MainActivity::class.java)
            }
            startActivity(intent)
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        glitchAnimator?.cancel()
    }
}