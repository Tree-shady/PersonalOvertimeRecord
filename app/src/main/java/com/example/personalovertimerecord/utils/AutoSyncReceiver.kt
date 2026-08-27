package com.example.personalovertimerecord.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 自动同步广播接收器
 * 用于接收定时器触发并执行同步任务
 *
 * 使用 goAsync()/finish() 保持进程存活，确保协程中的同步逻辑执行完成，
 * 避免 BroadcastReceiver.onReceive 返回后进程被系统回收导致同步中断。
 */
class AutoSyncReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // 初始化自动同步管理器
        AutoSyncManager.init(context)

        // 保持进程存活直至异步同步完成
        val pendingResult = goAsync()
        AutoSyncManager.performSync(context) { _, _ ->
            pendingResult.finish()
        }
    }
}
