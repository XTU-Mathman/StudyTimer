package com.example.studytimer

import android.content.Context

/**
 * 白噪音用户偏好存取（SharedPreferences）
 */
object WhiteNoiseStorage {

    private const val PREFS_NAME = "white_noise_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_NOISE_TYPE = "noise_type"

    /** 是否启用白噪音 */
    fun isEnabled(ctx: Context): Boolean {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)
    }

    fun setEnabled(ctx: Context, enabled: Boolean) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** 获取用户选择的白噪音类型，null 表示未选择 */
    fun getSelectedType(ctx: Context): NoiseType? {
        val name = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_NOISE_TYPE, null) ?: return null
        return try { NoiseType.valueOf(name) } catch (_: Exception) { null }
    }

    fun setSelectedType(ctx: Context, type: NoiseType?) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_NOISE_TYPE, type?.name).apply()
    }
}
