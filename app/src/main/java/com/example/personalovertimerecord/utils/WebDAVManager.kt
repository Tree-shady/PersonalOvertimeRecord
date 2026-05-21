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

data class WebDAVConfig(
    val serverUrl: String,
    val username: String,
    val password: String,
    val remotePath: String = "/overtime_record/"
)

class WebDAVManager(private val context: Context) {

    private val backupFileName = "overtime_backup.json"

    suspend fun testConnection(config: WebDAVConfig): Boolean = withContext(Dispatchers.IO) {
        try {
            val dirUrl = buildUrl(config.serverUrl, config.remotePath)
            
            // 先尝试创建目录
            createDirIfNotExists(dirUrl, config)
            
            // 检查是否可以访问
            val url = URL(dirUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "PROPFIND"
            connection.setRequestProperty("Authorization", getBasicAuth(config.username, config.password))
            connection.setRequestProperty("Depth", "0")
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            
            val responseCode = connection.responseCode
            connection.disconnect()
            
            return@withContext responseCode in 200..299
        } catch (e: Exception) {
            AppLogger.e("WebDAV连接测试失败", e)
            false
        }
    }

    suspend fun uploadFile(config: WebDAVConfig, content: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val dirUrl = buildUrl(config.serverUrl, config.remotePath)
            val fileUrl = buildUrl(config.serverUrl, config.remotePath, backupFileName)
            
            // 确保目录存在
            createDirIfNotExists(dirUrl, config)
            
            // 上传文件
            val url = URL(fileUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "PUT"
            connection.setRequestProperty("Authorization", getBasicAuth(config.username, config.password))
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            
            DataOutputStream(connection.outputStream).use { it.write(content.toByteArray(Charsets.UTF_8)) }
            
            val responseCode = connection.responseCode
            connection.disconnect()
            
            if (responseCode in 200..299) {
                AppLogger.d("WebDAV文件上传成功: $fileUrl")
                true
            } else {
                AppLogger.e("WebDAV文件上传失败: $responseCode")
                false
            }
        } catch (e: Exception) {
            AppLogger.e("WebDAV文件上传失败", e)
            false
        }
    }

    suspend fun downloadFile(config: WebDAVConfig): String? = withContext(Dispatchers.IO) {
        try {
            val fileUrl = buildUrl(config.serverUrl, config.remotePath, backupFileName)
            
            val url = URL(fileUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", getBasicAuth(config.username, config.password))
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            
            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val content = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { it.readText() }
                connection.disconnect()
                AppLogger.d("WebDAV文件下载成功")
                content
            } else {
                connection.disconnect()
                AppLogger.e("WebDAV文件不存在或下载失败: $responseCode")
                null
            }
        } catch (e: Exception) {
            AppLogger.e("WebDAV文件下载失败", e)
            null
        }
    }

    suspend fun getFileModifiedTime(config: WebDAVConfig): Long? = withContext(Dispatchers.IO) {
        try {
            val fileUrl = buildUrl(config.serverUrl, config.remotePath, backupFileName)
            
            val url = URL(fileUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.setRequestProperty("Authorization", getBasicAuth(config.username, config.password))
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            
            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val lastModified = connection.getHeaderField("Last-Modified")
                connection.disconnect()
                lastModified?.let {
                    try {
                        val dateFormat = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.ENGLISH)
                        dateFormat.parse(it)?.time
                    } catch (e: Exception) {
                        null
                    }
                }
            } else {
                connection.disconnect()
                null
            }
        } catch (e: Exception) {
            AppLogger.e("获取WebDAV文件修改时间失败", e)
            null
        }
    }

    private suspend fun createDirIfNotExists(dirUrl: String, config: WebDAVConfig) = withContext(Dispatchers.IO) {
        try {
            // 检查目录是否存在
            val checkUrl = URL(dirUrl)
            val checkConnection = checkUrl.openConnection() as HttpURLConnection
            checkConnection.requestMethod = "PROPFIND"
            checkConnection.setRequestProperty("Authorization", getBasicAuth(config.username, config.password))
            checkConnection.setRequestProperty("Depth", "0")
            checkConnection.connectTimeout = 30000
            checkConnection.readTimeout = 30000
            
            val checkResponseCode = checkConnection.responseCode
            checkConnection.disconnect()
            
            if (checkResponseCode !in 200..299) {
                // 目录不存在，创建它
                val mkcolUrl = URL(dirUrl)
                val mkcolConnection = mkcolUrl.openConnection() as HttpURLConnection
                mkcolConnection.requestMethod = "MKCOL"
                mkcolConnection.setRequestProperty("Authorization", getBasicAuth(config.username, config.password))
                mkcolConnection.connectTimeout = 30000
                mkcolConnection.readTimeout = 30000
                
                mkcolConnection.responseCode
                mkcolConnection.disconnect()
            }
        } catch (e: Exception) {
            AppLogger.e("创建WebDAV目录失败", e)
        }
    }

    private fun getBasicAuth(username: String, password: String): String {
        val credentials = "$username:$password"
        return "Basic " + Base64.encodeToString(credentials.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

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
