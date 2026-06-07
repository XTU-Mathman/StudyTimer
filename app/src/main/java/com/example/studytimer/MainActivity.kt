package com.example.studytimer

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

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
}
