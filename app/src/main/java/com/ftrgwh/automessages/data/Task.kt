package com.ftrgwh.automessages.data

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 重复方式 */
enum class Repeat(val label: String) {
    ONCE("单次"),
    DAILY("每天"),
    WEEKLY("每周");

    companion object {
        fun from(value: Int): Repeat = entries.firstOrNull { it.ordinal == value } ?: ONCE
    }
}

/** 1=周一 … 7=周日 */
val WEEKDAY_NAMES = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

data class Task(
    val id: Long = 0,
    /** 对方QQ号(纯数字)或备注/昵称 */
    val target: String = "",
    val message: String = "",
    val hour: Int = 9,
    val minute: Int = 0,
    val repeat: Repeat = Repeat.ONCE,
    /** 仅 WEEKLY 时使用 */
    val weekday: Int = 1,
    /** 仅 ONCE 时使用的绝对触发时间戳 */
    val onceAt: Long = 0,
    /** 仅后台直发:通过通知栏快捷回复发送,绝不打开QQ */
    val backgroundOnly: Boolean = false,
    val enabled: Boolean = true,
    val lastResult: String? = null,
    val lastResultAt: Long = 0,
) {
    fun timeText(): String = "%02d:%02d".format(hour, minute)

    fun scheduleText(): String = when (repeat) {
        Repeat.ONCE -> "单次 " + SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(onceAt))
        Repeat.DAILY -> "每天 ${timeText()}"
        Repeat.WEEKLY -> "每${WEEKDAY_NAMES.getOrElse(weekday - 1) { "?" }} ${timeText()}"
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("target", target)
        put("message", message)
        put("hour", hour)
        put("minute", minute)
        put("repeat", repeat.ordinal)
        put("weekday", weekday)
        put("onceAt", onceAt)
        put("backgroundOnly", backgroundOnly)
        put("enabled", enabled)
        put("lastResult", lastResult ?: "")
        put("lastResultAt", lastResultAt)
    }

    companion object {
        fun fromJson(o: JSONObject): Task = Task(
            id = o.getLong("id"),
            target = o.optString("target"),
            message = o.optString("message"),
            hour = o.optInt("hour", 9),
            minute = o.optInt("minute", 0),
            repeat = Repeat.from(o.optInt("repeat", 0)),
            weekday = o.optInt("weekday", 1),
            onceAt = o.optLong("onceAt", 0),
            backgroundOnly = o.optBoolean("backgroundOnly", false),
            enabled = o.optBoolean("enabled", true),
            lastResult = o.optString("lastResult").ifEmpty { null },
            lastResultAt = o.optLong("lastResultAt", 0),
        )
    }
}

/** 计算下一次触发时间;无法触发(如已过期的单次任务)返回 null */
fun nextFireTime(task: Task, now: Long = System.currentTimeMillis()): Long? {
    val cal = Calendar.getInstance()
    return when (task.repeat) {
        Repeat.ONCE -> if (task.onceAt > now) task.onceAt else null

        Repeat.DAILY -> {
            cal.timeInMillis = now
            cal.set(Calendar.HOUR_OF_DAY, task.hour)
            cal.set(Calendar.MINUTE, task.minute)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
            cal.timeInMillis
        }

        Repeat.WEEKLY -> {
            // 本项目 weekday: 1=周一..7=周日;Calendar.DAY_OF_WEEK: 1=周日..7=周六
            val targetDow = if (task.weekday == 7) Calendar.SUNDAY else task.weekday + 1
            cal.timeInMillis = now
            cal.set(Calendar.HOUR_OF_DAY, task.hour)
            cal.set(Calendar.MINUTE, task.minute)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            var diff = targetDow - cal.get(Calendar.DAY_OF_WEEK)
            if (diff < 0 || (diff == 0 && cal.timeInMillis <= now)) diff += 7
            cal.add(Calendar.DAY_OF_YEAR, diff)
            cal.timeInMillis
        }
    }
}
