package com.example.personalovertimerecord.utils

import android.util.Log
import com.example.personalovertimerecord.BuildConfig

object AppLogger {
    
    private const val TAG_PREFIX = "OvertimeApp"
    
    private val isDebug: Boolean
        get() = com.example.personalovertimerecord.BuildConfig.DEBUG
    
    fun d(message: String) {
        if (isDebug) {
            Log.d(TAG_PREFIX, message)
        }
    }
    
    fun d(tag: String, message: String) {
        if (isDebug) {
            Log.d("$TAG_PREFIX.$tag", message)
        }
    }
    
    fun i(message: String) {
        if (isDebug) {
            Log.i(TAG_PREFIX, message)
        }
    }
    
    fun i(tag: String, message: String) {
        if (isDebug) {
            Log.i("$TAG_PREFIX.$tag", message)
        }
    }
    
    fun w(message: String) {
        if (isDebug) {
            Log.w(TAG_PREFIX, message)
        }
    }
    
    fun w(tag: String, message: String) {
        if (isDebug) {
            Log.w("$TAG_PREFIX.$tag", message)
        }
    }
    
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (isDebug) {
            if (throwable != null) {
                Log.w("$TAG_PREFIX.$tag", message, throwable)
            } else {
                Log.w("$TAG_PREFIX.$tag", message)
            }
        }
    }
    
    fun e(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(TAG_PREFIX, message, throwable)
        } else {
            Log.e(TAG_PREFIX, message)
        }
    }
    
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e("$TAG_PREFIX.$tag", message, throwable)
        } else {
            Log.e("$TAG_PREFIX.$tag", message)
        }
    }
    
    fun sensitive(operation: String, success: Boolean) {
        if (isDebug) {
            Log.d(TAG_PREFIX, "Operation: $operation, Status: ${if(success) "Success" else "Failed"}")
        }
    }
    
    fun sensitive(operation: String, detail: String, success: Boolean) {
        if (isDebug) {
            Log.d(TAG_PREFIX, "Operation: $operation - $detail, Status: ${if(success) "Success" else "Failed"}")
        }
    }
}