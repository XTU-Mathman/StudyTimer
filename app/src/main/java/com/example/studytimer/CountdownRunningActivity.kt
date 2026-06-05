package com.example.studytimer

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
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
 * 全屏倒计时：时间戳差值计时，切后台不中断
 * 高级简约风格 — 大数字 + 缩放动画 + 药丸按钮
 */
class CountdownRunningActivity : AppCompatActivity() {

    private lateinit var tvSubjectInfo: TextView
    private lateinit var tvTargetInfo: TextView
    private lateinit var tvTimerDisplay: TextView
    private lateinit var btnPauseResume: Button
    private lateinit var btnEnd: Button

    // 时间戳差值倒计时
    private var targetMillis = 0L
    private var startTimestamp = 0L
    private var pausedRemaining = 0L
    private var isPaused = false
    private var isFinished = false
    private var finishedByUser = false

    // 数字动画追踪
    private var lastDisplayText = ""

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
                val text = formatMillis(remaining)
                if (text != tvTimerDisplay.text.toString()) {
                    tvTimerDisplay.text = text
                    animateDigitChange()
                }
                handler.postDelayed(this, 200)
            }
        }
    }

    private var subjectGroup = ""
    private var subjectName = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_countdown_running)

        // 自定义背景
        val bgPath = ProfileStorage.getBackgroundPath(this)
        val ivBg = findViewById<ImageView>(R.id.iv_background)
        if (bgPath != null) {
            ivBg.setImageBitmap(android.graphics.BitmapFactory.decodeFile(bgPath))
            ivBg.visibility = View.VISIBLE
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

        tvSubjectInfo.text = "$subjectGroup — $subjectName"
        tvTargetInfo.text = "目标：${formatDuration(targetMillis / 1000)}"
        tvTimerDisplay.text = formatMillis(targetMillis)
        lastDisplayText = formatMillis(targetMillis)

        // 显示格言
        findViewById<TextView>(R.id.tv_motto).text = MottoStorage.getCurrent(this)

        // 开始
        startTimestamp = System.currentTimeMillis()
        handler.post(refreshRunnable)

        // 白噪音 & 音乐
        startWhiteNoiseIfEnabled()
        startMusicIfEnabled()

        // 页面进入动画（淡入）
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

    /** 计时数字变化时的缩放弹跳动画 */
    private fun animateDigitChange() {
        val scaleUp = ObjectAnimator.ofFloat(tvTimerDisplay, View.SCALE_X, 1f, 1.08f)
        val scaleUpY = ObjectAnimator.ofFloat(tvTimerDisplay, View.SCALE_Y, 1f, 1.08f)
        val scaleDown = ObjectAnimator.ofFloat(tvTimerDisplay, View.SCALE_X, 1.08f, 1f)
        val scaleDownY = ObjectAnimator.ofFloat(tvTimerDisplay, View.SCALE_Y, 1.08f, 1f)

        scaleUp.duration = 100
        scaleUpY.duration = 100
        scaleDown.duration = 120
        scaleDownY.duration = 120
        scaleDown.interpolator = AccelerateDecelerateInterpolator()
        scaleDownY.interpolator = AccelerateDecelerateInterpolator()

        val set = AnimatorSet()
        set.play(scaleUp).with(scaleUpY)
        set.play(scaleDown).with(scaleDownY).after(scaleUp)
        set.start()
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
