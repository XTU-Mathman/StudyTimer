package com.example.studytimer

import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * 计时页基类 — 提取共享逻辑
 * - 背景图安全加载（避免 OOM）
 * - 白噪音 & 音乐管理
 * - 格言显示
 * - 进入动画
 */
abstract class BaseTimerActivity : AppCompatActivity() {

    protected var mediaPlayer: MediaPlayer? = null

    // ==================== 背景图加载 ====================

    /** 安全加载自定义背景，带采样缩放 */
    protected fun loadCustomBackground(ivBg: ImageView) {
        val bgPath = ProfileStorage.getBackgroundPath(this) ?: return
        val bitmap = decodeSampledBitmap(bgPath, 1080, 1920)
        if (bitmap != null) {
            ivBg.setImageBitmap(bitmap)
            ivBg.visibility = View.VISIBLE
        }
    }

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

    // ==================== 白噪音 & 音乐 ====================

    protected fun startWhiteNoiseIfEnabled() {
        if (WhiteNoiseStorage.isEnabled(this)) {
            val type = WhiteNoiseStorage.getSelectedType(this)
            if (type != null) {
                WhiteNoiseEngine.getInstance().volume = AudioSettingsStorage.getNoiseVolume(this) / 100f
                WhiteNoiseEngine.getInstance().play(type)
            }
        }
    }

    protected fun startMusicIfEnabled() {
        if (MusicStorage.isEnabled(this)) {
            val track = MusicStorage.getSelectedTrack(this) ?: return
            val targetVolume = AudioSettingsStorage.getMusicVolume(this) / 100f
            val fadeInSec = AudioSettingsStorage.getFadeInSeconds(this)
            try {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(track.path)
                    prepare()
                    isLooping = true
                    if (fadeInSec > 0) {
                        setVolume(0f, 0f)
                        start()
                        fadeInVolume(targetVolume, fadeInSec * 1000L)
                    } else {
                        setVolume(targetVolume, targetVolume)
                        start()
                    }
                }
            } catch (_: Exception) {
                mediaPlayer?.release()
                mediaPlayer = null
            }
        }
    }

    private fun fadeInVolume(target: Float, durationMs: Long) {
        val steps = 20
        val interval = durationMs / steps
        val handler = Handler(Looper.getMainLooper())
        for (i in 1..steps) {
            handler.postDelayed({
                try {
                    val v = target * i / steps
                    mediaPlayer?.setVolume(v, v)
                } catch (_: Exception) {}
            }, interval * i)
        }
    }

    protected fun stopMusic() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
    }

    protected fun stopAllAudio() {
        WhiteNoiseEngine.getInstance().stop()
        stopMusic()
    }

    // ==================== 格言 ====================

    protected fun loadMotto(tvMotto: TextView) {
        tvMotto.text = MottoStorage.getCurrent(this)
    }

    // ==================== 进入动画 ====================

    protected fun playEnterAnimation(timerView: CircularTimerView, contentView: View) {
        timerView.scaleX = 0.8f
        timerView.scaleY = 0.8f
        timerView.alpha = 0f
        timerView.animate()
            .scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(500)
            .setInterpolator(DecelerateInterpolator())
            .start()

        contentView.alpha = 0f
        contentView.animate()
            .alpha(1f)
            .setDuration(400)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    // ==================== 格式化 ====================

    protected fun formatMillis(millis: Long): String {
        val totalSeconds = millis / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return String.format("%02d:%02d:%02d", h, m, s)
    }

    protected fun formatDuration(sec: Long): String {
        val h = sec / 3600; val m = (sec % 3600) / 60
        return buildString {
            if (h > 0) append("${h}小时")
            if (m > 0) append("${m}分")
            if (isEmpty()) append("0分")
        }
    }

    // ==================== 生命周期 ====================

    override fun onDestroy() {
        super.onDestroy()
        stopMusic()
    }
}
