package com.example.studytimer

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 番茄钟全屏运行页
 * 专注 → 短休息 → 专注 → 短休息 → ... → 长休息 → 下一轮
 * 每 4 轮（可配）后进入长休息
 */
class PomodoroRunningActivity : BaseTimerActivity() {

    private lateinit var circularTimer: CircularTimerView
    private lateinit var btnPauseResume: Button
    private lateinit var btnEnd: Button
    private lateinit var tvPhase: TextView

    // 番茄钟参数
    private var workMinutes = 25
    private var breakMinutes = 5
    private var longBreakMinutes = 15
    private var totalRounds = 4

    // 运行状态
    private var currentRound = 1
    private var isWorkPhase = true      // true=专注, false=休息
    private var isPaused = false
    private var isFinished = false
    private var targetMillis = 0L
    private var startTimestamp = 0L
    private var pausedRemaining = 0L

    private var subjectGroup = ""
    private var subjectName = ""
    private var totalWorkSeconds = 0L   // 累计专注时长

    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (!isPaused && !isFinished) {
                val elapsed = System.currentTimeMillis() - startTimestamp
                val remaining = pausedRemaining - elapsed
                if (remaining <= 0) {
                    onPhaseComplete()
                    return
                }
                circularTimer.timeText = formatMillis(remaining)
                circularTimer.progress = (remaining.toFloat() / targetMillis).coerceIn(0f, 1f)
                val phaseLabel = if (isWorkPhase) "专注中" else "休息中"
                circularTimer.statusText = "$phaseLabel · 第 $currentRound/$totalRounds 轮"
                handler.postDelayed(this, 200)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_countdown_running)  // 复用倒计时布局

        circularTimer = findViewById(R.id.circular_timer)
        btnPauseResume = findViewById(R.id.btn_pause_resume)
        btnEnd = findViewById(R.id.btn_end)

        loadCustomBackground(findViewById<ImageView>(R.id.iv_background))

        // 读取参数
        subjectGroup = intent.getStringExtra("subject_group") ?: "未分类"
        subjectName = intent.getStringExtra("subject_name") ?: "未命名"
        workMinutes = intent.getIntExtra("work_minutes", 25)
        breakMinutes = intent.getIntExtra("break_minutes", 5)
        longBreakMinutes = intent.getIntExtra("long_break_minutes", 15)
        totalRounds = intent.getIntExtra("total_rounds", 4)

        loadMotto(findViewById(R.id.tv_motto))

        // 隐藏格言（番茄钟用状态文字代替）
        findViewById<TextView>(R.id.tv_motto).visibility = View.GONE

        // 开始第一轮专注
        startWorkPhase()

        startWhiteNoiseIfEnabled()
        startMusicIfEnabled()
        playEnterAnimation(circularTimer, findViewById(R.id.layout_content))

        TimerService.startService(this, "🍅 $subjectGroup · $subjectName", true, workMinutes)

        btnPauseResume.setOnClickListener {
            if (isPaused) {
                resumeTimer()
            } else {
                pauseTimer()
            }
        }

