package com.example.studytimer

import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 待办页面：添加/完成/删除，支持每日重复 + 自定义截止日期
 */
class TodoFragment : Fragment() {

    private lateinit var etInput: EditText
    private lateinit var cbDaily: CheckBox
    private lateinit var btnPickDate: TextView
    private lateinit var btnAdd: Button
    private lateinit var layoutTodoList: LinearLayout

    private val handler = Handler(Looper.getMainLooper())
    private var pickedDueDate = ""  // 用户选择的截止日期
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayFormat = SimpleDateFormat("MM/dd", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_todo, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etInput = view.findViewById(R.id.et_todo_input)
        cbDaily = view.findViewById(R.id.cb_daily)
        btnPickDate = view.findViewById(R.id.btn_pick_date)
        btnAdd = view.findViewById(R.id.btn_add_todo)
        layoutTodoList = view.findViewById(R.id.layout_todo_list)

        TodoStorage.resetDailyIfNeeded(requireContext())

        // 日期选择按钮
        btnPickDate.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    cal.set(year, month, dayOfMonth)
                    pickedDueDate = dateFormat.format(cal.time)
                    btnPickDate.text = displayFormat.format(cal.time)  // 显示 MM/dd
                },
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        // 添加待办
        btnAdd.setOnClickListener {
            val text = etInput.text.toString().trim()
            if (text.isEmpty()) {
                Toast.makeText(requireContext(), "请输入待办内容", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val item = TodoItem(
                id = System.currentTimeMillis(),
                content = text,
                date = dateFormat.format(Date()),
                isDaily = cbDaily.isChecked,
                dueDate = pickedDueDate
            )
            TodoStorage.add(requireContext(), item)
            etInput.text?.clear()
            cbDaily.isChecked = false
            pickedDueDate = ""
            btnPickDate.text = "📅"
            refreshList()
        }

        refreshList()
    }

    private fun refreshList() {
        layoutTodoList.removeAllViews()
        val items = TodoStorage.getAll(requireContext())

        if (items.isEmpty()) {
            val emptyView = TextView(requireContext()).apply {
                text = "暂无待办事项，在上方添加吧 ✍️"
                textSize = 14f
                setTextColor(Color.parseColor("#FF888888"))
                gravity = android.view.Gravity.CENTER
                setPadding(0, 48, 0, 0)
            }
            layoutTodoList.addView(emptyView)
            return
        }

        for (item in items) {
            layoutTodoList.addView(createTodoRow(item))
        }
    }

    private fun createTodoRow(item: TodoItem): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 4, 8, 4)
            setBackgroundColor(if (item.isDone) Color.parseColor("#FFF5F5F5") else Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 2 }
        }

        // 第一行：内容 + 按钮
        val contentRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val label = if (item.isDaily) "🔄 ${item.content}" else item.content
        val tv = TextView(requireContext()).apply {
            text = label
            textSize = 15f
            setTextColor(Color.parseColor("#FF333333"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(8, 8, 8, 8)

            if (item.isDone) {
                setTextColor(Color.parseColor("#FFBBBBBB"))
                paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            }
        }
        contentRow.addView(tv)

        if (!item.isDone) {
            val btnDone = makeBtn("完成", "#FF6B9FC7") {
                TodoStorage.markDone(requireContext(), item.id)
            }
            contentRow.addView(btnDone)

            val btnDel = makeBtn("删除", "#FFFF6B6B") {
                TodoStorage.delete(requireContext(), item.id)
            }
            contentRow.addView(btnDel)
            (btnDel.layoutParams as LinearLayout.LayoutParams).marginStart = 4
        } else {
            val btnDel = makeBtn("删除", "#FFFF6B6B") {
                TodoStorage.delete(requireContext(), item.id)
            }
            contentRow.addView(btnDel)
        }

        row.addView(contentRow)

        // 第二行：剩余天数（如果有截止日期）
        if (item.dueDate.isNotEmpty() && !item.isDone) {
            val daysLeft = calcDaysLeft(item.dueDate)
            val dueLabel = when {
                daysLeft < 0 -> "⚠️ 已过期${-daysLeft}天"
                daysLeft == 0L -> "⚠️ 今天截止"
                else -> "📅 还有${daysLeft}天"
            }
            val color = when {
                daysLeft < 0 -> "#FFFF6B6B"
                daysLeft <= 3 -> "#FFFF9800"
                else -> "#FF888888"
            }
            val tvDue = TextView(requireContext()).apply {
                text = dueLabel
                textSize = 12f
                setTextColor(Color.parseColor(color))
                setPadding(16, 0, 0, 4)
            }
            row.addView(tvDue)
        }

        return row
    }

    /** 计算距离截止日期还有多少天 */
    private fun calcDaysLeft(dueDateStr: String): Long {
        try {
            val due = dateFormat.parse(dueDateStr) ?: return 0
            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val dueCal = Calendar.getInstance().apply {
                time = due
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val diffMillis = dueCal.timeInMillis - today.timeInMillis
            return diffMillis / (24 * 60 * 60 * 1000)
        } catch (e: Exception) {
            return 0
        }
    }

    /** 快捷创建小按钮 */
    private fun makeBtn(text: String, bgColor: String, onClick: () -> Unit): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor(bgColor))
            gravity = android.view.Gravity.CENTER
            setPadding(8, 8, 8, 8)
            setOnClickListener {
                onClick()
                // 延迟刷新，避免在点击事件中移除自身 View 导致崩溃
                handler.post { refreshList() }
            }
        }
    }
}
