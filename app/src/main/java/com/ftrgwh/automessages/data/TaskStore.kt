package com.ftrgwh.automessages.data

import android.content.Context
import org.json.JSONArray

/**
 * 任务持久化:SharedPreferences + JSON(任务量小,无需数据库)
 */
object TaskStore {
    private const val PREF = "auto_messages_tasks"
    private const val KEY = "tasks"
    private val lock = Any()

    fun load(context: Context): List<Task> = synchronized(lock) {
        val arr = JSONArray(prefs(context).getString(KEY, "[]") ?: "[]")
        (0 until arr.length()).mapNotNull {
            runCatching { Task.fromJson(arr.getJSONObject(it)) }.getOrNull()
        }
    }

    fun get(context: Context, id: Long): Task? = load(context).firstOrNull { it.id == id }

    fun nextId(context: Context): Long = synchronized(lock) {
        (load(context).maxOfOrNull { it.id } ?: 0L) + 1
    }

    fun upsert(context: Context, task: Task) = synchronized(lock) {
        save(context, load(context).filterNot { it.id == task.id } + task)
    }

    fun remove(context: Context, id: Long) = synchronized(lock) {
        save(context, load(context).filterNot { it.id == id })
    }

    fun setEnabled(context: Context, id: Long, enabled: Boolean) {
        get(context, id)?.let { upsert(context, it.copy(enabled = enabled)) }
    }

    /** 记录一次发送结果;任务不存在时忽略(兼容"立即发送测试"的临时任务) */
    fun updateResult(context: Context, id: Long, result: String) {
        val task = get(context, id) ?: return
        upsert(context, task.copy(lastResult = result, lastResultAt = System.currentTimeMillis()))
    }

    private fun save(context: Context, list: List<Task>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs(context).edit().putString(KEY, arr.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
