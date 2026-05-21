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
        private const val CONNECTION_TIMEOUT_MS = 30000
        private const val READ_TIMEOUT_MS = 30000
        private const val DATE_FORMAT_PATTERN = "EEE, dd MMM yyyy HH:mm:ss zzz"
    }

    private val backupFileName = DEFAULT_BACKUP_FILENAME

    /**
     * 测试 WebDAV 连接
     */
    suspend fun testConnection(config: WebDAVConfig): Boolean = withContext(Dispatchers.IO) {
        try {
            val dirUrl = buildUrl(config.serverUrl, config.remotePath)
            
            // 先尝试创建目录
            createDirIfNotExists(dirUrl, config)
            
            // 检查是否可以访问
            executeRequest(
                url = dirUrl,
                method = "PROPFIND",
                config = config,
                setup = { connection ->
                    connection.setRequestProperty("Depth", "0")
                },
                handleResponse = { connection ->
                    connection.responseCode in 200..299
                }
            )
        } catch (e: Exception) {
            AppLogger.e("WebDAV连接测试失败", e)
            false
        }
    }

    /**
     * 上传文件到 WebDAV
     */
    suspend fun uploadFile(config: WebDAVConfig, content: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val dirUrl = buildUrl(config.serverUrl, config.remotePath)
            val fileUrl = buildUrl(config.serverUrl, config.remotePath, backupFileName)
            
            // 确保目录存在
            createDirIfNotExists(dirUrl, config)
            
            // 上传文件
            executeRequest(
                url = fileUrl,
                method = "PUT",
                config = config,
                setup = { connection ->
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.doOutput = true
                },
                writeBody = { outputStream ->
                    DataOutputStream(outputStream).use { it.write(content.toByteArray(Charsets.UTF_8)) }
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
     * 从 WebDAV 下载文件
     */
    suspend fun downloadFile(config: WebDAVConfig): String? = withContext(Dispatchers.IO) {
        try {
            val fileUrl = buildUrl(config.serverUrl, config.remotePath, backupFileName)
            
            executeRequest(
                url = fileUrl,
                method = "GET",
                config = config,
                handleResponse = { connection ->
                    if (connection.responseCode in 200..299) {
                        val content = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { it.readText() }
                        AppLogger.d("WebDAV文件下载成功")
                        content
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
     * 如果目录不存在则创建
     */
    private suspend fun createDirIfNotExists(dirUrl: String, config: WebDAVConfig) = withContext(Dispatchers.IO) {
        try {
            // 检查目录是否存在
            val exists = executeRequest(
                url = dirUrl,
                method = "PROPFIND",
                config = config,
                setup = { connection ->
                    connection.setRequestProperty("Depth", "0")
                },
                handleResponse = { connection ->
                    connection.responseCode in 200..299
                }
            )
            
            if (!exists) {
                // 目录不存在，创建它
                executeRequest(
                    url = dirUrl,
                    method = "MKCOL",
                    config = config,
                    handleResponse = { connection ->
                        AppLogger.d("WebDAV目录创建状态: ${connection.responseCode}")
                    }
                )
            }
        } catch (e: Exception) {
            AppLogger.e("创建WebDAV目录失败", e)
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
            connection.connectTimeout = CONNECTION_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            
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
    private fun buildUrl(vararg parts: String): String {
        val urlBuilder = StringBuilder()
        parts.forEachIndexed { index, part ->
            val cleanPart = part.trim('/')
            if (cleanPart.isNotEmpty()) {
                if (index > 0 && urlBuilder.isNotEmpty() && !urlBuilder.endsWith('/')) {
                    urlBuilder.append('/')
                }
                urlBuilder.append(cleanPart)
            }
        }
        return urlBuilder.toString()
    }
}
