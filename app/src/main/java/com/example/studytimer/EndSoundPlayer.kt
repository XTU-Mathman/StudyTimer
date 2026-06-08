package com.example.studytimer

import android.content.Context
import android.media.ToneGenerator
import android.media.AudioManager

/**
 * 计时结束音效播放器 — 使用系统 ToneGenerator 产生提示音，无需资源文件
 */
object EndSoundPlayer {

    private var toneGen: ToneGenerator? = null

    /** 播放一个简短的提示音 */
    fun play(ctx: Context) {
        try {
            // 使用系统 STREAM_NOTIFICATION 通道播放标准提示音
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            tg.startTone(ToneGenerator.TONE_PROP_NACK, 300)
            // 短暂持有后释放
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try { tg.release() } catch (_: Exception) {}
            }, 500)
        } catch (_: Exception) {
            // 降级：使用系统默认通知音
            val uri = android.provider.Settings.System.DEFAULT_NOTIFICATION_URI
            val mediaPlayer = android.media.MediaPlayer().apply {
                try {
                    setDataSource(ctx, uri)
                    setVolume(0.5f, 0.5f)
                    prepare()
                    start()
                } catch (_: Exception) {}
            }
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try { mediaPlayer.release() } catch (_: Exception) {}
            }, 1000)
        }
    }
}
