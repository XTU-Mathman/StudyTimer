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
import java.text.SimpleDateFormat
import java.util.Calendar
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

    // 音乐试听播放器（类级别管理，避免泄漏）
    private var previewPlayer: android.media.MediaPlayer? = null

    // 对话框引用（防止堆叠）
    private var noiseDialog: AlertDialog? = null
    private var musicDialog: AlertDialog? = null
    private var pureDialog: AlertDialog? = null

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

        updateBgUI()
        updateCheckinStatus()
        updateNoiseUI()
        updateMusicUI()
        updatePureModeUI()
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
