package com.example.personalovertimerecord.utils

import android.util.Log
import com.example.personalovertimerecord.BuildConfig

object AppLogger {
    
    private const val TAG = "OvertimeApp"
    
    private val isDebug: Boolean
        get() = com.example.personalovertimerecord.BuildConfig.DEBUG
    
    fun d(message: String) {
        if (isDebug) {
            Log.d(TAG, message)
        }
    }
    
    fun i(message: String) {
        if (isDebug) {
            Log.i(TAG, message)
        }
    }
    
    fun w(message: String) {
        if (isDebug) {
            Log.w(TAG, message)
        }
    }
    
    fun e(message: String, throwable: Throwable? = null) {
        if (isDebug) {
            if (throwable != null) {
                Log.e(TAG, message, throwable)
            } else {
                Log.e(TAG, message)
            }
        }
    }
    
    fun sensitive(operation: String, success: Boolean) {
        if (isDebug) {
            Log.d(TAG, "Operation: $operation, Status: ${if(success) "Success" else "Failed"}")
        }
    }
    
    fun sensitive(operation: String, detail: String, success: Boolean) {
        if (isDebug) {
            Log.d(TAG, "Operation: $operation - $detail, Status: ${if(success) "Success" else "Failed"}")
        }
    }
}
