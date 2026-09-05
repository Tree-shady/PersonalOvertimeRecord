package com.example.personalovertimerecord.utils

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * WebDAV 配置
 */
data class WebDAVConfig(
    val serverUrl: String,
    val username: String,
    val password: String,
    val remotePath: String = "/overtime_record/"
)

/**
 * WebDAV 管理器
 * 负责与 WebDAV 服务器通信，支持文件上传、下载、连接测试等
 *
 * 重定向安全说明：本客户端关闭了系统级自动跳转（instanceFollowRedirects=false），
 * 只手动跟随“https 且主机/端口不变”的同源跳转；跨源跳转一律拒绝并当作请求失败处理。
 * 这样 Basic 认证凭据只会发送给用户在设置中配置的那一台服务器，不会被转发到第三方主机。
 */
class WebDAVManager(private val context: Context) {

    companion object {
        private const val DEFAULT_BACKUP_FILENAME = "overtime_backup.json"
        private const val CONNECTION_TIMEOUT_MS = 60000
        private const val READ_TIMEOUT_MS = 60000
        private const val DATE_FORMAT_PATTERN = "EEE, dd MMM yyyy HH:mm:ss zzz"

        /** 云端备份下载大小上限：防止异常/被篡改的超大文件整读进内存导致 OOM */
        private const val MAX_DOWNLOAD_BYTES = 64L * 1024 * 1024

        /** 手动跟随跳转的最大次数（防跳转环） */
        private const val MAX_REDIRECTS = 5

        /** 需要处理的 HTTP 重定向状态码 */
        private val REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)

