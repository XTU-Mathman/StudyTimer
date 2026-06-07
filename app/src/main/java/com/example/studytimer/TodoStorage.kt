package com.example.studytimer

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 待办事项数据类
 * @param id 唯一ID（时间戳毫秒）
 * @param content 待办内容
 * @param isDone 是否已完成
 * @param date 完成日期 yyyy-MM-dd
 * @param isDaily 是否每日待办
 * @param dueDate 截止日期 yyyy-MM-dd
 * @param itemType 类型："todo"=普通待办, "group"=待办集, "builtin_goal"=内置学习目标
 * @param groupId 所属待办集ID，0=不属于任何集
 * @param isBuiltIn 是否内置项（不可删除）
 * @param targetHours 内置学习目标：目标小时数
 * @param subjectTargets 内置学习目标：科目目标 JSON 字符串
 */
data class TodoItem(
    val id: Long,
    val content: String,
    val isDone: Boolean = false,
    val date: String = "",
    val isDaily: Boolean = false,
    val dueDate: String = "",
    val itemType: String = "todo",
    val groupId: Long = 0,
    val isBuiltIn: Boolean = false,
    val targetHours: Float = 0f,
    val subjectTargets: String = ""
) {
    companion object {
        const val TYPE_TODO = "todo"
        const val TYPE_GROUP = "group"
        const val TYPE_BUILTIN_GOAL = "builtin_goal"
    }
}

/**
 * 科目目标数据类
 */
data class SubjectTarget(
    val groupName: String,
    val subjectName: String,
    val targetMinutes: Int
)

/**
 * 学习进度
 */
data class StudyProgress(
    val totalMinutes: Long,
    val targetMinutes: Long,
    val subjectProgress: List<SubjectProgress>
)

data class SubjectProgress(
    val groupName: String,
    val subjectName: String,
    val currentMinutes: Long,
    val targetMinutes: Long,
    val isMet: Boolean
)

/**
 * 待办事项本地存储（SharedPreferences + JSON）
 */
object TodoStorage {

    private const val PREFS_NAME = "study_timer_data"
    private const val KEY_TODOS = "todo_items"

    // 内置学习目标
    private const val GOAL_PREFS = "study_timer_profile"
    private const val KEY_GOAL_HOURS = "study_goal_hours"
    private const val KEY_GOAL_SUBJECTS = "study_goal_subjects"
    private const val KEY_GOAL_ENABLED = "study_goal_enabled"
    private const val KEY_GOAL_DONE_PREFIX = "study_goal_done_"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // ==================== 基础 CRUD ====================

    /** 获取所有项目（待办 + 待办集） */
    fun getAll(context: Context): List<TodoItem> {
        val jsonStr = getPrefs(context).getString(KEY_TODOS, "[]") ?: "[]"
        val arr = JSONArray(jsonStr)
        val result = mutableListOf<TodoItem>()
        for (i in 0 until arr.length()) {
            result.add(parseItem(arr.getJSONObject(i)))
        }
        return result
    }

    /** 获取有序展示列表：待办集在前（含子项），独立待办在后 */
    fun getDisplayList(context: Context): List<TodoItem> {
        val all = getAll(context)
        val groups = all.filter { it.itemType == TodoItem.TYPE_GROUP }
        val todos = all.filter { it.itemType == TodoItem.TYPE_TODO }
        val builtin = all.find { it.itemType == TodoItem.TYPE_BUILTIN_GOAL }

        val display = mutableListOf<TodoItem>()

        // 1. 内置学习目标（始终置顶，未配置时也显示）
        if (builtin != null) {
            display.add(builtin)
        } else {
            // 未配置：显示占位项
            display.add(TodoItem(
                id = -1L,
                content = "今日学习目标",
                isDone = false,
                isDaily = true,
                itemType = TodoItem.TYPE_BUILTIN_GOAL,
                isBuiltIn = true
            ))
        }

        // 2. 待办集 + 集内待办
        for (group in groups) {
            val children = todos.filter { it.groupId == group.id }
            val groupDone = children.isNotEmpty() && children.all { it.isDone }
            display.add(group.copy(isDone = groupDone))
            // 未完成的集内待办展开显示
            if (!groupDone) {
                display.addAll(children.filter { !it.isDone })
            }
        }

        // 3. 独立待办（不属于任何集）
        val standalone = todos.filter { it.groupId == 0L }
        display.addAll(standalone.filter { !it.isDone })
        display.addAll(standalone.filter { it.isDone })

        return display
    }

