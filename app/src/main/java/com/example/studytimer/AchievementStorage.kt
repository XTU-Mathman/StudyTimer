package com.example.studytimer

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 成就系统 — 解锁式激励
 */
object AchievementStorage {

    private const val PREFS = "study_timer_profile"
    private const val KEY_UNLOCKED = "achievements_unlocked"
    private const val KEY_STREAK_RECORD = "achievement_streak_record"

    data class Achievement(
        val id: String,
        val name: String,
        val description: String,
        val icon: String
    )

    val ALL_ACHIEVEMENTS = listOf(
        Achievement("first_learn", "第一次专注", "完成第一次计时学习", "🎯"),
        Achievement("streak_3", "三日坚持", "连续学习 3 天", "🔥"),
        Achievement("streak_7", "一周战士", "连续学习 7 天", "⭐"),
        Achievement("streak_14", "半月自律", "连续学习 14 天", "🌟"),
        Achievement("streak_30", "月度强者", "连续学习 30 天", "💪"),
        Achievement("total_10h", "十小时", "累计学习 10 小时", "⏱"),
        Achievement("total_50h", "五十小时", "累计学习 50 小时", "⏰"),
        Achievement("total_100h", "百小时", "累计学习 100 小时", "🏆"),
        Achievement("single_6h", "全天冲刺", "单日学习 6 小时以上", "🎉"),
        Achievement("single_8h", "极限专注", "单日学习 8 小时以上", "👑"),
        Achievement("pomodoro_10", "番茄新手", "累计完成 10 个番茄", "🍅"),
        Achievement("pomodoro_50", "番茄达人", "累计完成 50 个番茄", "🍅"),
        Achievement("checkin_7", "打卡一周", "连续打卡 7 天", "✅"),
        Achievement("night_owl", "夜猫子", "晚上 11 点后还在学习", "🦉"),
        Achievement("early_bird", "早鸟", "早上 6 点前开始学习", "🌅")
    )

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getUnlocked(ctx: Context): Set<String> {
        return prefs(ctx).getStringSet(KEY_UNLOCKED, emptySet()) ?: emptySet()
    }

    fun unlock(ctx: Context, id: String): Boolean {
        val unlocked = getUnlocked(ctx).toMutableSet()
        if (unlocked.add(id)) {
            prefs(ctx).edit().putStringSet(KEY_UNLOCKED, unlocked).apply()
            return true // 新解锁
        }
        return false // 已解锁
    }

    fun isUnlocked(ctx: Context, id: String): Boolean = id in getUnlocked(ctx)

    fun checkAndUnlock(ctx: Context, forceCheck: Boolean = false): List<Achievement> {
        val newlyUnlocked = mutableListOf<Achievement>()
        val unlocked = getUnlocked(ctx).toMutableSet()

        val records = StorageHelper.getAllRecords(ctx)
        val dateSet = records.map { it.date }.toSet()
        val totalSeconds = records.sumOf { it.durationSeconds }
        val totalHours = totalSeconds / 3600f

        // 连续天数
        val streak = calculateStreak(ctx)

        for (ach in ALL_ACHIEVEMENTS) {
            if (ach.id in unlocked) continue
            val met = when (ach.id) {
                "first_learn" -> records.isNotEmpty()
                "streak_3" -> streak >= 3
                "streak_7" -> streak >= 7
                "streak_14" -> streak >= 14
                "streak_30" -> streak >= 30
                "total_10h" -> totalHours >= 10f
                "total_50h" -> totalHours >= 50f
                "total_100h" -> totalHours >= 100f
                "single_6h" -> {
                    records.groupBy { it.date }.any { (_, rs) ->
                        rs.sumOf { it.durationSeconds } >= 6 * 3600
                    }
                }
                "single_8h" -> {
                    records.groupBy { it.date }.any { (_, rs) ->
                        rs.sumOf { it.durationSeconds } >= 8 * 3600
                    }
                }
                "pomodoro_10", "pomodoro_50" -> false // 需从外部触发
                "checkin_7" -> {
                    val items = CheckInStorage.getItems(ctx)
                    if (items.isEmpty()) false else {
                        var streak2 = 0
                        val cal = Calendar.getInstance()
                        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        for (i in 0..30) {
                            val records2 = CheckInStorage.getRecordsByDate(ctx, dateFmt.format(cal.time))
                            if (records2.size == items.size) {
                                streak2++
                                cal.add(Calendar.DAY_OF_YEAR, -1)
                            } else break
                        }
                        streak2 >= 7
                    }
                }
                "night_owl" -> records.any {
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    // 粗略：检查它是否在23点后（实际上记录没有时间，所以我们跳过这个检查）
                    false
                }
                "early_bird" -> false
                else -> false
            }
            if (met) {
                unlocked.add(ach.id)
                newlyUnlocked.add(ach)
            }
        }

        if (newlyUnlocked.isNotEmpty()) {
            prefs(ctx).edit().putStringSet(KEY_UNLOCKED, unlocked).apply()
        }
        return newlyUnlocked
    }

    fun addPomodoroCount(ctx: Context, count: Int) {
        val key = "pomodoro_count"
        val current = prefs(ctx).getInt(key, 0)
        prefs(ctx).edit().putInt(key, current + count).apply()
    }

    fun getPomodoroCount(ctx: Context): Int = prefs(ctx).getInt("pomodoro_count", 0)

    private fun calculateStreak(ctx: Context): Int {
        val records = StorageHelper.getAllRecords(ctx)
        val dateSet = records.map { it.date }.toSet()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        var streak = 0
        val cal = Calendar.getInstance()
        val todayStr = dateFormat.format(cal.time)
        if (dateSet.contains(todayStr)) {
            streak = 1
            for (i in 1..365) {
                cal.add(Calendar.DAY_OF_YEAR, -1)
                if (dateSet.contains(dateFormat.format(cal.time))) streak++
                else break
            }
        } else {
            cal.add(Calendar.DAY_OF_YEAR, -1)
            for (i in 0..365) {
                val ds = dateFormat.format(cal.time)
                if (dateSet.contains(ds)) { streak++; cal.add(Calendar.DAY_OF_YEAR, -1) }
                else break
            }
        }
        return streak
    }
}