        btnEnd.setOnClickListener { confirmFinish() }

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                confirmFinish()
            }
        })
    }

    // ==================== 阶段控制 ====================

    private fun startWorkPhase() {
        isWorkPhase = true
        targetMillis = workMinutes * 60L * 1000L
        pausedRemaining = targetMillis
        startTimestamp = System.currentTimeMillis()

        circularTimer.subjectText = "🍅 $subjectGroup — $subjectName"
        circularTimer.isCountdown = true
        circularTimer.timeText = formatMillis(targetMillis)
        circularTimer.progress = 1f
        circularTimer.statusText = "专注中 · 第 $currentRound/$totalRounds 轮"
        // 专注时用红色调
        circularTimer.arcColors = intArrayOf(
            0xFFD4726A.toInt(), 0xFFC496A8.toInt(), 0xFFA088B8.toInt()
        )
        // 背景色调为暖色
        val bgView = findViewById<com.example.studytimer.DynamicBackgroundView>(R.id.iv_background)
        if (bgView != null) bgView.visibility = View.GONE

        handler.post(refreshRunnable)
    }

    private fun startBreakPhase(isLong: Boolean) {
        isWorkPhase = false
        val breakMs = (if (isLong) longBreakMinutes else breakMinutes) * 60L * 1000L
        targetMillis = breakMs
        pausedRemaining = targetMillis
        startTimestamp = System.currentTimeMillis()

        circularTimer.isCountdown = true
        circularTimer.timeText = formatMillis(targetMillis)
        circularTimer.progress = 1f
        val icon = if (isLong) "🌿" else "☕"
        val label = if (isLong) "长休息" else "短休息"
        circularTimer.subjectText = "$icon 休息时间 · 第 $currentRound/$totalRounds 轮"
        circularTimer.statusText = "$icon $label · 放松一下"
        // 休息时用绿色调 + 更醒目的标题
        circularTimer.arcColors = intArrayOf(
            0xFF6DB89A.toInt(), 0xFF6BA8A8.toInt(), 0xFF8B82B8.toInt()
        )
        // 振动提示休息开始
        vibrate(200)

        handler.post(refreshRunnable)
    }

    private fun onPhaseComplete() {
        handler.removeCallbacks(refreshRunnable)
        vibrate(300)
        playEndSound()

        if (isWorkPhase) {
            // 专注结束 → 记录时长
            totalWorkSeconds += workMinutes * 60L
            savePartialRecord(workMinutes * 60L)
            // 统计番茄钟数量（成就系统用）
            AchievementStorage.addPomodoroCount(this, 1)

            if (currentRound >= totalRounds) {
                // 全部完成
                Toast.makeText(this, "🎉 全部番茄完成！", Toast.LENGTH_LONG).show()
                finishPomodoro()
            } else {
                // 还有轮次 → 判断是否长休息
                val isLongBreak = (currentRound % 4 == 0)
                Toast.makeText(this,
                    if (isLongBreak) "🌿 开始长休息" else "☕ 开始短休息",
                    Toast.LENGTH_SHORT
                ).show()
                startBreakPhase(isLongBreak)
            }
        } else {
            // 休息结束 → 下一轮专注
            currentRound++
            Toast.makeText(this, "🍅 第 $currentRound 轮开始", Toast.LENGTH_SHORT).show()
            startWorkPhase()
        }
    }

    // ==================== 计时控制 ====================

    private fun pauseTimer() {
        isPaused = true
        pausedRemaining -= System.currentTimeMillis() - startTimestamp
        handler.removeCallbacks(refreshRunnable)
        btnPauseResume.text = "继续"
        circularTimer.statusText = "已暂停"
    }

    private fun resumeTimer() {
        isPaused = false
        startTimestamp = System.currentTimeMillis()
        handler.post(refreshRunnable)
        btnPauseResume.text = "暂停"
        circularTimer.pulse()
    }

    // ==================== 记录保存 ====================

    private fun savePartialRecord(seconds: Long) {
        if (seconds <= 0) return
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        StorageHelper.saveRecord(this, TimerRecord(
            subjectGroup, subjectName, dateFormat.format(Date()), seconds
        ))
        TodoStorage.checkAndUpdateStudyGoal(this)
    }

    private fun confirmFinish() {
        AlertDialog.Builder(this)
            .setTitle("结束番茄钟？")
            .setMessage("当前轮次尚未完成，已专注的时间会被保存。")
            .setPositiveButton("结束") { _, _ -> finishPomodoro() }
            .setNegativeButton("继续", null)
            .show()
    }

    private fun finishPomodoro() {
        if (isFinished) return
        isFinished = true
        handler.removeCallbacks(refreshRunnable)

        // 如果正在专注阶段中途退出，保存已用时间
        if (isWorkPhase && !isPaused) {
            val elapsed = System.currentTimeMillis() - startTimestamp
            val seconds = (elapsed / 1000).coerceAtLeast(0)
            if (seconds > 0) savePartialRecord(seconds)
        } else if (isWorkPhase && isPaused) {
            val elapsed = targetMillis - pausedRemaining
            val seconds = (elapsed / 1000).coerceAtLeast(0)
            if (seconds > 0) savePartialRecord(seconds)
        }

        MottoStorage.moveToNext(this)
        stopAllAudio()
        TimerService.stopService(this)
        finish()
    }

    // ==================== 系统 ====================

    private fun vibrate(ms: Long) {
        try {
            val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION") getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {}
    }

    override fun onStop() {
        super.onStop()
        if (!isFinished && PureModeStorage.isEnabled(this) && !isPaused) {
            finishPomodoro()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(refreshRunnable)
        WhiteNoiseEngine.getInstance().stop()
        TimerService.stopService(this)
    }
}
