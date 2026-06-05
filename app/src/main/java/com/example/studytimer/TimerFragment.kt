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
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

/**
 * 计时主页大改版
 * - ChipGroup 替代 Spinner 选科目
 * - 双卡片（正计时/倒计时）替代分段按钮
 * - 大号开始按钮
 */
class TimerFragment : Fragment() {

    private lateinit var chipGroup: ChipGroup
    private lateinit var chipSubject: ChipGroup
    private lateinit var cardCountUp: View
    private lateinit var cardCountDown: View
    private lateinit var layoutCountdown: View
    private lateinit var etHours: EditText
    private lateinit var etMinutes: EditText
    private lateinit var btnStart: Button

    private var isCountUpMode = true

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
        layoutCountdown = view.findViewById(R.id.layout_countdown)
        etHours = view.findViewById(R.id.et_hours)
        etMinutes = view.findViewById(R.id.et_minutes)
        btnStart = view.findViewById(R.id.btn_start)

        // 加载科目集 Chip
        refreshGroupChips()

        // 科目集选择 → 刷新科目 Chip
        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val chip = chipGroup.findViewById<Chip>(checkedIds[0])
                val name = chip?.text?.toString() ?: return@setOnCheckedStateChangeListener
                if (name == "+ 新增") {
                    chipGroup.clearCheck()
                    showAddGroupDialog()
                } else {
                    refreshSubjectChips(name)
                }
            }
        }

        chipSubject.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val chip = chipSubject.findViewById<Chip>(checkedIds[0])
                val name = chip?.text?.toString() ?: return@setOnCheckedStateChangeListener
                if (name == "+ 新增") {
                    chipSubject.clearCheck()
                    val groupName = getSelectedGroup()
                    showAddSubjectDialog(groupName)
                }
            }
        }

        // 模式卡片选择
        selectMode(true)
        cardCountUp.setOnClickListener { selectMode(true) }
        cardCountDown.setOnClickListener { selectMode(false) }

        // 开始按钮
        btnStart.setOnClickListener {
            val group = getSelectedGroup()
            val subject = getSelectedSubject()
            if (subject.isEmpty()) {
                Toast.makeText(requireContext(), "请选择科目", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (isCountUpMode) {
                startActivity(Intent(requireContext(), TimerRunningActivity::class.java).apply {
                    putExtra("subject_group", group)
                    putExtra("subject_name", subject)
                })
            } else {
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
        }
    }

    private fun selectMode(isUp: Boolean) {
        isCountUpMode = isUp
        val selectedBg = requireContext().getDrawable(R.drawable.btn_pill_primary)
        val defaultBg = requireContext().getDrawable(R.drawable.card_glass)

        cardCountUp.background = if (isUp) selectedBg else defaultBg
        cardCountDown.background = if (!isUp) selectedBg else defaultBg

        // 弹性缩放动画
        val target = if (isUp) cardCountUp else cardCountDown
        ObjectAnimator.ofFloat(target, View.SCALE_X, 1f, 1.05f, 1f).apply {
            duration = 400; interpolator = OvershootInterpolator(1.5f); start()
        }
        ObjectAnimator.ofFloat(target, View.SCALE_Y, 1f, 1.05f, 1f).apply {
            duration = 400; interpolator = OvershootInterpolator(1.5f); start()
        }

        layoutCountdown.visibility = if (isUp) View.GONE else View.VISIBLE
        btnStart.text = if (isUp) "开始专注" else "开始倒计时"
    }

    // ==================== ChipGroup 科目管理 ====================

    private fun refreshGroupChips() {
        chipGroup.removeAllViews()
        val names = SubjectData.getGroupNames()
        for (name in names) {
            chipGroup.addView(createChip(name))
        }
        // 新增按钮
        chipGroup.addView(createChip("+ 新增").apply { isCheckable = false })
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
        chipSubject.addView(createChip("+ 新增").apply { isCheckable = false })
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
            setChipBackgroundColorResource(android.R.color.transparent)
            chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                android.graphics.Color.parseColor("#18FFFFFF")
            )
            chipStrokeColor = android.content.res.ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(-android.R.attr.state_checked)
                ),
                intArrayOf(
                    0xFF6B9FC7.toInt(),
                    0x336B9FC7.toInt()
                )
            )
            chipStrokeWidth = 1.5f
            chipCornerRadius = 20f
            setTextColor(android.content.res.ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(-android.R.attr.state_checked)
                ),
                intArrayOf(
                    0xFF6B9FC7.toInt(),
                    0xFF8B8580.toInt()
                )
            ))
            setPadding(12, 8, 12, 8)
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
        return if (name == "+ 新增") "" else name
    }

    // ==================== 新增对话框 ====================

    private fun showAddGroupDialog() {
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
            .setNegativeButton("取消", null).show()
    }

    private fun showAddSubjectDialog(groupName: String) {
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
            .setNegativeButton("取消", null).show()
    }
}
