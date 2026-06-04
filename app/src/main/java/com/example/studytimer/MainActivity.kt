package com.example.studytimer

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * 主活动：管理底部导航栏和三个页面的切换
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化科目数据（全局只需一次）
        SubjectData.init(this)
        // 每日重置：过期的每日待办恢复为未完成
        TodoStorage.resetDailyIfNeeded(this)

        // 获取底部导航栏控件
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        // 默认显示计时页面（中间的按钮）
        if (savedInstanceState == null) {
            switchToFragment(TimerFragment())
        }

        // 监听底部导航栏的点击事件
        bottomNav.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                // 点击"待办"按钮 → 切换到待办页面
                R.id.nav_todo -> switchToFragment(TodoFragment())
                // 点击"计时"按钮 → 切换到计时页面
                R.id.nav_timer -> switchToFragment(TimerFragment())
                // 点击"统计"按钮 → 切换到统计页面
                R.id.nav_stats -> switchToFragment(StatsFragment())
                // 点击"我的"按钮 → 切换到我的页面
                R.id.nav_profile -> switchToFragment(ProfileFragment())
            }
            true // 返回 true 表示已处理点击
        }
    }

    /**
     * 切换到指定的 Fragment 页面
     * @param fragment 要显示的目标页面
     */
    private fun switchToFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment) // 用新页面替换容器中的旧页面
            .commit() // 提交事务
    }
}