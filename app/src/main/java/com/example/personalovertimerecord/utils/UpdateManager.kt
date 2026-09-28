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
 * - 是否有新版本以 versionName 的语义化版本号（major.minor.patch）比较为主，远端 versionName
 *   高于本地即判定为可更新，不受 versionCode 影响；versionName 解析失败时回退到 versionCode 比较；
 * - 强制更新同样以 minVersionName（语义化版本）优先，minVersionName 未设置时回退到 minVersionCode；
 * - 更新包必须与已安装应用使用同一把 release keystore 签名，否则系统拒绝覆盖安装；
 * - 更新清单与 APK 下载均要求 https（应用已禁止明文 HTTP 流量）。
 */
object UpdateManager {

    private const val TAG = "UpdateManager"
    private const val PREFS_NAME = "update_prefs"
    private const val KEY_LAST_CHECK_TIME = "last_check_time"
    private const val UPDATE_DIR = "updates"
    private const val APK_FILE_NAME = "app-update.apk"

    /** Gson 实例线程安全，可复用，避免每次请求都创建 */
    private val gson = Gson()

    /** 更新清单（latest.json），字段与 release.yml 生成的 JSON 保持一致 */
    data class UpdateInfo(
        val versionCode: Int = 0,
        val versionName: String = "",
        val apkUrl: String = "",
        val sha256: String? = null,
        val changelog: String? = null,
        val minVersionCode: Int = 0,
        val minVersionName: String = ""
    )

    // ---------- 版本信息 ----------

    /**
     * 获取当前应用的 PackageInfo。
     * API 33+ 使用 PackageInfoFlags（旧的 getPackageInfo(name, flags) 已废弃）；
     * 旧版本回退到已废弃的两参数重载。读取失败返回 null。
     */
    private fun getPackageInfo(context: Context): android.content.pm.PackageInfo? {
        val pm = context.packageManager
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "获取 PackageInfo 失败", e)
            null
        }
    }

    fun getCurrentVersionCode(context: Context): Int {
        val pmCode = getPackageInfo(context)?.let {
            @Suppress("DEPRECATION")
            it.versionCode
        } ?: 0
        // PackageManager 读取异常或返回 0（旧版 APK 版本号为空）时，回退到编译期常量
        return pmCode.takeIf { it > 0 } ?: com.example.personalovertimerecord.BuildConfig.VERSION_CODE
    }

    fun getCurrentVersionName(context: Context): String {
        val pmName = getPackageInfo(context)?.versionName
        return pmName?.takeIf { it.isNotBlank() }
            ?: com.example.personalovertimerecord.BuildConfig.VERSION_NAME
    }

    /**
     * 是否存在新版本。
     *
     * 比较策略：以 versionName 的语义化版本号（major.minor.patch）为主。
     * 当远端 versionName 严格高于本地 versionName 时，无论 versionCode 高低均判定为有新版本；
     * 若 versionName 解析失败（空串/非数字格式），则回退到 versionCode 比较。
     */
    fun isUpdateAvailable(context: Context, info: UpdateInfo): Boolean {
        val remoteParts = parseSemanticVersion(info.versionName)
        val localParts = parseSemanticVersion(getCurrentVersionName(context))
        return if (remoteParts != null && localParts != null) {
            remoteParts > localParts
        } else {
            info.versionCode > getCurrentVersionCode(context)
        }
    }

    /**
     * 解析语义化版本号（支持 major.minor.patch 三段，patch 可省略）。
     * 用正则提取所有数字序列，自动忽略前缀（如 "v"）和后缀（如 "-rc1"、"-beta"）。
     * 例如 "v1.2.3-rc1" -> [1, 2, 3]。无任何数字段时返回 null。
     */
    private fun parseSemanticVersion(versionName: String): List<Int>? {
        if (versionName.isBlank()) return null
        val parts = Regex("""\d+""").findAll(versionName).map { it.value.toInt() }.toList()
        return parts.takeIf { it.isNotEmpty() }
    }

    /**
     * 比较两个语义化版本号列表。
     * 列表长度不一致时，缺失段按 0 处理（例如 1.2 等价于 1.2.0）。
     */
    private operator fun List<Int>.compareTo(other: List<Int>): Int {
        val maxLen = maxOf(size, other.size)
        for (i in 0 until maxLen) {
            val a = getOrElse(i) { 0 }
            val b = other.getOrElse(i) { 0 }
            if (a != b) return a.compareTo(b)
        }
        return 0
    }

    /**
     * 是否需要强制更新。
     *
     * 优先使用清单的 minVersionName（语义化版本）：若当前 versionName 低于 minVersionName 则强制更新；
     * minVersionName 未设置或解析失败时，回退到 minVersionCode 比较。
     */
    fun isForceUpdate(context: Context, info: UpdateInfo): Boolean {
        val minParts = parseSemanticVersion(info.minVersionName)
        val localParts = parseSemanticVersion(getCurrentVersionName(context))
        return if (minParts != null && localParts != null) {
            minParts > localParts
        } else {
            info.minVersionCode > 0 && info.minVersionCode > getCurrentVersionCode(context)
        }
    }

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
                gson.fromJson(json, UpdateInfo::class.java)
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

                // contentLength 在服务器使用分块传输编码（chunked）时返回 -1，
                // 规范为 0 表示"未知大小"，UI 层据此走不确定进度展示。
                val total = connection.contentLength.takeIf { it > 0 }?.toLong() ?: 0L
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
