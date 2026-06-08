package com.example.studytimer

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

/**
 * 计时主页
 * - ChipGroup 替代 Spinner 选科目
 * - 三卡片（正计时/倒计时/番茄钟）
 * - 大号开始按钮
 */
class TimerFragment : Fragment() {

    private lateinit var chipGroup: ChipGroup
    private lateinit var chipSubject: ChipGroup
    private lateinit var cardCountUp: View
    private lateinit var cardCountDown: View
    private lateinit var cardPomodoro: View
    private lateinit var layoutCountdown: View
    private lateinit var layoutPomodoro: View
    private lateinit var etHours: EditText
    private lateinit var etMinutes: EditText
    private lateinit var etPomoWork: EditText
    private lateinit var etPomoBreak: EditText
    private lateinit var etPomoLongBreak: EditText
    private lateinit var etPomoRounds: EditText
    private lateinit var btnStart: Button

    // mode: 0=正计时, 1=倒计时, 2=番茄钟
    private var currentMode = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_timer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        chipGroup = view.findViewById(R.id.chip_group)
        chipSubject = view.findViewById(R.id.chip_subject)
        cardCountUp = view.findViewById(R.id.card_count_up)
        cardCountDown = view.findViewById(R.id.card_count_down)
        cardPomodoro = view.findViewById(R.id.card_pomodoro)
        layoutCountdown = view.findViewById(R.id.layout_countdown)
        layoutPomodoro = view.findViewById(R.id.layout_pomodoro)
        etHours = view.findViewById(R.id.et_hours)
        etMinutes = view.findViewById(R.id.et_minutes)
        etPomoWork = view.findViewById(R.id.et_pomo_work)
        etPomoBreak = view.findViewById(R.id.et_pomo_break)
        etPomoLongBreak = view.findViewById(R.id.et_pomo_long_break)
        etPomoRounds = view.findViewById(R.id.et_pomo_rounds)
        btnStart = view.findViewById(R.id.btn_start)

        // 加载科目集 Chip
        refreshGroupChips()

