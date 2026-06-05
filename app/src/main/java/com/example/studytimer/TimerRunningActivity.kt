package com.example.studytimer

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
 * 全屏正计时：时间戳差值计时，切后台不中断
 * 高级简约风格 — 大数字 + 缩放动画 + 药丸按钮
 */
class TimerRunningActivity : AppCompatActivity() {

    private lateinit var tvSubjectInfo: TextView
    private lateinit var tvTimerDisplay: TextView
    private lateinit var btnPauseResume: Button
    private lateinit var btnEnd: Button

    // 时间戳差值计时
    private var startTimestamp = 0L
    private var pausedElapsedMillis = 0L
    private var isPaused = false
    private var finished = false

    // 数字动画追踪
    private var lastDisplayText = "00:00:00"

    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (!isPaused) {
                val elapsed = System.currentTimeMillis() - startTimestamp + pausedElapsedMillis
                val text = formatMillis(elapsed)
                if (text != tvTimerDisplay.text.toString()) {
                    tvTimerDisplay.text = text
                    animateDigitChange()
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

        tvSubjectInfo = findViewById(R.id.tv_subject_info)
        tvTimerDisplay = findViewById(R.id.tv_timer_display)
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
        tvSubjectInfo.text = "$subjectGroup — $subjectName"

        // 显示格言
        findViewById<TextView>(R.id.tv_motto).text = MottoStorage.getCurrent(this)

        // 开始计时
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

        // 暂停/继续
        btnPauseResume.setOnClickListener {
            if (isPaused) {
                isPaused = false
                startTimestamp = System.currentTimeMillis()
                handler.post(refreshRunnable)
                btnPauseResume.text = "暂停"
            } else {
                isPaused = true
                pausedElapsedMillis += System.currentTimeMillis() - startTimestamp
                handler.removeCallbacks(refreshRunnable)
                btnPauseResume.text = "继续"
            }
        }

        // 结束
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

    private fun saveAndFinish() {
        if (finished) return
        finished = true
        handler.removeCallbacks(refreshRunnable)
        val totalMillis = if (isPaused) pausedElapsedMillis
            else pausedElapsedMillis + (System.currentTimeMillis() - startTimestamp)
        val totalSeconds = totalMillis / 1000

        if (totalSeconds > 0) {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val todayDate = dateFormat.format(Date())
            StorageHelper.saveRecord(this, TimerRecord(
                subjectGroup, subjectName, todayDate, totalSeconds
            ))
            Toast.makeText(this, "已记录：${formatMillis(totalMillis)}", Toast.LENGTH_SHORT).show()
        }
        MottoStorage.moveToNext(this)
        WhiteNoiseEngine.getInstance().stop()
        stopMusic()
        finish()
    }

    override fun onBackPressed() {
        AlertDialog.Builder(this)
            .setTitle("放弃计时？")
            .setMessage("计时仍在进行，确定要放弃吗？")
            .setPositiveButton("确定放弃") { _, _ ->
                finished = true
                handler.removeCallbacks(refreshRunnable)
                WhiteNoiseEngine.getInstance().stop()
                stopMusic()
                finish()
            }
            .setNegativeButton("继续计时", null).show()
    }

    private fun formatMillis(millis: Long): String {
        val totalSeconds = millis / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return String.format("%02d:%02d:%02d", h, m, s)
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
