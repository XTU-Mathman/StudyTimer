package com.example.studytimer

import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
 * 全屏倒计时：时间戳差值计时，切后台不影响
 */
class CountdownRunningActivity : AppCompatActivity() {

    private lateinit var tvSubjectInfo: TextView
    private lateinit var tvTargetInfo: TextView
    private lateinit var tvTimerDisplay: TextView
    private lateinit var btnPauseResume: Button
    private lateinit var btnEnd: Button

    // 时间戳差值倒计时
    private var targetMillis = 0L       // 目标总毫秒数
    private var startTimestamp = 0L     // 本轮开始时间戳
    private var pausedRemaining = 0L    // 暂停时剩余毫秒数
    private var isPaused = false
    private var isFinished = false
    private var finishedByUser = false   // saveAndFinish 已调用标记（防 onStop 重复触发）

    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (!isPaused && !isFinished) {
                val elapsed = System.currentTimeMillis() - startTimestamp
                val remaining = pausedRemaining - elapsed
                if (remaining <= 0) {
                    tvTimerDisplay.text = "00:00:00"
                    onTimeUp()
                    return
                }
                tvTimerDisplay.text = formatMillis(remaining)
                handler.postDelayed(this, 200)
            }
        }
    }

    private var subjectGroup = ""
    private var subjectName = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_countdown_running)

        // 加载自定义背景
        val bgPath = ProfileStorage.getBackgroundPath(this)
        val ivBg = findViewById<ImageView>(R.id.iv_background)
        val layoutContent = findViewById<android.view.View>(R.id.layout_content)
        if (bgPath != null) {
            ivBg.setImageBitmap(android.graphics.BitmapFactory.decodeFile(bgPath))
            ivBg.visibility = android.view.View.VISIBLE
            layoutContent.setBackgroundColor(0xCCF5F9FC.toInt())
        }

        tvSubjectInfo = findViewById(R.id.tv_subject_info)
        tvTargetInfo = findViewById(R.id.tv_target_info)
        tvTimerDisplay = findViewById(R.id.tv_timer_display)
        btnPauseResume = findViewById(R.id.btn_pause_resume)
        btnEnd = findViewById(R.id.btn_end)

        subjectGroup = intent.getStringExtra("subject_group") ?: "未分类"
        subjectName = intent.getStringExtra("subject_name") ?: "未命名"
        val totalMinutes = intent.getIntExtra("total_minutes", 30)

        targetMillis = totalMinutes * 60L * 1000L
        pausedRemaining = targetMillis

        tvSubjectInfo.text = "$subjectGroup - $subjectName"
        tvTargetInfo.text = "目标：${formatDuration(targetMillis / 1000)}"
        tvTimerDisplay.text = formatMillis(targetMillis)

        // 显示当前格言
        findViewById<TextView>(R.id.tv_motto).text = MottoStorage.getCurrent(this)

        // 开始
        startTimestamp = System.currentTimeMillis()
        handler.post(refreshRunnable)

        // 启动白噪音（如果已启用）
        startWhiteNoiseIfEnabled()

        // 启动音乐（如果已启用）
        startMusicIfEnabled()

        btnPauseResume.setOnClickListener {
            if (isPaused) {
                isPaused = false
                pausedRemaining -= System.currentTimeMillis() - startTimestamp
                startTimestamp = System.currentTimeMillis()
                handler.post(refreshRunnable)
                btnPauseResume.text = "暂停"
            } else {
                isPaused = true
                pausedRemaining -= System.currentTimeMillis() - startTimestamp
                handler.removeCallbacks(refreshRunnable)
                btnPauseResume.text = "继续"
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
        Toast.makeText(this, "⏰ 时间到！", Toast.LENGTH_LONG).show()
        MottoStorage.moveToNext(this)
        WhiteNoiseEngine.getInstance().stop()
        stopMusic()
        finish()
    }

    private fun saveAndFinish() {
        if (isFinished) return
        isFinished = true
        finishedByUser = true
        handler.removeCallbacks(refreshRunnable)

        val elapsedMillis = if (isPaused) targetMillis - pausedRemaining
            else targetMillis - (pausedRemaining - (System.currentTimeMillis() - startTimestamp))
        val elapsed = elapsedMillis / 1000
        if (elapsed > 0) {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            StorageHelper.saveRecord(this, TimerRecord(
                subjectGroup, subjectName, dateFormat.format(Date()), elapsed
            ))
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
                finishedByUser = true
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
        if (!finishedByUser && !isFinished && PureModeStorage.isEnabled(this) && !isPaused) {
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
