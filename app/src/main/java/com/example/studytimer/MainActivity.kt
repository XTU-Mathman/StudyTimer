package com.example.studytimer

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 主活动：管理底部导航栏和四个页面的切换
 * - Fragment 缓存 + show/hide 切换（保留页面状态）
 * - 自定义缩放+渐入 Fragment 过渡动画
 */
class MainActivity : AppCompatActivity() {

    // 缓存 Fragment 实例，切 Tab 不丢失状态
    private val fragmentCache = mutableMapOf<Int, Fragment>()
    private var activeFragment: Fragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 透明状态栏 + 沉浸
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        // 初始化科目数据和每日待办重置
        if (savedInstanceState == null) {
            SubjectData.init(this)
            TodoStorage.resetDailyIfNeeded(this)
            TodoStorage.initStudyGoal(this)
            // 清理 90 天前的旧记录，防止 JSON 膨胀
            StorageHelper.cleanupOldRecords(this)
            // 创建久坐提醒通知渠道
            SedentaryReminderReceiver.createChannel(this)
            ScheduleStartReminderReceiver.createChannel(this)
            // 检查成就解锁
            checkNewAchievements()
            // 每日首次打开显示复盘
            showDailySummaryIfNeeded()
        }

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        // 恢复或创建默认页面
        if (savedInstanceState == null) {
            val timerFragment = getOrCreateFragment(R.id.nav_timer)
            supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, timerFragment, "timer")
                .commit()
            activeFragment = timerFragment
        } else {
            // 恢复后找回当前显示的 Fragment
            activeFragment = supportFragmentManager.fragments.firstOrNull { it.isVisible }
            // 重建缓存映射
            for (f in supportFragmentManager.fragments) {
                val id = getFragmentId(f)
                if (id != 0) fragmentCache[id] = f
            }
        }

        bottomNav.setOnItemSelectedListener { menuItem ->
            switchToFragment(menuItem.itemId)
            true
        }
    }

    private fun getOrCreateFragment(itemId: Int): Fragment {
        return fragmentCache.getOrPut(itemId) {
            when (itemId) {
                R.id.nav_todo -> TodoFragment()
                R.id.nav_timer -> TimerFragment()
                R.id.nav_stats -> StatsFragment()
                R.id.nav_profile -> ProfileFragment()
                else -> TimerFragment()
            }
        }
    }

    private fun getFragmentId(fragment: Fragment): Int {
        return when (fragment) {
            is TodoFragment -> R.id.nav_todo
            is TimerFragment -> R.id.nav_timer
            is StatsFragment -> R.id.nav_stats
            is ProfileFragment -> R.id.nav_profile
            else -> 0
        }
    }

    private fun switchToFragment(itemId: Int) {
        val target = getOrCreateFragment(itemId)
        if (target === activeFragment) return // 已经是当前页

        val transaction = supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.fragment_enter,
                R.anim.fragment_exit
            )

        // 隐藏当前页
        activeFragment?.let { transaction.hide(it) }

        // 如果目标还没添加，先 add；否则 show
        if (!target.isAdded) {
            transaction.add(R.id.fragment_container, target, target.javaClass.simpleName)
        } else {
            transaction.show(target)
        }

        transaction.commit()
        activeFragment = target
    }

    // ==================== 成就检查 ====================

    private fun checkNewAchievements() {
        val newly = AchievementStorage.checkAndUnlock(this)
        if (newly.isNotEmpty()) {
            val msg = newly.joinToString("\n") { "${it.icon} ${it.name}：${it.description}" }
            AlertDialog.Builder(this)
                .setTitle("🎉 新成就解锁！")
                .setMessage(msg)
                .setPositiveButton("太棒了！", null)
                .show()
        }
    }

    // ==================== 每日复盘 ====================

    private fun showDailySummaryIfNeeded() {
        val prefs = getSharedPreferences("study_timer_profile", MODE_PRIVATE)
        val lastSummaryDate = prefs.getString("last_summary_date", "")
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .format(Calendar.getInstance().time)
        if (lastSummaryDate == today) return // 今天已显示过

        val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val yesterdayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(yesterdayCal.time)
        val records = StorageHelper.getAllRecords(this).filter { it.date == yesterdayStr }
        if (records.isEmpty()) return // 昨天没有记录

        prefs.edit().putString("last_summary_date", today).apply()

        val totalSec = records.sumOf { it.durationSeconds }
        val h = totalSec / 3600; val m = (totalSec % 3600) / 60
        val grouped = records.groupBy { it.subjectGroup }
        val details = grouped.map { (g, rs) ->
            val sh = rs.sumOf { it.durationSeconds } / 3600f
            "$g ${"%.1f".format(sh)}h"
        }

        val streak = calculateStreak()
        val msg = buildString {
            appendLine("📅 昨日学习回顾")
            appendLine()
            appendLine("⏱ 总时长：${h}小时${m}分")
            appendLine("📚 科目：${details.joinToString("、")}")
            if (streak > 0) appendLine("🔥 连续学习：${streak} 天")
            appendLine()
            append("今天也要加油哦 💪")
        }

        AlertDialog.Builder(this)
            .setTitle("📊 昨日总结")
            .setMessage(msg)
            .setPositiveButton("开始今天的学习", null)
            .show()
    }

    private fun calculateStreak(): Int {
        val records = StorageHelper.getAllRecords(this)
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
