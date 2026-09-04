package com.example.personalovertimerecord.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.personalovertimerecord.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全局异常处理器
 * 捕获应用中的未处理异常，防止应用崩溃
 */
object GlobalExceptionHandler {

    internal const val TAG = "GlobalException"
    private var isInitialized = false
    
    // 主线程Handler，用于安全地处理异常
    internal val mainHandler = Handler(Looper.getMainLooper())
    
    // 异常回调（遍历时先快照，避免崩溃线程与注册线程并发修改）
    private val exceptionCallbacks = mutableListOf<(Thread, Throwable) -> Unit>()

    // 崩溃日志目录（应用私有目录，最多保留 CRASH_LOG_KEEP 份）
    private const val CRASH_LOG_DIR = "crash_logs"
    private const val CRASH_LOG_KEEP = 20
    private var appContext: Context? = null

    /**
     * 初始化全局异常处理器
     * 应该在 Application.onCreate 中调用
     */
    fun init(context: Context) {
        if (isInitialized) return
        appContext = context.applicationContext
        
        // 设置默认的未捕获异常处理器
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            handleException(thread, throwable)
            // 调用系统默认处理器
            defaultHandler?.uncaughtException(thread, throwable)
        }
        
        isInitialized = true
        AppLogger.i(TAG, "Global exception handler initialized")
    }

    /**
     * 处理异常
     */
    private fun handleException(thread: Thread, throwable: Throwable) {
        // 记录异常信息
        val errorMessage = buildString {
            append("=== UNCAUGHT EXCEPTION ===\n")
            append("Thread: ${thread.name}\n")
            append("Exception: ${throwable.javaClass.simpleName}\n")
            append("Message: ${throwable.message}\n")
            append("StackTrace:\n")
            throwable.stackTrace.take(10).forEach { stackTrace ->
                append("  at ${stackTrace.className}.${stackTrace.methodName}(${stackTrace.fileName}:${stackTrace.lineNumber})\n")
            }
            append("==========================")
        }
        
        AppLogger.e(TAG, errorMessage, throwable)
        
        // 崩溃堆栈落盘（release 也保留，便于用户反馈定位）
        writeCrashToFile(thread, throwable)
        
        // 执行注册的回调（快照遍历，避免并发修改异常）
        exceptionCallbacks.toList().forEach { callback ->
            try {
                callback(thread, throwable)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Exception in callback", e)
            }
        }
    }

    /**
     * 把崩溃信息写入应用私有目录 crash_logs/ 下（文件名含时间戳，天然按时间排序去重）。
     * 只记录崩溃本身，不包含业务数据；写失败静默忽略，不影响崩溃流程。
     */
    private fun writeCrashToFile(thread: Thread, throwable: Throwable) {
        val context = appContext ?: return
        try {
            val dir = File(context.filesDir, CRASH_LOG_DIR)
            dir.mkdirs()
            val now = Date()
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(now)
            val displayTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(now)

            val content = buildString {
                append("=== UNCAUGHT EXCEPTION ===\n")
                append("Time:    ").append(displayTime).append('\n')
                append("Version: ").append(BuildConfig.VERSION_NAME)
                    .append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
                append("Thread:  ").append(thread.name).append('\n')
                append("Exception: ").append(throwable.javaClass.name).append('\n')
                append("Message:  ").append(throwable.message).append('\n')
                append("StackTrace:\n")
                // 限制长度，避免超大文件
                throwable.stackTrace.take(60).forEach { append("  at ").append(it).append('\n') }
                var cause = throwable.cause
                var depth = 0
                while (cause != null && depth < 5) {
                    append("Caused by: ").append(cause).append('\n')
                    cause.stackTrace.take(40).forEach { append("  at ").append(it).append('\n') }
                    cause = cause.cause
                    depth++
                }
                append("================================\n")
            }
            File(dir, "crash_$timeStamp.log").writeText(content)

            // 保留最近 CRASH_LOG_KEEP 份，防止无限增长
            dir.listFiles()
                ?.sortedByDescending { it.lastModified() }
                ?.drop(CRASH_LOG_KEEP)
                ?.forEach { it.delete() }
        } catch (e: Exception) {
            AppLogger.e(TAG, "写入崩溃日志失败", e)
        }
    }

    /**
     * 注册异常回调
     */
    fun registerExceptionCallback(callback: (Thread, Throwable) -> Unit) {
        exceptionCallbacks.add(callback)
    }

    /**
     * 注销异常回调
     */
    fun unregisterExceptionCallback(callback: (Thread, Throwable) -> Unit) {
        exceptionCallbacks.remove(callback)
    }

    /**
     * 安全执行代码块
     * 如果发生异常，返回 null 或指定的默认值
     */
    fun <T> safeCall(defaultValue: T? = null, block: () -> T?): T? {
        return try {
            block()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Safe call failed", e)
            defaultValue
        }
    }

    /**
     * 安全执行代码块，返回 Result
     */
    fun <T> safeResult(block: () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (e: Exception) {
            AppLogger.e(TAG, "Safe result failed", e)
            Result.failure(e)
        }
    }

    /**
     * 安全执行异步代码块（主线程）
     */
    fun safeRun(block: () -> Unit) {
        mainHandler.post {
            try {
                block()
            } catch (e: Exception) {
                AppLogger.e(TAG, "Safe run failed", e)
            }
        }
    }

    /**
     * 安全执行带回调的代码块
     */
    fun <T> safeExecute(
        onSuccess: (T) -> Unit,
        onError: (Exception) -> Unit = {},
        block: () -> T
    ) {
        try {
            val result = block()
            mainHandler.post { onSuccess(result) }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Safe execute failed", e)
            mainHandler.post { onError(e) }
        }
    }
}

