package com.example.personalovertimerecord.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class PermissionManager(
    private val activity: AppCompatActivity,
    private val onAllPermissionsGranted: () -> Unit
) {

    companion object {
        private const val PREFS_NAME = "permission_prefs"
        private const val KEY_IS_FIRST_LAUNCH = "is_first_launch"
    }

    private val requiredPermissions = mutableListOf<String>()

    init {
        // 添加基础网络权限（Android 6.0+ 不需要运行时请求，但需要在清单中声明）
        // 添加存储权限（用于数据导入导出）
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            requiredPermissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        // Android 13+ 的通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private val permissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            onAllPermissionsGranted()
        } else {
            showPermissionRationaleDialog()
        }
    }

    fun checkAndRequestPermissions() {
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isFirstLaunch = prefs.getBoolean(KEY_IS_FIRST_LAUNCH, true)

        if (isFirstLaunch) {
            showWelcomeDialog()
        } else {
            checkPermissions()
        }
    }

    private fun showWelcomeDialog() {
        AlertDialog.Builder(activity)
            .setTitle("欢迎使用加班记录助手")
            .setMessage(
                "为了提供更好的使用体验，我们需要您授予以下权限：\n\n" +
                "• 网络权限：用于 WebDAV 同步数据\n" +
                "• 存储权限：用于数据的导入导出\n" +
                "• 通知权限：用于提醒您的加班记录\n\n" +
                "请在接下来的对话框中授予相应权限。"
            )
            .setPositiveButton("开始设置") { _, _ ->
                requestPermissions()
            }
            .setCancelable(false)
            .show()
    }

    private fun checkPermissions() {
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            onAllPermissionsGranted()
        } else {
            requestPermissions()
        }
    }

    private fun requestPermissions() {
        if (requiredPermissions.isNotEmpty()) {
            permissionLauncher.launch(requiredPermissions.toTypedArray())
        } else {
            onAllPermissionsGranted()
        }
    }

    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(activity)
            .setTitle("权限请求")
            .setMessage(
                "部分权限未授予，这可能会影响应用的部分功能：\n\n" +
                "• 没有网络权限：无法使用 WebDAV 同步\n" +
                "• 没有存储权限：无法导入导出数据\n" +
                "• 没有通知权限：无法收到加班提醒\n\n" +
                "是否前往设置页面授予权限？"
            )
            .setPositiveButton("前往设置") { _, _ ->
                // 这里可以跳转到应用设置页面，但为了简化，我们直接继续
                markFirstLaunchComplete()
                onAllPermissionsGranted()
            }
            .setNegativeButton("稍后再说") { _, _ ->
                markFirstLaunchComplete()
                onAllPermissionsGranted()
            }
            .setCancelable(false)
            .show()
    }

    fun markFirstLaunchComplete() {
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_IS_FIRST_LAUNCH, false).apply()
    }
}
