package com.example.personalovertimerecord.dialog

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.personalovertimerecord.BuildConfig
import com.example.personalovertimerecord.utils.Constants
import com.example.personalovertimerecord.utils.UpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 软件更新相关对话框与下载安装流程。
 */
object UpdateDialog {

    /**
     * 发现新版本对话框。
     * @param force true 表示强制更新：不可取消、无"稍后"按钮。
     */
    fun showUpdateAvailable(
        context: Context,
        info: UpdateManager.UpdateInfo,
        force: Boolean,
        onDownload: () -> Unit
    ) {
        val message = buildString {
            append("当前版本：v").append(UpdateManager.getCurrentVersionName(context)).append('\n')
            append("最新版本：v").append(info.versionName).append('\n')
            if (force) {
                append("⚠️ 此版本为强制更新，更新后才能继续使用。").append('\n')
            }
            if (!info.changelog.isNullOrBlank()) {
                append("\n更新内容：\n").append(info.changelog.trim())
            }
        }

        AlertDialog.Builder(context)
            .setTitle("发现新版本")
            .setMessage(message)
            .setCancelable(!force)
            .setPositiveButton("立即更新") { _, _ -> onDownload() }
            .apply {
                if (!force) {
                    setNegativeButton("稍后", null)
                }
            }
            .show()
    }

    /** 当前已是最新版本 */
    fun showNoUpdate(context: Context) {
        AlertDialog.Builder(context)
            .setTitle("检查更新")
            .setMessage("当前已是最新版本 v${UpdateManager.getCurrentVersionName(context)}")
            .setPositiveButton("确定", null)
            .show()
    }

    /** 检查更新失败 */
    fun showError(context: Context, message: String) {
        AlertDialog.Builder(context)
            .setTitle("检查更新失败")
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show()
    }

    /** 缺少"安装未知来源应用"权限，引导用户去系统设置开启 */
    fun showInstallPermissionDialog(context: Context) {
        AlertDialog.Builder(context)
            .setTitle("需要安装权限")
            .setMessage("更新需要允许安装未知来源应用。请在系统设置中为本应用开启该权限后重试。")
            .setPositiveButton("去设置") { _, _ -> UpdateManager.openInstallPermissionSettings(context) }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 调试版 → 正式版迁移引导。
     * Android 要求覆盖安装必须同签名：调试版（debug 签名）无法被正式签名版直接覆盖，
     * 只能先卸载调试版（应用数据会被清除），再安装正式版。
     */
    private fun showDebugToReleaseMigration(context: Context, info: UpdateManager.UpdateInfo) {
        AlertDialog.Builder(context)
            .setTitle("调试版 → 正式版")
            .setMessage(
                "当前安装的是调试版本（debug 签名），Android 不允许不同签名直接覆盖升级。\n\n" +
                    "请先卸载当前调试版（应用数据会被清除），再安装正式版 v${info.versionName}。"
            )
            .setPositiveButton("卸载调试版") { _, _ ->
                uninstallCurrentApp(context)
            }
            .setNeutralButton("打开正式版下载页") { _, _ ->
                openReleasesPage(context)
            }
            .setNegativeButton("稍后", null)
            .show()
    }

    /** 调起系统卸载器卸载本应用（卸载后进程结束，随后由用户安装正式版） */
    private fun uninstallCurrentApp(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${context.packageName}"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "无法调起系统卸载，请到系统设置手动卸载", Toast.LENGTH_LONG).show()
        }
    }

    /** 在浏览器中打开最新 Release 下载页 */
    private fun openReleasesPage(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(Constants.UPDATE_RELEASES_URL))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "无法打开浏览器，请手动访问最新 Release 页", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * 完整更新流程：下载 -> SHA-256 校验 -> 签名校验 -> 调起系统安装器。
     * 需传入 Activity 的 lifecycleScope，页面销毁时协程自动取消。
     */
    fun startUpdateFlow(scope: CoroutineScope, context: Context, info: UpdateManager.UpdateInfo) {
        // Android 8.0+ 必须拥有"安装未知来源应用"权限才能调起安装器
        if (!UpdateManager.canRequestPackageInstalls(context)) {
            showInstallPermissionDialog(context)
            return
        }

        val progress = showDownloadDialog(context)

        scope.launch {
            val apkFile = withContext(Dispatchers.IO) {
                UpdateManager.downloadApk(
                    context = context,
                    url = info.apkUrl,
                    sha256 = info.sha256
                ) { downloaded, total ->
                    // 进度回调发生在 IO 线程，切回主线程更新 UI
                    withContext(Dispatchers.Main) { progress.update(downloaded, total) }
                }
            }

            progress.dismiss()

            if (apkFile == null) {
                Toast.makeText(context, "下载失败，请检查网络后重试", Toast.LENGTH_LONG).show()
                return@launch
            }

            // 签名一致性校验（下载包被篡改/签名不兼容时拒绝安装）
            when (UpdateManager.verifyApkSignature(context, apkFile)) {
                is UpdateManager.SignatureVerifyResult.Match -> {
                    val started = UpdateManager.installApk(context, apkFile)
                    if (!started) {
                        Toast.makeText(context, "启动安装失败，请重试", Toast.LENGTH_LONG).show()
                    }
                }
                is UpdateManager.SignatureVerifyResult.Mismatch -> {
                    apkFile.delete()
                    if (BuildConfig.DEBUG) {
                        // 当前安装的是调试版（本地运行/旧 CI 调试包），更新包为正式签名：
                        // Android 不允许不同签名覆盖升级，引导先卸载调试版再安装正式版
                        showDebugToReleaseMigration(context, info)
                    } else {
                        Toast.makeText(
                            context,
                            "更新包签名与当前应用不一致，已取消安装。请确认更新来自官方渠道后重试。",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                is UpdateManager.SignatureVerifyResult.Unreadable -> {
                    apkFile.delete()
                    Toast.makeText(context, "无法验证更新包签名，安装包可能已损坏，请重新下载", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ---------- 下载进度对话框 ----------

    private fun showDownloadDialog(context: Context): DownloadProgress {
        val progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
        }
        val textView = TextView(context).apply {
            text = "准备下载..."
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 0)
        }
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
            addView(
                progressBar,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
            addView(textView)
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle("正在下载更新")
            .setView(layout)
            .setCancelable(false)
            .create()
        dialog.show()
        return DownloadProgress(dialog, progressBar, textView)
    }

    class DownloadProgress(
        private val dialog: Dialog,
        private val progressBar: ProgressBar,
        private val textView: TextView
    ) {
        fun update(downloaded: Long, total: Long) {
            val percent = if (total > 0) ((downloaded * 100) / total).toInt() else 0
            progressBar.progress = percent
            textView.text = if (total > 0) {
                "已下载 ${formatSize(downloaded)} / ${formatSize(total)}（$percent%）"
            } else {
                "已下载 ${formatSize(downloaded)}"
            }
        }

        fun dismiss() {
            if (dialog.isShowing) {
                dialog.dismiss()
            }
        }

        private fun formatSize(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB")
            val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
            val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
            return String.format(Locale.US, "%.1f %s", value, units[digitGroups.coerceAtMost(units.size - 1)])
        }
    }
}
