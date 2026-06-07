package com.example.studytimer

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin
import java.util.Random

/**
 * 白噪音类型
 */
enum class NoiseType(val label: String) {
    RAIN("雨声"),
    SNOW("雪声"),
    RAIN_ON_GLASS("雨打玻璃"),
    FROG("青蛙叫"),
    STREAM("溪流"),
    WIND("风声"),
    CICADA("蝉鸣")
}

/**
 * 基于 AudioTrack 的合成白噪音引擎
 * 纯算法生成 PCM 音频，无需外部资源文件
 */
class WhiteNoiseEngine {

    private var audioTrack: AudioTrack? = null
    @Volatile private var playing = false
    private var playThread: Thread? = null
    private val random = Random()

    companion object {
        const val SAMPLE_RATE = 22050
        private val instance = WhiteNoiseEngine()

        fun getInstance() = instance
    }

    /** 开始播放指定类型的白噪音 */
    fun play(type: NoiseType) {
        stop()
        playing = true
        playThread = Thread {
            var localTrack: AudioTrack? = null
            try {
                val minBuf = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val bufferSize = maxOf(minBuf, SAMPLE_RATE / 10) // ~100ms per write
                val buffer = ShortArray(bufferSize)

                localTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize * 2) // 2 bytes per short
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack = localTrack
                localTrack.play()

                val state = GeneratorState()

                while (playing) {
                    when (type) {
                        NoiseType.RAIN -> fillRain(buffer, state)
                        NoiseType.SNOW -> fillSnow(buffer, state)
                        NoiseType.RAIN_ON_GLASS -> fillRainOnGlass(buffer, state)
                        NoiseType.FROG -> fillFrog(buffer, state)
                        NoiseType.STREAM -> fillStream(buffer, state)
                        NoiseType.WIND -> fillWind(buffer, state)
                        NoiseType.CICADA -> fillCicada(buffer, state)
                    }
                    localTrack.write(buffer, 0, buffer.size)
                }
            } catch (_: Exception) {
                // 线程被中断或 AudioTrack 已释放
            } finally {
                localTrack?.stop()
                localTrack?.release()
                localTrack = null
            }
        }
        playThread?.start()
    }

    /** 停止播放 */
    fun stop() {
        playing = false
        playThread?.interrupt()
        // 等待播放线程结束（finally 中会清理 AudioTrack）
        val t = playThread
        playThread = null
        if (t != null && t.isAlive && t != Thread.currentThread()) {
            try { t.join(500) } catch (_: Exception) {}
        }
        // 超时兜底：如果线程还没结束，强制清理
        if (audioTrack != null) {
            releaseAudioTrack()
        }
    }

    val isPlaying: Boolean get() = playing

    private fun releaseAudioTrack() {
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) { }
        audioTrack = null
    }

    // ==================== 噪音生成算法 ====================

    /**
     * 每个生成器维护的状态。所有数字变量都是跨 buffer 连续的，
     * 保证滤波器和振荡器在 buffer 边界处不跳变。
     */
    private class GeneratorState {
        // 通用
        var lpf = 0.0       // 低通滤波器状态
        var lpf2 = 0.0      // 第二级低通
        var phase = 0.0     // 振荡器相位

        // 粉红噪声
        var p0 = 0.0; var p1 = 0.0; var p2 = 0.0
        var p3 = 0.0; var p4 = 0.0; var p5 = 0.0

        // 青蛙叫
        var frogTimer = 0   // 距下一次蛙叫的采样数
        var frogEnv = 0.0   // 当前蛙叫包络
        var frogFreq = 0.0  // 当前蛙叫频率
        var frogPhase = 0.0

        // 蝉鸣
        var cicadaFmPhase = 0.0
        var cicadaAmPhase = 0.0
    }

    /** 下一帧递进相位，溢出回绕 */
    private inline fun advancePhase(state: GeneratorState, hz: Double) {
        state.phase += hz / SAMPLE_RATE * 2.0 * PI
        if (state.phase > 2.0 * PI) state.phase -= 2.0 * PI
    }

    // ---- 雨声：白噪声 + 强低通 + 慢幅调制 ----
    private fun fillRain(buf: ShortArray, s: GeneratorState) {
        for (i in buf.indices) {
            val white = random.nextGaussian() * 0.35
            s.lpf += (white - s.lpf) * 0.04              // 强低通
            advancePhase(s, 0.4)
            val mod = 0.65 + 0.35 * sin(s.phase)         // 0.4 Hz 调制
            val v = (s.lpf * mod * Short.MAX_VALUE * 0.75).toInt()
            buf[i] = v.coerceIn(-32768, 32767).toShort()
        }
    }

    // ---- 雪声：粉红噪声（多级低通叠加近似 1/f 谱） ----
    private fun fillSnow(buf: ShortArray, s: GeneratorState) {
        for (i in buf.indices) {
            val white = random.nextGaussian() * 0.15
            // 6 级不同截止频率的低通叠加 → 近似粉红噪声
            s.p0 += (white - s.p0) * 0.001
            s.p1 += (white - s.p1) * 0.003
            s.p2 += (white - s.p2) * 0.01
            s.p3 += (white - s.p3) * 0.03
            s.p4 += (white - s.p4) * 0.1
            s.p5 += (white - s.p5) * 0.3
            val pink = (s.p0 + s.p1 + s.p2 + s.p3 + s.p4 + s.p5) / 6.0 * 0.7
            val v = (pink * Short.MAX_VALUE * 0.9).toInt()
            buf[i] = v.coerceIn(-32768, 32767).toShort()
        }
    }

    // ---- 雨打玻璃：低底噪 + 随机尖锐脉冲 ----
    private fun fillRainOnGlass(buf: ShortArray, s: GeneratorState) {
        for (i in buf.indices) {
            // 底噪（极柔和的低通白噪）
            val white = random.nextGaussian() * 0.05
            s.lpf += (white - s.lpf) * 0.02
            var sample = s.lpf

            // 随机脉冲（模拟雨滴打在玻璃上）
            if (random.nextDouble() < 0.008) {  // ~每秒 175 个脉冲
                val strength = 0.6 + random.nextDouble() * 0.4
                sample += random.nextGaussian() * strength
            }

            // 第二级低通柔化脉冲
            s.lpf2 += (sample - s.lpf2) * 0.1
            val v = (s.lpf2 * Short.MAX_VALUE * 0.7).toInt()
            buf[i] = v.coerceIn(-32768, 32767).toShort()
        }
    }

    // ---- 青蛙叫：随机间隔的低频脉冲 ----
    private fun fillFrog(buf: ShortArray, s: GeneratorState) {
        for (i in buf.indices) {
            // 计时触发下一次蛙叫
            if (s.frogTimer <= 0) {
                // 随机间隔 0.8~3.5 秒
                s.frogTimer = (SAMPLE_RATE * (0.8 + random.nextDouble() * 2.7)).toInt()
                s.frogEnv = 1.0
                s.frogFreq = 180.0 + random.nextDouble() * 120.0  // 180-300 Hz
                s.frogPhase = 0.0
            }

            var sample = 0.0
            if (s.frogEnv > 0.001) {
                // 包络：快速起音 → 指数衰减
                s.frogPhase += s.frogFreq / SAMPLE_RATE * 2.0 * PI
                if (s.frogPhase > 2.0 * PI) s.frogPhase -= 2.0 * PI
                sample = sin(s.frogPhase) * s.frogEnv
                s.frogEnv *= 0.997  // 衰减
            } else {
                s.frogEnv = 0.0
            }

            // 混入极低底噪
            sample += random.nextGaussian() * 0.01

            s.frogTimer--
            val v = (sample * Short.MAX_VALUE * 0.7).toInt()
            buf[i] = v.coerceIn(-32768, 32767).toShort()
        }
    }

    // ---- 溪流：高频白噪 + 带通效果 + 幅调制 ----
    private fun fillStream(buf: ShortArray, s: GeneratorState) {
        for (i in buf.indices) {
            val white = random.nextGaussian() * 0.25
            // 高通 + 低通 → 带通
            val hp = white - s.lpf
            s.lpf += (white - s.lpf) * 0.05
            s.lpf2 += (hp - s.lpf2) * 0.08

            advancePhase(s, 0.6)
            val mod = 0.6 + 0.4 * sin(s.phase)
            val v = (s.lpf2 * mod * Short.MAX_VALUE * 0.7).toInt()
            buf[i] = v.coerceIn(-32768, 32767).toShort()
        }
    }

    // ---- 风声：极低频噪声 + 阵风幅调制 ----
    private fun fillWind(buf: ShortArray, s: GeneratorState) {
        for (i in buf.indices) {
            val white = random.nextGaussian() * 0.3
            // 强低通 → 只有低频分量
            s.lpf += (white - s.lpf) * 0.008

            // 阵风：慢速大幅调制 + 快速小幅调制
            advancePhase(s, 0.15)     // 0.15 Hz — 主阵风周期
            val gust = 0.5 + 0.5 * sin(s.phase)
            // 叠加一个稍快的波动模拟阵风中的变化
            val ripple = 1.0 + 0.2 * sin(s.phase * 5.0)
            val mod = gust * ripple

            val v = (s.lpf * mod * Short.MAX_VALUE * 0.8).toInt()
            buf[i] = v.coerceIn(-32768, 32767).toShort()
        }
    }

    // ---- 蝉鸣：~6kHz 调频持续音 + 脉冲幅调制 ----
    private fun fillCicada(buf: ShortArray, s: GeneratorState) {
        for (i in buf.indices) {
            // FM 调制器：独立的相位累加器
            s.cicadaAmPhase += 23.0 / SAMPLE_RATE * 2.0 * PI
            if (s.cicadaAmPhase > 2.0 * PI) s.cicadaAmPhase -= 2.0 * PI
            val fm = sin(s.cicadaAmPhase) * 300.0           // ±300 Hz 频偏

            // 载波
            s.cicadaFmPhase += (6000.0 + fm) / SAMPLE_RATE * 2.0 * PI
            if (s.cicadaFmPhase > 2.0 * PI) s.cicadaFmPhase -= 2.0 * PI

            var sample = sin(s.cicadaFmPhase)

            // AM 脉冲调制：~100 Hz 方波
            advancePhase(s, 100.0)
            val pulse = 0.4 + 0.6 * (if (sin(s.phase) > 0.0) 1.0 else 0.0)
            sample *= pulse * 0.5

            // 混入少量高频噪底
            sample += random.nextGaussian() * 0.02

            val v = (sample * Short.MAX_VALUE).toInt()
            buf[i] = v.coerceIn(-32768, 32767).toShort()
        }
    }
}
