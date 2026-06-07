package com.example.studytimer

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * 简易本地存储工具类
 * 使用 SharedPreferences + JSON 存储计时记录
 * 不引入第三方库，全部使用 Android 原生 API
 */
object StorageHelper {

    // SharedPreferences 的文件名
    private const val PREFS_NAME = "study_timer_data"

    // 存储计时记录的 key
    private const val KEY_RECORDS = "timer_records"

    // 读-改-写锁
    private val lock = Any()

    /**
     * 获取 SharedPreferences 实例
     */
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 保存一条计时记录
     */
    fun saveRecord(context: Context, record: TimerRecord) {
        synchronized(lock) {
            val prefs = getPrefs(context)
            val jsonStr = prefs.getString(KEY_RECORDS, "[]") ?: "[]"
            val jsonArray = JSONArray(jsonStr)
            val newRecordJson = JSONObject().apply {
                put("subjectGroup", record.subjectGroup)
                put("subject", record.subject)
                put("date", record.date)
                put("durationSeconds", record.durationSeconds)
            }
            jsonArray.put(newRecordJson)
            prefs.edit().putString(KEY_RECORDS, jsonArray.toString()).commit()
        }
    }

    /**
     * 获取所有计时记录
     */
    fun getAllRecords(context: Context): List<TimerRecord> {
        synchronized(lock) {
            val prefs = getPrefs(context)
            val jsonStr = prefs.getString(KEY_RECORDS, "[]") ?: "[]"
            val jsonArray = JSONArray(jsonStr)
            val records = mutableListOf<TimerRecord>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                records.add(
                    TimerRecord(
                        subjectGroup = obj.getString("subjectGroup"),
                        subject = obj.getString("subject"),
                        date = obj.getString("date"),
                        durationSeconds = obj.getLong("durationSeconds")
                    )
                )
            }
            return records
        }
    }

    /**
     * 清理 90 天前的旧记录，防止 JSON 无限膨胀
     */
    fun cleanupOldRecords(context: Context) {
        synchronized(lock) {
            val prefs = getPrefs(context)
            val jsonStr = prefs.getString(KEY_RECORDS, "[]") ?: "[]"
            val jsonArray = JSONArray(jsonStr)
            val cutoff = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(java.util.Date(System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000))
            val filtered = JSONArray()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                if (obj.getString("date") >= cutoff) {
                    filtered.put(obj)
                }
            }
            if (filtered.length() < jsonArray.length()) {
                prefs.edit().putString(KEY_RECORDS, filtered.toString()).commit()
            }
        }
    }
}
