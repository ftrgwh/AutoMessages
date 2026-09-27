package com.ftrgwh.automessages.auto

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import com.ftrgwh.automessages.data.Task
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 常驻无障碍服务:「前台回退」路径的执行者——亮屏 → 启动QQ → 自动填消息发送。
 * 仅当「通知栏后台直发」不可用时才会走这条路径。
 * 需要用户在系统设置中手动开启,并同时授予悬浮窗权限(用于后台唤起QQ)。
 */
class AutoSendAccessibilityService : AccessibilityService() {

    companion object {
        const val QQ_PACKAGE = "com.tencent.mobileqq"

        @Volatile
        var instance: AutoSendAccessibilityService? = null
            private set
    }

    private var handler: Handler? = null
    private var thread: HandlerThread? = null
    private val running = AtomicBoolean(false)

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        thread = HandlerThread("auto-send").also { it.start() }
        handler = Handler(thread!!.looper)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        thread?.quitSafely()
        thread = null
        handler = null
        super.onDestroy()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    /** 无障碍前台路径入口(由 SendDispatcher 调用) */
    fun runUiSend(task: Task) {
        if (!running.compareAndSet(false, true)) {
            SendDispatcher.finish(this, task, false, "上一次发送尚未结束")
            return
        }
        val posted = handler?.post {
            try {
                execute(task)
            } catch (e: Exception) {
                SendDispatcher.finish(this, task, false, "异常:${e.message ?: e.javaClass.simpleName}")
            } finally {
                running.set(false)
            }
        }
        if (posted != true) running.set(false)
    }

    private fun execute(task: Task) {
        val pm = getSystemService(PowerManager::class.java)
        @Suppress("DEPRECATION")
        val wakeLock = pm.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            "automessages:send",
        )
        if (!pm.isInteractive) wakeLock.acquire(60_000L)

        val km = getSystemService(KeyguardManager::class.java)
        if (km.isKeyguardLocked) {
            SendDispatcher.finish(
                this, task, false,
                "设备处于锁屏状态,无法自动操作QQ。请保持屏幕解锁(或在系统设置中关闭锁屏密码)",
            )
            return
        }

        val result = QqAutomator(this).send(task)
        SendDispatcher.finish(this, task, result.success, result.message)
    }
}
