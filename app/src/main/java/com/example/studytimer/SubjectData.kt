package com.example.studytimer

import android.content.Context
import org.json.JSONArray

/**
 * 科目集数据类
 * @param name 科目集名称，如"数学"、"英语"
 * @param subjects 该科目集下的具体科目列表
 */
data class SubjectGroup(
    val name: String,
    val subjects: MutableList<String>,
    val color: String = ""  // 十六进制颜色，如 "#6BA4D1"，空=自动分配
)

/**
 * 计时记录数据类
 * @param subjectGroup 科目集名称
 * @param subject 具体科目名称
 * @param date 记录日期，格式 yyyy-MM-dd
 * @param durationSeconds 计时时长（秒）
 */
data class TimerRecord(
    val subjectGroup: String,
    val subject: String,
    val date: String,
    val durationSeconds: Long
)

/**
 * 科目数据管理（支持持久化存储 + 用户自定义增删）
 */
object SubjectData {

    private const val PREFS_NAME = "study_timer_data"
    private const val KEY_SUBJECTS = "subject_data"

    // 内存中的科目集列表
    private var groups: MutableList<SubjectGroup> = mutableListOf()

    /**
     * 初始化：从 SharedPreferences 加载，首次使用时写入预设数据
     */
    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_SUBJECTS, null)

        if (jsonStr != null) {
            // 已有数据 → 加载
            groups = parseFromJson(jsonStr)
        } else {
            // 首次使用 → 使用预设数据
            groups = mutableListOf(
                SubjectGroup("数学", mutableListOf("高数", "概率论", "线代")),
                SubjectGroup("英语", mutableListOf("阅读理解", "单词背诵", "语法练习")),
                SubjectGroup("政治", mutableListOf("马原", "毛中特", "史纲")),
                SubjectGroup("专业课", mutableListOf("数据结构", "操作系统", "计算机网络"))
            )
            save(context)  // 持久化
        }
    }

    /**
     * 获取所有科目集的名称列表
     */
    fun getGroupNames(): List<String> = groups.map { it.name }

    /**
     * 获取科目集的颜色（自动分配 + 持久化）
     */
    private val morandiPalette = intArrayOf(
        0xFF6BA4D1.toInt(),  // 雾蓝
        0xFF8B82B8.toInt(),  // 靛紫
        0xFFC496A8.toInt(),  // 莫兰迪粉
        0xFFD4A574.toInt(),  // 暖橙
        0xFF6DB89A.toInt(),  // 莫兰迪绿
        0xFF6BA8A8.toInt(),  // 青绿
        0xFFA088B8.toInt(),  // 淡紫
        0xFFD4726A.toInt(),  // 莫兰迪红
        0xFFA8CBE3.toInt(),  // 天蓝
        0xFFB8A882.toInt(),  // 卡其
        // 扩展 10 色
        0xFF7EB5C8.toInt(),  // 灰蓝
        0xFF9AB8D4.toInt(),  // 浅钢蓝
        0xFFD4AFA8.toInt(),  // 贝壳粉
        0xFFC8BFA8.toInt(),  // 米灰
        0xFF8FA8A0.toInt(),  // 鼠尾草
        0xFFC09AB0.toInt(),  // 薰衣草粉
        0xFFA8A0C0.toInt(),  // 淡灰紫
        0xFFD0A880.toInt(),  // 杏色
        0xFF80A8B8.toInt(),  // 灰绿蓝
        0xFFC0A888.toInt()   // 驼色
    )

    /** 获取所有可用的莫兰迪颜色（供颜色选择器使用） */
    fun getAllMorandiColors(): IntArray = morandiPalette

    fun getGroupColor(groupName: String): Int {
        val group = groups.find { it.name == groupName }
        return if (group != null && group.color.isNotEmpty()) {
            try { android.graphics.Color.parseColor(group.color) } catch (_: Exception) {
                morandiPalette[groups.indexOf(group) % morandiPalette.size]
            }
        } else {
            val idx = groups.indexOf(group).coerceAtLeast(0)
            morandiPalette[idx % morandiPalette.size]
        }
    }

    /**
     * 设置科目集颜色
     */
    fun setGroupColor(context: Context, groupName: String, colorHex: String) {
        groups.find { it.name == groupName }?.let { group ->
            val idx = groups.indexOf(group)
            groups[idx] = group.copy(color = colorHex)
            save(context)
        }
    }

    /**
     * 设置科目集颜色（Int 版本，颜色选择器用）
     */
    fun setGroupColorInt(context: Context, groupName: String, colorInt: Int) {
        val hex = String.format("#%08X", colorInt)
        setGroupColor(context, groupName, hex)
    }

    /** 获取所有科目集名称→颜色映射 */
    fun getGroupColorMap(): Map<String, Int> {
        return groups.associate { it.name to getGroupColor(it.name) }
    }

    /**
     * 根据科目集名称获取该科目集下的科目列表
     */
    fun getSubjectsByGroup(groupName: String): List<String> {
        return groups.find { it.name == groupName }?.subjects ?: emptyList()
    }

    /**
     * 添加科目集
     */
    fun addGroup(context: Context, name: String) {
        if (name.isBlank()) return
        if (groups.any { it.name == name }) return  // 不能重名
        groups.add(SubjectGroup(name, mutableListOf()))
        save(context)
    }

    /**
     * 删除科目集
     */
    fun deleteGroup(context: Context, name: String) {
        groups.removeAll { it.name == name }
        save(context)
    }

    /**
     * 重命名科目集
     */
    fun renameGroup(context: Context, oldName: String, newName: String) {
        if (newName.isBlank()) return
        groups.find { it.name == oldName }?.let { group ->
            val index = groups.indexOf(group)
            groups[index] = group.copy(name = newName, subjects = group.subjects)
        }
        save(context)
    }

    /**
     * 上移科目集
     */
    fun moveGroupUp(context: Context, name: String) {
        val idx = groups.indexOfFirst { it.name == name }
        if (idx > 0) {
            val tmp = groups[idx]
            groups[idx] = groups[idx - 1]
            groups[idx - 1] = tmp
            save(context)
        }
    }

    /**
     * 下移科目集
     */
    fun moveGroupDown(context: Context, name: String) {
        val idx = groups.indexOfFirst { it.name == name }
        if (idx in 0 until groups.size - 1) {
            val tmp = groups[idx]
            groups[idx] = groups[idx + 1]
            groups[idx + 1] = tmp
            save(context)
        }
    }

    /**
     * 上移科目
     */
    fun moveSubjectUp(context: Context, groupName: String, subjectName: String) {
        val group = groups.find { it.name == groupName } ?: return
        val idx = group.subjects.indexOf(subjectName)
        if (idx > 0) {
            val tmp = group.subjects[idx]
            group.subjects[idx] = group.subjects[idx - 1]
            group.subjects[idx - 1] = tmp
            save(context)
        }
    }

    /**
     * 下移科目
     */
    fun moveSubjectDown(context: Context, groupName: String, subjectName: String) {
        val group = groups.find { it.name == groupName } ?: return
        val idx = group.subjects.indexOf(subjectName)
        if (idx in 0 until group.subjects.size - 1) {
            val tmp = group.subjects[idx]
            group.subjects[idx] = group.subjects[idx + 1]
            group.subjects[idx + 1] = tmp
            save(context)
        }
    }

    /**
     * 向指定科目集添加科目
     */
    fun addSubject(context: Context, groupName: String, subjectName: String) {
        if (subjectName.isBlank()) return
        val group = groups.find { it.name == groupName } ?: return
        if (group.subjects.contains(subjectName)) return  // 不能重名
        group.subjects.add(subjectName)
        save(context)
    }

    /**
     * 重命名科目
     */
    fun renameSubject(context: Context, groupName: String, oldName: String, newName: String) {
        if (newName.isBlank()) return
        val group = groups.find { it.name == groupName } ?: return
        val idx = group.subjects.indexOf(oldName)
        if (idx >= 0) {
            group.subjects[idx] = newName
            save(context)
        }
    }

    /**
     * 从指定科目集删除科目
     */
    fun deleteSubject(context: Context, groupName: String, subjectName: String) {
        groups.find { it.name == groupName }?.subjects?.remove(subjectName)
        save(context)
    }

    /**
     * 保存到 SharedPreferences
     */
    private fun save(context: Context) {
        val jsonArray = JSONArray()
        for (group in groups) {
            val groupJson = org.json.JSONObject()
            groupJson.put("name", group.name)
            groupJson.put("color", group.color)
            val subjectsArray = JSONArray()
            for (s in group.subjects) {
                subjectsArray.put(s)
            }
            groupJson.put("subjects", subjectsArray)
            jsonArray.put(groupJson)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SUBJECTS, jsonArray.toString())
            .apply()
    }

    /**
     * 从 JSON 字符串解析科目集列表
     */
    private fun parseFromJson(jsonStr: String): MutableList<SubjectGroup> {
        val result = mutableListOf<SubjectGroup>()
        val jsonArray = JSONArray(jsonStr)
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val name = obj.getString("name")
            val color = obj.optString("color", "")
            val subjectsArray = obj.getJSONArray("subjects")
            val subjects = mutableListOf<String>()
            for (j in 0 until subjectsArray.length()) {
                subjects.add(subjectsArray.getString(j))
            }
            result.add(SubjectGroup(name, subjects, color))
        }
        return result
    }
}
