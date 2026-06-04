package com.example.studytimer

import android.content.Context
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * 个人设置存储
 */
object ProfileStorage {

    private const val PREFS_NAME = "study_timer_profile"
    private const val KEY_BG_PATH = "timer_bg_path"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** 保存背景图片（复制到内部存储），返回保存后的路径 */
    fun saveBackground(context: Context, uri: Uri): String? {
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return null
            val dir = File(context.filesDir, "backgrounds")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "timer_bg.jpg")
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
            input.close()
            val path = file.absolutePath
            getPrefs(context).edit().putString(KEY_BG_PATH, path).apply()
            path
        } catch (e: Exception) {
            null
        }
    }

    /** 获取背景图片路径（不存在返回 null） */
    fun getBackgroundPath(context: Context): String? {
        val path = getPrefs(context).getString(KEY_BG_PATH, null) ?: return null
        // 检查文件是否存在
        return if (File(path).exists()) path else {
            getPrefs(context).edit().remove(KEY_BG_PATH).apply()
            null
        }
    }

    /** 清除背景图片 */
    fun clearBackground(context: Context) {
        val path = getBackgroundPath(context)
        if (path != null) File(path).delete()
        getPrefs(context).edit().remove(KEY_BG_PATH).apply()
    }
}
