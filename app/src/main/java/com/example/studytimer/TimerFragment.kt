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
                refreshSubjectChips(name)
            }
        }

        chipSubject.setOnCheckedStateChangeListener { _, _ ->
            // 科目选择不需要额外处理
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

            // 选中态：莫兰迪蓝底 + 白色文字
            // 未选中态：半透明底 + 蓝描边 + 灰色文字
            chipBackgroundColor = android.content.res.ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(-android.R.attr.state_checked)
                ),
                intArrayOf(
                    0xFF6BA4D1.toInt(),   // 选中：莫兰迪蓝
                    0x14FFFFFF.toInt()    // 未选中：半透明
                )
            )
            chipStrokeColor = android.content.res.ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(-android.R.attr.state_checked)
                ),
                intArrayOf(
                    0xFF5B95C0.toInt(),   // 选中：深蓝描边
                    0x266BA4D1.toInt()    // 未选中：淡蓝描边
                )
            )
            chipStrokeWidth = (1.5f * resources.displayMetrics.density)

            setTextColor(android.content.res.ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(-android.R.attr.state_checked)
                ),
                intArrayOf(
                    0xFFFFFFFF.toInt(),   // 选中：白色（蓝底上清晰可见）
                    0xFF78716C.toInt()    // 未选中：灰色
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
            .setItems(arrayOf("➕ 新增科目集", "🗑 删除科目集", "✏️ 重命名科目集")) { _, which ->
                when (which) {
                    0 -> showGroupAddDialog()
                    1 -> showGroupDeleteDialog()
                    2 -> showGroupRenameDialog()
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
            .setItems(arrayOf("➕ 新增科目", "🗑 删除科目")) { _, which ->
                when (which) {
                    0 -> showSubjectAddDialog(groupName)
                    1 -> showSubjectDeleteDialog(groupName)
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
}
