package com.example.studytimer

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全屏倒计时 — 圆形进度环 UI
 * 时间戳差值倒计时，进度环从 100% → 0%
 */
class CountdownRunningActivity : AppCompatActivity() {

    private lateinit var circularTimer: CircularTimerView
    private lateinit var btnPauseResume: Button
    private lateinit var btnEnd: Button

    // 倒计时参数
    private var targetMillis = 0L
    private var startTimestamp = 0L
    private var pausedRemaining = 0L
    private var isPaused = false
    private var isFinished = false

    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (!isPaused && !isFinished) {
                val elapsed = System.currentTimeMillis() - startTimestamp
                val remaining = pausedRemaining - elapsed
                if (remaining <= 0) {
                    circularTimer.timeText = "00:00:00"
                    circularTimer.progress = 0f
                    circularTimer.statusText = "时间到！"
                    onTimeUp()
                    return
                }
                val text = formatMillis(remaining)
                val prog = (remaining.toFloat() / targetMillis).coerceIn(0f, 1f)
                circularTimer.timeText = text
                circularTimer.progress = prog
                circularTimer.statusText = "剩余 ${formatDuration(remaining / 1000)}"
                handler.postDelayed(this, 200)
            }
        }
    }

    private var subjectGroup = ""
    private var subjectName = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_countdown_running)

        circularTimer = findViewById(R.id.circular_timer)
        btnPauseResume = findViewById(R.id.btn_pause_resume)
        btnEnd = findViewById(R.id.btn_end)

        // 自定义背景
        val bgPath = ProfileStorage.getBackgroundPath(this)
        val ivBg = findViewById<ImageView>(R.id.iv_background)
        if (bgPath != null) {
            ivBg.setImageBitmap(BitmapFactory.decodeFile(bgPath))
            ivBg.visibility = View.VISIBLE
        }

        subjectGroup = intent.getStringExtra("subject_group") ?: "未分类"
        subjectName = intent.getStringExtra("subject_name") ?: "未命名"
        val totalMinutes = intent.getIntExtra("total_minutes", 30)

        targetMillis = totalMinutes * 60L * 1000L
        pausedRemaining = targetMillis

        circularTimer.subjectText = "$subjectGroup — $subjectName"
        circularTimer.isCountdown = true
        circularTimer.timeText = formatMillis(targetMillis)
        circularTimer.progress = 1f  // 开始满环
        circularTimer.statusText = "目标：${formatDuration(targetMillis / 1000)}"

        // 显示格言
        findViewById<TextView>(R.id.tv_motto).text = MottoStorage.getCurrent(this)

        // 开始计时
        startTimestamp = System.currentTimeMillis()
        handler.post(refreshRunnable)

        // 白噪音 & 音乐
        startWhiteNoiseIfEnabled()
        startMusicIfEnabled()

        // 进入动画
        circularTimer.scaleX = 0.8f
        circularTimer.scaleY = 0.8f
        circularTimer.alpha = 0f
        circularTimer.animate()
            .scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(500)
            .setInterpolator(DecelerateInterpolator())
            .start()

        findViewById<View>(R.id.layout_content).apply {
            alpha = 0f
            animate()
                .alpha(1f)
                .setDuration(400)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        btnPauseResume.setOnClickListener {
            if (isPaused) {
                isPaused = false
                startTimestamp = System.currentTimeMillis()
                handler.post(refreshRunnable)
                btnPauseResume.text = "暂停"
                circularTimer.pulse()
            } else {
                isPaused = true
                pausedRemaining -= System.currentTimeMillis() - startTimestamp
                handler.removeCallbacks(refreshRunnable)
                btnPauseResume.text = "继续"
                circularTimer.statusText = "已暂停"
            }
        }

        btnEnd.setOnClickListener { saveAndFinish() }
    }

    private fun onTimeUp() {
        if (isFinished) return
        isFinished = true
        handler.removeCallbacks(refreshRunnable)
        vibrate(500)
        saveRecord(targetMillis / 1000)
        TodoStorage.checkAndUpdateStudyGoal(this)
        Toast.makeText(this, "⏰ 时间到！", Toast.LENGTH_LONG).show()
        MottoStorage.moveToNext(this)
        WhiteNoiseEngine.getInstance().stop()
        stopMusic()
        finish()
    }

    private fun saveAndFinish() {
        if (isFinished) return
        isFinished = true
        handler.removeCallbacks(refreshRunnable)

        val elapsedMillis = if (isPaused) targetMillis - pausedRemaining
            else targetMillis - (pausedRemaining - (System.currentTimeMillis() - startTimestamp))
        val elapsed = elapsedMillis / 1000
        if (elapsed > 0) {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            StorageHelper.saveRecord(this, TimerRecord(
                subjectGroup, subjectName, dateFormat.format(Date()), elapsed
            ))
            TodoStorage.checkAndUpdateStudyGoal(this)
            Toast.makeText(this, "已记录：${formatDuration(elapsed)}", Toast.LENGTH_SHORT).show()
        }
        MottoStorage.moveToNext(this)
        WhiteNoiseEngine.getInstance().stop()
        stopMusic()
        finish()
    }

    private fun saveRecord(seconds: Long) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        StorageHelper.saveRecord(this, TimerRecord(
            subjectGroup, subjectName, dateFormat.format(Date()), seconds
        ))
    }

    private fun vibrate(ms: Long) {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    override fun onBackPressed() {
        AlertDialog.Builder(this)
            .setTitle("放弃倒计时？")
            .setMessage("倒计时仍在进行，确定要放弃吗？")
            .setPositiveButton("确定放弃") { _, _ ->
                isFinished = true
                handler.removeCallbacks(refreshRunnable)
                WhiteNoiseEngine.getInstance().stop()
                stopMusic()
                finish()
            }.setNegativeButton("继续计时", null).show()
    }

    private fun formatMillis(millis: Long): String {
        val s = millis / 1000
        return String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    }

    private fun formatDuration(sec: Long): String {
        val h = sec / 3600; val m = (sec % 3600) / 60
        return buildString {
            if (h > 0) append("${h}小时")
            if (m > 0) append("${m}分")
            if (isEmpty()) append("0分")
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isFinished && PureModeStorage.isEnabled(this) && !isPaused) {
            saveAndFinish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(refreshRunnable)
        WhiteNoiseEngine.getInstance().stop()
        stopMusic()
    }

    private fun stopMusic() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
    }

    private fun startMusicIfEnabled() {
        if (MusicStorage.isEnabled(this)) {
            val track = MusicStorage.getSelectedTrack(this) ?: return
            try {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(track.path)
                    prepare()
                    isLooping = true
                    start()
                }
            } catch (_: Exception) {
                mediaPlayer?.release()
                mediaPlayer = null
            }
        }
    }

    private fun startWhiteNoiseIfEnabled() {
        if (WhiteNoiseStorage.isEnabled(this)) {
            val type = WhiteNoiseStorage.getSelectedType(this)
            if (type != null) {
                WhiteNoiseEngine.getInstance().play(type)
            }
        }
    }
}
