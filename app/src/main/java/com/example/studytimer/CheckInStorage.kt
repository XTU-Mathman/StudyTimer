package com.example.studytimer

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 打卡项目
 */
data class CheckInItem(
    val id: Long,
    val name: String,
    val isPreset: Boolean = false  // 是否为预设项目（起床/睡觉）
)

/**
 * 打卡记录
 */
data class CheckInRecord(
    val itemId: Long,
    val date: String,     // yyyy-MM-dd
    val time: String      // HH:mm:ss
)

/**
 * 自律打卡存储
 */
object CheckInStorage {

    private const val PREFS_NAME = "study_timer_profile"
    private const val KEY_ITEMS = "checkin_items"
    private const val KEY_RECORDS = "checkin_records"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** 获取所有打卡项目 */
    fun getItems(context: Context): List<CheckInItem> {
        val jsonStr = getPrefs(context).getString(KEY_ITEMS, null)
        return if (jsonStr != null) {
            parseItems(jsonStr)
        } else {
            // 首次 → 预设起床和睡觉
            val defaults = listOf(
                CheckInItem(1L, "起床打卡", isPreset = true),
                CheckInItem(2L, "睡觉打卡", isPreset = true)
            )
            saveItems(context, defaults)
            defaults
        }
    }

    /** 添加打卡项目 */
    fun addItem(context: Context, name: String) {
        val items = getItems(context).toMutableList()
        val newId = if (items.isEmpty()) 1L else items.maxOf { it.id } + 1
        items.add(CheckInItem(newId, name))
        saveItems(context, items)
    }

    /** 删除打卡项目 */
    fun deleteItem(context: Context, id: Long) {
        val items = getItems(context).filter { it.id != id }
        saveItems(context, items)
    }

    /** 获取今日打卡记录（某项目是否已打卡） */
    fun getTodayRecord(context: Context, itemId: Long): CheckInRecord? {
        val today = todayStr()
        return getRecords(context).find { it.itemId == itemId && it.date == today }
    }

    /** 执行打卡 */
    fun checkIn(context: Context, itemId: Long) {
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val record = CheckInRecord(
            itemId = itemId,
            date = todayStr(),
            time = timeFormat.format(Date())
        )
        val records = getRecords(context).toMutableList()
        // 移除今日同项目旧记录（防止重复）
        records.removeAll { it.itemId == itemId && it.date == todayStr() }
        records.add(record)
        saveRecords(context, records)
    }

    /** 获取今日所有打卡记录 */
    fun getTodayRecords(context: Context): List<CheckInRecord> {
        return getRecordsByDate(context, todayStr())
    }

    /** 获取指定日期的打卡记录 */
    fun getRecordsByDate(context: Context, date: String): List<CheckInRecord> {
        return getRecords(context).filter { it.date == date }
    }

    /** 获取所有打卡记录 */
    private fun getRecords(context: Context): MutableList<CheckInRecord> {
        val jsonStr = getPrefs(context).getString(KEY_RECORDS, "[]") ?: "[]"
        val arr = JSONArray(jsonStr)
        val result = mutableListOf<CheckInRecord>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            result.add(CheckInRecord(
                itemId = obj.getLong("itemId"),
                date = obj.getString("date"),
                time = obj.getString("time")
            ))
        }
        return result
    }

    private val saveLock = Any()

    private fun saveRecords(context: Context, records: List<CheckInRecord>) {
        synchronized(saveLock) {
            val arr = JSONArray()
            for (r in records) {
                val obj = JSONObject()
                obj.put("itemId", r.itemId)
                obj.put("date", r.date)
                obj.put("time", r.time)
                arr.put(obj)
            }
            getPrefs(context).edit().putString(KEY_RECORDS, arr.toString()).commit()
        }
    }

    private fun saveItems(context: Context, items: List<CheckInItem>) {
        val arr = JSONArray()
        for (item in items) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("name", item.name)
            obj.put("isPreset", item.isPreset)
            arr.put(obj)
        }
        getPrefs(context).edit().putString(KEY_ITEMS, arr.toString()).apply()
    }

    private fun parseItems(jsonStr: String): List<CheckInItem> {
        val arr = JSONArray(jsonStr)
        val result = mutableListOf<CheckInItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            result.add(CheckInItem(
                id = obj.getLong("id"),
                name = obj.getString("name"),
                isPreset = obj.optBoolean("isPreset", false)
            ))
        }
        return result
    }

    private fun todayStr(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }
}
