package com.example.studytimer

import android.content.Context

/**
 * 音频设置存储：白噪音音量、音乐音量、淡入时长
 */
object AudioSettingsStorage {

    private const val PREFS = "study_timer_profile"
    private const val KEY_NOISE_VOLUME = "audio_noise_volume"
    private const val KEY_MUSIC_VOLUME = "audio_music_volume"
    private const val KEY_FADE_IN = "audio_fade_in_seconds"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 白噪音音量 0-100，默认 70 */
    fun getNoiseVolume(ctx: Context): Int = prefs(ctx).getInt(KEY_NOISE_VOLUME, 70)
    fun setNoiseVolume(ctx: Context, v: Int) { prefs(ctx).edit().putInt(KEY_NOISE_VOLUME, v.coerceIn(0, 100)).apply() }

    /** 音乐音量 0-100，默认 80 */
    fun getMusicVolume(ctx: Context): Int = prefs(ctx).getInt(KEY_MUSIC_VOLUME, 80)
    fun setMusicVolume(ctx: Context, v: Int) { prefs(ctx).edit().putInt(KEY_MUSIC_VOLUME, v.coerceIn(0, 100)).apply() }

    /** 淡入秒数 0/15/30/60，默认 0（不淡入） */
    fun getFadeInSeconds(ctx: Context): Int = prefs(ctx).getInt(KEY_FADE_IN, 0)
    fun setFadeInSeconds(ctx: Context, v: Int) { prefs(ctx).edit().putInt(KEY_FADE_IN, v).apply() }
}
