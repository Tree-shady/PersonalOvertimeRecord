package com.example.personalovertimerecord

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.personalovertimerecord.data.FirebaseSyncManager
import com.example.personalovertimerecord.data.SyncState
import com.example.personalovertimerecord.databinding.ActivityCloudSyncBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CloudSyncActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityCloudSyncBinding
    private var firebaseSyncManager: FirebaseSyncManager? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            binding = ActivityCloudSyncBinding.inflate(layoutInflater)
            setContentView(binding.root)
            
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            title = "云同步"
            
            firebaseSyncManager = FirebaseSyncManager.getInstance(this)
            
            setupClickListeners()
            observeState()
        } catch (e: Exception) {
            Toast.makeText(this, "启动云同步失败: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            try {
                finish()
            } catch (e: Exception) {
                Toast.makeText(this, "返回失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text?.toString()?.trim() ?: ""
            val password = binding.etPassword.text?.toString() ?: ""
            
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
        
        binding.btnRegister.setOnClickListener {
            val email = binding.etEmail.text?.toString()?.trim() ?: ""
            val password = binding.etPassword.text?.toString() ?: ""
            
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
        
        binding.btnLogout.setOnClickListener {
            try {
                firebaseSyncManager?.signOut()
                Toast.makeText(this, "已退出登录", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "退出异常: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        
        binding.btnSyncToCloud.setOnClickListener {
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
        
        binding.btnSyncFromCloud.setOnClickListener {
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
        
        binding.btnSyncAll.setOnClickListener {
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
                        binding.tvUserInfo.text = "当前用户: ${it.email}"
                    } ?: run {
                        binding.tvUserInfo.text = "未登录"
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
                binding.loginContainer.visibility = android.view.View.GONE
                binding.logoutContainer.visibility = android.view.View.VISIBLE
            } else {
                binding.loginContainer.visibility = android.view.View.VISIBLE
                binding.logoutContainer.visibility = android.view.View.GONE
                binding.etPassword.text?.clear()
            }
        } catch (e: Exception) {
            // 忽略更新错误
        }
    }
    
    private fun handleSyncState(state: SyncState) {
        try {
            when (state) {
                is SyncState.Idle -> {
                    binding.progressIndicator.visibility = android.view.View.GONE
                    binding.tvSyncStatus.text = "状态: 空闲"
                    setButtonsEnabled(true)
                }
                is SyncState.Loading -> {
                    binding.progressIndicator.visibility = android.view.View.VISIBLE
                    binding.tvSyncStatus.text = state.message
                    setButtonsEnabled(false)
                }
                is SyncState.Success -> {
                    binding.progressIndicator.visibility = android.view.View.GONE
                    binding.tvSyncStatus.text = state.message
                    setButtonsEnabled(true)
                }
                is SyncState.Error -> {
                    binding.progressIndicator.visibility = android.view.View.GONE
                    binding.tvSyncStatus.text = state.message
                    setButtonsEnabled(true)
                }
            }
        } catch (e: Exception) {
            // 忽略状态更新错误
        }
    }
    
    private fun setButtonsEnabled(enabled: Boolean) {
        binding.btnLogin.isEnabled = enabled
        binding.btnRegister.isEnabled = enabled
        binding.btnLogout.isEnabled = enabled
        binding.btnSyncToCloud.isEnabled = enabled
        binding.btnSyncFromCloud.isEnabled = enabled
        binding.btnSyncAll.isEnabled = enabled
    }
    
    private fun validateInput(email: String, password: String): Boolean {
        if (email.isEmpty()) {
            binding.etEmail.error = "请输入邮箱"
            return false
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = "请输入有效的邮箱地址"
            return false
        }
        if (password.isEmpty()) {
            binding.etPassword.error = "请输入密码"
            return false
        }
        if (password.length < 6) {
            binding.etPassword.error = "密码至少需要6位"
            return false
        }
        binding.etEmail.error = null
        binding.etPassword.error = null
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