/**
 * 空安全扩展函数
 */
object NullSafety {
    
    /**
     * 安全执行 let（不为 null 才执行）
     */
    inline fun <T : Any, R> T?.safeLet(block: (T) -> R): R? {
        return this?.let(block)
    }

    /**
     * 安全执行 let（为 null 时执行替代逻辑）
     */
    inline fun <T : Any, R> T?.safeLetOrElse(
        block: (T) -> R,
        orElse: () -> R
    ): R {
        return this?.let(block) ?: orElse()
    }

    /**
     * 安全获取字符串（null 或空时返回默认值）
     */
    fun String?.orDefault(default: String = ""): String {
        return this?.takeIf { it.isNotBlank() } ?: default
    }

    /**
     * 安全获取数字（null 或无效时返回默认值）
     */
    fun String?.toDoubleOrDefault(default: Double = 0.0): Double {
        return this?.toDoubleOrNull() ?: default
    }

    fun String?.toIntOrDefault(default: Int = 0): Int {
        return this?.toIntOrNull() ?: default
    }

    fun String?.toLongOrDefault(default: Long = 0L): Long {
        return this?.toLongOrNull() ?: default
    }

    /**
     * 安全比较字符串（忽略大小写）
     */
    fun String?.equalsIgnoreCase(other: String?): Boolean {
        return this?.equals(other, ignoreCase = true) == true
    }

    /**
     * 安全列表操作
     */
    fun <T> List<T>?.orEmpty(): List<T> {
        return this ?: emptyList()
    }

    fun <T> List<T>?.isNullOrEmpty(): Boolean {
        return this == null || this.isEmpty()
    }

    /**
     * 安全 Map 操作
     */
    fun <K, V> Map<K, V>?.orEmpty(): Map<K, V> {
        return this ?: emptyMap()
    }

    /**
     * 安全获取 Map 值
     */
    fun <K, V> Map<K, V>.getOrDefaultSafe(key: K, default: V): V {
        return this[key] ?: default
    }
}

/**
 * 重试机制
 */
object RetryUtils {
    
    /**
     * 带重试的执行
     * @param maxRetries 最大重试次数
     * @param delayMillis 重试间隔（毫秒）
     * @param block 执行代码块
     */
    fun <T> retry(
        maxRetries: Int = 3,
        delayMillis: Long = 1000,
        exponentialBackoff: Boolean = true,
        block: () -> T
    ): Result<T> {
        var currentDelay = delayMillis
        var lastException: Exception? = null
        
        repeat(maxRetries + 1) { attempt ->
            try {
                return Result.success(block())
            } catch (e: Exception) {
                lastException = e
                AppLogger.w("Retry", "Attempt $attempt failed: ${e.message}")
                
                if (attempt < maxRetries) {
                    Thread.sleep(currentDelay)
                    if (exponentialBackoff) {
                        currentDelay *= 2
                    }
                }
            }
        }
        
        return Result.failure(lastException ?: RuntimeException("Unknown error during retry"))
    }
}

/**
 * 资源清理接口
 */
interface Cleanupable {
    fun cleanup()
}

/**
 * 使用后自动清理的资源封装
 */
class AutoCloseableResource<T : AutoCloseable>(
    private val creator: () -> T
) {
    private var resource: T? = null
    
    fun get(): T? {
        if (resource == null) {
            resource = creator()
        }
        return resource
    }
    
    fun close() {
        resource?.close()
        resource = null
    }
}