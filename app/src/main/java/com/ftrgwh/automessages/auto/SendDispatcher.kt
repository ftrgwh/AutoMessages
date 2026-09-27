package com.ftrgwh.automessages.auto

import android.content.Context
import com.ftrgwh.automessages.data.Task
import com.ftrgwh.automessages.data.TaskStore
import com.ftrgwh.automessages.util.Notify

/**
 * 发送编排:
 * 1. 优先「通知栏后台直发」——不打开QQ(需通知使用权 + 该联系人有未读消息通知);
 * 2. 没有通知时,回退到「无障碍前台操作QQ」;
 * 3. 任务勾选「仅后台直发」时绝不打开QQ,没有通知则直接失败。
 */
object SendDispatcher {

    fun dispatch(context: Context, task: Task) {
        val listener = QqNotificationListener.instance
        if (listener != null) {
            val reply = listener.findReplyActionByUin(task.target)
            if (reply != null) {
                val sent = runCatching { listener.sendReply(reply, task.message) }.isSuccess
                finish(context, task, sent, if (sent) "已通过通知栏后台直发(未打开QQ)" else "通知栏直发调用失败")
                return
            }
            if (task.backgroundOnly) {
                finish(
                    context, task, false,
                    "通知栏中没有能识别QQ号 ${task.target} 的未读消息:后台直发需要该联系人有未读通知且能识别出QQ号,或关闭「仅后台直发」",
                )
                return
            }
        } else if (task.backgroundOnly) {
            finish(context, task, false, "未开启「通知使用权」,无法后台直发")
            return
        }

        val ui = AutoSendAccessibilityService.instance
        if (ui != null) {
            ui.runUiSend(task)
            return
        }
        finish(context, task, false, "「通知使用权」与「无障碍服务」均未开启,无法发送")
    }

    fun finish(context: Context, task: Task, ok: Boolean, message: String) {
        TaskStore.updateResult(context, task.id, (if (ok) "成功:" else "失败:") + message)
        Notify.post(
            context, ok,
            if (ok) "定时消息已发送" else "定时消息发送失败",
            "${task.target} · $message",
        )
    }
}
