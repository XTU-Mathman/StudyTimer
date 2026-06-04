package com.example.studytimer

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * 音乐文件存储 + 用户偏好
 */
object MusicStorage {

    private const val PREFS_NAME = "music_prefs"
    private const val KEY_TRACKS = "tracks"        // JSON 数组
    private const val KEY_SELECTED = "selected"     // 选中的 index（-1 表示无）
    private const val KEY_ENABLED = "enabled"

    /**
     * 一首音乐
     */
    data class MusicTrack(
        val name: String,
        val path: String   // 内部存储绝对路径
    )

    // ============ 存取 ============

    private fun getPrefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 获取全部曲目列表 */
    fun getTracks(ctx: Context): List<MusicTrack> {
        val json = getPrefs(ctx).getString(KEY_TRACKS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val path = obj.getString("path")
                // 过滤已删除的文件
                if (File(path).exists()) {
                    MusicTrack(obj.getString("name"), path)
                } else null
            }.filterNotNull()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** 添加一首音乐（复制到内部存储） */
    fun addTrack(ctx: Context, uri: Uri): MusicTrack? {
        return try {
            val dir = File(ctx.filesDir, "music")
            if (!dir.exists()) dir.mkdirs()

            // 从 URI 推断文件名
            val displayName = getDisplayName(ctx, uri) ?: "track_${System.currentTimeMillis()}.mp3"
            val fileName = "${System.currentTimeMillis()}_${displayName.replace("/", "_")}"
            val file = File(dir, fileName)

            // 复制文件
            val input = ctx.contentResolver.openInputStream(uri) ?: return null
            FileOutputStream(file).use { output -> input.copyTo(output) }
            input.close()

            // 存到 JSON 列表
            val tracks = getTracks(ctx).toMutableList()
            val track = MusicTrack(displayName, file.absolutePath)
            tracks.add(track)
            saveTracks(ctx, tracks)

            track
        } catch (_: Exception) {
            null
        }
    }

    /** 删除一首音乐（同时删除文件） */
    fun deleteTrack(ctx: Context, index: Int) {
        val tracks = getTracks(ctx).toMutableList()
        if (index in tracks.indices) {
            val track = tracks[index]
            try { File(track.path).delete() } catch (_: Exception) {}
            tracks.removeAt(index)
            saveTracks(ctx, tracks)

            // 如果删除的是当前选中的，清除选中
            val sel = getSelectedIndex(ctx)
            if (sel == index) setSelectedIndex(ctx, -1)
            else if (sel > index) setSelectedIndex(ctx, sel - 1) // 调整 index
        }
    }

    private fun saveTracks(ctx: Context, tracks: List<MusicTrack>) {
        val arr = JSONArray()
        for (t in tracks) {
            arr.put(JSONObject().apply {
                put("name", t.name)
                put("path", t.path)
            })
        }
        getPrefs(ctx).edit().putString(KEY_TRACKS, arr.toString()).apply()
    }

    // ============ 选中 ============

    fun getSelectedIndex(ctx: Context): Int =
        getPrefs(ctx).getInt(KEY_SELECTED, -1)

    fun setSelectedIndex(ctx: Context, index: Int) {
        getPrefs(ctx).edit().putInt(KEY_SELECTED, index).apply()
    }

    /** 获取当前选中的曲目，null 表示未选择 */
    fun getSelectedTrack(ctx: Context): MusicTrack? {
        val idx = getSelectedIndex(ctx)
        val tracks = getTracks(ctx)
        return if (idx in tracks.indices) tracks[idx] else {
            // 修复无效选中
            if (idx != -1) setSelectedIndex(ctx, -1)
            null
        }
    }

    // ============ 开关 ============

    fun isEnabled(ctx: Context): Boolean =
        getPrefs(ctx).getBoolean(KEY_ENABLED, false)

    fun setEnabled(ctx: Context, enabled: Boolean) {
        getPrefs(ctx).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    // ============ 工具 ============

    /** 从 content URI 推断文件名 */
    private fun getDisplayName(ctx: Context, uri: Uri): String? {
        val cursor = ctx.contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) it.getString(idx) else null
            } else null
        }
    }
}
