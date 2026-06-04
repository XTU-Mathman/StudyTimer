package com.example.studytimer

import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
 * 全屏正计时：时间戳差值计时，切后台不影响
 */
class TimerRunningActivity : AppCompatActivity() {

    private lateinit var tvSubjectInfo: TextView
    private lateinit var tvTimerDisplay: TextView
    private lateinit var btnPauseResume: Button
    private lateinit var btnEnd: Button

    // 时间戳差值计时
    private var startTimestamp = 0L       // 本轮开始时间戳
    private var pausedElapsedMillis = 0L  // 暂停时已累计的毫秒数
    private var isPaused = false
    private var finished = false

    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (!isPaused) {
                // 实时计算：已过时间 = (现在 - 开始) + 暂停前累计
                val elapsed = System.currentTimeMillis() - startTimestamp + pausedElapsedMillis
                tvTimerDisplay.text = formatMillis(elapsed)
            }
            handler.postDelayed(this, 200)  // 200ms 刷新一次，比 1s 更流畅
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

        // 加载自定义背景
        val bgPath = ProfileStorage.getBackgroundPath(this)
        val ivBg = findViewById<ImageView>(R.id.iv_background)
        val layoutContent = findViewById<android.view.View>(R.id.layout_content)
        if (bgPath != null) {
            ivBg.setImageBitmap(BitmapFactory.decodeFile(bgPath))
            ivBg.visibility = android.view.View.VISIBLE
            // 内容区改为半透明，让背景透出
            layoutContent.setBackgroundColor(0xCCF5F9FC.toInt())
        }

        subjectGroup = intent.getStringExtra("subject_group") ?: "未分类"
        subjectName = intent.getStringExtra("subject_name") ?: "未命名"
        tvSubjectInfo.text = "$subjectGroup - $subjectName"

        // 显示当前格言
        findViewById<TextView>(R.id.tv_motto).text = MottoStorage.getCurrent(this)

        // 开始计时
        startTimestamp = System.currentTimeMillis()
        handler.post(refreshRunnable)

        // 启动白噪音（如果已启用）
        startWhiteNoiseIfEnabled()

        // 启动音乐（如果已启用）
        startMusicIfEnabled()

        // 暂停/继续
        btnPauseResume.setOnClickListener {
            if (isPaused) {
                // 恢复：重置开始时间戳
                isPaused = false
                startTimestamp = System.currentTimeMillis()
                handler.post(refreshRunnable)
                btnPauseResume.text = "暂停"
            } else {
                // 暂停：保存已累计时间
                isPaused = true
                pausedElapsedMillis += System.currentTimeMillis() - startTimestamp
                handler.removeCallbacks(refreshRunnable)
                btnPauseResume.text = "继续"
            }
        }

        // 结束
        btnEnd.setOnClickListener { saveAndFinish() }
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
        // 切换到下一条格言
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
                finished = true  // 防 onStop 纯净模式误存
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
