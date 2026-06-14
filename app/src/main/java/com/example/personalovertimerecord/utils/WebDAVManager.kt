package com.example.personalovertimerecord.utils

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
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
 */
class WebDAVManager(private val context: Context) {

    companion object {
        private const val DEFAULT_BACKUP_FILENAME = "overtime_backup.json"
        private const val CONNECTION_TIMEOUT_MS = 60000
        private const val READ_TIMEOUT_MS = 60000
        private const val DATE_FORMAT_PATTERN = "EEE, dd MMM yyyy HH:mm:ss zzz"
        
        var lastResponseCode: Int = 0
    }

    private val backupFileName = DEFAULT_BACKUP_FILENAME

    /**
     * 测试 WebDAV 连接（简化版，使用 PUT 简单测试）
     */
    suspend fun testConnection(config: WebDAVConfig): Boolean = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val testFileUrl = buildUrl(config.serverUrl, config.remotePath, ".test_connection")
            
            AppLogger.d("WebDAV 测试连接: $testFileUrl")
            
            val urlObj = URL(testFileUrl)
            connection = urlObj.openConnection() as HttpURLConnection
            
            // 设置基本属性
            connection.requestMethod = "PUT"
            connection.setRequestProperty("Authorization", getBasicAuth(config.username, config.password))
            connection.setRequestProperty("User-Agent", "Android-WebDAV-Client/1.0")
            connection.setRequestProperty("Content-Type", "text/plain")
            connection.connectTimeout = CONNECTION_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.doOutput = true
            
            // 写入空内容
            DataOutputStream(connection.outputStream).use { it.write("".toByteArray(Charsets.UTF_8)) }
            
            val responseCode = connection.responseCode
            lastResponseCode = responseCode
            AppLogger.d("WebDAV 测试响应码: $responseCode")
            
            // 检查响应码，200-299 都表示成功
            val success = responseCode in 200..299
            
            // 测试成功后尝试删除测试文件
            if (success) {
                tryDeleteFile(testFileUrl, config)
            }
            
            success
        } catch (e: Exception) {
            AppLogger.e("WebDAV连接测试失败", e)
            lastResponseCode = -1
            false
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * 尝试删除文件（忽略错误）
     */
    private fun tryDeleteFile(fileUrl: String, config: WebDAVConfig) {
        var connection: HttpURLConnection? = null
        try {
            val urlObj = URL(fileUrl)
            connection = urlObj.openConnection() as HttpURLConnection
            connection.requestMethod = "DELETE"
            connection.setRequestProperty("Authorization", getBasicAuth(config.username, config.password))
            connection.setRequestProperty("User-Agent", "Android-WebDAV-Client/1.0")
            connection.connectTimeout = CONNECTION_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            
            val responseCode = connection.responseCode
            AppLogger.d("删除测试文件响应: $responseCode")
        } catch (e: Exception) {
            AppLogger.d("删除测试文件失败，但不影响连接测试: ${e.message}")
        } finally {
            connection?.disconnect()
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
                    if (connection.responseCode in 200..299) {
                        val content = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { it.readText() }
                        AppLogger.d("WebDAV文件下载成功")
                        
                        if (!decryptPassword.isNullOrBlank()) {
                            try {
                                EncryptionUtils.decryptString(content, decryptPassword)
                            } catch (ex: Exception) {
                                AppLogger.w("WebDAV", "解密失败，可能数据未加密: ${ex.message}")
                                content
                            }
                        } else {
                            content
                        }
                    } else {
                        AppLogger.e("WebDAV文件不存在或下载失败: ${connection.responseCode}")
                        null
                    }
                }
            )
        } catch (e: Exception) {
            AppLogger.e("WebDAV文件下载失败", e)
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
     * 通用的 HTTP 请求执行方法
     */
    private inline fun <T> executeRequest(
        url: String,
        method: String,
        config: WebDAVConfig,
        setup: (HttpURLConnection) -> Unit = {},
        writeBody: (java.io.OutputStream) -> Unit = {},
        handleResponse: (HttpURLConnection) -> T
    ): T {
        val urlObj = URL(url)
        var connection: HttpURLConnection? = null
        
        return try {
            connection = urlObj.openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.setRequestProperty("Authorization", getBasicAuth(config.username, config.password))
            connection.setRequestProperty("User-Agent", "Android-WebDAV-Client/1.0")
            connection.connectTimeout = CONNECTION_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            
            // 自定义设置
            setup(connection)
            
            // 写入请求体
            if (method in listOf("PUT", "POST")) {
                writeBody(connection.outputStream)
            }
            
            // 处理响应
            handleResponse(connection)
        } finally {
            connection?.disconnect()
        }
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