    /** 添加普通待办 */
    fun add(context: Context, item: TodoItem) {
        val all = getAll(context).toMutableList()
        all.add(item)
        saveAll(context, all)
    }

    /** 添加待办集 */
    fun addGroup(context: Context, item: TodoItem) {
        val all = getAll(context).toMutableList()
        all.add(item.copy(itemType = TodoItem.TYPE_GROUP))
        saveAll(context, all)
    }

    /** 标记为已完成 */
    fun markDone(context: Context, id: Long) {
        val all = getAll(context).toMutableList()
        val idx = all.indexOfFirst { it.id == id }
        if (idx >= 0) {
            val today = todayStr()
            all[idx] = all[idx].copy(isDone = true, date = today)
            saveAll(context, all)
        }
    }

    /** 取消完成（恢复为未完成） */
    fun markUndone(context: Context, id: Long) {
        val all = getAll(context).toMutableList()
        val idx = all.indexOfFirst { it.id == id }
        if (idx >= 0) {
            all[idx] = all[idx].copy(isDone = false, date = "")
            saveAll(context, all)
        }
    }

    /** 删除待办（同时删除其子待办） */
    fun delete(context: Context, id: Long) {
        val all = getAll(context).toMutableList()
        // 如果是待办集，同时删除集内待办
        val item = all.find { it.id == id }
        if (item != null && item.itemType == TodoItem.TYPE_GROUP) {
            all.removeAll { it.groupId == id }
        }
        all.removeAll { it.id == id }
        saveAll(context, all)
    }

    /** 获取所有待办集 */
    fun getAllGroups(context: Context): List<TodoItem> {
        return getAll(context).filter { it.itemType == TodoItem.TYPE_GROUP }
    }

    /** 获取集内待办 */
    fun getTodosByGroup(context: Context, groupId: Long): List<TodoItem> {
        return getAll(context).filter { it.itemType == TodoItem.TYPE_TODO && it.groupId == groupId }
    }

    /** 判断待办集是否全部完成 */
    fun isGroupDone(context: Context, groupId: Long): Boolean {
        val children = getTodosByGroup(context, groupId)
        return children.isNotEmpty() && children.all { it.isDone }
    }

    // ==================== 内置学习目标 ====================

    /** 获取学习目标配置 */
    fun getStudyGoalHours(context: Context): Float {
        return context.getSharedPreferences(GOAL_PREFS, Context.MODE_PRIVATE)
            .getFloat(KEY_GOAL_HOURS, 0f)
    }

    fun setStudyGoalHours(context: Context, hours: Float) {
        val prefs = context.getSharedPreferences(GOAL_PREFS, Context.MODE_PRIVATE)
        prefs.edit().putFloat(KEY_GOAL_HOURS, hours).apply()
        // 同步更新 TodoItem
        syncBuiltInGoalItem(context)
    }

