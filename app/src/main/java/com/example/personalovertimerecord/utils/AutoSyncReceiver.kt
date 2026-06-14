package com.example.personalovertimerecord.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 自动同步广播接收器
 * 用于接收定时器触发并执行同步任务
 */
class AutoSyncReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        // 初始化自动同步管理器
        AutoSyncManager.init(context)
        
        // 执行同步
        AutoSyncManager.performSync(context)
    }
}