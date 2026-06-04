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

    /**
     * 获取 SharedPreferences 实例
     */
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 保存一条计时记录
     * @param context 上下文
     * @param record 要保存的计时记录
     */
    fun saveRecord(context: Context, record: TimerRecord) {
        // 1. 先从 SharedPreferences 中取出已有的记录 JSON 字符串
        val prefs = getPrefs(context)
        val jsonStr = prefs.getString(KEY_RECORDS, "[]") ?: "[]"

        // 2. 解析为 JSONArray
        val jsonArray = JSONArray(jsonStr)

        // 3. 把新记录转成 JSONObject 并追加到数组中
        val newRecordJson = JSONObject().apply {
            put("subjectGroup", record.subjectGroup)
            put("subject", record.subject)
            put("date", record.date)
            put("durationSeconds", record.durationSeconds)
        }
        jsonArray.put(newRecordJson)

        // 4. 写回 SharedPreferences
        prefs.edit().putString(KEY_RECORDS, jsonArray.toString()).apply()
    }

    /**
     * 获取所有计时记录
     * @param context 上下文
     * @return 计时记录列表，按保存顺序排列
     */
    fun getAllRecords(context: Context): List<TimerRecord> {
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
