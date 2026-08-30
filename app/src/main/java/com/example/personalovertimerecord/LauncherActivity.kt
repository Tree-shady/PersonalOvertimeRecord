package com.example.personalovertimerecord

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * 纯中转启动页：唯一的 exported 入口 Activity。
 *
 * 生物识别保护依赖 MainActivity 不被外部应用直接启动——
 * MainActivity 已设为 exported=false，外部应用（或 adb）无法再
 * 通过携带 Intent Extra（如 biometric_passed=true）绕过身份验证
 * 直接进入主界面；一切外部启动请求都先经过本页，再转交 MainActivity
 * 由其内部的验证逻辑决定是否要求生物识别。
 */
class LauncherActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
