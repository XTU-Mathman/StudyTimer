package com.example.studytimer

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 我的页面：自定义背景 + 格言入口
 */
class ProfileFragment : Fragment() {

    private lateinit var tvBgStatus: TextView
    private lateinit var btnClearBg: TextView
    private lateinit var tvCheckinStatus: TextView
    private lateinit var tvNoiseStatus: TextView
    private lateinit var tvMusicStatus: TextView
    private lateinit var tvPureModeStatus: TextView
    private lateinit var tvStreakStatus: TextView
    private lateinit var tvSedentaryStatus: TextView

    // 音乐试听播放器（类级别管理，避免泄漏）
    private var previewPlayer: android.media.MediaPlayer? = null

    // 对话框引用（防止堆叠）
    private var noiseDialog: AlertDialog? = null
    private var musicDialog: AlertDialog? = null
    private var pureDialog: AlertDialog? = null

    // 折叠组状态跟踪（用于 collapsible groups 优化）
    private val collapsedGroups = mutableSetOf<String>()

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            ProfileStorage.saveBackground(requireContext(), uri)
            Toast.makeText(requireContext(), "背景已设置！", Toast.LENGTH_SHORT).show()
            updateBgUI()
        }
    }

    private val pickMusicLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val track = MusicStorage.addTrack(requireContext(), uri)
            if (track != null) {
                Toast.makeText(requireContext(), "已添加：${track.name}", Toast.LENGTH_SHORT).show()
                updateMusicUI()
            } else {
                Toast.makeText(requireContext(), "添加失败，请重试", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val importDataLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            importDataFromUri(uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvBgStatus = view.findViewById(R.id.tv_bg_status)
        btnClearBg = view.findViewById(R.id.btn_clear_bg)
        tvCheckinStatus = view.findViewById(R.id.tv_checkin_status)
        tvNoiseStatus = view.findViewById(R.id.tv_noise_status)
        tvMusicStatus = view.findViewById(R.id.tv_music_status)
        tvPureModeStatus = view.findViewById(R.id.tv_pure_mode_status)

        view.findViewById<View>(R.id.item_background).setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        btnClearBg.setOnClickListener {
            ProfileStorage.clearBackground(requireContext())
            Toast.makeText(requireContext(), "已清除背景", Toast.LENGTH_SHORT).show()
            updateBgUI()
        }

        // 格言入口
        view.findViewById<View>(R.id.item_motto).setOnClickListener {
            showMottoDialog()
        }

        // 自律打卡入口
        view.findViewById<View>(R.id.item_checkin).setOnClickListener {
            showCheckinDialog()
        }

        // 白噪音入口
        view.findViewById<View>(R.id.item_white_noise).setOnClickListener {
            showWhiteNoiseDialog()
        }

        // 音乐入口
        view.findViewById<View>(R.id.item_music).setOnClickListener {
            showMusicDialog()
        }

        // 纯净模式入口
        view.findViewById<View>(R.id.item_pure_mode).setOnClickListener {
            showPureModeDialog()
        }

        // 音频设置入口
        view.findViewById<View>(R.id.item_audio_settings).setOnClickListener {
            showAudioSettingsDialog()
        }

        // 连续学习天数
        tvStreakStatus = view.findViewById(R.id.tv_streak_status)
        view.findViewById<View>(R.id.item_streak).setOnClickListener {
            showStreakDetailDialog()
        }

        // 学习记录管理
        view.findViewById<View>(R.id.item_records).setOnClickListener {
            showRecordsDialog()
        }

        // 数据备份
        view.findViewById<View>(R.id.item_backup).setOnClickListener {
            showBackupDialog()
        }

        // 久坐提醒
        tvSedentaryStatus = view.findViewById(R.id.tv_sedentary_status)
        view.findViewById<View>(R.id.item_sedentary).setOnClickListener {
            showSedentaryDialog()
        }

        updateBgUI()
        updateCheckinStatus()
        updateNoiseUI()
        updateMusicUI()
        updatePureModeUI()
        updateStreakStatus()
        updateSedentaryStatus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        releasePreviewPlayer()
        noiseDialog?.dismiss(); noiseDialog = null
        musicDialog?.dismiss(); musicDialog = null
        pureDialog?.dismiss(); pureDialog = null
    }

    /** 安全释放试听播放器 */
    private fun releasePreviewPlayer() {
        try {
            previewPlayer?.stop()
            previewPlayer?.release()
        } catch (_: Exception) {}
        previewPlayer = null
    }

    private fun updateBgUI() {
        val hasBg = ProfileStorage.getBackgroundPath(requireContext()) != null
        tvBgStatus.text = if (hasBg) "已设置 ✅" else "未设置"
        btnClearBg.visibility = if (hasBg) View.VISIBLE else View.GONE
    }

    /**
     * 弹出格言管理对话框（完整列表 + 添加/编辑/删除）
     */
    private fun showMottoDialog() {
        val ctx = requireContext()

        // 对话框内容布局
        val contentLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 0)
        }

        // 添加输入行
        val inputRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 12)
        }
        val etInput = EditText(ctx).apply {
            hint = "输入新格言..."
            textSize = 14f
            setPadding(16, 8, 16, 8)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        inputRow.addView(etInput)
        val btnAdd = makeBtn("添加", "#FF6BA4D1") {
            val text = etInput.text.toString().trim()
            if (text.isNotEmpty()) {
                MottoStorage.add(ctx, text)
                refreshMottoContent(contentLayout)
            }
        }
        inputRow.addView(btnAdd)
        contentLayout.addView(inputRow)

        // 格言列表容器
        val listLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        contentLayout.addView(listLayout)

        fun refreshList() {
            listLayout.removeAllViews()
            val mottos = MottoStorage.getAll(ctx)
            for (i in mottos.indices) {
                val idx = i
                val motto = mottos[i]
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(0, 4, 0, 4)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                val tv = TextView(ctx).apply {
                    text = "${idx + 1}. $motto"
                    textSize = 14f
                    setTextColor(Color.parseColor("#FF2D2D2D"))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                row.addView(tv)
                val btnEdit = makeBtn("改", "#FF6BA4D1") {
                    showEditDialog(ctx, idx, motto) { refreshList() }
                }
                row.addView(btnEdit)
                val btnDel = makeBtn("删", "#FFFF6B6B") {
                    MottoStorage.delete(ctx, idx)
                    refreshList()
                }
                row.addView(btnDel)
                (btnDel.layoutParams as LinearLayout.LayoutParams).marginStart = 4
                listLayout.addView(row)
            }
        }

        refreshList()

        // 包裹在 ScrollView 中
        val scrollView = ScrollView(ctx).apply {
            addView(contentLayout)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 600
            )
        }

        AlertDialog.Builder(ctx)
            .setTitle("管理格言")
            .setView(scrollView)
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun refreshMottoContent(contentLayout: LinearLayout) {
        // 重新构建列表（简单粗暴：重新 show dialog）
        // 重新触发对话框刷新 → 关闭旧对话框开新的
        (contentLayout.parent as? ViewGroup)?.let { parent ->
            val listLayout = (contentLayout.getChildAt(1) as? LinearLayout) ?: return
            listLayout.removeAllViews()
            val mottos = MottoStorage.getAll(requireContext())
            for (i in mottos.indices) {
                val idx = i
                val motto = mottos[i]
                val row = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(0, 4, 0, 4)
                }
                val tv = TextView(requireContext()).apply {
                    text = "${idx + 1}. $motto"
                    textSize = 14f
                    setTextColor(Color.parseColor("#FF2D2D2D"))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                row.addView(tv)
                val btnEdit = makeBtn("改", "#FF6BA4D1") {
                    showEditDialog(requireContext(), idx, motto) {
                        refreshMottoContent(contentLayout)
                    }
                }
                row.addView(btnEdit)
                val btnDel = makeBtn("删", "#FFFF6B6B") {
                    MottoStorage.delete(requireContext(), idx)
                    refreshMottoContent(contentLayout)
                }
                row.addView(btnDel)
                (btnDel.layoutParams as LinearLayout.LayoutParams).marginStart = 4
                listLayout.addView(row)
            }
        }
    }

    private fun showEditDialog(ctx: android.content.Context, index: Int, oldText: String, onDone: () -> Unit) {
        val input = EditText(ctx).apply {
            setText(oldText)
            setPadding(32, 16, 32, 16)
        }
        AlertDialog.Builder(ctx)
            .setTitle("编辑格言")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val newText = input.text.toString().trim()
                if (newText.isNotEmpty()) {
                    MottoStorage.update(ctx, index, newText)
                    onDone()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun updateCheckinStatus() {
        val items = CheckInStorage.getItems(requireContext())
        val todayRecords = CheckInStorage.getTodayRecords(requireContext())
        tvCheckinStatus.text = "${todayRecords.size}/${items.size} 已打卡"
    }

    private fun updateNoiseUI() {
        val enabled = WhiteNoiseStorage.isEnabled(requireContext())
        val type = WhiteNoiseStorage.getSelectedType(requireContext())
        tvNoiseStatus.text = if (enabled && type != null) "✅ ${type.label}" else "未启用"
    }

    private fun updateMusicUI() {
        val enabled = MusicStorage.isEnabled(requireContext())
        val track = MusicStorage.getSelectedTrack(requireContext())
        if (enabled && track != null) {
            tvMusicStatus.text = "✅ ${track.name}"
        } else if (enabled) {
            tvMusicStatus.text = "已启用（未选曲）"
        } else {
            tvMusicStatus.text = "未启用"
        }
    }

    private fun updatePureModeUI() {
        val enabled = PureModeStorage.isEnabled(requireContext())
        tvPureModeStatus.text = if (enabled) "✅ 已开启" else "关闭"
    }

    private var checkinCalendar = Calendar.getInstance()
    private val checkinDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("MM月dd日", Locale.getDefault())

    private fun isToday(cal: Calendar): Boolean {
        val today = Calendar.getInstance()
        return cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
    }

    private fun showCheckinDialog() {
        val ctx = requireContext()
        checkinCalendar = Calendar.getInstance()  // 默认今天

        val contentLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 0)
        }

        lateinit var refreshList: () -> Unit
        lateinit var addRow: LinearLayout

        // 日期导航栏
        val dateNav = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 12)
        }
        val btnPrev = makeBtn("◀", "#FF6BA4D1") {
            checkinCalendar.add(Calendar.DAY_OF_YEAR, -1)
            refreshList()
        }
        dateNav.addView(btnPrev)
        (btnPrev.layoutParams as LinearLayout.LayoutParams).width = 80

        val tvDate = TextView(ctx).apply {
            textSize = 16f
            setTextColor(Color.parseColor("#FF2D2D2D"))
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        dateNav.addView(tvDate)

        val btnNext = makeBtn("▶", "#FF6BA4D1") {
            checkinCalendar.add(Calendar.DAY_OF_YEAR, 1)
            refreshList()
        }
        dateNav.addView(btnNext)
        (btnNext.layoutParams as LinearLayout.LayoutParams).width = 80
        contentLayout.addView(dateNav)

        val listLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }

        refreshList = {
            val dateStr = checkinDateFormat.format(checkinCalendar.time)
            val isToday = isToday(checkinCalendar)
            tvDate.text = if (isToday) "今天（${displayDateFormat.format(checkinCalendar.time)}）"
                else displayDateFormat.format(checkinCalendar.time)

            // 更新添加行的可见性
            addRow.visibility = if (isToday) View.VISIBLE else View.GONE

            listLayout.removeAllViews()
            val records = CheckInStorage.getRecordsByDate(ctx, dateStr)
            val currentItems = CheckInStorage.getItems(ctx)

            for (item in currentItems) {
                val record = records.find { it.itemId == item.id }
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(0, 6, 0, 6)
                }

                val info = if (record != null) {
                    "${item.name}  ✅ ${record.time}"
                } else {
                    "${item.name}  — 未打卡"
                }
                val tv = TextView(ctx).apply {
                    text = info
                    textSize = 15f
                    setTextColor(Color.parseColor(if (record != null) "#FF34C759" else "#FFB0AAA5"))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                row.addView(tv)

                if (record == null && isToday) {
                    // 今天 + 未打卡 → 打卡按钮
                    val btn = makeBtn("打卡", "#FF6BA4D1") {
                        CheckInStorage.checkIn(ctx, item.id)
                        refreshList()
                        updateCheckinStatus()
                    }
                    row.addView(btn)
                } else if (record != null && !item.isPreset) {
                    // 已打卡的自定义项目 → 删除
                    val btn = makeBtn("删", "#FFFF6B6B") {
                        CheckInStorage.deleteItem(ctx, item.id)
                        refreshList()
                        updateCheckinStatus()
                    }
                    row.addView(btn)
                    (btn.layoutParams as LinearLayout.LayoutParams).marginStart = 4
                }

                listLayout.addView(row)
            }
        }

        contentLayout.addView(listLayout)

        // 添加打卡项目（仅今天可见）
        addRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12, 0, 0)
            visibility = if (isToday(checkinCalendar)) View.VISIBLE else View.GONE
        }
        val etInput = EditText(ctx).apply {
            hint = "新增打卡项目..."
            textSize = 14f
            setPadding(16, 8, 16, 8)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        addRow.addView(etInput)
        val btnAdd = makeBtn("添加", "#FF6BA4D1") {
            val name = etInput.text.toString().trim()
            if (name.isNotEmpty()) {
                CheckInStorage.addItem(ctx, name)
                etInput.text?.clear()
                refreshList()
                updateCheckinStatus()
            }
        }
        addRow.addView(btnAdd)
        contentLayout.addView(addRow)

        refreshList()

        val scrollView = ScrollView(ctx).apply {
            addView(contentLayout)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 500)
        }

        AlertDialog.Builder(ctx)
            .setTitle("自律打卡")
            .setView(scrollView)
            .setNegativeButton("关闭") { _, _ -> updateCheckinStatus() }
            .show()
    }

    /**
     * 白噪音选择弹窗
     */
    private fun showWhiteNoiseDialog() {
        val ctx = requireContext()
        val currentType = WhiteNoiseStorage.getSelectedType(ctx)
        val enabled = WhiteNoiseStorage.isEnabled(ctx)
        noiseDialog?.dismiss()
        noiseDialog = null

        val contentLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 0)
        }

        // 开关行
        val toggleRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 12)
        }
        val tvToggleLabel = TextView(ctx).apply {
            text = "启用白噪音"
            textSize = 16f
            setTextColor(Color.parseColor("#FF2D2D2D"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        toggleRow.addView(tvToggleLabel)

        val tvToggle = makeBtn(
            if (enabled) "关闭" else "开启",
            if (enabled) "#FFB0AAA5" else "#FF6BA4D1"
        ) {
            val newEnabled = !WhiteNoiseStorage.isEnabled(ctx)
            WhiteNoiseStorage.setEnabled(ctx, newEnabled)
            // 重建对话框
            updateNoiseUI()
            // 重新弹窗
            showWhiteNoiseDialog()
        }
        toggleRow.addView(tvToggle)
        contentLayout.addView(toggleRow)

        // 分隔线
        val divider = TextView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            )
            setBackgroundColor(Color.parseColor("#1A000000"))
        }
        contentLayout.addView(divider)

        // 噪音类型列表
        val listLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 0)
        }
        for (type in NoiseType.entries) {
            val isSelected = currentType == type
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(8, 10, 8, 10)
                setOnClickListener {
                    WhiteNoiseStorage.setSelectedType(ctx, type)
                    if (!WhiteNoiseStorage.isEnabled(ctx)) {
                        WhiteNoiseStorage.setEnabled(ctx, true)
                    }
                    updateNoiseUI()
                    // 停止当前播放并试听新选择
                    stopAndPreview(ctx, type)
                    // 重建弹窗刷新选中状态
                    showWhiteNoiseDialog()
                }
            }
            // 选中标记
            val tvCheck = TextView(ctx).apply {
                text = if (isSelected) "● " else "○ "
                textSize = 16f
                setTextColor(Color.parseColor(if (isSelected) "#FF6BA4D1" else "#FFCCCCCC"))
            }
            row.addView(tvCheck)
            // 名称
            val tvLabel = TextView(ctx).apply {
                text = type.label
                textSize = 15f
                setTextColor(Color.parseColor("#FF2D2D2D"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            row.addView(tvLabel)
            // 试听按钮
            val btnPreview = makeBtn("试听", "#FF6BA4D1") {
                stopAndPreview(ctx, type)
            }
            row.addView(btnPreview)
            listLayout.addView(row)
        }
        contentLayout.addView(listLayout)

        // 提示
        val tvHint = TextView(ctx).apply {
            text = "选择后，开始计时时将自动播放"
            textSize = 12f
            setTextColor(Color.parseColor("#FFB0AAA5"))
            setPadding(0, 12, 0, 0)
            gravity = android.view.Gravity.CENTER
        }
        contentLayout.addView(tvHint)

        noiseDialog = AlertDialog.Builder(ctx)
            .setTitle("白噪音")
            .setView(contentLayout)
            .setNegativeButton("关闭") { _, _ -> }
            .setOnDismissListener {
                WhiteNoiseEngine.getInstance().stop()
                noiseDialog = null
            }
            .show()
    }

    /** 停止当前播放并试听指定类型 */
    private fun stopAndPreview(ctx: android.content.Context, type: NoiseType) {
        val engine = WhiteNoiseEngine.getInstance()
        engine.stop()
        // 短暂延迟后试听
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            engine.play(type)
        }, 100)
    }

    /**
     * 音乐管理弹窗：上传 / 选择 / 删除 / 试听
     */
    private fun showMusicDialog() {
        val ctx = requireContext()
        val tracks = MusicStorage.getTracks(ctx)
        val selectedIdx = MusicStorage.getSelectedIndex(ctx)
        val enabled = MusicStorage.isEnabled(ctx)
        // 先释放旧的试听播放器
        releasePreviewPlayer()
        musicDialog?.dismiss()
        musicDialog = null

        val contentLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 0)
        }

        // 开关行
        val toggleRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 12)
        }
        toggleRow.addView(TextView(ctx).apply {
            text = "启用音乐"
            textSize = 16f
            setTextColor(Color.parseColor("#FF2D2D2D"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        toggleRow.addView(makeBtn(
            if (enabled) "关闭" else "开启",
            if (enabled) "#FFB0AAA5" else "#FF6BA4D1"
        ) {
            val newEnabled = !MusicStorage.isEnabled(ctx)
            MusicStorage.setEnabled(ctx, newEnabled)
            if (!newEnabled) releasePreviewPlayer()
            updateMusicUI()
            showMusicDialog()
        })
        contentLayout.addView(toggleRow)

        // 上传按钮
        contentLayout.addView(TextView(ctx).apply {
            text = "＋ 上传音乐"
            textSize = 15f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#FF6BA4D1"))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 12, 0, 12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { pickMusicLauncher.launch("audio/*") }
        })

        // 曲目列表
        if (tracks.isEmpty()) {
            contentLayout.addView(TextView(ctx).apply {
                text = "暂无音乐，点击上方上传"
                textSize = 13f
                setTextColor(Color.parseColor("#FFB0AAA5"))
                gravity = android.view.Gravity.CENTER
                setPadding(0, 24, 0, 12)
            })
        } else {
            val listLayout = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 8, 0, 0)
            }
            for (i in tracks.indices) {
                val track = tracks[i]
                val isSelected = i == selectedIdx
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(8, 10, 8, 10)
                }
                // 选中标记
                row.addView(TextView(ctx).apply {
                    text = if (isSelected) "● " else "○ "
                    textSize = 16f
                    setTextColor(Color.parseColor(if (isSelected) "#FF6BA4D1" else "#FFCCCCCC"))
                    setOnClickListener {
                        MusicStorage.setSelectedIndex(ctx, i)
                        if (!MusicStorage.isEnabled(ctx)) MusicStorage.setEnabled(ctx, true)
                        updateMusicUI()
                        showMusicDialog()
                    }
                })
                // 曲名
                row.addView(TextView(ctx).apply {
                    text = track.name
                    textSize = 14f
                    setTextColor(Color.parseColor("#FF2D2D2D"))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setOnClickListener {
                        MusicStorage.setSelectedIndex(ctx, i)
                        if (!MusicStorage.isEnabled(ctx)) MusicStorage.setEnabled(ctx, true)
                        updateMusicUI()
                        showMusicDialog()
                    }
                })
                // 试听
                row.addView(makeBtn("试听", "#FF6BA4D1") {
                    try {
                        releasePreviewPlayer()
                        previewPlayer = android.media.MediaPlayer().apply {
                            setDataSource(track.path)
                            prepare()
                            isLooping = true
                            start()
                        }
                    } catch (_: Exception) {
                        Toast.makeText(ctx, "无法播放此文件", Toast.LENGTH_SHORT).show()
                    }
                })
                // 删除
                row.addView(makeBtn("删", "#FFFF6B6B") {
                    releasePreviewPlayer()
                    MusicStorage.deleteTrack(ctx, i)
                    updateMusicUI()
                    showMusicDialog()
                })
                listLayout.addView(row)
            }
            contentLayout.addView(listLayout)
        }

        contentLayout.addView(TextView(ctx).apply {
            text = "选择曲目后，开始计时时将自动循环播放"
            textSize = 12f
            setTextColor(Color.parseColor("#FFB0AAA5"))
            setPadding(0, 12, 0, 0)
            gravity = android.view.Gravity.CENTER
        })

        musicDialog = AlertDialog.Builder(ctx)
            .setTitle("音乐")
            .setView(contentLayout)
            .setNegativeButton("关闭") { _, _ -> }
            .setOnDismissListener {
                releasePreviewPlayer()
                musicDialog = null
            }
            .show()
    }

    /**
     * 纯净模式弹窗：开关 + 说明
     */
    private fun showPureModeDialog() {
        val ctx = requireContext()
        val enabled = PureModeStorage.isEnabled(ctx)
        pureDialog?.dismiss()
        pureDialog = null

        val contentLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 0)
        }

        // 说明文字
        contentLayout.addView(TextView(ctx).apply {
            text = "开启后，计时过程中切换到后台将立即停止计时并保存进度。强制专注，无法切出。"
            textSize = 14f
            setTextColor(Color.parseColor("#FF666666"))
            setPadding(8, 0, 8, 16)
        })

        // 开关行
        val toggleRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        toggleRow.addView(TextView(ctx).apply {
            text = if (enabled) "纯净模式：已开启" else "纯净模式：已关闭"
            textSize = 16f
            setTextColor(Color.parseColor("#FF2D2D2D"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        toggleRow.addView(makeBtn(
            if (enabled) "关闭" else "开启",
            if (enabled) "#FFB0AAA5" else "#FF6BA4D1"
        ) {
            PureModeStorage.setEnabled(ctx, !enabled)
            updatePureModeUI()
            showPureModeDialog()
        })
        contentLayout.addView(toggleRow)

        pureDialog = AlertDialog.Builder(ctx)
            .setTitle("纯净模式")
            .setView(contentLayout)
            .setNegativeButton("关闭") { _, _ -> pureDialog = null }
            .show()
    }

    private fun showAudioSettingsDialog() {
        val ctx = requireContext()
        val dp = resources.displayMetrics.density

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * dp).toInt(), (16 * dp).toInt(), (20 * dp).toInt(), (8 * dp).toInt())
        }

        // 白噪音音量
        layout.addView(TextView(ctx).apply {
            text = "白噪音音量：${AudioSettingsStorage.getNoiseVolume(ctx)}%"
            textSize = 14f
            setTextColor(Color.parseColor("#FF2D2D2D"))
        })
        val noiseSlider = android.widget.SeekBar(ctx).apply {
            max = 100
            progress = AudioSettingsStorage.getNoiseVolume(ctx)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (16 * dp).toInt() }
        }
        layout.addView(noiseSlider)

        // 音乐音量
        layout.addView(TextView(ctx).apply {
            text = "音乐音量：${AudioSettingsStorage.getMusicVolume(ctx)}%"
            textSize = 14f
            setTextColor(Color.parseColor("#FF2D2D2D"))
        })
        val musicSlider = android.widget.SeekBar(ctx).apply {
            max = 100
            progress = AudioSettingsStorage.getMusicVolume(ctx)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (16 * dp).toInt() }
        }
        layout.addView(musicSlider)

        // 淡入时长
        layout.addView(TextView(ctx).apply {
            text = "淡入效果"
            textSize = 14f
            setTextColor(Color.parseColor("#FF2D2D2D"))
            setPadding(0, (8 * dp).toInt(), 0, (8 * dp).toInt())
        })
        val fadeOptions = arrayOf("关闭", "15秒", "30秒", "60秒")
        val fadeValues = intArrayOf(0, 15, 30, 60)
        val currentFade = AudioSettingsStorage.getFadeInSeconds(ctx)
        val fadeIndex = fadeValues.indexOf(currentFade).coerceAtLeast(0)
        val fadeGroup = android.widget.RadioGroup(ctx).apply {
            orientation = android.widget.RadioGroup.HORIZONTAL
        }
        for (i in fadeOptions.indices) {
            fadeGroup.addView(android.widget.RadioButton(ctx).apply {
                text = fadeOptions[i]
                textSize = 13f
                isChecked = i == fadeIndex
                id = i
                layoutParams = android.widget.RadioGroup.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
        }
        layout.addView(fadeGroup)

        // 实时更新标签
        noiseSlider.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                (layout.getChildAt(0) as? TextView)?.text = "白噪音音量：${progress}%"
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
        })
        musicSlider.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                (layout.getChildAt(2) as? TextView)?.text = "音乐音量：${progress}%"
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
        })

        AlertDialog.Builder(ctx)
            .setTitle("🔊 音频设置")
            .setView(layout)
            .setPositiveButton("确定") { _, _ ->
                AudioSettingsStorage.setNoiseVolume(ctx, noiseSlider.progress)
                AudioSettingsStorage.setMusicVolume(ctx, musicSlider.progress)
                val checkedId = fadeGroup.checkedRadioButtonId
                if (checkedId >= 0 && checkedId < fadeValues.size) {
                    AudioSettingsStorage.setFadeInSeconds(ctx, fadeValues[checkedId])
                }
                Toast.makeText(ctx, "音频设置已保存", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ==================== 连续学习天数 ====================

    private fun updateStreakStatus() {
        val streak = calculateStreak()
        tvStreakStatus.text = if (streak > 0) "已连续学习 $streak 天 🔥" else "今天还没有学习哦"
    }

    private fun calculateStreak(): Int {
        val records = StorageHelper.getAllRecords(requireContext())
        val dateSet = records.map { it.date }.toSet()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        var streak = 0
        val cal = Calendar.getInstance()
        // 检查今天是否有记录
        val todayStr = dateFormat.format(cal.time)
        if (dateSet.contains(todayStr)) {
            streak = 1
            // 往前数连续天数
            for (i in 1..365) {
                cal.add(Calendar.DAY_OF_YEAR, -1)
                val dateStr = dateFormat.format(cal.time)
                if (dateSet.contains(dateStr)) {
                    streak++
                } else {
                    break
                }
            }
        } else {
            // 今天没有记录，检查昨天开始的连续天数
            cal.add(Calendar.DAY_OF_YEAR, -1)
            for (i in 0..365) {
                val dateStr = dateFormat.format(cal.time)
                if (dateSet.contains(dateStr)) {
                    streak++
                    cal.add(Calendar.DAY_OF_YEAR, -1)
                } else {
                    break
                }
            }
        }
        return streak
    }

    private fun showStreakDetailDialog() {
        val streak = calculateStreak()
        val records = StorageHelper.getAllRecords(requireContext())
        val totalDays = records.map { it.date }.toSet().size
        val totalSeconds = records.sumOf { it.durationSeconds }
        val totalHours = totalSeconds / 3600f

        val msg = buildString {
            appendLine("🔥 当前连续学习：${streak} 天")
            appendLine()
            appendLine("📊 总计统计")
            appendLine("  学习天数：${totalDays} 天")
            appendLine("  总学习时长：${"%.1f".format(totalHours)} 小时")
            if (totalDays > 0) {
                appendLine("  日均学习：${"%.1f".format(totalHours / totalDays)} 小时")
            }
            appendLine()
            appendLine("坚持就是胜利 💪")
        }

        AlertDialog.Builder(requireContext())
            .setTitle("🔥 学习连续记录")
            .setMessage(msg)
            .setPositiveButton("好的", null)
            .setNeutralButton("🏆 成就") { _, _ -> showAchievementDialog() }
            .show()
    }

    // ==================== 学习记录管理 ====================

    private fun showRecordsDialog() {
        val ctx = requireContext()
        val allRecords = StorageHelper.getAllRecords(ctx)
        if (allRecords.isEmpty()) {
            Toast.makeText(ctx, "暂无学习记录", Toast.LENGTH_SHORT).show()
            return
        }

        val dp = resources.displayMetrics.density
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        // 按日期分组（倒序）
        val grouped = allRecords.groupBy { it.date }
            .toSortedMap(compareByDescending { it })

        val contentLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (8 * dp).toInt(), 0, 0)
        }

        fun refreshRecords() {
            contentLayout.removeAllViews()
            val currentRecords = StorageHelper.getAllRecords(ctx)
            if (currentRecords.isEmpty()) {
                contentLayout.addView(TextView(ctx).apply {
                    text = "暂无学习记录"
                    textSize = 14f
                    setTextColor(resources.getColor(R.color.text_tertiary, null))
                    gravity = android.view.Gravity.CENTER
                    setPadding(0, (24 * dp).toInt(), 0, 0)
                })
                return
            }
            val currentGrouped = currentRecords.groupBy { it.date }
                .toSortedMap(compareByDescending { it })

            for ((date, records) in currentGrouped) {
                // 日期标题
                val dayTotal = records.sumOf { it.durationSeconds }
                contentLayout.addView(TextView(ctx).apply {
                    text = "📅 $date（${formatRecordDuration(dayTotal)}）"
                    textSize = 13f
                    setTextColor(resources.getColor(R.color.blue_primary, null))
                    paint.isFakeBoldText = true
                    setPadding((12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt(), (6 * dp).toInt())
                })

                for (record in records) {
                    val row = LinearLayout(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
                    }
                    row.addView(TextView(ctx).apply {
                        text = "${record.subjectGroup} · ${record.subject}"
                        textSize = 14f
                        setTextColor(resources.getColor(R.color.text_primary, null))
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    row.addView(TextView(ctx).apply {
                        text = formatRecordDuration(record.durationSeconds)
                        textSize = 13f
                        setTextColor(resources.getColor(R.color.text_secondary, null))
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { marginEnd = (12 * dp).toInt() }
                    })
                    row.addView(makeBtn("删", "#FFFF6B6B") {
                        AlertDialog.Builder(ctx)
                            .setTitle("删除记录")
                            .setMessage("确定删除这条 ${formatRecordDuration(record.durationSeconds)} 的记录吗？")
                            .setPositiveButton("删除") { _, _ ->
                                // 找到原始索引
                                val jsonRecords = StorageHelper.getAllRecordsJson(ctx)
                                val idx = jsonRecords.indexOfFirst {
                                    val obj = it.second
                                    obj.getString("date") == record.date &&
                                        obj.getString("subjectGroup") == record.subjectGroup &&
                                        obj.getString("subject") == record.subject &&
                                        obj.getLong("durationSeconds") == record.durationSeconds
                                }
                                if (idx >= 0) {
                                    StorageHelper.deleteRecord(ctx, jsonRecords[idx].first)
                                }
                                Toast.makeText(ctx, "已删除", Toast.LENGTH_SHORT).show()
                                refreshRecords()
                            }
                            .setNegativeButton("取消", null)
                            .show()
                    })
                    contentLayout.addView(row)
                }
            }
        }

        refreshRecords()

        val scrollView = ScrollView(ctx).apply {
            addView(contentLayout)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 700
            )
        }

        AlertDialog.Builder(ctx)
            .setTitle("📋 学习记录管理")
            .setView(scrollView)
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun formatRecordDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return buildString {
            if (h > 0) append("${h}小时")
            if (m > 0) append("${m}分")
            if (s > 0 || isEmpty()) append("${s}秒")
        }
    }

    // ==================== 数据备份 ====================

    private fun showBackupDialog() {
        val ctx = requireContext()
        AlertDialog.Builder(ctx)
            .setTitle("📦 数据备份")
            .setItems(arrayOf("📤 导出数据", "📥 导入数据")) { _, which ->
                when (which) {
                    0 -> exportData()
                    1 -> importData()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun exportData() {
        val ctx = requireContext()
        try {
            val exportJson = JSONObject()

            // 1. 计时记录
            val recordsArr = JSONArray()
            for (record in StorageHelper.getAllRecords(ctx)) {
                recordsArr.put(JSONObject().apply {
                    put("subjectGroup", record.subjectGroup)
                    put("subject", record.subject)
                    put("date", record.date)
                    put("durationSeconds", record.durationSeconds)
                })
            }
            exportJson.put("records", recordsArr)

            // 2. 科目数据
            val prefs = ctx.getSharedPreferences("study_timer_data", android.content.Context.MODE_PRIVATE)
            exportJson.put("subjectData", prefs.getString("subject_data", "[]"))
            exportJson.put("todoItems", prefs.getString("todo_items", "[]"))

            // 3. 个人设置
            val profilePrefs = ctx.getSharedPreferences("study_timer_profile", android.content.Context.MODE_PRIVATE)
            val profileJson = JSONObject()
            profileJson.put("mottos", profilePrefs.getString("mottos", "[]"))
            profileJson.put("checkin_items", profilePrefs.getString("checkin_items", "[]"))
            profileJson.put("checkin_records", profilePrefs.getString("checkin_records", "[]"))
            profileJson.put("study_goal_hours", profilePrefs.getFloat("study_goal_hours", 0f).toDouble())
            profileJson.put("study_goal_subjects", profilePrefs.getString("study_goal_subjects", "[]"))
            profileJson.put("study_goal_enabled", profilePrefs.getBoolean("study_goal_enabled", false))
            exportJson.put("profile", profileJson)

            // 4. 音频设置
            val audioJson = JSONObject()
            audioJson.put("noise_volume", profilePrefs.getInt("audio_noise_volume", 70))
            audioJson.put("music_volume", profilePrefs.getInt("audio_music_volume", 80))
            audioJson.put("fade_in", profilePrefs.getInt("audio_fade_in_seconds", 0))
            exportJson.put("audio", audioJson)

            // 5. 久坐提醒设置
            val sedJson = JSONObject()
            sedJson.put("enabled", profilePrefs.getBoolean("sedentary_enabled", false))
            sedJson.put("minutes", profilePrefs.getInt("sedentary_minutes", 60))
            exportJson.put("sedentary", sedJson)

            // 6. 白噪音 & 纯净模式
            val noisePrefs = ctx.getSharedPreferences("white_noise_prefs", android.content.Context.MODE_PRIVATE)
            exportJson.put("white_noise", JSONObject().apply {
                put("enabled", noisePrefs.getBoolean("enabled", false))
                put("type", noisePrefs.getString("noise_type", ""))
            })
            val purePrefs = ctx.getSharedPreferences("pure_mode_prefs", android.content.Context.MODE_PRIVATE)
            exportJson.put("pure_mode", purePrefs.getBoolean("enabled", false))

            exportJson.put("version", "1.8")
            exportJson.put("exportDate", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))

            // 保存到 Download 目录
            val fileName = "StudyTimer_Backup_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.json"
            val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val file = java.io.File(dir, fileName)
            file.writeText(exportJson.toString(2))

            Toast.makeText(requireContext(), "✅ 已导出到 Download/$fileName", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "导出失败：${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun importData() {
        importDataLauncher.launch("application/json")
    }

    private fun importDataFromUri(uri: Uri) {
        val ctx = requireContext()
        try {
            val inputStream = ctx.contentResolver.openInputStream(uri) ?: return
            val text = inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(text)

            AlertDialog.Builder(ctx)
                .setTitle("📥 导入数据")
                .setMessage("确定要导入备份数据吗？\n当前数据将被覆盖。")
                .setPositiveButton("确定导入") { _, _ ->
                    try {
                        doImport(json)
                        Toast.makeText(ctx, "✅ 导入成功！请重启应用", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "导入失败：${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        } catch (e: Exception) {
            Toast.makeText(ctx, "读取文件失败：${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun doImport(json: JSONObject) {
        val ctx = requireContext()

        // 1. 计时记录
        val recordsArr = json.optJSONArray("records")
        if (recordsArr != null) {
            val prefs = ctx.getSharedPreferences("study_timer_data", android.content.Context.MODE_PRIVATE)
            prefs.edit().putString("timer_records", recordsArr.toString()).commit()
        }

        // 2. 科目 + 待办
        val dataPrefs = ctx.getSharedPreferences("study_timer_data", android.content.Context.MODE_PRIVATE).edit()
        json.optString("subjectData").let { if (it.isNotEmpty()) dataPrefs.putString("subject_data", it) }
        json.optString("todoItems").let { if (it.isNotEmpty()) dataPrefs.putString("todo_items", it) }
        dataPrefs.commit()

        // 3. 个人设置
        val profilePrefs = ctx.getSharedPreferences("study_timer_profile", android.content.Context.MODE_PRIVATE).edit()
        json.optJSONObject("profile")?.let { p ->
            p.optString("mottos").let { if (it.isNotEmpty()) profilePrefs.putString("mottos", it) }
            p.optString("checkin_items").let { if (it.isNotEmpty()) profilePrefs.putString("checkin_items", it) }
            p.optString("checkin_records").let { if (it.isNotEmpty()) profilePrefs.putString("checkin_records", it) }
            profilePrefs.putFloat("study_goal_hours", p.optDouble("study_goal_hours", 0.0).toFloat())
            p.optString("study_goal_subjects").let { if (it.isNotEmpty()) profilePrefs.putString("study_goal_subjects", it) }
            profilePrefs.putBoolean("study_goal_enabled", p.optBoolean("study_goal_enabled", false))
        }
        json.optJSONObject("audio")?.let { a ->
            profilePrefs.putInt("audio_noise_volume", a.optInt("noise_volume", 70))
            profilePrefs.putInt("audio_music_volume", a.optInt("music_volume", 80))
            profilePrefs.putInt("audio_fade_in_seconds", a.optInt("fade_in", 0))
        }
        json.optJSONObject("sedentary")?.let { s ->
            profilePrefs.putBoolean("sedentary_enabled", s.optBoolean("enabled", false))
            profilePrefs.putInt("sedentary_minutes", s.optInt("minutes", 60))
        }
        profilePrefs.commit()

        // 4. 白噪音
        json.optJSONObject("white_noise")?.let { n ->
            ctx.getSharedPreferences("white_noise_prefs", android.content.Context.MODE_PRIVATE).edit()
                .putBoolean("enabled", n.optBoolean("enabled", false))
                .putString("noise_type", n.optString("type", ""))
                .commit()
        }

        // 5. 纯净模式
        json.opt("pure_mode")?.let {
            ctx.getSharedPreferences("pure_mode_prefs", android.content.Context.MODE_PRIVATE).edit()
                .putBoolean("enabled", json.optBoolean("pure_mode", false))
                .commit()
        }

        // 重新加载科目数据
        SubjectData.init(ctx)
    }

    // ==================== 久坐提醒 ====================

    private fun updateSedentaryStatus() {
        val ctx = requireContext()
        val prefs = ctx.getSharedPreferences("study_timer_profile", android.content.Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("sedentary_enabled", false)
        val minutes = prefs.getInt("sedentary_minutes", 60)
        tvSedentaryStatus.text = if (enabled) "✅ 每 ${minutes} 分钟提醒" else "未开启"
    }

    private fun showSedentaryDialog() {
        val ctx = requireContext()
        val prefs = ctx.getSharedPreferences("study_timer_profile", android.content.Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("sedentary_enabled", false)
        val currentMinutes = prefs.getInt("sedentary_minutes", 60)
        val dp = resources.displayMetrics.density

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * dp).toInt(), (16 * dp).toInt(), (20 * dp).toInt(), (8 * dp).toInt())
        }

        // 说明文字
        layout.addView(TextView(ctx).apply {
            text = "开启后，每隔指定时间会通知你起身活动，防止久坐。"
            textSize = 14f
            setTextColor(resources.getColor(R.color.text_secondary, null))
            setPadding(0, 0, 0, (16 * dp).toInt())
        })

        // 时间选项
        val timeOptions = intArrayOf(30, 45, 60, 90, 120)
        val timeLabels = arrayOf("30 分钟", "45 分钟", "60 分钟", "90 分钟", "120 分钟")
        var selectedMinutes = currentMinutes

        layout.addView(TextView(ctx).apply {
            text = "提醒间隔"
            textSize = 14f
            setTextColor(resources.getColor(R.color.text_primary, null))
            paint.isFakeBoldText = true
            setPadding(0, 0, 0, (8 * dp).toInt())
        })

        val timeGroup = android.widget.RadioGroup(ctx).apply {
            orientation = android.widget.RadioGroup.VERTICAL
        }
        for (i in timeOptions.indices) {
            timeGroup.addView(android.widget.RadioButton(ctx).apply {
                text = timeLabels[i]
                textSize = 14f
                id = i
                isChecked = timeOptions[i] == currentMinutes
                setPadding(0, (4 * dp).toInt(), 0, (4 * dp).toInt())
            })
        }
        layout.addView(timeGroup)

        // 学习计划提醒入口
        val scheduleBtn = TextView(ctx).apply {
            text = "📚 设置学习计划提醒"
            textSize = 14f
            setTextColor(resources.getColor(R.color.blue_primary, null))
            gravity = android.view.Gravity.CENTER
            setPadding(0, (16 * dp).toInt(), 0, 0)
            paint.isFakeBoldText = true
            setOnClickListener {
                showScheduleReminderDialog()
            }
        }
        layout.addView(scheduleBtn)

        AlertDialog.Builder(ctx)
            .setTitle("🧘 久坐提醒")
            .setView(layout)
            .setPositiveButton(if (enabled) "关闭提醒" else "开启提醒") { _, _ ->
                if (enabled) {
                    // 关闭
                    prefs.edit()
                        .putBoolean("sedentary_enabled", false)
                        .commit()
                    stopSedentaryReminder()
                    Toast.makeText(ctx, "已关闭久坐提醒", Toast.LENGTH_SHORT).show()
                } else {
                    // 开启
                    val checkedId = timeGroup.checkedRadioButtonId
                    if (checkedId >= 0 && checkedId < timeOptions.size) {
                        selectedMinutes = timeOptions[checkedId]
                    }
                    prefs.edit()
                        .putBoolean("sedentary_enabled", true)
                        .putInt("sedentary_minutes", selectedMinutes)
                        .commit()
                    startSedentaryReminder(selectedMinutes)
                    Toast.makeText(ctx, "已开启：每 ${selectedMinutes} 分钟提醒", Toast.LENGTH_SHORT).show()
                }
                updateSedentaryStatus()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun startSedentaryReminder(minutes: Int) {
        val ctx = requireContext()
        val alarmManager = ctx.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = android.content.Intent(ctx, SedentaryReminderReceiver::class.java)
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            ctx, 100, intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
        val intervalMs = minutes * 60L * 1000L
        val triggerAt = android.os.SystemClock.elapsedRealtime() + intervalMs
        try {
            alarmManager.setRepeating(
                android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAt,
                intervalMs,
                pendingIntent
            )
        } catch (e: SecurityException) {
            // Android 12+ 需要精确闹钟权限
            Toast.makeText(ctx, "请在系统设置中允许精确闹钟权限", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopSedentaryReminder() {
        val ctx = requireContext()
        val alarmManager = ctx.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = android.content.Intent(ctx, SedentaryReminderReceiver::class.java)
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            ctx, 100, intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
        alarmManager.cancel(pendingIntent)
    }

    // ==================== 成就展示 ====================

    private fun showAchievementDialog() {
        val ctx = requireContext()
        val unlocked = AchievementStorage.getUnlocked(ctx)
        val dp = resources.displayMetrics.density

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())
        }

        val progress = "${unlocked.size} / ${AchievementStorage.ALL_ACHIEVEMENTS.size}"
        layout.addView(TextView(ctx).apply {
            text = "已解锁：$progress"
            textSize = 14f
            setTextColor(resources.getColor(R.color.text_primary, null))
            paint.isFakeBoldText = true
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, (12 * dp).toInt())
        })

        for (ach in AchievementStorage.ALL_ACHIEVEMENTS) {
            val isUnlocked = ach.id in unlocked
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding((8 * dp).toInt(), (6 * dp).toInt(), (8 * dp).toInt(), (6 * dp).toInt())
            }
            row.addView(TextView(ctx).apply {
                text = ach.icon
                textSize = 20f
                setPadding(0, 0, (8 * dp).toInt(), 0)
            })
            val textLayout = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            textLayout.addView(TextView(ctx).apply {
                text = ach.name
                textSize = 14f
                setTextColor(resources.getColor(if (isUnlocked) R.color.text_primary else R.color.text_tertiary, null))
                paint.isFakeBoldText = isUnlocked
            })
            textLayout.addView(TextView(ctx).apply {
                text = ach.description
                textSize = 12f
                setTextColor(resources.getColor(R.color.text_secondary, null))
            })
            row.addView(textLayout)
            row.addView(TextView(ctx).apply {
                text = if (isUnlocked) "✅" else "🔒"
                textSize = 16f
                setPadding((8 * dp).toInt(), 0, 0, 0)
            })
            layout.addView(row)
        }

        val scrollView = ScrollView(ctx).apply {
            addView(layout)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 700
            )
        }

        AlertDialog.Builder(ctx)
            .setTitle("🏆 成就系统")
            .setView(scrollView)
            .setPositiveButton("关闭", null)
            .show()
    }

    // ==================== 学习计划提醒 ====================

    private fun showScheduleReminderDialog() {
        val ctx = requireContext()
        val prefs = ctx.getSharedPreferences("study_timer_profile", android.content.Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("schedule_enabled", false)
        val currentHour = prefs.getInt("schedule_hour", 9)
        val currentMinute = prefs.getInt("schedule_minute", 0)

        val dp = resources.displayMetrics.density
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * dp).toInt(), (16 * dp).toInt(), (20 * dp).toInt(), (8 * dp).toInt())
        }

        layout.addView(TextView(ctx).apply {
            text = "设定每天固定时间提醒开始学习，养成良好的学习习惯。"
            textSize = 14f
            setTextColor(resources.getColor(R.color.text_secondary, null))
            setPadding(0, 0, 0, (16 * dp).toInt())
        })

        val timeText = TextView(ctx).apply {
            text = "⏰ ${"%02d".format(currentHour)}:${"%02d".format(currentMinute)}"
            textSize = 28f
            setTextColor(resources.getColor(R.color.blue_primary, null))
            gravity = android.view.Gravity.CENTER
            setPadding(0, (8 * dp).toInt(), 0, (8 * dp).toInt())
        }
        layout.addView(timeText)

        layout.addView(TextView(ctx).apply {
            text = "点击上方时间修改"
            textSize = 12f
            setTextColor(resources.getColor(R.color.text_tertiary, null))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, (16 * dp).toInt())
        })

        layout.addView(TextView(ctx).apply {
            text = if (enabled) "✅ 已开启每日提醒" else "⏸ 提醒已关闭"
            textSize = 14f
            setTextColor(resources.getColor(R.color.text_primary, null))
            gravity = android.view.Gravity.CENTER
        })

        timeText.setOnClickListener {
            android.app.TimePickerDialog(
                ctx,
                { _, h, m ->
                    prefs.edit().putInt("schedule_hour", h).putInt("schedule_minute", m).apply()
                    timeText.text = "⏰ ${"%02d".format(h)}:${"%02d".format(m)}"
                    if (prefs.getBoolean("schedule_enabled", false)) {
                        stopScheduleReminder()
                        startScheduleReminder(h, m)
                    }
                },
                currentHour,
                currentMinute,
                true
            ).show()
        }

        AlertDialog.Builder(ctx)
            .setTitle("📚 学习计划提醒")
            .setView(layout)
            .setPositiveButton(if (enabled) "关闭提醒" else "开启提醒") { _, _ ->
                if (enabled) {
                    prefs.edit().putBoolean("schedule_enabled", false).apply()
                    stopScheduleReminder()
                    Toast.makeText(ctx, "已关闭学习计划提醒", Toast.LENGTH_SHORT).show()
                } else {
                    val h = prefs.getInt("schedule_hour", 9)
                    val m = prefs.getInt("schedule_minute", 0)
                    prefs.edit().putBoolean("schedule_enabled", true).apply()
                    startScheduleReminder(h, m)
                    Toast.makeText(ctx, "已开启每日 ${"%02d".format(h)}:${"%02d".format(m)} 学习提醒", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun updateScheduleStatus() {
        val ctx = requireContext()
        val prefs = ctx.getSharedPreferences("study_timer_profile", android.content.Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("schedule_enabled", false)
        val hour = prefs.getInt("schedule_hour", 9)
        val minute = prefs.getInt("schedule_minute", 0)
        // 状态更新预留（可用于更新 UI 状态文本）
    }

    private fun startScheduleReminder(hour: Int, minute: Int) {
        val ctx = requireContext()
        val alarmManager = ctx.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = android.content.Intent(ctx, ScheduleStartReminderReceiver::class.java)
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            ctx, 200, intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            // 如果时间已过，设为明天
            if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        try {
            alarmManager.setRepeating(
                android.app.AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                android.app.AlarmManager.INTERVAL_DAY,
                pendingIntent
            )
        } catch (e: SecurityException) {
            Toast.makeText(ctx, "请在系统设置中允许精确闹钟权限", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopScheduleReminder() {
        val ctx = requireContext()
        val alarmManager = ctx.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = android.content.Intent(ctx, ScheduleStartReminderReceiver::class.java)
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            ctx, 200, intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun makeBtn(text: String, bgColor: String, onClick: () -> Unit): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor(bgColor))
            gravity = android.view.Gravity.CENTER
            setPadding(8, 6, 8, 6)
            setOnClickListener { onClick() }
        }
    }
}