        // 科目集选择 → 刷新科目 Chip
        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val chip = chipGroup.findViewById<Chip>(checkedIds[0])
                val name = chip?.text?.toString() ?: return@setOnCheckedStateChangeListener
                refreshSubjectChips(name)
            }
        }

        chipSubject.setOnCheckedStateChangeListener { _, _ ->
            // 科目选择不需要额外处理
        }

        // 模式卡片选择
        selectMode(0)
        cardCountUp.setOnClickListener { selectMode(0) }
        cardCountDown.setOnClickListener { selectMode(1) }
        cardPomodoro.setOnClickListener { selectMode(2) }

        // 开始按钮
        btnStart.setOnClickListener {
            val group = getSelectedGroup()
            val subject = getSelectedSubject()
            if (subject.isEmpty()) {
                Toast.makeText(requireContext(), "请选择科目", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            when (currentMode) {
                0 -> startActivity(Intent(requireContext(), TimerRunningActivity::class.java).apply {
                    putExtra("subject_group", group)
                    putExtra("subject_name", subject)
                })
                1 -> {
                    val h = etHours.text.toString().toIntOrNull() ?: 0
                    val m = etMinutes.text.toString().toIntOrNull() ?: 0
                    if (h == 0 && m == 0) {
                        Toast.makeText(requireContext(), "请设置至少 1 分钟", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    startActivity(Intent(requireContext(), CountdownRunningActivity::class.java).apply {
                        putExtra("subject_group", group)
                        putExtra("subject_name", subject)
                        putExtra("total_minutes", h * 60 + m)
                    })
                }
                2 -> {
                    val workMin = etPomoWork.text.toString().toIntOrNull() ?: 25
                    val breakMin = etPomoBreak.text.toString().toIntOrNull() ?: 5
                    val longBreakMin = etPomoLongBreak.text.toString().toIntOrNull() ?: 15
                    val rounds = etPomoRounds.text.toString().toIntOrNull() ?: 4
                    if (workMin < 1) {
                        Toast.makeText(requireContext(), "专注时长至少 1 分钟", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    startActivity(Intent(requireContext(), PomodoroRunningActivity::class.java).apply {
                        putExtra("subject_group", group)
                        putExtra("subject_name", subject)
                        putExtra("work_minutes", workMin)
                        putExtra("break_minutes", breakMin)
                        putExtra("long_break_minutes", longBreakMin)
                        putExtra("total_rounds", rounds)
                    })
                }
            }
        }
    }

    private fun selectMode(mode: Int) {
        currentMode = mode
        val selectedBg = requireContext().getDrawable(R.drawable.btn_pill_primary)
        val defaultBg = requireContext().getDrawable(R.drawable.card_glass)

        cardCountUp.background = if (mode == 0) selectedBg else defaultBg
        cardCountDown.background = if (mode == 1) selectedBg else defaultBg
        cardPomodoro.background = if (mode == 2) selectedBg else defaultBg

        // 弹性缩放动画
        val target = when (mode) {
            0 -> cardCountUp
            1 -> cardCountDown
            else -> cardPomodoro
        }
        ObjectAnimator.ofFloat(target, View.SCALE_X, 1f, 1.05f, 1f).apply {
            duration = 400; interpolator = OvershootInterpolator(1.5f); start()
        }
        ObjectAnimator.ofFloat(target, View.SCALE_Y, 1f, 1.05f, 1f).apply {
            duration = 400; interpolator = OvershootInterpolator(1.5f); start()
        }

        layoutCountdown.visibility = if (mode == 1) View.VISIBLE else View.GONE
        layoutPomodoro.visibility = if (mode == 2) View.VISIBLE else View.GONE
        btnStart.text = when (mode) {
            0 -> "开始专注"
            1 -> "开始倒计时"
            else -> "开始番茄钟"
        }
    }

    // ==================== ChipGroup 科目管理 ====================

    private fun refreshGroupChips() {
        chipGroup.removeAllViews()
        val names = SubjectData.getGroupNames()
        for (name in names) {
            chipGroup.addView(createChip(name))
        }
        chipGroup.addView(createChip("⚙ 管理").apply {
            isCheckable = false
            setOnClickListener { showGroupManageDialog() }
        })
        if (names.isNotEmpty()) {
            (chipGroup.getChildAt(0) as? Chip)?.isChecked = true
        }
    }

    private fun refreshSubjectChips(groupName: String) {
        chipSubject.removeAllViews()
        val subjects = SubjectData.getSubjectsByGroup(groupName)
        for (sub in subjects) {
            chipSubject.addView(createChip(sub))
        }
        chipSubject.addView(createChip("⚙ 管理").apply {
            isCheckable = false
            setOnClickListener { showSubjectManageDialog() }
        })
        if (subjects.isNotEmpty()) {
            (chipSubject.getChildAt(0) as? Chip)?.isChecked = true
        }
    }

    private fun createChip(text: String): Chip {
        return Chip(requireContext()).apply {
            this.text = text
            isClickable = true
            isCheckable = true
            textSize = 13f
            chipCornerRadius = 20f * resources.displayMetrics.density
            chipMinHeight = 36f * resources.displayMetrics.density
            setPadding(
                (14 * resources.displayMetrics.density).toInt(),
                (8 * resources.displayMetrics.density).toInt(),
                (14 * resources.displayMetrics.density).toInt(),
                (8 * resources.displayMetrics.density).toInt()
            )

            // 选中态：主色底 + 白色文字（适配暗色模式）
            // 未选中态：半透明底 + 蓝描边 + 次要文字色
            val primaryColor = resources.getColor(R.color.blue_primary, null)
            val secondaryTextColor = resources.getColor(R.color.text_secondary, null)

            chipBackgroundColor = android.content.res.ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(-android.R.attr.state_checked)
                ),
                intArrayOf(
                    primaryColor,             // 选中：跟随主题主色
                    0x14FFFFFF.toInt()        // 未选中：半透明
                )
            )
            chipStrokeColor = android.content.res.ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(-android.R.attr.state_checked)
                ),
                intArrayOf(
                    primaryColor,             // 选中：主色描边
                    0x26FFFFFF.toInt()        // 未选中：半透明描边
                )
            )
            chipStrokeWidth = (1.5f * resources.displayMetrics.density)

            setTextColor(android.content.res.ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(-android.R.attr.state_checked)
                ),
                intArrayOf(
                    0xFFFFFFFF.toInt(),       // 选中：白色
                    secondaryTextColor        // 未选中：跟随主题
                )
            ))
        }
    }

    private fun getSelectedGroup(): String {
        val checkedId = chipGroup.checkedChipId
        if (checkedId == View.NO_ID) return SubjectData.getGroupNames().firstOrNull() ?: "未分类"
        val chip = chipGroup.findViewById<Chip>(checkedId) ?: return "未分类"
        return chip.text.toString()
    }

    private fun getSelectedSubject(): String {
        val checkedId = chipSubject.checkedChipId
        if (checkedId == View.NO_ID) return ""
        val chip = chipSubject.findViewById<Chip>(checkedId) ?: return ""
        val name = chip.text.toString()
        return if (name == "⚙ 管理") "" else name
    }

    // ==================== 科目集管理 ====================

    private fun showGroupManageDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("科目集管理")
            .setItems(arrayOf(
                "➕ 新增科目集",
                "🗑 删除科目集",
                "✏️ 重命名科目集",
                "🎨 设置颜色",
                "↕️ 调整顺序"
            )) { _, which ->
                when (which) {
                    0 -> showGroupAddDialog()
                    1 -> showGroupDeleteDialog()
                    2 -> showGroupRenameDialog()
                    3 -> showGroupColorDialog()
                    4 -> showGroupReorderDialog()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showGroupAddDialog() {
        val input = EditText(requireContext()).apply {
            hint = "输入科目集名称"
            setPadding(32, 16, 32, 16)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("新增科目集")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    SubjectData.addGroup(requireContext(), name)
                    refreshGroupChips()
                    Toast.makeText(requireContext(), "已添加「$name」", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showGroupDeleteDialog() {
        val names = SubjectData.getGroupNames()
        if (names.isEmpty()) {
            Toast.makeText(requireContext(), "暂无科目集", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle("选择要删除的科目集")
            .setItems(names.toTypedArray()) { _, which ->
                val name = names[which]
                AlertDialog.Builder(requireContext())
                    .setTitle("确认删除")
                    .setMessage("确定要删除科目集「$name」吗？\n（该科目集下的计时记录不会被删除）")
                    .setPositiveButton("删除") { _, _ ->
                        SubjectData.deleteGroup(requireContext(), name)
                        refreshGroupChips()
                        Toast.makeText(requireContext(), "已删除「$name」", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showGroupRenameDialog() {
        val names = SubjectData.getGroupNames()
        if (names.isEmpty()) {
            Toast.makeText(requireContext(), "暂无科目集", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle("选择要重命名的科目集")
            .setItems(names.toTypedArray()) { _, which ->
                val oldName = names[which]
                val input = EditText(requireContext()).apply {
                    setText(oldName)
                    hint = "输入新名称"
                    setPadding(32, 16, 32, 16)
                }
                AlertDialog.Builder(requireContext())
                    .setTitle("重命名「$oldName」")
                    .setView(input)
                    .setPositiveButton("确定") { _, _ ->
                        val newName = input.text.toString().trim()
                        if (newName.isNotEmpty() && newName != oldName) {
                            SubjectData.renameGroup(requireContext(), oldName, newName)
                            refreshGroupChips()
                            Toast.makeText(requireContext(), "已重命名为「$newName」", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ==================== 科目管理 ====================

    private fun showSubjectManageDialog() {
        val groupName = getSelectedGroup()
        AlertDialog.Builder(requireContext())
            .setTitle("「$groupName」科目管理")
            .setItems(arrayOf(
                "➕ 新增科目",
                "🗑 删除科目",
                "✏️ 重命名科目",
                "↕️ 调整顺序"
            )) { _, which ->
                when (which) {
                    0 -> showSubjectAddDialog(groupName)
                    1 -> showSubjectDeleteDialog(groupName)
                    2 -> showSubjectRenameDialog(groupName)
                    3 -> showSubjectReorderDialog(groupName)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showSubjectAddDialog(groupName: String) {
        val input = EditText(requireContext()).apply {
            hint = "输入科目名称"
            setPadding(32, 16, 32, 16)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("向「$groupName」添加科目")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    SubjectData.addSubject(requireContext(), groupName, name)
                    refreshSubjectChips(groupName)
                    Toast.makeText(requireContext(), "已添加「$name」", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showSubjectDeleteDialog(groupName: String) {
        val subjects = SubjectData.getSubjectsByGroup(groupName)
        if (subjects.isEmpty()) {
            Toast.makeText(requireContext(), "该科目集下暂无科目", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle("选择要删除的科目")
            .setItems(subjects.toTypedArray()) { _, which ->
                val name = subjects[which]
                AlertDialog.Builder(requireContext())
                    .setTitle("确认删除")
                    .setMessage("确定要删除科目「$name」吗？")
                    .setPositiveButton("删除") { _, _ ->
                        SubjectData.deleteSubject(requireContext(), groupName, name)
                        refreshSubjectChips(groupName)
                        Toast.makeText(requireContext(), "已删除「$name」", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ==================== 科目集颜色选择 ====================

    private fun showGroupColorDialog() {
        val names = SubjectData.getGroupNames()
        if (names.isEmpty()) {
            Toast.makeText(requireContext(), "暂无科目集", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle("选择要设置颜色的科目集")
            .setItems(names.toTypedArray()) { _, which ->
                showColorPickerDialog(names[which])
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showColorPickerDialog(groupName: String) {
        val ctx = requireContext()
        val dp = resources.displayMetrics.density
        val colors = SubjectData.getAllMorandiColors()
        val currentColor = SubjectData.getGroupColor(groupName)

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * dp).toInt(), (16 * dp).toInt(), (20 * dp).toInt(), (8 * dp).toInt())
        }

        layout.addView(TextView(ctx).apply {
            text = "「$groupName」当前颜色"
            textSize = 14f
            setTextColor(resources.getColor(R.color.text_secondary, null))
            setPadding(0, 0, 0, (12 * dp).toInt())
        })

        // 当前颜色预览
        val previewRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (16 * dp).toInt())
        }
        previewRow.addView(View(ctx).apply {
            setBackgroundColor(currentColor)
            layoutParams = LinearLayout.LayoutParams((32 * dp).toInt(), (32 * dp).toInt()).apply {
                marginEnd = (12 * dp).toInt()
            }
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(currentColor)
                cornerRadius = 100f * dp
            }
        })
        previewRow.addView(TextView(ctx).apply {
            text = String.format("#%06X", 0xFFFFFF and currentColor)
            textSize = 14f
            setTextColor(resources.getColor(R.color.text_primary, null))
        })
        layout.addView(previewRow)

        // 颜色网格
        val gridLayout = android.widget.GridLayout(ctx).apply {
            columnCount = 5
            rowCount = (colors.size + 4) / 5
        }

        var selectedColor = currentColor
        val colorViews = mutableListOf<View>()

        for (color in colors) {
            val colorCircle = View(ctx).apply {
                val bg = android.graphics.drawable.GradientDrawable().apply {
                    setColor(color)
                    cornerRadius = 100f * dp
                    if (color == currentColor) {
                        setStroke((2 * dp).toInt(), 0xFFFFFFFF.toInt())
                    }
                }
                background = bg
                layoutParams = android.widget.GridLayout.LayoutParams().apply {
                    width = (40 * dp).toInt()
                    height = (40 * dp).toInt()
                    setMargins((6 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt())
                }
                setOnClickListener {
                    // 重置所有边框
                    for (v in colorViews) {
                        (v.background as? android.graphics.drawable.GradientDrawable)?.setStroke(0, 0)
                    }
                    // 选中当前
                    (background as? android.graphics.drawable.GradientDrawable)?.setStroke(
                        (2 * dp).toInt(), 0xFFFFFFFF.toInt()
                    )
                    selectedColor = color
                }
            }
            colorViews.add(colorCircle)
            gridLayout.addView(colorCircle)
        }
        layout.addView(gridLayout)

        AlertDialog.Builder(ctx)
            .setTitle("🎨 设置颜色")
            .setView(layout)
            .setPositiveButton("确定") { _, _ ->
                SubjectData.setGroupColorInt(ctx, groupName, selectedColor)
                Toast.makeText(ctx, "「$groupName」颜色已更新", Toast.LENGTH_SHORT).show()
                refreshGroupChips()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ==================== 科目集排序 ====================

    private fun showGroupReorderDialog() {
        val names = SubjectData.getGroupNames().toMutableList()
        if (names.size < 2) {
            Toast.makeText(requireContext(), "至少需要 2 个科目集才能排序", Toast.LENGTH_SHORT).show()
            return
        }

        val ctx = requireContext()
        val dp = resources.displayMetrics.density
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (8 * dp).toInt(), 0, 0)
        }

        fun refresh() {
            layout.removeAllViews()
            val currentNames = SubjectData.getGroupNames()
            for ((i, name) in currentNames.withIndex()) {
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding((16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())
                }

                // 颜色圆点
                row.addView(View(ctx).apply {
                    setBackgroundColor(SubjectData.getGroupColor(name))
                    layoutParams = LinearLayout.LayoutParams((10 * dp).toInt(), (10 * dp).toInt()).apply {
                        marginEnd = (10 * dp).toInt()
                    }
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(SubjectData.getGroupColor(name))
                        cornerRadius = 100f * dp
                    }
                })

                // 名称
                row.addView(TextView(ctx).apply {
                    text = name
                    textSize = 15f
                    setTextColor(resources.getColor(R.color.text_primary, null))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })

                // 上移按钮
                if (i > 0) {
                    row.addView(makeSmallBtn("▲") {
                        SubjectData.moveGroupUp(ctx, name)
                        refreshGroupChips()
                        refresh()
                    })
                } else {
                    row.addView(makeSmallBtn("　") {})
                }

                // 下移按钮
                if (i < currentNames.size - 1) {
                    row.addView(makeSmallBtn("▼") {
                        SubjectData.moveGroupDown(ctx, name)
                        refreshGroupChips()
                        refresh()
                    })
                } else {
                    row.addView(makeSmallBtn("　") {})
                }

                layout.addView(row)
            }
        }
        refresh()

        AlertDialog.Builder(ctx)
            .setTitle("↕️ 调整科目集顺序")
            .setView(layout)
            .setPositiveButton("完成", null)
            .show()
    }

    // ==================== 科目重命名 ====================

    private fun showSubjectRenameDialog(groupName: String) {
        val subjects = SubjectData.getSubjectsByGroup(groupName)
        if (subjects.isEmpty()) {
            Toast.makeText(requireContext(), "该科目集下暂无科目", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle("选择要重命名的科目")
            .setItems(subjects.toTypedArray()) { _, which ->
                val oldName = subjects[which]
                val input = EditText(requireContext()).apply {
                    setText(oldName)
                    hint = "输入新名称"
                    setPadding(32, 16, 32, 16)
                }
                AlertDialog.Builder(requireContext())
                    .setTitle("重命名「$oldName」")
                    .setView(input)
                    .setPositiveButton("确定") { _, _ ->
                        val newName = input.text.toString().trim()
                        if (newName.isNotEmpty() && newName != oldName) {
                            SubjectData.renameSubject(requireContext(), groupName, oldName, newName)
                            refreshSubjectChips(groupName)
                            Toast.makeText(requireContext(), "已重命名为「$newName」", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ==================== 科目排序 ====================

    private fun showSubjectReorderDialog(groupName: String) {
        val subjects = SubjectData.getSubjectsByGroup(groupName).toMutableList()
        if (subjects.size < 2) {
            Toast.makeText(requireContext(), "至少需要 2 个科目才能排序", Toast.LENGTH_SHORT).show()
            return
        }

        val ctx = requireContext()
        val dp = resources.displayMetrics.density
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (8 * dp).toInt(), 0, 0)
        }

        buildSubjectReorderList(ctx, dp, container, groupName)

        AlertDialog.Builder(ctx)
            .setTitle("↕️ 调整「$groupName」科目顺序")
            .setView(container)
            .setPositiveButton("完成", null)
            .show()
    }

    private fun buildSubjectReorderList(
        ctx: android.content.Context,
        dp: Float,
        container: LinearLayout,
        groupName: String
    ) {
        container.removeAllViews()
        val currentSubjects = SubjectData.getSubjectsByGroup(groupName)
        for ((i, name) in currentSubjects.withIndex()) {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding((16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())
            }

            row.addView(TextView(ctx).apply {
                text = name
                textSize = 15f
                setTextColor(resources.getColor(R.color.text_primary, null))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })

            if (i > 0) {
                row.addView(makeSmallBtn("▲") {
                    SubjectData.moveSubjectUp(ctx, groupName, name)
                    refreshSubjectChips(groupName)
                    buildSubjectReorderList(ctx, dp, container, groupName)
                })
            } else {
                row.addView(makeSmallBtn("　") {})
            }

            if (i < currentSubjects.size - 1) {
                row.addView(makeSmallBtn("▼") {
                    SubjectData.moveSubjectDown(ctx, groupName, name)
                    refreshSubjectChips(groupName)
                    buildSubjectReorderList(ctx, dp, container, groupName)
                })
            } else {
                row.addView(makeSmallBtn("　") {})
            }

            container.addView(row)
        }
    }

    /** 小按钮工具方法（排序用） */
    private fun makeSmallBtn(text: String, onClick: () -> Unit): android.widget.TextView {
        val ctx = requireContext()
        return android.widget.TextView(ctx).apply {
            this.text = text
            textSize = 16f
            setTextColor(resources.getColor(R.color.blue_primary, null))
            gravity = android.view.Gravity.CENTER
            setPadding(16, 8, 16, 8)
            setOnClickListener { onClick() }
        }
    }
}
