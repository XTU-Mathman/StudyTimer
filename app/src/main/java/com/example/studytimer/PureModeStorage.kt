package com.example.studytimer

import android.content.Context

/**
 * 纯净模式：开启后切后台计时立刻停止
 */
object PureModeStorage {

    private const val PREFS_NAME = "pure_mode_prefs"
    private const val KEY_ENABLED = "enabled"

    fun isEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun setEnabled(ctx: Context, enabled: Boolean) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
    }
}
