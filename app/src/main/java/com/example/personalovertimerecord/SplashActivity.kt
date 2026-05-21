package com.example.personalovertimerecord

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AlphaAnimation
import androidx.appcompat.app.AppCompatActivity
import com.example.personalovertimerecord.utils.PermissionManager

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    companion object {
        private const val SPLASH_DURATION = 2000L
    }

    private lateinit var permissionManager: PermissionManager
    private var isAnimationFinished = false
    private var isPermissionChecked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        permissionManager = PermissionManager(this) {
            onPermissionsComplete()
        }

        startAnimation()
        permissionManager.checkAndRequestPermissions()
    }

    private fun startAnimation() {
        val fadeIn = AlphaAnimation(0.0f, 1.0f).apply {
            duration = 1000
            fillAfter = true
        }
        
        findViewById<android.widget.ImageView>(R.id.ivLogo).startAnimation(fadeIn)
        findViewById<android.widget.TextView>(R.id.tvAppName).startAnimation(fadeIn)
        findViewById<android.widget.TextView>(R.id.tvSubtitle).startAnimation(fadeIn)

        Handler(Looper.getMainLooper()).postDelayed({
            isAnimationFinished = true
            tryNavigateToMain()
        }, SPLASH_DURATION)
    }

    private fun onPermissionsComplete() {
        permissionManager.markFirstLaunchComplete()
        isPermissionChecked = true
        tryNavigateToMain()
    }

    private fun tryNavigateToMain() {
        if (isAnimationFinished && isPermissionChecked) {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }
    }
}
