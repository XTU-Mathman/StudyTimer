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
 * 全屏正计时 — 圆形进度环 UI
 * 时间戳差值计时，切后台不中断
 */
class TimerRunningActivity : AppCompatActivity() {

    private lateinit var circularTimer: CircularTimerView
    private lateinit var btnPauseResume: Button
    private lateinit var btnEnd: Button

    // 时间戳差值计时
    private var startTimestamp = 0L
    private var pausedElapsedMillis = 0L
    private var isPaused = false
    private var finished = false

    // 计时总时长（用于进度计算，正计时无上限，用当前最大值模拟）
    private var maxExpectedMillis = 3600_000L  // 默认1小时满环

    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (!isPaused) {
                val elapsed = System.currentTimeMillis() - startTimestamp + pausedElapsedMillis
                val text = formatMillis(elapsed)

                // 更新环：随着时间推移，进度从0→1
                // 1小时满环，超过后继续但环已满
                val prog = (elapsed.toFloat() / maxExpectedMillis).coerceAtMost(1f)
                circularTimer.timeText = text
                circularTimer.progress = prog

                // 状态文本显示已用时间描述
                circularTimer.statusText = if (elapsed < 60_000) "刚刚开始"
                else if (elapsed < 600_000) "持续专注中"
                else if (elapsed < 1800_000) "状态良好"
                else "深度专注"
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

        // 自定义背景（安全加载，避免 OOM）
        val bgPath = ProfileStorage.getBackgroundPath(this)
        val ivBg = findViewById<ImageView>(R.id.iv_background)
        if (bgPath != null) {
            val bitmap = decodeSampledBitmap(bgPath, 1080, 1920)
            if (bitmap != null) {
                ivBg.setImageBitmap(bitmap)
                ivBg.visibility = View.VISIBLE
            }
        }

        subjectGroup = intent.getStringExtra("subject_group") ?: "未分类"
        subjectName = intent.getStringExtra("subject_name") ?: "未命名"
        circularTimer.subjectText = "$subjectGroup — $subjectName"

        // 显示格言
        findViewById<TextView>(R.id.tv_motto).text = MottoStorage.getCurrent(this)

        // 设置倒计时模式=false（正计时）
        circularTimer.isCountdown = false
        circularTimer.timeText = "00:00:00"
        circularTimer.progress = 0f
        circularTimer.statusText = "准备开始"

        // 开始计时
        startTimestamp = System.currentTimeMillis()
        handler.post(refreshRunnable)

        // 启动前台服务保活
        TimerService.startService(this, "$subjectGroup · $subjectName", false)

        // 白噪音 & 音乐
        startWhiteNoiseIfEnabled()
        startMusicIfEnabled()

        // 页面进入动画（淡入 + 环缩放）
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

        // 暂停/继续
        btnPauseResume.setOnClickListener {
            if (isPaused) {
                isPaused = false
                startTimestamp = System.currentTimeMillis()
                handler.post(refreshRunnable)
                btnPauseResume.text = "暂停"
                circularTimer.pulse()  // 恢复时弹跳
            } else {
                isPaused = true
                pausedElapsedMillis += System.currentTimeMillis() - startTimestamp
                handler.removeCallbacks(refreshRunnable)
                btnPauseResume.text = "继续"
                circularTimer.statusText = "已暂停"
            }
        }

        // 结束
        btnEnd.setOnClickListener { saveAndFinish() }

        // 返回键拦截（替代已弃用的 onBackPressed）
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                AlertDialog.Builder(this@TimerRunningActivity)
                    .setTitle("放弃计时？")
                    .setMessage("计时仍在进行，确定要放弃吗？")
                    .setPositiveButton("确定放弃") { _, _ ->
                        finished = true
                        handler.removeCallbacks(refreshRunnable)
                        WhiteNoiseEngine.getInstance().stop()
                        stopMusic()
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
        val totalMillis = if (isPaused) pausedElapsedMillis
            else pausedElapsedMillis + (System.currentTimeMillis() - startTimestamp)
        val totalSeconds = totalMillis / 1000

        if (totalSeconds > 0) {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val todayDate = dateFormat.format(Date())
            StorageHelper.saveRecord(this, TimerRecord(
                subjectGroup, subjectName, todayDate, totalSeconds
            ))
            // 桥接：检查内置学习目标
            TodoStorage.checkAndUpdateStudyGoal(this)
            Toast.makeText(this, "已记录：${formatMillis(totalMillis)}", Toast.LENGTH_SHORT).show()
        }
        MottoStorage.moveToNext(this)
        WhiteNoiseEngine.getInstance().stop()
        stopMusic()
        TimerService.stopService(this)
        finish()
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

    /** 安全加载大图：先采样尺寸，再按比例加载，避免 OOM */
    private fun decodeSampledBitmap(path: String, reqWidth: Int, reqHeight: Int): android.graphics.Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, options)
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            BitmapFactory.decodeFile(path, options)
        } catch (_: Exception) { null }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height, width) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
