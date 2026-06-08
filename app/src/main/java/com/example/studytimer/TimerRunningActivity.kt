package com.example.studytimer

import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
 * 全屏正计时 — 圆形进度环 UI
 * 时间戳差值计时，切后台不中断
 */
class TimerRunningActivity : BaseTimerActivity() {

    private lateinit var circularTimer: CircularTimerView
    private lateinit var btnPauseResume: Button
    private lateinit var btnEnd: Button

    private var startTimestamp = 0L
    private var pausedElapsedMillis = 0L
    private var isPaused = false
    private var finished = false
    private var maxExpectedMillis = 3600_000L

    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (!isPaused) {
                val elapsed = System.currentTimeMillis() - startTimestamp + pausedElapsedMillis
                val prog = (elapsed.toFloat() / maxExpectedMillis).coerceAtMost(1f)
                circularTimer.timeText = formatMillis(elapsed)
                circularTimer.progress = prog
                circularTimer.statusText = when {
                    elapsed < 60_000 -> "刚刚开始"
                    elapsed < 600_000 -> "持续专注中"
                    elapsed < 1800_000 -> "状态良好"
                    else -> "深度专注"
                }
            }
            handler.postDelayed(this, 200)
        }
    }

    private var subjectGroup = ""
    private var subjectName = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_timer_running)

        circularTimer = findViewById(R.id.circular_timer)
        btnPauseResume = findViewById(R.id.btn_pause_resume)
        btnEnd = findViewById(R.id.btn_end)

        loadCustomBackground(findViewById<ImageView>(R.id.iv_background))

        subjectGroup = intent.getStringExtra("subject_group") ?: "未分类"
        subjectName = intent.getStringExtra("subject_name") ?: "未命名"
        circularTimer.subjectText = "⏱ 正计时 · $subjectGroup — $subjectName"
        circularTimer.isCountdown = false
        circularTimer.timeText = "00:00:00"
        circularTimer.progress = 0f
        circularTimer.statusText = "准备开始"
        // 显示计时模式标签
        circularTimer.isCountdown = false

        loadMotto(findViewById(R.id.tv_motto))

        startTimestamp = System.currentTimeMillis()
        handler.post(refreshRunnable)

        TimerService.startService(this, "$subjectGroup · $subjectName", false)
        startWhiteNoiseIfEnabled()
        startMusicIfEnabled()
        playEnterAnimation(circularTimer, findViewById(R.id.layout_content))

        btnPauseResume.setOnClickListener {
            if (isPaused) {
                isPaused = false
                startTimestamp = System.currentTimeMillis()
                handler.post(refreshRunnable)
                btnPauseResume.text = "暂停"
                circularTimer.pulse()
            } else {
                isPaused = true
                pausedElapsedMillis += System.currentTimeMillis() - startTimestamp
                handler.removeCallbacks(refreshRunnable)
                btnPauseResume.text = "继续"
                circularTimer.statusText = "已暂停"
            }
        }

        btnEnd.setOnClickListener { saveAndFinish() }

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                AlertDialog.Builder(this@TimerRunningActivity)
                    .setTitle("放弃计时？")
                    .setMessage("计时仍在进行，确定要放弃吗？")
                    .setPositiveButton("确定放弃") { _, _ ->
                        finished = true
                        handler.removeCallbacks(refreshRunnable)
                        stopAllAudio()
                        TimerService.stopService(this@TimerRunningActivity)
                        finish()
                    }
                    .setNegativeButton("继续计时", null).show()
            }
        })
    }

    private fun saveAndFinish() {
        if (finished) return
        finished = true
        handler.removeCallbacks(refreshRunnable)
        playEndSound()
        val totalMillis = if (isPaused) pausedElapsedMillis
            else pausedElapsedMillis + (System.currentTimeMillis() - startTimestamp)
        val totalSeconds = totalMillis / 1000

        if (totalSeconds > 0) {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            StorageHelper.saveRecord(this, TimerRecord(
                subjectGroup, subjectName, dateFormat.format(Date()), totalSeconds
            ))
            TodoStorage.checkAndUpdateStudyGoal(this)
            Toast.makeText(this, "已记录：${formatMillis(totalMillis)}", Toast.LENGTH_SHORT).show()
        }
        MottoStorage.moveToNext(this)
        stopAllAudio()
        TimerService.stopService(this)
        finish()
    }

    override fun onStop() {
        super.onStop()
        if (!finished && PureModeStorage.isEnabled(this) && !isPaused) {
            saveAndFinish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(refreshRunnable)
        WhiteNoiseEngine.getInstance().stop()
        TimerService.stopService(this)
    }
}
