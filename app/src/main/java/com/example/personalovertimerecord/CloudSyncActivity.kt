package com.example.personalovertimerecord

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.personalovertimerecord.data.FirebaseSyncManager
import com.example.personalovertimerecord.data.SyncState
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CloudSyncActivity : AppCompatActivity() {

    private var firebaseSyncManager: FirebaseSyncManager? = null
    
    private var loginContainer: View? = null
    private var logoutContainer: View? = null
    private var etEmail: TextInputEditText? = null
    private var etPassword: TextInputEditText? = null
    private var btnBack: MaterialButton? = null
    private var btnLogin: MaterialButton? = null
    private var btnRegister: MaterialButton? = null
    private var btnLogout: MaterialButton? = null
    private var btnSyncToCloud: MaterialButton? = null
    private var btnSyncFromCloud: MaterialButton? = null
    private var btnSyncAll: MaterialButton? = null
    private var tvUserInfo: android.widget.TextView? = null
    private var tvSyncStatus: android.widget.TextView? = null
    private var progressIndicator: LinearProgressIndicator? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            setContentView(R.layout.activity_cloud_sync)
            
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            title = "云同步"
            
            firebaseSyncManager = FirebaseSyncManager.getInstance(this)
            
            initViews()
            setupClickListeners()
            observeState()
        } catch (e: Exception) {
            Toast.makeText(this, "启动云同步失败: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    private fun initViews() {
        try {
            loginContainer = findViewById(R.id.loginContainer)
            logoutContainer = findViewById(R.id.logoutContainer)
            etEmail = findViewById(R.id.etEmail)
            etPassword = findViewById(R.id.etPassword)
            btnBack = findViewById(R.id.btnBack)
            btnLogin = findViewById(R.id.btnLogin)
            btnRegister = findViewById(R.id.btnRegister)
            btnLogout = findViewById(R.id.btnLogout)
            btnSyncToCloud = findViewById(R.id.btnSyncToCloud)
            btnSyncFromCloud = findViewById(R.id.btnSyncFromCloud)
            btnSyncAll = findViewById(R.id.btnSyncAll)
            tvUserInfo = findViewById(R.id.tvUserInfo)
            tvSyncStatus = findViewById(R.id.tvSyncStatus)
            progressIndicator = findViewById(R.id.progressIndicator)
        } catch (e: Exception) {
            Toast.makeText(this, "初始化视图失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setupClickListeners() {
        btnBack?.setOnClickListener {
            try {
                finish()
            } catch (e: Exception) {
                Toast.makeText(this, "返回失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        
        btnLogin?.setOnClickListener {
            val email = etEmail?.text?.toString()?.trim() ?: ""
            val password = etPassword?.text?.toString() ?: ""
            
            if (validateInput(email, password)) {
                lifecycleScope.launch {
                    try {
                        val result = firebaseSyncManager?.signIn(email, password)
                        result?.fold(
                            onSuccess = { Toast.makeText(this@CloudSyncActivity, "登录成功", Toast.LENGTH_SHORT).show() },
                            onFailure = { e -> Toast.makeText(this@CloudSyncActivity, "登录失败: ${e.message}", Toast.LENGTH_SHORT).show() }
                        )
                    } catch (e: Exception) {
                        Toast.makeText(this@CloudSyncActivity, "登录异常: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        
        btnRegister?.setOnClickListener {
            val email = etEmail?.text?.toString()?.trim() ?: ""
            val password = etPassword?.text?.toString() ?: ""
            
            if (validateInput(email, password)) {
                lifecycleScope.launch {
                    try {
                        val result = firebaseSyncManager?.signUp(email, password)
                        result?.fold(
                            onSuccess = { Toast.makeText(this@CloudSyncActivity, "注册成功", Toast.LENGTH_SHORT).show() },
                            onFailure = { e -> Toast.makeText(this@CloudSyncActivity, "注册失败: ${e.message}", Toast.LENGTH_SHORT).show() }
                        )
                    } catch (e: Exception) {
                        Toast.makeText(this@CloudSyncActivity, "注册异常: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        
        btnLogout?.setOnClickListener {
            try {
                firebaseSyncManager?.signOut()
                Toast.makeText(this, "已退出登录", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "退出异常: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        
        btnSyncToCloud?.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val result = firebaseSyncManager?.syncToCloud()
                    result?.fold(
                        onSuccess = { Toast.makeText(this@CloudSyncActivity, "同步成功", Toast.LENGTH_SHORT).show() },
                        onFailure = { e -> Toast.makeText(this@CloudSyncActivity, "同步失败: ${e.message}", Toast.LENGTH_LONG).show() }
                    )
                } catch (e: Exception) {
                    Toast.makeText(this@CloudSyncActivity, "同步异常: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
        
        btnSyncFromCloud?.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val result = firebaseSyncManager?.syncFromCloud()
                    result?.fold(
                        onSuccess = { Toast.makeText(this@CloudSyncActivity, "下载成功", Toast.LENGTH_SHORT).show() },
                        onFailure = { e -> Toast.makeText(this@CloudSyncActivity, "下载失败: ${e.message}", Toast.LENGTH_LONG).show() }
                    )
                } catch (e: Exception) {
                    Toast.makeText(this@CloudSyncActivity, "下载异常: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
        
        btnSyncAll?.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val result = firebaseSyncManager?.syncAll()
                    result?.fold(
                        onSuccess = { Toast.makeText(this@CloudSyncActivity, "全量同步成功", Toast.LENGTH_SHORT).show() },
                        onFailure = { e -> Toast.makeText(this@CloudSyncActivity, "全量同步失败: ${e.message}", Toast.LENGTH_LONG).show() }
                    )
                } catch (e: Exception) {
                    Toast.makeText(this@CloudSyncActivity, "全量同步异常: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun observeState() {
        try {
            lifecycleScope.launch {
                firebaseSyncManager?.isLoggedIn?.collectLatest { isLoggedIn ->
                    updateLoginState(isLoggedIn)
                }
            }
            
            lifecycleScope.launch {
                firebaseSyncManager?.currentUser?.collectLatest { user ->
                    user?.let {
                        tvUserInfo?.text = "当前用户: ${it.email}"
                    } ?: run {
                        tvUserInfo?.text = "未登录"
                    }
                }
            }
            
            lifecycleScope.launch {
                firebaseSyncManager?.syncState?.collectLatest { state ->
                    handleSyncState(state)
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "状态监听异常: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updateLoginState(isLoggedIn: Boolean) {
        try {
            if (isLoggedIn) {
                loginContainer?.visibility = View.GONE
                logoutContainer?.visibility = View.VISIBLE
            } else {
                loginContainer?.visibility = View.VISIBLE
                logoutContainer?.visibility = View.GONE
                etPassword?.text?.clear()
            }
        } catch (e: Exception) {
            // 忽略更新错误
        }
    }
    
    private fun handleSyncState(state: SyncState) {
        try {
            when (state) {
                is SyncState.Idle -> {
                    progressIndicator?.visibility = View.GONE
                    tvSyncStatus?.text = "状态: 空闲"
                    setButtonsEnabled(true)
                }
                is SyncState.Loading -> {
                    progressIndicator?.visibility = View.VISIBLE
                    tvSyncStatus?.text = state.message
                    setButtonsEnabled(false)
                }
                is SyncState.Success -> {
                    progressIndicator?.visibility = View.GONE
                    tvSyncStatus?.text = state.message
                    setButtonsEnabled(true)
                }
                is SyncState.Error -> {
                    progressIndicator?.visibility = View.GONE
                    tvSyncStatus?.text = state.message
                    setButtonsEnabled(true)
                }
            }
        } catch (e: Exception) {
            // 忽略状态更新错误
        }
    }
    
    private fun setButtonsEnabled(enabled: Boolean) {
        btnLogin?.isEnabled = enabled
        btnRegister?.isEnabled = enabled
        btnLogout?.isEnabled = enabled
        btnSyncToCloud?.isEnabled = enabled
        btnSyncFromCloud?.isEnabled = enabled
        btnSyncAll?.isEnabled = enabled
    }
    
    private fun validateInput(email: String, password: String): Boolean {
        if (email.isEmpty()) {
            etEmail?.error = "请输入邮箱"
            return false
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail?.error = "请输入有效的邮箱地址"
            return false
        }
        if (password.isEmpty()) {
            etPassword?.error = "请输入密码"
            return false
        }
        if (password.length < 6) {
            etPassword?.error = "密码至少需要6位"
            return false
        }
        etEmail?.error = null
        etPassword?.error = null
        return true
    }
    
    override fun onSupportNavigateUp(): Boolean {
        try {
            onBackPressedDispatcher.onBackPressed()
        } catch (e: Exception) {
            finish()
        }
        return true
    }
}
