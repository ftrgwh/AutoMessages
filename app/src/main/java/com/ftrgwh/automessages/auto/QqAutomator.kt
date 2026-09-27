package com.ftrgwh.automessages.auto

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.ftrgwh.automessages.auto.AutoSendAccessibilityService.Companion.QQ_PACKAGE
import com.ftrgwh.automessages.data.Task

/**
 * 通过无障碍服务自动操作手机QQ。聊天定位【仅支持QQ号】:
 * 依次尝试 mqq:// 与 mqqwpa:// 协议按QQ号直达聊天窗口,
 * 然后把消息写入输入框、点击"发送",并确认输入框被清空。
 *
 * QQ 各版本界面差异较大,这里使用 文本/描述/id 关键字等宽松启发式匹配,
 * 全程限时,失败会返回带原因的结果。
 */
class QqAutomator(private val service: AccessibilityService) {

    data class StepResult(val success: Boolean, val message: String)

    fun send(task: Task): StepResult {
        if (service.packageManager.getLaunchIntentForPackage(QQ_PACKAGE) == null) {
            return StepResult(false, "未安装手机QQ")
        }
        val uin = task.target.trim()
        if (!uin.all { it.isDigit() } || uin.length !in 5..12) {
            return StepResult(false, "仅支持通过QQ号定位聊天,请填写纯数字QQ号")
        }

        // 依次尝试两种QQ协议直达与该QQ号的聊天窗口
        for (uri in listOf(
            "mqq://im/chat?chat_type=wpa&uin=$uin&version=1&src_type=app",
            "mqqwpa://im/chat?chat_type=wpa&uin=$uin",
        )) {
            openUri(uri)
            if (waitChatInput(7_000) != null) return sendInChat(task.message)
        }
        return StepResult(false, "未能通过QQ号 $uin 打开聊天窗口(协议可能被当前QQ版本限制)")
    }

    // ---------- 对外流程 ----------

    private fun openUri(uri: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { service.startActivity(intent) }
    }

    private fun sendInChat(message: String): StepResult {
        val input = pickChatInput() ?: return StepResult(false, "未找到聊天输入框")
        if (!fillText(input, message)) return StepResult(false, "无法写入消息内容")

        val sendBtn = waitSendButton(3_000)
            ?: return StepResult(false, "已填入消息但未找到「发送」按钮")
        if (!clickableClick(sendBtn)) return StepResult(false, "点击「发送」按钮失败")

        // 输入框被清空通常代表发送成功
        val cleared = waitFor(4_000) {
            if (pickChatInput()?.text?.isEmpty() == true) true else null
        } != null
        return StepResult(true, if (cleared) "发送成功" else "已点击发送(未能确认结果)")
    }

    // ---------- 节点查找 ----------

    private fun waitChatInput(timeout: Long): AccessibilityNodeInfo? = waitFor(timeout) { pickChatInput() }

    /** 聊天窗口的消息输入框:优先 id 含 input、非焦点、文本为空的可编辑框 */
    private fun pickChatInput(): AccessibilityNodeInfo? {
        val list = allNodes { it.className == EDIT_TEXT && it.isVisibleToUser }
        if (list.isEmpty()) return null
        val byId = list.filter {
            val id = it.viewIdResourceName ?: ""
            id.contains("input") && !id.contains("search")
        }
        return byId.firstOrNull()
            ?: list.firstOrNull { !it.isFocused && it.text.isNullOrEmpty() }
            ?: list.firstOrNull { it.text.isNullOrEmpty() }
            ?: list.firstOrNull { !it.isFocused }
            ?: list.firstOrNull()
    }

    private fun waitSendButton(timeout: Long): AccessibilityNodeInfo? = waitFor(timeout) {
        val list = allNodes { n -> !n.isEditable && (n.text?.toString() == "发送" || n.text?.toString() == "Send") }
        list.firstOrNull { it.isClickable && it.isVisibleToUser }
            ?: list.firstOrNull { it.isClickable }
            ?: list.firstOrNull { hasClickableParent(it) }
    }

    /** 深度优先收集当前窗口中满足条件的节点 */
    private fun allNodes(pred: (AccessibilityNodeInfo) -> Boolean): List<AccessibilityNodeInfo> {
        val root = service.rootInActiveWindow ?: return emptyList()
        val out = ArrayList<AccessibilityNodeInfo>()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        var guard = 0
        while (stack.isNotEmpty() && guard++ < 5000) {
            val node = stack.removeLast()
            try {
                if (pred(node)) out.add(node)
            } catch (_: Exception) {
                // 个别节点在界面刷新瞬间可能失效,跳过
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { stack.add(it) }
            }
        }
        return out
    }

    private fun fillText(node: AccessibilityNodeInfo, text: String): Boolean {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return true
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    /** 在节点自身或祖先上执行点击 */
    private fun clickableClick(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        var depth = 0
        while (n != null && depth < 8) {
            if (n.isClickable && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            n = n.parent
            depth++
        }
        return false
    }

    private fun hasClickableParent(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        var depth = 0
        while (n != null && depth < 8) {
            if (n.isClickable) return true
            n = n.parent
            depth++
        }
        return false
    }

    /** 轮询等待,直到 pred 返回非 null 或超时 */
    private fun <T> waitFor(timeoutMs: Long, intervalMs: Long = 300, pred: () -> T?): T? {
        val end = SystemClock.elapsedRealtime() + timeoutMs
        while (true) {
            pred()?.let { return it }
            if (SystemClock.elapsedRealtime() >= end) return null
            SystemClock.sleep(intervalMs)
        }
    }

    private companion object {
        const val EDIT_TEXT = "android.widget.EditText"
    }
}
