package com.ftrgwh.automessages.auto

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * 通知使用权服务:实现「纯后台发送」——QQ 对好友消息的通知带"快捷回复"动作,
 * 把消息填进 RemoteInput 并触发该动作,QQ 进程即可在后台完成发送,
 * 全程不需要把 QQ 窗口切到前台。
 *
 * 聊天定位【仅支持QQ号】:在通知的 标题/正文/tag/发送者信息 等字段里
 * 寻找该QQ号;识别不到时后台直发不可用,由 SendDispatcher 决定回退或失败。
 */
class QqNotificationListener : NotificationListenerService() {

    companion object {
        @Volatile
        var instance: QqNotificationListener? = null
            private set
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        super.onListenerDisconnected()
    }

    /** 在当前通知栏里查找"能识别出该QQ号"的QQ会话通知及其「快捷回复」动作 */
    fun findReplyActionByUin(uin: String): Pair<StatusBarNotification, Notification.Action>? {
        val sbns = runCatching { activeNotifications }.getOrNull() ?: return null
        for (sbn in sbns) {
            if (sbn.packageName != AutoSendAccessibilityService.QQ_PACKAGE) continue
            if (!mentionsUin(sbn, uin)) continue
            val replyAction = sbn.notification?.actions
                ?.firstOrNull { !it.remoteInputs.isNullOrEmpty() } ?: continue
            return sbn to replyAction
        }
        return null
    }

    /** 通过 RemoteInput 把消息注入QQ的快捷回复并触发,后台完成发送 */
    fun sendReply(reply: Pair<StatusBarNotification, Notification.Action>, message: String) {
        val action = reply.second
        val remoteInputs = action.remoteInputs ?: return
        val results = Bundle().apply { putCharSequence(remoteInputs[0].resultKey, message) }
        val intent = Intent().addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        RemoteInput.addResultsToIntent(remoteInputs, intent, results)
        action.actionIntent.send(this, 0, intent)
    }

    /** 判断这条QQ通知里是否能识别出该QQ号 */
    private fun mentionsUin(sbn: StatusBarNotification, uin: String): Boolean {
        if (sbn.tag?.contains(uin) == true || sbn.key?.contains(uin) == true) return true
        val extras = sbn.notification?.extras ?: return false
        for (key in arrayOf(
            Notification.EXTRA_TITLE,
            Notification.EXTRA_TEXT,
            Notification.EXTRA_BIG_TEXT,
            Notification.EXTRA_SUB_TEXT,
            Notification.EXTRA_INFO_TEXT,
            Notification.EXTRA_SUMMARY_TEXT,
        )) {
            if (extras.getCharSequence(key)?.contains(uin) == true) return true
        }
        // 会话型通知(MessagingStyle):发送者信息里可能携带QQ号
        val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return false
        for (m in messages) {
            val msg = m as? Notification.MessagingStyle.Message ?: continue
            if (msg.sender?.contains(uin) == true) return true
            if (Build.VERSION.SDK_INT >= 28) {
                val person = msg.senderPerson ?: continue
                if (person.key?.contains(uin) == true || person.uri?.contains(uin) == true) return true
            }
        }
        return false
    }
}
