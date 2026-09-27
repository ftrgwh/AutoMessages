package com.ftrgwh.automessages.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ftrgwh.automessages.auto.SendDispatcher
import com.ftrgwh.automessages.data.Repeat
import com.ftrgwh.automessages.data.TaskStore

/** 闹钟触发:排下一次 + 交给 SendDispatcher(后台直发优先,无障碍回退) */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(Scheduler.EXTRA_TASK_ID, -1L)
        if (id <= 0) return
        val task = TaskStore.get(context, id) ?: return
        if (!task.enabled) return

        if (task.repeat == Repeat.ONCE) {
            // 单次任务发出后立即停用,避免时钟误差导致重复触发
            TaskStore.setEnabled(context, id, false)
        } else {
            Scheduler.scheduleTask(context, task)
        }

        SendDispatcher.dispatch(context, task)
    }
}
