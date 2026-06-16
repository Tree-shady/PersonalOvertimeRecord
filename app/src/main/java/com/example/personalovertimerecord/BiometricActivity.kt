package com.example.personalovertimerecord

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.personalovertimerecord.utils.BiometricManager

class BiometricActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (!BiometricManager.needsAuthentication(this)) {
            proceedToMain()
            return
        }
        
        BiometricManager.showAuthenticationPrompt(
            activity = this,
            title = "身份验证",
            subtitle = "请验证身份以访问加班记录",
            description = "您已启用生物识别保护",
            onSuccess = {
                proceedToMain()
            },
            onError = { error ->
                Toast.makeText(this, "验证失败: $error", Toast.LENGTH_SHORT).show()
                finish()
            },
            onCancel = {
                finish()
            }
        )
    }
    
    private fun proceedToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }
}