    fun getStudyGoalSubjects(context: Context): List<SubjectTarget> {
        val json = context.getSharedPreferences(GOAL_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_GOAL_SUBJECTS, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                SubjectTarget(
                    obj.getString("groupName"),
                    obj.getString("subjectName"),
                    obj.getInt("targetMinutes")
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    fun setStudyGoalSubjects(context: Context, subjects: List<SubjectTarget>) {
        val arr = JSONArray()
        for (s in subjects) {
            arr.put(JSONObject().apply {
                put("groupName", s.groupName)
                put("subjectName", s.subjectName)
                put("targetMinutes", s.targetMinutes)
            })
        }
        context.getSharedPreferences(GOAL_PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_GOAL_SUBJECTS, arr.toString()).apply()
        syncBuiltInGoalItem(context)
    }

    fun isStudyGoalEnabled(context: Context): Boolean {
        return context.getSharedPreferences(GOAL_PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_GOAL_ENABLED, false)
    }

    fun setStudyGoalEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(GOAL_PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_GOAL_ENABLED, enabled).apply()
        syncBuiltInGoalItem(context)
    }

    /** 初始化学习目标（App启动时调用） */
    fun initStudyGoal(context: Context) {
        syncBuiltInGoalItem(context)
    }

    /** 同步内置学习目标 TodoItem（确保存在于列表中或从列表中移除） */
    private fun syncBuiltInGoalItem(context: Context) {
        val all = getAll(context).toMutableList()
        val existingIdx = all.indexOfFirst { it.itemType == TodoItem.TYPE_BUILTIN_GOAL }
        val enabled = isStudyGoalEnabled(context)
        val hours = getStudyGoalHours(context)
        val subjects = getStudyGoalSubjects(context)

        if (enabled && hours > 0f) {
            val content = buildGoalContent(hours, subjects)
            val subjectsJson = serializeSubjectTargets(subjects)
            val todayDone = isStudyGoalDoneToday(context)
            val item = TodoItem(
                id = -1L,
                content = content,
                isDone = todayDone,
                date = if (todayDone) todayStr() else "",
                isDaily = true,
                itemType = TodoItem.TYPE_BUILTIN_GOAL,
                isBuiltIn = true,
                targetHours = hours,
                subjectTargets = subjectsJson
            )
            if (existingIdx >= 0) {
                all[existingIdx] = item
            } else {
                all.add(0, item)
            }
        } else {
            if (existingIdx >= 0) all.removeAt(existingIdx)
        }
        saveAll(context, all)
    }

    private fun buildGoalContent(hours: Float, subjects: List<SubjectTarget>): String {
        val h = if (hours == hours.toLong().toFloat()) "${hours.toLong()}" else "$hours"
        return if (subjects.isEmpty()) {
            "今日学习 ${h} 小时"
        } else {
            val parts = subjects.map { s ->
                val m = s.targetMinutes
                if (s.subjectName.isNotEmpty()) "${s.groupName}·${s.subjectName} ${m}min"
                else "${s.groupName} ${m}min"
            }
            "今日学习 ${h} 小时（${parts.joinToString("、")}）"
        }
    }

    /** 检查今日学习目标是否已完成 */
    private fun isStudyGoalDoneToday(context: Context): Boolean {
        return context.getSharedPreferences(GOAL_PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_GOAL_DONE_PREFIX + todayStr(), false)
    }

    /** 计时结束时调用：检查并更新内置学习目标完成状态 */
    fun checkAndUpdateStudyGoal(context: Context) {
        if (!isStudyGoalEnabled(context)) return
        val hours = getStudyGoalHours(context)
        if (hours <= 0f) return

        val today = todayStr()
        val todayRecords = StorageHelper.getAllRecords(context).filter { it.date == today }
        val subjects = getStudyGoalSubjects(context)

        // 检查总时长
        val totalMinutes = todayRecords.sumOf { it.durationSeconds } / 60
        val totalMet = totalMinutes >= (hours * 60).toLong()

        // 检查各科目目标
        val subjectsMet = subjects.all { target ->
            checkSubjectMet(todayRecords, target)
        }

        if (totalMet && subjectsMet) {
            // 标记今日完成
            context.getSharedPreferences(GOAL_PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_GOAL_DONE_PREFIX + today, true).apply()

            // 更新 TodoItem
            val all = getAll(context).toMutableList()
            val idx = all.indexOfFirst { it.itemType == TodoItem.TYPE_BUILTIN_GOAL }
            if (idx >= 0) {
                all[idx] = all[idx].copy(isDone = true, date = today)
                saveAll(context, all)
            }
        }
    }

    private fun checkSubjectMet(records: List<TimerRecord>, target: SubjectTarget): Boolean {
        val matched = records.filter { r ->
            r.subjectGroup == target.groupName &&
                (target.subjectName.isEmpty() || r.subject == target.subjectName)
        }
        return matched.sumOf { it.durationSeconds } / 60 >= target.targetMinutes
    }

    /** 获取今日学习进度 */
    fun getStudyProgress(context: Context): StudyProgress? {
        if (!isStudyGoalEnabled(context)) return null
        val hours = getStudyGoalHours(context)
        if (hours <= 0f) return null

        val today = todayStr()
        val todayRecords = StorageHelper.getAllRecords(context).filter { it.date == today }
        val subjects = getStudyGoalSubjects(context)

        val totalMinutes = todayRecords.sumOf { it.durationSeconds } / 60
        val targetMinutes = (hours * 60).toLong()

        val subjectProgress = subjects.map { target ->
            val matched = todayRecords.filter { r ->
                r.subjectGroup == target.groupName &&
                    (target.subjectName.isEmpty() || r.subject == target.subjectName)
            }
            val current = matched.sumOf { it.durationSeconds } / 60
            SubjectProgress(
                target.groupName, target.subjectName,
                current, target.targetMinutes.toLong(),
                current >= target.targetMinutes
            )
        }

        return StudyProgress(totalMinutes, targetMinutes, subjectProgress)
    }

    // ==================== 每日重置 ====================

    /** 每日重置：将过期的每日待办恢复为未完成（只清状态，保留配置） */
    fun resetDailyIfNeeded(context: Context) {
        val today = todayStr()
        val all = getAll(context).toMutableList()
        var changed = false

        for (i in all.indices) {
            val item = all[i]
            if (item.isDaily && item.isDone && item.date != today) {
                all[i] = item.copy(isDone = false, date = "")
                changed = true
            }
        }

        // 重置内置学习目标的今日完成标记（保留配置）
        // 由 isStudyGoalDoneToday 自动返回 false（新的一天没有记录）

        if (changed) saveAll(context, all)
    }

    // ==================== 序列化 ====================

    private fun saveAll(context: Context, items: List<TodoItem>) {
        val arr = JSONArray()
        for (item in items) {
            arr.put(serializeItem(item))
        }
        getPrefs(context).edit().putString(KEY_TODOS, arr.toString()).apply()
    }

    private fun serializeItem(item: TodoItem): JSONObject {
        return JSONObject().apply {
            put("id", item.id)
            put("content", item.content)
            put("isDone", item.isDone)
            put("date", item.date)
            put("isDaily", item.isDaily)
            if (item.dueDate.isNotEmpty()) put("dueDate", item.dueDate)
            if (item.itemType != "todo") put("itemType", item.itemType)
            if (item.groupId != 0L) put("groupId", item.groupId)
            if (item.isBuiltIn) put("isBuiltIn", true)
            if (item.targetHours > 0f) put("targetHours", item.targetHours.toDouble())
            if (item.subjectTargets.isNotEmpty()) put("subjectTargets", item.subjectTargets)
        }
    }

    private fun parseItem(obj: JSONObject): TodoItem {
        return TodoItem(
            id = obj.getLong("id"),
            content = obj.getString("content"),
            isDone = obj.getBoolean("isDone"),
            date = obj.optString("date", ""),
            isDaily = obj.optBoolean("isDaily", false),
            dueDate = obj.optString("dueDate", ""),
            itemType = obj.optString("itemType", "todo"),
            groupId = obj.optLong("groupId", 0),
            isBuiltIn = obj.optBoolean("isBuiltIn", false),
            targetHours = obj.optDouble("targetHours", 0.0).toFloat(),
            subjectTargets = obj.optString("subjectTargets", "")
        )
    }

    private fun serializeSubjectTargets(subjects: List<SubjectTarget>): String {
        if (subjects.isEmpty()) return ""
        val arr = JSONArray()
        for (s in subjects) {
            arr.put(JSONObject().apply {
                put("groupName", s.groupName)
                put("subjectName", s.subjectName)
                put("targetMinutes", s.targetMinutes)
            })
        }
        return arr.toString()
    }

    fun parseSubjectTargets(json: String): List<SubjectTarget> {
        if (json.isEmpty()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                SubjectTarget(
                    obj.getString("groupName"),
                    obj.getString("subjectName"),
                    obj.getInt("targetMinutes")
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    fun todayStr(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }
}
