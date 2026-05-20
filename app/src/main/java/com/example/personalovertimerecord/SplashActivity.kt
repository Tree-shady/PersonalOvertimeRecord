package com.example.personalovertimerecord

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AlphaAnimation
import androidx.appcompat.app.AppCompatActivity

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    companion object {
        private const val SPLASH_DURATION = 2000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        startAnimation()
        navigateToMain()
    }

    private fun startAnimation() {
        val fadeIn = AlphaAnimation(0.0f, 1.0f).apply {
            duration = 1000
            fillAfter = true
        }
        
        findViewById<android.widget.ImageView>(R.id.ivLogo).startAnimation(fadeIn)
        findViewById<android.widget.TextView>(R.id.tvAppName).startAnimation(fadeIn)
        findViewById<android.widget.TextView>(R.id.tvSubtitle).startAnimation(fadeIn)
    }

    private fun navigateToMain() {
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, SPLASH_DURATION)
    }
}
