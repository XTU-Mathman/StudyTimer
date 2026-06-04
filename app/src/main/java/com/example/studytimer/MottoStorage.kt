package com.example.studytimer

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

/**
 * 格言管理：预置 20 条 + 用户自定义增删 + 计时结束时自动切换
 */
object MottoStorage {

    private const val PREFS_NAME = "study_timer_profile"
    private const val KEY_MOTTOS = "mottos"
    private const val KEY_INDEX = "motto_index"

    // 预置 20 条自律/努力格言
    private val DEFAULT_MOTTOS = listOf(
        "自律即自由",
        "今天不走，明天要跑",
        "你有多自律，就有多自由",
        "每一个不曾起舞的日子，都是对生命的辜负",
        "比你优秀的人比你还努力",
        "不积跬步，无以至千里",
        "将来的你，一定会感谢现在拼命的自己",
        "越努力，越幸运",
        "自律是最高级的自由",
        "今天的汗水，是明天的勋章",
        "天道酬勤，功不唐捐",
        "博观而约取，厚积而薄发",
        "宝剑锋从磨砺出，梅花香自苦寒来",
        "千里之行，始于足下",
        "业精于勤，荒于嬉",
        "没有天赋，就用勤奋来凑",
        "坚持就是胜利",
        "有志者，事竟成",
        "路虽远，行则将至",
        "星光不问赶路人，时光不负有心人"
    )

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** 获取所有格言 */
    fun getAll(context: Context): List<String> {
        val jsonStr = getPrefs(context).getString(KEY_MOTTOS, null)
        return if (jsonStr != null) {
            val arr = JSONArray(jsonStr)
            (0 until arr.length()).map { arr.getString(it) }
        } else {
            // 首次使用 → 写入预置数据
            saveAll(context, DEFAULT_MOTTOS.toMutableList())
            DEFAULT_MOTTOS
        }
    }

    /** 获取当前格言（根据 index） */
    fun getCurrent(context: Context): String {
        val all = getAll(context)
        if (all.isEmpty()) return ""
        val index = getCurrentIndex(context) % all.size
        return all[index]
    }

    /** 切换到下一条格言（计时结束时调用） */
    fun moveToNext(context: Context) {
        val all = getAll(context)
        if (all.isEmpty()) return
        val newIndex = (getCurrentIndex(context) + 1) % all.size
        getPrefs(context).edit().putInt(KEY_INDEX, newIndex).apply()
    }

    /** 获取当前索引 */
    private fun getCurrentIndex(context: Context): Int {
        return getPrefs(context).getInt(KEY_INDEX, 0)
    }

    /** 添加格言 */
    fun add(context: Context, motto: String) {
        val all = getAll(context).toMutableList()
        all.add(motto)
        saveAll(context, all)
    }

    /** 删除格言 */
    fun delete(context: Context, index: Int) {
        val all = getAll(context).toMutableList()
        if (index in all.indices) {
            all.removeAt(index)
            saveAll(context, all)
        }
    }

    /** 编辑格言 */
    fun update(context: Context, index: Int, newText: String) {
        val all = getAll(context).toMutableList()
        if (index in all.indices) {
            all[index] = newText
            saveAll(context, all)
        }
    }

    private fun saveAll(context: Context, items: List<String>) {
        val arr = JSONArray()
        for (item in items) arr.put(item)
        getPrefs(context).edit().putString(KEY_MOTTOS, arr.toString()).apply()
    }
}
