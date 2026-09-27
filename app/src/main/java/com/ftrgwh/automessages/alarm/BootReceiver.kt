package com.ftrgwh.automessages.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 开机后重排所有任务的闹钟 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Scheduler.syncAll(context)
        }
    }
}
