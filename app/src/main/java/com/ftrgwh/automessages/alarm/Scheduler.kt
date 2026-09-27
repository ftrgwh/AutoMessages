package com.ftrgwh.automessages.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ftrgwh.automessages.data.Repeat
import com.ftrgwh.automessages.data.Task
import com.ftrgwh.automessages.data.TaskStore
import com.ftrgwh.automessages.data.nextFireTime

/** AlarmManager 精确闹钟调度(Android 12+ 的 SCHEDULE_EXACT_ALARM 已处理) */
object Scheduler {
    const val EXTRA_TASK_ID = "task_id"

    private fun pending(context: Context, taskId: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).putExtra(EXTRA_TASK_ID, taskId)
        return PendingIntent.getBroadcast(
            context, taskId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** 是否拥有精确闹钟权限(Android 12 以下恒为 true) */
    fun canExact(context: Context): Boolean {
        val am = context.getSystemService(AlarmManager::class.java) ?: return false
        return Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
    }

    /** 为单个任务设定下一次闹钟 */
    fun scheduleTask(context: Context, task: Task) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val at = nextFireTime(task) ?: return
        if (canExact(context)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context, task.id))
        } else {
            // 未授权精确闹钟时退化为窗口闹钟,允许几分钟内的误差
            am.setWindow(AlarmManager.RTC_WAKEUP, at, 60_000L, pending(context, task.id))
        }
    }

    fun cancel(context: Context, taskId: Long) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pending(context, taskId))
    }

    /** 对齐所有任务:重设闹钟;停用已过期的单次任务 */
    fun syncAll(context: Context) {
        TaskStore.load(context).forEach { task ->
            if (!task.enabled) {
                cancel(context, task.id)
                return@forEach
            }
            val next = nextFireTime(task)
            if (next == null) {
                if (task.repeat == Repeat.ONCE) TaskStore.setEnabled(context, task.id, false)
                cancel(context, task.id)
            } else {
                scheduleTask(context, task)
            }
        }
    }
}
