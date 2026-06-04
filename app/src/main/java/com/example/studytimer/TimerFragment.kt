package com.example.studytimer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment

/**
 * 计时主页：选择科目与模式，下拉框内直接新增科目集/科目
 */
class TimerFragment : Fragment() {

    // ==================== 视图控件 ====================
    private lateinit var spinnerGroup: Spinner
    private lateinit var spinnerSubject: Spinner
    private lateinit var btnCountUp: Button
    private lateinit var btnCountDown: Button
    private lateinit var btnStartTimer: Button
    private lateinit var layoutCountdown: View
    private lateinit var etHours: EditText
    private lateinit var etMinutes: EditText
    private lateinit var btnStartCountdown: Button

    // ==================== 适配器 ====================
    private lateinit var groupAdapter: ArrayAdapter<String>
    private lateinit var subjectAdapter: ArrayAdapter<String>

    private var isCountUpMode = true
    private var isAdding = false  // 防止新增时循环触发 onItemSelected

    // 特殊选项文字
    private val ADD_NEW_GROUP = "+ 新增科目集"
    private val ADD_NEW_SUBJECT = "+ 新增科目"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_timer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 绑定控件
        spinnerGroup = view.findViewById(R.id.spinner_group)
        spinnerSubject = view.findViewById(R.id.spinner_subject)
        btnCountUp = view.findViewById(R.id.btn_count_up)
        btnCountDown = view.findViewById(R.id.btn_count_down)
        btnStartTimer = view.findViewById(R.id.btn_start_timer)
        layoutCountdown = view.findViewById(R.id.layout_countdown)
        etHours = view.findViewById(R.id.et_hours)
        etMinutes = view.findViewById(R.id.et_minutes)
        btnStartCountdown = view.findViewById(R.id.btn_start_countdown)

        // 加载科目集（含"+ 新增科目集"）
        refreshGroupSpinner()

        // 科目集选择 → 联动科目 / 新增
        spinnerGroup.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (isAdding) { isAdding = false; return }  // 新增操作触发，跳过
                val selectedText = parent?.getItemAtPosition(pos)?.toString() ?: return
                if (selectedText == ADD_NEW_GROUP) {
                    isAdding = true
                    showAddGroupDialog()
                } else {
                    updateSubjectSpinner(selectedText)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 科目选择 → 新增
        spinnerSubject.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (isAdding) { isAdding = false; return }
                val selectedText = parent?.getItemAtPosition(pos)?.toString() ?: return
                if (selectedText == ADD_NEW_SUBJECT) {
                    isAdding = true
                    val groupName = getSelectedGroupName()
                    showAddSubjectDialog(groupName)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 正计时模式
        btnCountUp.setOnClickListener {
            isCountUpMode = true
            btnStartTimer.visibility = View.VISIBLE
            layoutCountdown.visibility = View.GONE
        }

        // 倒计时模式
        btnCountDown.setOnClickListener {
            isCountUpMode = false
            btnStartTimer.visibility = View.GONE
            layoutCountdown.visibility = View.VISIBLE
        }

        // 正计时 → 跳转
        btnStartTimer.setOnClickListener {
            val intent = Intent(requireContext(), TimerRunningActivity::class.java).apply {
                putExtra("subject_group", getSelectedGroupName())
                putExtra("subject_name", getSelectedSubjectName())
            }
            startActivity(intent)
        }

        // 倒计时 → 跳转
        btnStartCountdown.setOnClickListener {
            val h = etHours.text.toString().toIntOrNull() ?: 0
            val m = etMinutes.text.toString().toIntOrNull() ?: 0
            if (h == 0 && m == 0) {
                Toast.makeText(requireContext(), "请设置至少 1 分钟", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val intent = Intent(requireContext(), CountdownRunningActivity::class.java).apply {
                putExtra("subject_group", getSelectedGroupName())
                putExtra("subject_name", getSelectedSubjectName())
                putExtra("total_minutes", h * 60 + m)
            }
            startActivity(intent)
        }
    }

    // ==================== 科目集/科目下拉框 ====================

    /** 刷新科目集 Spinner，末尾追加"新增" */
    private fun refreshGroupSpinner() {
        val names = SubjectData.getGroupNames().toMutableList()
        names.add(ADD_NEW_GROUP)
        groupAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, names)
        groupAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerGroup.adapter = groupAdapter
        if (SubjectData.getGroupNames().isNotEmpty()) {
            spinnerGroup.setSelection(0)
        }
    }

    /** 更新科目 Spinner，末尾追加"新增" */
    private fun updateSubjectSpinner(groupName: String) {
        val subjects = SubjectData.getSubjectsByGroup(groupName).toMutableList()
        subjects.add(ADD_NEW_SUBJECT)
        subjectAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, subjects)
        subjectAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSubject.adapter = subjectAdapter
        spinnerSubject.setSelection(0)
    }

    /** 获取当前选中的科目集名（过滤掉"新增"选项） */
    private fun getSelectedGroupName(): String {
        val text = spinnerGroup.selectedItem?.toString() ?: ""
        return if (text == ADD_NEW_GROUP) SubjectData.getGroupNames().firstOrNull() ?: "未分类" else text
    }

    /** 获取当前选中的科目名（过滤掉"新增"选项） */
    private fun getSelectedSubjectName(): String {
        val text = spinnerSubject.selectedItem?.toString() ?: ""
        return if (text == ADD_NEW_SUBJECT) {
            val gn = getSelectedGroupName()
            SubjectData.getSubjectsByGroup(gn).firstOrNull() ?: "未命名"
        } else text
    }

    // ==================== 新增对话框 ====================

    private fun showAddGroupDialog() {
        val input = EditText(requireContext()).apply {
            hint = "输入科目集名称（如：专业课）"
            setPadding(32, 16, 32, 16)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("新增科目集")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    SubjectData.addGroup(requireContext(), name)
                    refreshGroupSpinner()
                    // 选中新添加的项
                    val idx = SubjectData.getGroupNames().indexOf(name)
                    if (idx >= 0) spinnerGroup.setSelection(idx)
                    Toast.makeText(requireContext(), "已添加「$name」", Toast.LENGTH_SHORT).show()
                } else {
                    resetGroupSelection()
                }
            }
            .setNegativeButton("取消") { _, _ -> resetGroupSelection() }
            .setOnCancelListener { resetGroupSelection() }
            .show()
    }

    private fun showAddSubjectDialog(groupName: String) {
        val input = EditText(requireContext()).apply {
            hint = "输入科目名称（如：线代）"
            setPadding(32, 16, 32, 16)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("向「$groupName」添加科目")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    SubjectData.addSubject(requireContext(), groupName, name)
                    updateSubjectSpinner(groupName)
                    val idx = SubjectData.getSubjectsByGroup(groupName).indexOf(name)
                    if (idx >= 0) spinnerSubject.setSelection(idx)
                    Toast.makeText(requireContext(), "已添加「$name」", Toast.LENGTH_SHORT).show()
                } else {
                    resetSubjectSelection()
                }
            }
            .setNegativeButton("取消") { _, _ -> resetSubjectSelection() }
            .setOnCancelListener { resetSubjectSelection() }
            .show()
    }

    /** 取消新增科目集 → 恢复到第一个已有项 */
    private fun resetGroupSelection() {
        if (SubjectData.getGroupNames().isNotEmpty()) spinnerGroup.setSelection(0)
    }

    /** 取消新增科目 → 恢复到第一个已有项 */
    private fun resetSubjectSelection() {
        spinnerSubject.setSelection(0)
    }
}
