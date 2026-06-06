package com.example.studytimer

import android.animation.ObjectAnimator
import android.app.DatePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 待办页面
 * - FAB 弹出菜单：新增待办 / 新增待办集
 * - 内置学习目标（带进度条，自动完成）
 * - 待办集（可折叠，子项全部完成则集完成）
 * - 独立待办
 */
class TodoFragment : Fragment() {

    private lateinit var layoutTodoList: LinearLayout
    private lateinit var fabAdd: FloatingActionButton

    private val handler = Handler(Looper.getMainLooper())
    private var isAnimating = false
    private val expandedGroups = mutableSetOf<Long>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_todo, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        layoutTodoList = view.findViewById(R.id.layout_todo_list)
        fabAdd = view.findViewById(R.id.fab_add)
        refreshList()
        fabAdd.setOnClickListener { showFabMenu() }
    }

    override fun onResume() {
        super.onResume()
        // 每次回到待办页面时检查学习目标
        TodoStorage.checkAndUpdateStudyGoal(requireContext())
        refreshList()
    }

    // ==================== FAB 弹出菜单 ====================

    private fun showFabMenu() {
        AlertDialog.Builder(requireContext())
            .setTitle("添加")
            .setItems(arrayOf("➕ 新增待办", "📁 新增待办集")) { _, which ->
                when (which) {
                    0 -> showAddTodoDialog()
                    1 -> showAddGroupDialog()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ==================== 新增待办 ====================

    private fun showAddTodoDialog() {
        val ctx = requireContext()
        val density = ctx.resources.displayMetrics.density
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
        }

        val etInput = EditText(ctx).apply {
            hint = "添加待办事项..."
            textSize = 16f
            setPadding(0, 16, 0, 16)
            setBackgroundColor(Color.TRANSPARENT)
        }
        layout.addView(etInput)

        // 待办集选择
        val groups = TodoStorage.getAllGroups(ctx)
        var selectedGroupId = 0L
        val tvGroup = TextView(ctx).apply {
            text = "📁 所属待办集：无"
            textSize = 14f
            setTextColor(resources.getColor(R.color.blue_primary, null))
            setPadding(0, 8, 0, 8)
            if (groups.isNotEmpty()) {
                setOnClickListener {
                    val names = arrayOf("无") + groups.map { it.content }
                    AlertDialog.Builder(ctx)
                        .setTitle("选择待办集")
                        .setItems(names) { _, idx ->
                            selectedGroupId = if (idx == 0) 0L else groups[idx - 1].id
                            text = "📁 所属待办集：${names[idx]}"
                        }
                        .show()
                }
            }
        }
        layout.addView(tvGroup)

        val cbDaily = CheckBox(ctx).apply { text = "每日重复"; textSize = 14f }
        layout.addView(cbDaily)

        var dueDate = ""
        val tvPickDate = TextView(ctx).apply {
            text = "📅 设置截止日期"
            textSize = 14f
            setTextColor(resources.getColor(R.color.blue_primary, null))
            setPadding(0, 8, 0, 8)
            setOnClickListener {
                val cal = Calendar.getInstance()
                DatePickerDialog(ctx, { _, y, m, d ->
                    dueDate = String.format("%d-%02d-%02d", y, m + 1, d)
                    this.text = "📅 $dueDate"
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            }
        }
        layout.addView(tvPickDate)

        AlertDialog.Builder(ctx)
            .setTitle("添加待办")
            .setView(layout)
            .setPositiveButton("添加") { _, _ ->
                val text = etInput.text.toString().trim()
                if (text.isNotEmpty()) {
                    TodoStorage.add(ctx, TodoItem(
                        id = System.currentTimeMillis(),
                        content = text,
                        isDaily = cbDaily.isChecked,
                        dueDate = dueDate,
                        groupId = selectedGroupId
                    ))
                    refreshList()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ==================== 新增待办集 ====================

    private fun showAddGroupDialog() {
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
        }

        val etInput = EditText(ctx).apply {
            hint = "待办集名称..."
            textSize = 16f
            setPadding(0, 16, 0, 16)
            setBackgroundColor(Color.TRANSPARENT)
        }
        layout.addView(etInput)

        val cbDaily = CheckBox(ctx).apply { text = "每日重复"; textSize = 14f }
        layout.addView(cbDaily)

        var dueDate = ""
        val tvPickDate = TextView(ctx).apply {
            text = "📅 设置截止日期"
            textSize = 14f
            setTextColor(resources.getColor(R.color.blue_primary, null))
            setPadding(0, 8, 0, 8)
            setOnClickListener {
                val cal = Calendar.getInstance()
                DatePickerDialog(ctx, { _, y, m, d ->
                    dueDate = String.format("%d-%02d-%02d", y, m + 1, d)
                    this.text = "📅 $dueDate"
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            }
        }
        layout.addView(tvPickDate)

        AlertDialog.Builder(ctx)
            .setTitle("新增待办集")
            .setView(layout)
            .setPositiveButton("创建") { _, _ ->
                val text = etInput.text.toString().trim()
                if (text.isNotEmpty()) {
                    TodoStorage.addGroup(ctx, TodoItem(
                        id = System.currentTimeMillis(),
                        content = text,
                        isDaily = cbDaily.isChecked,
                        dueDate = dueDate
                    ))
                    refreshList()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ==================== 列表渲染 ====================

    private fun refreshList() {
        layoutTodoList.removeAllViews()
        val displayList = TodoStorage.getDisplayList(requireContext())
        var index = 0

        for (item in displayList) {
            when (item.itemType) {
                TodoItem.TYPE_BUILTIN_GOAL -> {
                    layoutTodoList.addView(createGoalView(item))
                }
                TodoItem.TYPE_GROUP -> {
                    layoutTodoList.addView(createGroupView(item))
                    // 如果展开，渲染子项
                    if (expandedGroups.contains(item.id)) {
                        val children = displayList.filter {
                            it.itemType == TodoItem.TYPE_TODO && it.groupId == item.id
                        }
                        for (child in children) {
                            layoutTodoList.addView(createTodoItem(child, index, isChild = true))
                            index++
                        }
                    }
                }
                else -> {
                    layoutTodoList.addView(createTodoItem(item, index, isChild = false))
                    index++
                }
            }
        }
    }

    // ==================== 内置学习目标视图 ====================

    private fun createGoalView(item: TodoItem): View {
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density
        val sp = ctx.resources.displayMetrics.scaledDensity
        val studyProgress = TodoStorage.getStudyProgress(ctx)
        val configured = studyProgress != null

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.card_goal)
            elevation = 3f * dp
            setPadding((20 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, (12 * dp).toInt()) }
        }

        // ---- 标题行 ----
        val titleRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        titleRow.addView(TextView(ctx).apply {
            text = "📚"
            textSize = 18f
        })
        titleRow.addView(TextView(ctx).apply {
            text = item.content
            textSize = 16f
            setTextColor(resources.getColor(R.color.text_primary, null))
            paint.isFakeBoldText = true
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (8 * dp).toInt()
            }
        })
        // 设置按钮（实心药丸样式，确保在卡片上可见）
        titleRow.addView(TextView(ctx).apply {
            text = "⚙ 设置"
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(resources.getColor(R.color.blue_primary, null))
            setPadding((12 * dp).toInt(), (6 * dp).toInt(), (12 * dp).toInt(), (6 * dp).toInt())
            // 圆角背景
            val bg = android.graphics.drawable.GradientDrawable().apply {
                setColor(resources.getColor(R.color.blue_primary, null))
                cornerRadius = 100f * dp
            }
            background = bg
            setOnClickListener { showStudyGoalSettingsDialog() }
        })
        if (item.isDone) {
            titleRow.addView(TextView(ctx).apply {
                text = " ✅"
                textSize = 16f
            })
        }
        card.addView(titleRow)

        if (configured) {
            // ---- 总进度区 ----
            val totalPercent = ((studyProgress.totalMinutes.toFloat() / studyProgress.targetMinutes.coerceAtLeast(1)) * 100).toInt().coerceIn(0, 100)

            // 数字标签行
            val labelRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (12 * dp).toInt() }
            }
            labelRow.addView(TextView(ctx).apply {
                text = "已学 ${formatMinutes(studyProgress.totalMinutes)}"
                textSize = 14f
                setTextColor(resources.getColor(R.color.blue_primary, null))
                paint.isFakeBoldText = true
            })
            labelRow.addView(TextView(ctx).apply {
                text = " / ${formatMinutes(studyProgress.targetMinutes)}"
                textSize = 13f
                setTextColor(resources.getColor(R.color.text_tertiary, null))
            })
            labelRow.addView(View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
            })
            labelRow.addView(TextView(ctx).apply {
                text = "$totalPercent%"
                textSize = 14f
                setTextColor(resources.getColor(R.color.blue_primary, null))
                paint.isFakeBoldText = true
            })
            card.addView(labelRow)

            // 总进度条
            card.addView(ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = totalPercent
                progressDrawable = resources.getDrawable(R.drawable.progress_bar_goal, null)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (8 * dp).toInt()
                ).apply { topMargin = (6 * dp).toInt() }
            })

            // ---- 各科目进度 ----
            if (studyProgress.subjectProgress.isNotEmpty()) {
                // 分隔线
                card.addView(View(ctx).apply {
                    setBackgroundColor(0x0F000000)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1
                    ).apply { setMargins(0, (12 * dp).toInt(), 0, (8 * dp).toInt()) }
                })

                for (sp in studyProgress.subjectProgress) {
                    val spPercent = ((sp.currentMinutes.toFloat() / sp.targetMinutes.coerceAtLeast(1)) * 100).toInt().coerceIn(0, 100)
                    val name = if (sp.subjectName.isNotEmpty()) "${sp.groupName} · ${sp.subjectName}" else sp.groupName

                    // 科目名 + 数字
                    val spRow = LinearLayout(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = (6 * dp).toInt() }
                    }
                    spRow.addView(TextView(ctx).apply {
                        text = if (sp.isMet) "✓ $name" else name
                        textSize = 13f
                        setTextColor(resources.getColor(if (sp.isMet) R.color.success else R.color.text_secondary, null))
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    spRow.addView(TextView(ctx).apply {
                        text = "${formatMinutes(sp.currentMinutes)}/${formatMinutes(sp.targetMinutes)}"
                        textSize = 12f
                        setTextColor(resources.getColor(R.color.text_tertiary, null))
                    })
                    card.addView(spRow)

                    // 科目进度条
                    card.addView(ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
                        max = 100
                        progress = spPercent
                        progressDrawable = resources.getDrawable(R.drawable.progress_bar_goal, null)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            (4 * dp).toInt()
                        ).apply { topMargin = (3 * dp).toInt() }
                    })
                }
            }

            // ---- 底部提示 ----
            card.addView(TextView(ctx).apply {
                text = if (item.isDone) "🎉 今日目标已达成！" else "⏱ 计时结束自动更新进度"
                textSize = 12f
                setTextColor(resources.getColor(if (item.isDone) R.color.success else R.color.text_tertiary, null))
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (10 * dp).toInt() }
            })
        } else {
            // 未配置状态
            card.addView(TextView(ctx).apply {
                text = "设定每日学习目标，计时结束自动完成"
                textSize = 13f
                setTextColor(resources.getColor(R.color.text_tertiary, null))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (8 * dp).toInt() }
            })
        }

        // 入场动画
        card.translationY = 20f; card.alpha = 0f
        card.animate().translationY(0f).alpha(1f).setDuration(350).setInterpolator(DecelerateInterpolator()).start()
        return card
    }

    private fun formatMinutes(minutes: Long): String {
        val h = minutes / 60; val m = minutes % 60
        return if (h > 0) "${h}h${m}min" else "${m}min"
    }

    // ==================== 待办集视图 ====================

    private fun createGroupView(item: TodoItem): View {
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density
        val children = TodoStorage.getTodosByGroup(ctx, item.id)
        val doneCount = children.count { it.isDone }
        val totalCount = children.size
        val isExpanded = expandedGroups.contains(item.id)
        val groupDone = totalCount > 0 && doneCount == totalCount

        // 外层容器（卡片 + 子项）
        val wrapper = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (4 * dp).toInt() }
        }

        // ---- 卡片头 ----
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.card_glass)
            elevation = 1.5f * dp
            setPadding((16 * dp).toInt(), (14 * dp).toInt(), (14 * dp).toInt(), (14 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // 色条（完成=绿，未完成=蓝）
        card.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                (4 * dp).toInt(), LinearLayout.LayoutParams.MATCH_PARENT
            ).apply { marginEnd = (12 * dp).toInt() }
            setBackgroundColor(if (groupDone) resources.getColor(R.color.success, null) else resources.getColor(R.color.blue_primary, null))
        })

        // 展开箭头
        card.addView(TextView(ctx).apply {
            text = if (isExpanded) "▾" else "▸"
            textSize = 14f
            setTextColor(resources.getColor(R.color.text_secondary, null))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = (8 * dp).toInt() }
        })

        // 文字区
        val textArea = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        // 标题
        textArea.addView(TextView(ctx).apply {
            text = item.content
            textSize = 15f
            setTextColor(resources.getColor(R.color.text_primary, null))
            paint.isFakeBoldText = true
            paint.isStrikeThruText = groupDone
            alpha = if (groupDone) 0.5f else 1f
        })

        // 进度条 + 副标题
        if (totalCount > 0) {
            val percent = (doneCount * 100 / totalCount.coerceAtLeast(1))
            textArea.addView(ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100; progress = percent
                progressDrawable = resources.getDrawable(R.drawable.progress_bar_goal, null)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (4 * dp).toInt()
                ).apply { topMargin = (6 * dp).toInt() }
            })
        }

        val subInfo = mutableListOf<String>()
        subInfo.add("$doneCount/$totalCount 已完成")
        if (item.isDaily) subInfo.add("每日")
        if (item.dueDate.isNotEmpty()) {
            val days = daysUntil(item.dueDate)
            subInfo.add("截止${item.dueDate}")
            if (days < 0) subInfo.add("已超期${-days}天")
        }
        textArea.addView(TextView(ctx).apply {
            text = subInfo.joinToString(" · ")
            textSize = 12f
            setTextColor(resources.getColor(R.color.text_tertiary, null))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (4 * dp).toInt() }
        })
        card.addView(textArea)

        // 删除按钮
        if (!item.isBuiltIn) {
            card.addView(TextView(ctx).apply {
                text = "✕"
                textSize = 14f
                setTextColor(resources.getColor(R.color.text_tertiary, null))
                alpha = 0.6f
                gravity = android.view.Gravity.CENTER
                setPadding((10 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt())
                setOnClickListener {
                    AlertDialog.Builder(ctx)
                        .setTitle("删除待办集")
                        .setMessage("确定删除「${item.content}」？\n集内 $totalCount 个待办也会被删除。")
                        .setPositiveButton("删除") { _, _ ->
                            TodoStorage.delete(ctx, item.id)
                            expandedGroups.remove(item.id); refreshList()
                        }
                        .setNegativeButton("取消", null).show()
                }
            })
        }

        card.setOnClickListener {
            if (isExpanded) expandedGroups.remove(item.id) else expandedGroups.add(item.id)
            refreshList()
        }
        wrapper.addView(card)

        // ---- 展开的子项 ----
        if (isExpanded && !groupDone) {
            val childContainer = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((20 * dp).toInt(), 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            for (child in children.filter { !it.isDone }) {
                childContainer.addView(createTodoItem(child, 0, isChild = true))
            }
            wrapper.addView(childContainer)
        }

        // 入场动画
        wrapper.translationY = 16f; wrapper.alpha = 0f
        wrapper.animate().translationY(0f).alpha(1f).setDuration(280).setInterpolator(DecelerateInterpolator()).start()
        return wrapper
    }

    // ==================== 普通待办视图 ====================

    private fun createTodoItem(item: TodoItem, index: Int, isChild: Boolean): View {
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            if (isChild) {
                setBackgroundColor(0x08000000)
                setBackgroundResource(R.drawable.card_glass)
                alpha = 0.9f
            } else {
                setBackgroundResource(R.drawable.card_glass)
            }
            elevation = if (isChild) 0.3f * dp else 1f * dp
            setPadding(
                if (isChild) (14 * dp).toInt() else (16 * dp).toInt(),
                (10 * dp).toInt(), (14 * dp).toInt(), (10 * dp).toInt()
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(
                    if (isChild) (8 * dp).toInt() else 0, 0, 0,
                    (4 * dp).toInt()
                )
            }
        }

        // 色条
        card.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                (3 * dp).toInt(), LinearLayout.LayoutParams.MATCH_PARENT
            ).apply { marginEnd = (10 * dp).toInt() }
            setBackgroundColor(
                if (item.dueDate.isNotEmpty()) {
                    val daysLeft = daysUntil(item.dueDate)
                    when {
                        daysLeft < 0 -> resources.getColor(R.color.error, null)
                        daysLeft <= 1 -> resources.getColor(R.color.warning, null)
                        else -> resources.getColor(R.color.blue_primary, null)
                    }
                } else resources.getColor(R.color.blue_primary, null)
            )
        })

        // 勾选
        val cb = CheckBox(ctx).apply {
            isChecked = item.isDone
            buttonTintList = android.content.res.ColorStateList.valueOf(
                resources.getColor(R.color.blue_primary, null)
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = (8 * dp).toInt() }
        }
        card.addView(cb)

        // 文字区
        val textArea = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        textArea.addView(TextView(ctx).apply {
            text = item.content
            textSize = if (isChild) 14f else 15f
            setTextColor(resources.getColor(R.color.text_primary, null))
            paint.isStrikeThruText = item.isDone
            alpha = if (item.isDone) 0.4f else 1f
        })

        val subInfo = mutableListOf<String>()
        if (item.isDaily) subInfo.add("每日")
        if (item.dueDate.isNotEmpty()) {
            val days = daysUntil(item.dueDate)
            subInfo.add("截止${item.dueDate}")
            if (days >= 0) subInfo.add("剩余${days}天")
            else subInfo.add("已超期${-days}天")
        }
        if (subInfo.isNotEmpty()) {
            textArea.addView(TextView(ctx).apply {
                text = subInfo.joinToString(" · ")
                textSize = 11f
                setTextColor(resources.getColor(R.color.text_tertiary, null))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (2 * dp).toInt() }
            })
        }
        card.addView(textArea)

        // 删除
        card.addView(TextView(ctx).apply {
            text = "✕"
            textSize = 14f
            setTextColor(resources.getColor(R.color.text_tertiary, null))
            alpha = 0.5f
            gravity = android.view.Gravity.CENTER
            setPadding((8 * dp).toInt(), (4 * dp).toInt(), (2 * dp).toInt(), (4 * dp).toInt())
            setOnClickListener {
                TodoStorage.delete(ctx, item.id)
                deleteAnimate(card) { refreshList() }
            }
        })

        // 勾选事件
        cb.setOnCheckedChangeListener { _, isChecked ->
            if (isAnimating) return@setOnCheckedChangeListener
            isAnimating = true
            if (isChecked) {
                TodoStorage.markDone(ctx, item.id)
                card.animate()
                    .alpha(0.2f).translationX(30f)
                    .setDuration(280).setInterpolator(DecelerateInterpolator())
                    .withEndAction { isAnimating = false; refreshList() }
                    .start()
            } else {
                TodoStorage.markUndone(ctx, item.id)
                isAnimating = false; refreshList()
            }
        }

        // 入场动画
        if (!isChild) {
            card.translationY = 24f; card.alpha = 0f
            card.postDelayed({
                card.animate().translationY(0f).alpha(1f).setDuration(280).setInterpolator(DecelerateInterpolator()).start()
            }, (index * 35).toLong())
        }

        return card
    }

    // ==================== 学习目标设置 ====================

    private fun showStudyGoalSettingsDialog() {
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density
        val currentHours = TodoStorage.getStudyGoalHours(ctx)
        val currentSubjects = TodoStorage.getStudyGoalSubjects(ctx)

        val contentLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * dp).toInt(), (12 * dp).toInt(), (20 * dp).toInt(), 0)
        }

        // ---- 总目标时长区 ----
        contentLayout.addView(TextView(ctx).apply {
            text = "每日学习目标"
            textSize = 13f
            setTextColor(resources.getColor(R.color.text_secondary, null))
            paint.isFakeBoldText = true
            setPadding(0, 0, 0, (6 * dp).toInt())
        })

        // 输入框卡片
        val hoursCard = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.card_glass)
            setPadding((14 * dp).toInt(), (4 * dp).toInt(), (14 * dp).toInt(), (4 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (16 * dp).toInt() }
        }
        hoursCard.addView(TextView(ctx).apply {
            text = "⏱"
            textSize = 18f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = (10 * dp).toInt() }
        })
        val etHours = EditText(ctx).apply {
            hint = "输入小时数，如 3"
            setText(if (currentHours > 0f) currentHours.toString().removeSuffix(".0") else "")
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            textSize = 16f
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(0, (10 * dp).toInt(), 0, (10 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        hoursCard.addView(etHours)
        hoursCard.addView(TextView(ctx).apply {
            text = "小时"
            textSize = 14f
            setTextColor(resources.getColor(R.color.text_secondary, null))
        })
        contentLayout.addView(hoursCard)

        // ---- 科目目标区 ----
        contentLayout.addView(TextView(ctx).apply {
            text = "科目目标（可选）"
            textSize = 13f
            setTextColor(resources.getColor(R.color.text_secondary, null))
            paint.isFakeBoldText = true
            setPadding(0, (4 * dp).toInt(), 0, (8 * dp).toInt())
        })

        val subjectListLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }
        contentLayout.addView(subjectListLayout)

        val subjectTargets = currentSubjects.toMutableList()

        fun refreshSubjectList() {
            subjectListLayout.removeAllViews()
            for ((idx, target) in subjectTargets.withIndex()) {
                val name = if (target.subjectName.isNotEmpty()) "${target.groupName} · ${target.subjectName}" else target.groupName

                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setBackgroundResource(R.drawable.card_glass)
                    setPadding((14 * dp).toInt(), (10 * dp).toInt(), (10 * dp).toInt(), (10 * dp).toInt())
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = (6 * dp).toInt() }
                }
                // 蓝色圆点
                row.addView(View(ctx).apply {
                    setBackgroundColor(resources.getColor(R.color.blue_primary, null))
                    layoutParams = LinearLayout.LayoutParams(
                        (6 * dp).toInt(), (6 * dp).toInt()
                    ).apply { marginEnd = (10 * dp).toInt() }
                })
                // 科目名
                row.addView(TextView(ctx).apply {
                    text = name
                    textSize = 14f
                    setTextColor(resources.getColor(R.color.text_primary, null))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                // 时长标签
                row.addView(TextView(ctx).apply {
                    text = "${target.targetMinutes}min"
                    textSize = 12f
                    setTextColor(resources.getColor(R.color.blue_primary, null))
                    paint.isFakeBoldText = true
                    setBackgroundResource(R.drawable.btn_pill_outline)
                    setPadding((8 * dp).toInt(), (3 * dp).toInt(), (8 * dp).toInt(), (3 * dp).toInt())
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { marginEnd = (8 * dp).toInt() }
                })
                // 删除
                row.addView(TextView(ctx).apply {
                    text = "✕"
                    textSize = 13f
                    setTextColor(resources.getColor(R.color.text_tertiary, null))
                    alpha = 0.6f
                    setPadding((6 * dp).toInt(), (4 * dp).toInt(), (2 * dp).toInt(), (4 * dp).toInt())
                    setOnClickListener {
                        subjectTargets.removeAt(idx)
                        refreshSubjectList()
                    }
                })
                subjectListLayout.addView(row)
            }
        }
        refreshSubjectList()

        // ---- 添加科目目标按钮（药丸形） ----
        val addBtn = TextView(ctx).apply {
            text = "＋ 添加科目目标"
            textSize = 14f
            setTextColor(resources.getColor(R.color.blue_primary, null))
            paint.isFakeBoldText = true
            gravity = android.view.Gravity.CENTER
            setBackgroundResource(R.drawable.btn_pill_outline)
            setPadding((16 * dp).toInt(), (10 * dp).toInt(), (16 * dp).toInt(), (10 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (8 * dp).toInt() }
            setOnClickListener { showAddSubjectTargetDialog(subjectTargets) { refreshSubjectList() } }
        }
        contentLayout.addView(addBtn)

        val scrollView = ScrollView(ctx).apply {
            addView(contentLayout)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        AlertDialog.Builder(ctx)
            .setTitle("📚 学习目标设置")
            .setView(scrollView)
            .setPositiveButton("保存") { _, _ ->
                val hours = etHours.text.toString().toFloatOrNull() ?: 0f
                if (hours > 0f) {
                    TodoStorage.setStudyGoalHours(ctx, hours)
                    TodoStorage.setStudyGoalSubjects(ctx, subjectTargets)
                    TodoStorage.setStudyGoalEnabled(ctx, true)
                    refreshList()
                    Toast.makeText(ctx, "学习目标已保存", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(ctx, "请输入有效的小时数", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("关闭目标") { _, _ ->
                TodoStorage.setStudyGoalEnabled(ctx, false)
                refreshList()
                Toast.makeText(ctx, "已关闭学习目标", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showAddSubjectTargetDialog(
        targets: MutableList<SubjectTarget>,
        onAdded: () -> Unit
    ) {
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density
        val groups = SubjectData.getGroupNames()
        if (groups.isEmpty()) {
            Toast.makeText(ctx, "请先在计时页添加科目", Toast.LENGTH_SHORT).show()
            return
        }

        var selectedGroup = groups[0]
        var selectedSubject = ""

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * dp).toInt(), (12 * dp).toInt(), (20 * dp).toInt(), 0)
        }

        // ---- 科目集选择 ----
        layout.addView(TextView(ctx).apply {
            text = "科目集"
            textSize = 13f
            setTextColor(resources.getColor(R.color.text_secondary, null))
            paint.isFakeBoldText = true
            setPadding(0, 0, 0, (6 * dp).toInt())
        })

        val tvGroup = TextView(ctx).apply {
            text = "  $selectedGroup  ▾"
            textSize = 15f
            setTextColor(resources.getColor(R.color.text_primary, null))
            setBackgroundResource(R.drawable.card_glass)
            setPadding((14 * dp).toInt(), (12 * dp).toInt(), (14 * dp).toInt(), (12 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (14 * dp).toInt() }
            setOnClickListener {
                AlertDialog.Builder(ctx)
                    .setTitle("选择科目集")
                    .setItems(groups.toTypedArray()) { _, idx ->
                        selectedGroup = groups[idx]
                        selectedSubject = ""
                        text = "  $selectedGroup  ▾"
                    }.show()
            }
        }
        layout.addView(tvGroup)

        // ---- 科目选择 ----
        layout.addView(TextView(ctx).apply {
            text = "科目"
            textSize = 13f
            setTextColor(resources.getColor(R.color.text_secondary, null))
            paint.isFakeBoldText = true
            setPadding(0, 0, 0, (6 * dp).toInt())
        })

        val tvSubject = TextView(ctx).apply {
            text = "  全部科目  ▾"
            textSize = 15f
            setTextColor(resources.getColor(R.color.text_primary, null))
            setBackgroundResource(R.drawable.card_glass)
            setPadding((14 * dp).toInt(), (12 * dp).toInt(), (14 * dp).toInt(), (12 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (14 * dp).toInt() }
            setOnClickListener {
                val subjects = SubjectData.getSubjectsByGroup(selectedGroup)
                val items = arrayOf("全部科目") + subjects
                AlertDialog.Builder(ctx)
                    .setTitle("选择科目")
                    .setItems(items) { _, idx ->
                        selectedSubject = if (idx == 0) "" else subjects[idx - 1]
                        text = "  ${items[idx]}  ▾"
                    }.show()
            }
        }
        layout.addView(tvSubject)

        // ---- 目标时长 ----
        layout.addView(TextView(ctx).apply {
            text = "目标时长"
            textSize = 13f
            setTextColor(resources.getColor(R.color.text_secondary, null))
            paint.isFakeBoldText = true
            setPadding(0, 0, 0, (6 * dp).toInt())
        })

        val minutesCard = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.card_glass)
            setPadding((14 * dp).toInt(), (4 * dp).toInt(), (14 * dp).toInt(), (4 * dp).toInt())
        }
        val etMinutes = EditText(ctx).apply {
            hint = "30"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("30")
            textSize = 16f
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(0, (10 * dp).toInt(), 0, (10 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        minutesCard.addView(etMinutes)
        minutesCard.addView(TextView(ctx).apply {
            text = "分钟"
            textSize = 14f
            setTextColor(resources.getColor(R.color.text_secondary, null))
        })
        layout.addView(minutesCard)

        AlertDialog.Builder(ctx)
            .setTitle("➕ 添加科目目标")
            .setView(layout)
            .setPositiveButton("添加") { _, _ ->
                val mins = etMinutes.text.toString().toIntOrNull() ?: 0
                if (mins > 0) {
                    targets.add(SubjectTarget(selectedGroup, selectedSubject, mins))
                    onAdded()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ==================== 工具 ====================

    private fun deleteAnimate(view: View, onEnd: () -> Unit) {
        ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 0.8f).apply { duration = 150; start() }
        ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 0.8f).apply { duration = 150; start() }
        view.animate()
            .alpha(0f).translationX(-200f)
            .setDuration(250)
            .withEndAction(onEnd)
            .start()
    }

    private fun daysUntil(dateStr: String): Int {
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = fmt.parse(dateStr) ?: return 999
            val now = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            ((date.time - now.timeInMillis) / (1000 * 60 * 60 * 24)).toInt()
        } catch (_: Exception) { 999 }
    }
}
