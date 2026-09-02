package com.example.personalovertimerecord.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * 软件更新管理器（GitHub Releases 分发，方案 A）
 *
 * 流程：拉取 latest.json 清单 -> 比较 versionCode -> 下载 APK ->
 *       SHA-256 校验 -> 签名一致性校验 -> 调起系统安装器。
 *
 * 说明：
 * - 版本比较一律使用 versionCode（纯数字），不使用 versionName 字符串比较；
 * - 更新包必须与已安装应用使用同一把 release keystore 签名，否则系统拒绝覆盖安装；
 * - 更新清单与 APK 下载均要求 https（应用已禁止明文 HTTP 流量）。
 */
object UpdateManager {

    private const val TAG = "UpdateManager"
    private const val PREFS_NAME = "update_prefs"
    private const val KEY_LAST_CHECK_TIME = "last_check_time"
    private const val UPDATE_DIR = "updates"
    private const val APK_FILE_NAME = "app-update.apk"

    /** 更新清单（latest.json），字段与 release.yml 生成的 JSON 保持一致 */
    data class UpdateInfo(
        val versionCode: Int = 0,
        val versionName: String = "",
        val apkUrl: String = "",
        val sha256: String? = null,
        val changelog: String? = null,
        val minVersionCode: Int = 0
    )

    // ---------- 版本信息 ----------

