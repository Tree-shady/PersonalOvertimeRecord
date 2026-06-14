package com.example.personalovertimerecord.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

/**
 * 网络工具类
 * 提供网络状态检查、WebDAV操作等功能
 */
object NetworkUtils {

    private const val TAG = "NetworkUtils"
    private const val TIMEOUT_CONNECT = 15000 // 15秒
    private const val TIMEOUT_READ = 15000   // 15秒

    /**
     * 检查网络是否可用
     */
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return false
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
                
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.activeNetworkInfo
                @Suppress("DEPRECATION")
                networkInfo != null && networkInfo.isConnected
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error checking network availability", e)
            false
        }
    }

    /**
     * 检查是否为WiFi连接
     */
    fun isWifiConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return false
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.activeNetworkInfo
                @Suppress("DEPRECATION")
                networkInfo?.type == ConnectivityManager.TYPE_WIFI
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error checking WiFi connection", e)
            false
        }
    }

    /**
     * 测试网络连接速度
     * 返回延迟时间（毫秒）
     */
    suspend fun testConnectionSpeed(url: String = "https://www.google.com"): Long {
        return withContext(Dispatchers.IO) {
            try {
                val startTime = System.currentTimeMillis()
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "HEAD"
                connection.connectTimeout = TIMEOUT_CONNECT
                connection.readTimeout = TIMEOUT_READ
                connection.doInput = true
                
                connection.connect()
                val responseCode = connection.responseCode
                connection.disconnect()
                
                if (responseCode in 200..399) {
                    System.currentTimeMillis() - startTime
                } else {
                    -1
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Connection test failed", e)
                -1
            }
        }
    }

    /**
     * 测试服务器是否可达
     */
    suspend fun testServerReachability(serverUrl: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(normalizeUrl(serverUrl))
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "HEAD"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                
                val responseCode = connection.responseCode
                connection.disconnect()
                
                responseCode in 200..499 // HEAD请求不需要成功响应
            } catch (e: Exception) {
                AppLogger.w(TAG, "Server not reachable: $serverUrl", e)
                false
            }
        }
    }

    /**
     * WebDAV请求结果
     */
    sealed class WebDAVResult {
        data class Success(val statusCode: Int, val message: String = "") : WebDAVResult()
        data class Error(val code: Int, val message: String) : WebDAVResult()
        
        val isSuccess: Boolean get() = this is Success
    }

    /**
     * 发送WebDAV请求（支持HTTP Basic认证）
     */
    suspend fun sendWebDAVRequest(
        url: String,
        method: String,
        username: String?,
        password: String?,
        body: String? = null,
        contentType: String = "application/xml"
    ): WebDAVResult {
        return withContext(Dispatchers.IO) {
            try {
                AppLogger.d(TAG, "WebDAV $method: $url")
                
                val connection = createConnection(url)
                connection.requestMethod = method
                connection.setRequestProperty("Content-Type", contentType)
                
                // 设置认证
                if (!username.isNullOrBlank()) {
                    val auth = "$username:$password"
                    val encodedAuth = java.util.Base64.getEncoder().encodeToString(auth.toByteArray())
                    connection.setRequestProperty("Authorization", "Basic $encodedAuth")
                }
                
                // 发送请求体
                if (!body.isNullOrBlank()) {
                    connection.doOutput = true
                    connection.outputStream.use { output ->
                        output.write(body.toByteArray())
                    }
                }
                
                val statusCode = connection.responseCode
                val responseMessage = connection.responseMessage ?: ""
                
                AppLogger.d(TAG, "WebDAV response: $statusCode $responseMessage")
                
                connection.disconnect()
                
                if (statusCode in 200..299) {
                    WebDAVResult.Success(statusCode, responseMessage)
                } else {
                    WebDAVResult.Error(statusCode, responseMessage)
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "WebDAV request failed", e)
                WebDAVResult.Error(-1, e.message ?: "Unknown error")
            }
        }
    }

    /**
     * 上传文件到WebDAV服务器
     */
    suspend fun uploadToWebDAV(
        serverUrl: String,
        path: String,
        data: ByteArray,
        username: String?,
        password: String?,
        contentType: String = "application/octet-stream"
    ): WebDAVResult {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$serverUrl${normalizePath(path)}"
                AppLogger.d(TAG, "Uploading to: $url (${data.size} bytes)")
                
                val connection = createConnection(url)
                connection.requestMethod = "PUT"
                connection.setRequestProperty("Content-Type", contentType)
                connection.setRequestProperty("Content-Length", data.size.toString())
                connection.doOutput = true
                
                // 设置认证
                if (!username.isNullOrBlank()) {
                    val auth = "$username:$password"
                    val encodedAuth = java.util.Base64.getEncoder().encodeToString(auth.toByteArray())
                    connection.setRequestProperty("Authorization", "Basic $encodedAuth")
                }
                
                // 上传数据
                connection.outputStream.use { output ->
                    output.write(data)
                }
                
                val statusCode = connection.responseCode
                val responseMessage = connection.responseMessage ?: ""
                
                AppLogger.d(TAG, "Upload response: $statusCode $responseMessage")
                
                connection.disconnect()
                
                if (statusCode in 200..299 || statusCode == 201) {
                    WebDAVResult.Success(statusCode, responseMessage)
                } else {
                    WebDAVResult.Error(statusCode, responseMessage)
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Upload failed", e)
                WebDAVResult.Error(-1, e.message ?: "Upload failed")
            }
        }
    }

    /**
     * 下载文件从WebDAV服务器
     */
    suspend fun downloadFromWebDAV(
        serverUrl: String,
        path: String,
        username: String?,
        password: String?
    ): Result<ByteArray> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$serverUrl${normalizePath(path)}"
                AppLogger.d(TAG, "Downloading from: $url")
                
                val connection = createConnection(url)
                connection.requestMethod = "GET"
                
                // 设置认证
                if (!username.isNullOrBlank()) {
                    val auth = "$username:$password"
                    val encodedAuth = java.util.Base64.getEncoder().encodeToString(auth.toByteArray())
                    connection.setRequestProperty("Authorization", "Basic $encodedAuth")
                }
                
                val statusCode = connection.responseCode
                
                if (statusCode == 200) {
                    val data = connection.inputStream.use { input ->
                        input.readBytes()
                    }
                    AppLogger.d(TAG, "Downloaded ${data.size} bytes")
                    connection.disconnect()
                    Result.success(data)
                } else {
                    val errorMessage = connection.errorStream?.bufferedReader()?.readText() ?: "HTTP $statusCode"
                    connection.disconnect()
                    Result.failure(Exception(errorMessage))
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Download failed", e)
                Result.failure(e)
            }
        }
    }

    /**
     * 创建HTTP/HTTPS连接
     */
    private fun createConnection(urlString: String): HttpURLConnection {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_CONNECT
        connection.readTimeout = TIMEOUT_READ
        connection.instanceFollowRedirects = true
        
        // HTTPS配置
        if (connection is HttpsURLConnection) {
            try {
                // 创建信任所有证书的SSLContext（用于自签名证书）
                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, trustAllCerts, null)
                connection.sslSocketFactory = sslContext.socketFactory
            } catch (e: Exception) {
                AppLogger.w(TAG, "Could not configure SSL", e)
            }
        }
        
        return connection
    }

    /**
     * 信任所有证书的TrustManager（仅用于测试）
     */
    private val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    })

    /**
     * 规范化URL
     */
    private fun normalizeUrl(url: String): String {
        var normalized = url.trim()
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "https://$normalized"
        }
        return normalized.removeSuffix("/")
    }

    /**
     * 规范化路径
     */
    private fun normalizePath(path: String): String {
        var normalized = path.trim()
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1)
        }
        return "/" + URLEncoder.encode(normalized, "UTF-8")
    }

    /**
     * 计算MD5
     */
    fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}