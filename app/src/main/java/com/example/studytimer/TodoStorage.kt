package com.example.studytimer

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * 待办事项数据类
 * @param id 唯一ID（时间戳毫秒）
 * @param content 待办内容
 * @param isDone 是否已完成
 * @param date 创建日期 yyyy-MM-dd
 */
data class TodoItem(
    val id: Long,
    val content: String,
    val isDone: Boolean = false,
    val date: String = "",
    val isDaily: Boolean = false,  // 是否每日待办
    val dueDate: String = ""       // 截止日期 yyyy-MM-dd，空表示未设置
)

/**
 * 待办事项本地存储（SharedPreferences + JSON）
 */
object TodoStorage {

    private const val PREFS_NAME = "study_timer_data"
    private const val KEY_TODOS = "todo_items"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** 获取所有待办（未完成在前，已完成在后） */
    fun getAll(context: Context): List<TodoItem> {
        val jsonStr = getPrefs(context).getString(KEY_TODOS, "[]") ?: "[]"
        val arr = JSONArray(jsonStr)
        val result = mutableListOf<TodoItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            result.add(TodoItem(
                id = obj.getLong("id"),
                content = obj.getString("content"),
                isDone = obj.getBoolean("isDone"),
                date = obj.optString("date", ""),
                isDaily = obj.optBoolean("isDaily", false),
                dueDate = obj.optString("dueDate", "")
            ))
        }
        // 排序：未完成在前，已完成在后
        return result.sortedBy { if (it.isDone) 1 else 0 }
    }

    /** 添加待办 */
    fun add(context: Context, item: TodoItem) {
        val all = getAll(context).toMutableList()
        all.add(0, item)  // 新项放最前
        saveAll(context, all)
    }

    /** 标记为已完成（移到列表底部） */
    fun markDone(context: Context, id: Long) {
        val all = getAll(context).toMutableList()
        val idx = all.indexOfFirst { it.id == id }
        if (idx >= 0) {
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(java.util.Date())
            all[idx] = all[idx].copy(isDone = true, date = today)
            saveAll(context, all)
        }
    }

    /** 删除待办 */
    fun delete(context: Context, id: Long) {
        val all = getAll(context).filter { it.id != id }
        saveAll(context, all)
    }

    /** 每日重置：将过期的每日待办恢复为未完成 */
    fun resetDailyIfNeeded(context: Context) {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date())
        val all = getAll(context).toMutableList()
        var changed = false
        for (i in all.indices) {
            val item = all[i]
            if (item.isDaily && item.isDone && item.date != today) {
                all[i] = item.copy(isDone = false)
                changed = true
            }
        }
        if (changed) saveAll(context, all)
    }

    private fun saveAll(context: Context, items: List<TodoItem>) {
        val arr = JSONArray()
        for (item in items) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("content", item.content)
            obj.put("isDone", item.isDone)
            obj.put("date", item.date)
            obj.put("isDaily", item.isDaily)
            if (item.dueDate.isNotEmpty()) obj.put("dueDate", item.dueDate)
            arr.put(obj)
        }
        getPrefs(context).edit().putString(KEY_TODOS, arr.toString()).apply()
    }
}
