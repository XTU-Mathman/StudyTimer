package com.example.studytimer

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * 主活动：管理底部导航栏和四个页面的切换
 * - 自定义缩放+渐入 Fragment 过渡
 * - 底部导航图标选中弹性动画
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 透明状态栏 + 沉浸
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        // 初始化科目数据和每日待办重置
        SubjectData.init(this)
        TodoStorage.resetDailyIfNeeded(this)
        TodoStorage.initStudyGoal(this)

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
            // 选中图标的弹性弹跳
            val iconView = menuItem.actionView
            if (iconView != null) {
                ObjectAnimator.ofFloat(iconView, "scaleX", 0.8f, 1.15f, 1.0f).apply {
                    duration = 450
                    interpolator = OvershootInterpolator(1.5f)
                    start()
                }
                ObjectAnimator.ofFloat(iconView, "scaleY", 0.8f, 1.15f, 1.0f).apply {
                    duration = 450
                    interpolator = OvershootInterpolator(1.5f)
                    start()
                }
            }
            true
        }
    }

    /**
     * 自定义出场/入场动画切换 Fragment
     */
    private fun switchToFragment(fragment: Fragment) {
        val tag = fragment.javaClass.simpleName
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.fragment_enter,    // 进入：缩放+渐入+上滑
                R.anim.fragment_exit,     // 退出：缩放+渐出
                R.anim.fragment_pop_enter, // 返回进入：渐入+下滑
                R.anim.fragment_pop_exit   // 返回退出：渐出+上滑
            )
            .replace(R.id.fragment_container, fragment, tag)
            .commit()
    }
}