        var lastResponseCode: Int = 0
    }

    private val backupFileName = DEFAULT_BACKUP_FILENAME

    /**
     * 测试 WebDAV 连接（使用 PUT 向服务器写入一个空测试文件，成功后再删除）
     */
    suspend fun testConnection(config: WebDAVConfig): Boolean = withContext(Dispatchers.IO) {
        try {
            val testFileUrl = buildUrl(config.serverUrl, config.remotePath, ".test_connection")

            AppLogger.d("WebDAV 测试连接: $testFileUrl")

            val success = executeRequest(
                url = testFileUrl,
                method = "PUT",
                config = config,
                setup = { connection ->
                    connection.setRequestProperty("Content-Type", "text/plain")
                    connection.doOutput = true
                },
                writeBody = { outputStream ->
                    // 写入空内容（关闭输出流以完成请求）
                    DataOutputStream(outputStream).use { /* 空请求体 */ }
                },
                handleResponse = { connection ->
                    val responseCode = connection.responseCode
                    lastResponseCode = responseCode
                    AppLogger.d("WebDAV 测试响应码: $responseCode")
                    // 检查响应码，200-299 都表示成功
                    responseCode in 200..299
                }
            )

            // 测试成功后尝试删除测试文件
            if (success) {
                tryDeleteFile(testFileUrl, config)
            }

            success
        } catch (e: Exception) {
            AppLogger.e("WebDAV连接测试失败", e)
            lastResponseCode = -1
            false
        }
    }

    /**
     * 尝试删除文件（忽略错误，仅用于清理连接测试产生的临时文件）
     */
    private fun tryDeleteFile(fileUrl: String, config: WebDAVConfig) {
        try {
            executeRequest(
                url = fileUrl,
                method = "DELETE",
                config = config,
                handleResponse = { connection ->
                    AppLogger.d("删除测试文件响应: ${connection.responseCode}")
                    true
                }
            )
        } catch (e: Exception) {
            AppLogger.d("删除测试文件失败，但不影响连接测试: ${e.message}")
        }
    }

    /**
     * 上传文件到 WebDAV
     * @param config WebDAV配置
     * @param content 要上传的内容
     * @param encryptPassword 加密密码（可选，为空则不加密）
     */
    suspend fun uploadFile(config: WebDAVConfig, content: String, encryptPassword: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            // 如果设置了密码，先加密内容
            val uploadContent = if (!encryptPassword.isNullOrBlank()) {
                EncryptionUtils.encryptString(content, encryptPassword)
            } else {
                content
            }
            
            val fileUrl = buildUrl(config.serverUrl, config.remotePath, backupFileName)
            
            AppLogger.d("WebDAV 上传文件: $fileUrl")
            
            // 确保目录存在
            ensureDirectoryExists(config)
            
            executeRequest(
                url = fileUrl,
                method = "PUT",
                config = config,
                setup = { connection ->
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.doOutput = true
                },
                writeBody = { outputStream ->
                    DataOutputStream(outputStream).use { it.write(uploadContent.toByteArray(Charsets.UTF_8)) }
                },
                handleResponse = { connection ->
                    val success = connection.responseCode in 200..299
                    if (success) {
                        AppLogger.d("WebDAV文件上传成功: $fileUrl")
                    } else {
                        AppLogger.e("WebDAV文件上传失败: ${connection.responseCode}")
                    }
                    success
                }
            )
        } catch (e: Exception) {
            AppLogger.e("WebDAV文件上传失败", e)
            false
        }
    }

    /**
     * 确保目录存在
     */
    private suspend fun ensureDirectoryExists(config: WebDAVConfig) {
        val dirUrl = buildUrl(config.serverUrl, config.remotePath)
        AppLogger.d("确保目录存在: $dirUrl")
        
        try {
            // 尝试 MKCOL 创建目录，大多数服务会忽略已存在的目录
            executeRequest(
                url = dirUrl,
                method = "MKCOL",
                config = config,
                setup = { connection ->
                    connection.setRequestProperty("Content-Type", "text/xml")
                },
                handleResponse = { connection ->
                    AppLogger.d("MKCOL 响应: ${connection.responseCode}")
                    true
                }
            )
        } catch (e: Exception) {
            AppLogger.d("创建目录失败（可能已存在）: ${e.message}")
        }
    }

    /**
     * 从 WebDAV 下载文件
     * @param config WebDAV配置
     * @param decryptPassword 解密密码（可选，为空则不解密）
     */
    suspend fun downloadFile(config: WebDAVConfig, decryptPassword: String? = null): String? = withContext(Dispatchers.IO) {
        try {
            val fileUrl = buildUrl(config.serverUrl, config.remotePath, backupFileName)
            
            AppLogger.d("WebDAV 下载文件: $fileUrl")
            
            executeRequest(
                url = fileUrl,
                method = "GET",
                config = config,
                setup = { connection ->
                    connection.setRequestProperty("Accept", "*/*")
                },
                handleResponse = { connection ->
                    val responseCode = connection.responseCode
                    lastResponseCode = responseCode
                    if (responseCode in 200..299) {
                        val content = connection.inputStream.use { readBodyCapped(it, MAX_DOWNLOAD_BYTES) }
                        AppLogger.d("WebDAV文件下载成功")
                        
                        if (!decryptPassword.isNullOrBlank()) {
                            try {
                                EncryptionUtils.decryptString(content, decryptPassword)
                            } catch (ex: Exception) {
                                // 解密失败（数据未加密或密码错误）时返回 null，
                                // 由调用方走"不使用密码重试"的兼容逻辑，避免把密文当明文解析
                                AppLogger.w("WebDAV", "解密失败，可能数据未加密或密码错误: ${ex.message}")
                                null
                            }
                        } else {
                            content
                        }
                    } else {
                        AppLogger.e("WebDAV文件不存在或下载失败: $responseCode")
                        null
                    }
                }
            )
        } catch (e: Exception) {
            AppLogger.e("WebDAV文件下载失败", e)
            // 网络/IO 异常时清除响应码，防止残留上一次的 404 被误判为"云端无数据"
            lastResponseCode = -1
            null
        }
    }

    /**
     * 获取文件最后修改时间
     */
    suspend fun getFileModifiedTime(config: WebDAVConfig): Long? = withContext(Dispatchers.IO) {
        try {
            val fileUrl = buildUrl(config.serverUrl, config.remotePath, backupFileName)
            
            executeRequest(
                url = fileUrl,
                method = "HEAD",
                config = config,
                setup = { connection ->
                    connection.setRequestProperty("Accept", "*/*")
                },
                handleResponse = { connection ->
                    if (connection.responseCode in 200..299) {
                        val lastModified = connection.getHeaderField("Last-Modified")
                        lastModified?.let {
                            try {
                                val dateFormat = java.text.SimpleDateFormat(DATE_FORMAT_PATTERN, java.util.Locale.ENGLISH)
                                dateFormat.parse(it)?.time
                            } catch (e: Exception) {
                                null
                            }
                        }
                    } else {
                        null
                    }
                }
            )
        } catch (e: Exception) {
            AppLogger.e("获取WebDAV文件修改时间失败", e)
            null
        }
    }

    /**
     * 通用的 HTTP 请求执行方法（带同源重定向限制）。
     *
     * 重定向策略：
     * - 关闭系统级自动跳转，改为手动处理；
     * - 仅当 Location 指向“https 且主机/端口与原请求一致”时才继续（最多 [MAX_REDIRECTS] 次）；
     * - 跨源（含 https→http 降级）跳转直接拒绝，把 3xx 原样交给 [handleResponse]，
     *   由调用方按“非 2xx”处理——保证 Authorization 凭据永远不会发给第三方主机。
     */
    private inline fun <T> executeRequest(
        url: String,
        method: String,
        config: WebDAVConfig,
        setup: (HttpURLConnection) -> Unit = {},
        writeBody: (java.io.OutputStream) -> Unit = {},
        handleResponse: (HttpURLConnection) -> T
    ): T {
        val originalUrl = URL(url)
        var currentUrl = url
        var redirectCount = 0

        while (true) {
            var connection: HttpURLConnection? = null
            try {
                connection = URL(currentUrl).openConnection() as HttpURLConnection
                connection.requestMethod = method
                connection.setRequestProperty("Authorization", getBasicAuth(config.username, config.password))
                connection.setRequestProperty("User-Agent", "Android-WebDAV-Client/1.0")
                connection.connectTimeout = CONNECTION_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.instanceFollowRedirects = false

                // 自定义设置
                setup(connection)

                // 写入请求体
                if (method in listOf("PUT", "POST")) {
                    writeBody(connection.outputStream)
                }

                val responseCode = connection.responseCode

                // 手动跟随同源重定向
                if (responseCode in REDIRECT_STATUS_CODES && redirectCount < MAX_REDIRECTS) {
                    val location = connection.getHeaderField("Location")
                    if (!location.isNullOrBlank()) {
                        val next = URL(URL(currentUrl), location)
                        if (isSameOrigin(originalUrl, next)) {
                            redirectCount++
                            currentUrl = next.toExternalForm()
                            continue
                        }
                        // 跨源跳转：拒绝跟随，把 3xx 交回调用方按失败处理
                        AppLogger.w("WebDAV", "拒绝跨源重定向（$responseCode → ${next.protocol}://${next.host}），已中止以保护凭据")
                    }
                }

                // 处理响应
                return handleResponse(connection)
            } finally {
                connection?.disconnect()
            }
        }
    }

    /**
     * 是否为同源（仅允许 https，且协议/主机/端口完全一致）。
     * 用于限制重定向跟随，防止 Basic 凭据被转发到其它主机或降级到明文 HTTP。
     */
    private fun isSameOrigin(a: URL, b: URL): Boolean {
        if (a.protocol != "https" || b.protocol != "https") return false
        if (!a.host.equals(b.host, ignoreCase = true)) return false
        return effectivePort(a) == effectivePort(b)
    }

    private fun effectivePort(url: URL): Int {
        return if (url.port != -1) url.port else if (url.protocol == "https") 443 else if (url.protocol == "http") 80 else url.port
    }

    /**
     * 限制大小地读取响应体为 UTF-8 文本（超限抛 IOException，由调用方按“下载失败”处理，
     * 不会把截断内容当作完整备份解析）。
     */
    private fun readBodyCapped(input: java.io.InputStream, maxBytes: Long): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            total += read
            if (total > maxBytes) {
                throw IOException("云端备份超过大小上限（${maxBytes / (1024 * 1024)} MB），已中止下载")
            }
            out.write(buffer, 0, read)
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    /**
     * 获取 Basic Auth 头部
     */
    private fun getBasicAuth(username: String, password: String): String {
        val credentials = "$username:$password"
        return "Basic " + Base64.encodeToString(credentials.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    /**
     * 构建 URL
     */
    private fun buildUrl(baseUrl: String, vararg pathParts: String): String {
        val cleanBaseUrl = baseUrl.trimEnd('/')
        val result = StringBuilder(cleanBaseUrl)
        
        pathParts.forEach { part ->
            val cleanPart = part.trim('/')
            if (cleanPart.isNotEmpty()) {
                result.append('/').append(cleanPart)
            }
        }
        
        return result.toString()
    }
}
