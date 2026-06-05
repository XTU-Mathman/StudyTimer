package com.example.studytimer

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * 主活动：管理底部导航栏和四个页面的切换
 * 带有淡入/淡出 Fragment 切换动画
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化科目数据和每日待办重置
        SubjectData.init(this)
        TodoStorage.resetDailyIfNeeded(this)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        // 默认显示计时页面
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, TimerFragment(), "timer")
                .commit()
        }

        bottomNav.setOnItemSelectedListener { menuItem ->
            val fragment: Fragment = when (menuItem.itemId) {
                R.id.nav_todo -> TodoFragment()
                R.id.nav_timer -> TimerFragment()
                R.id.nav_stats -> StatsFragment()
                R.id.nav_profile -> ProfileFragment()
                else -> return@setOnItemSelectedListener false
            }
            switchToFragment(fragment)
            true
        }
    }

    /**
     * 淡入淡出切换到指定 Fragment
     */
    private fun switchToFragment(fragment: Fragment) {
        val tag = fragment.javaClass.simpleName
        supportFragmentManager.beginTransaction()
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
            .replace(R.id.fragment_container, fragment, tag)
            .commit()
    }
}