    fun getCurrentVersionCode(context: Context): Int {
        val pmCode = try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode
        } catch (e: Exception) {
            AppLogger.e(TAG, "获取当前版本号失败", e)
            0
        }
        // PackageManager 读取异常或返回 0（旧版 APK 版本号为空）时，回退到编译期常量
        return pmCode.takeIf { it > 0 } ?: com.example.personalovertimerecord.BuildConfig.VERSION_CODE
    }

    fun getCurrentVersionName(context: Context): String {
        val pmName = try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: Exception) {
            AppLogger.e(TAG, "获取当前版本名失败", e)
            null
        }
        return pmName?.takeIf { it.isNotBlank() }
            ?: com.example.personalovertimerecord.BuildConfig.VERSION_NAME
    }

    /** 是否存在新版本（清单 versionCode > 当前 versionCode） */
    fun isUpdateAvailable(context: Context, info: UpdateInfo): Boolean =
        info.versionCode > getCurrentVersionCode(context)

    /** 是否需要强制更新（清单声明的 minVersionCode 高于当前版本） */
    fun isForceUpdate(context: Context, info: UpdateInfo): Boolean =
        info.minVersionCode > 0 && info.minVersionCode > getCurrentVersionCode(context)

    // ---------- 检查节流（避免每次冷启动都请求服务器） ----------

    /** 距上次成功检查是否已超过间隔；未到间隔则跳过自动检查 */
    fun shouldCheck(context: Context, intervalMs: Long): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_LAST_CHECK_TIME, 0L)
        return System.currentTimeMillis() - last >= intervalMs
    }

    /** 记录一次成功的检查时间（仅在拿到有效清单后调用） */
    fun markChecked(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_CHECK_TIME, System.currentTimeMillis())
            .apply()
    }

    // ---------- 获取更新清单 ----------

    /**
     * 拉取并解析 latest.json。
     * 失败（网络异常/非 2xx/解析失败）返回 null，不抛出，由调用方决定提示方式。
     */
    fun fetchUpdateInfo(context: Context): UpdateInfo? {
        return try {
            val url = URL(Constants.UPDATE_MANIFEST_URL)
            require(url.protocol == "https") { "更新清单必须使用 https" }
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.setRequestProperty("Accept", "application/json")
                if (connection.responseCode !in 200..299) {
                    AppLogger.e(TAG, "更新清单请求失败: HTTP ${connection.responseCode}")
                    return null
                }
                val json = connection.inputStream.bufferedReader().use { it.readText() }
                Gson().fromJson(json, UpdateInfo::class.java)
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "获取更新清单失败", e)
            null
        }
    }

    // ---------- 下载与校验 ----------

    /**
     * 下载 APK 到应用缓存目录，并按清单 SHA-256 校验。
     * 成功返回 APK 文件；失败返回 null（自动清理残留文件）。
     */
    suspend fun downloadApk(
        context: Context,
        url: String,
        sha256: String?,
        onProgress: suspend (downloaded: Long, total: Long) -> Unit
    ): File? {
        val target = getApkFile(context)
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.setRequestProperty("Accept", "application/vnd.android.package-archive")
                if (connection.responseCode !in 200..299) {
                    AppLogger.e(TAG, "APK 下载失败: HTTP ${connection.responseCode}")
                    return null
                }

                val total = connection.contentLength.toLong()
                val digest = MessageDigest.getInstance("SHA-256")

                connection.inputStream.use { input ->
                    FileOutputStream(target).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloaded = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            downloaded += read
                            onProgress(downloaded, total)
                        }
                    }
                }

                // SHA-256 校验（清单提供时）
                if (!sha256.isNullOrBlank()) {
                    val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
                    if (!sha256.equals(actualSha, ignoreCase = true)) {
                        AppLogger.e(TAG, "SHA-256 校验失败: 期望 $sha256 实际 $actualSha")
                        target.delete()
                        return null
                    }
                }
                target
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "APK 下载失败", e)
            target.delete()
            null
        }
    }

    private fun getApkFile(context: Context): File {
        val dir = File(context.cacheDir, UPDATE_DIR)
        dir.mkdirs()
        return File(dir, APK_FILE_NAME)
    }

    /**
     * 签名校验结果。
     * - [Match]：签名一致，可以安装；
     * - [Mismatch]：能读到双方签名但密钥不一致（如已装调试版、安装包被替换）；
     * - [Unreadable]：某一方签名读不出来（包损坏/解析失败）。
     */
    sealed class SignatureVerifyResult {
        object Match : SignatureVerifyResult()
        data class Mismatch(val installedDigest: String?, val archiveDigest: String?) : SignatureVerifyResult()
        object Unreadable : SignatureVerifyResult()
    }

    /**
     * 校验下载的 APK 签名与当前已安装应用一致，防止下载包被篡改。
     *
     * 注意：Android 9（API 28）起必须使用 GET_SIGNING_CERTIFICATES 配合
     * SigningInfo.apkContentsSigners 读取签名；旧的 GET_SIGNATURES 方式对
     * 仅使用 v2/v3 签名方案（无 v1 JAR 签名）的安装包会返回空签名，导致误判为校验失败。
     */
    fun verifyApkSignature(context: Context, apkFile: File): SignatureVerifyResult {
        return try {
            val pm = context.packageManager
            // API 28+ 使用 GET_SIGNING_CERTIFICATES（正确支持 v1/v2/v3 签名方案）；
            // 旧版本回退 GET_SIGNATURES（API 24-27 的 PackageParser 可从 v2 签名回填 certificates）
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }

            val installed = try {
                pm.getPackageInfo(context.packageName, flags)
            } catch (e: PackageManager.NameNotFoundException) {
                AppLogger.e(TAG, "读取已安装应用信息失败", e)
                null
            }
            val archive = pm.getPackageArchiveInfo(apkFile.absolutePath, flags)

            val installedSigs = installed?.let { extractSignatures(it) } ?: emptyArray()
            val archiveSigs = archive?.let { extractSignatures(it) } ?: emptyArray()

            if (installedSigs.isEmpty() || archiveSigs.isEmpty()) {
                AppLogger.e(
                    TAG,
                    "无法读取签名: 已安装应用签名数=${installedSigs.size}, 更新包签名数=${archiveSigs.size}"
                )
                return SignatureVerifyResult.Unreadable
            }

            val installedDigests = installedSigs.map { fingerprint(it) }
            val archiveDigests = archiveSigs.map { fingerprint(it) }
            AppLogger.i(TAG, "已安装应用证书指纹: $installedDigests")
            AppLogger.i(TAG, "更新包证书指纹: $archiveDigests")

            val matched = archiveSigs.any { archiveSig ->
                installedSigs.any { it.toByteArray().contentEquals(archiveSig.toByteArray()) }
            }
            if (matched) {
                SignatureVerifyResult.Match
            } else {
                SignatureVerifyResult.Mismatch(
                    installedDigest = installedDigests.firstOrNull(),
                    archiveDigest = archiveDigests.firstOrNull()
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "APK 签名校验失败", e)
            SignatureVerifyResult.Unreadable
        }
    }

    /** 从 PackageInfo 中提取签名证书，兼容新旧两套 API */
    @Suppress("DEPRECATION")
    private fun extractSignatures(packageInfo: android.content.pm.PackageInfo): Array<android.content.pm.Signature> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // apkContentsSigners 为实际签署 APK 内容的证书，覆盖 v1/v2/v3 签名方案
            packageInfo.signingInfo?.apkContentsSigners ?: emptyArray()
        } else {
            packageInfo.signatures ?: emptyArray()
        }
    }

    /** 签名证书的 SHA-256 指纹（用于日志比对排查） */
    private fun fingerprint(signature: android.content.pm.Signature): String {
        return try {
            MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
                .joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            "unknown"
        }
    }

    // ---------- 安装 ----------

    /** 是否已具备"安装未知来源应用"权限（Android 8.0 以下默认允许，无需申请） */
    fun canRequestPackageInstalls(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()
    }

    /** 跳转系统设置，引导用户允许安装未知来源应用 */
    fun openInstallPermissionSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            )
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * 通过 FileProvider 调起系统安装器安装 APK。
     * 返回是否成功启动安装流程。
     */
    fun installApk(context: Context, apkFile: File): Boolean {
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "启动系统安装器失败", e)
            false
        }
    }
}
