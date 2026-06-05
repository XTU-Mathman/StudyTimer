package com.example.studytimer

import android.animation.ObjectAnimator
import android.app.DatePickerDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.studytimer.TodoItem
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 待办页面大改版
 * - FAB 添加（底部弹出对话框）
 * - 玻璃卡片列表项 + 优先级色条
 * - 完成动画
 */
class TodoFragment : Fragment() {

    private lateinit var layoutTodoList: LinearLayout
    private lateinit var fabAdd: FloatingActionButton

    private val handler = Handler(Looper.getMainLooper())
    private var isAnimating = false

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

        fabAdd.setOnClickListener { showAddDialog() }
    }

    private fun refreshList() {
        layoutTodoList.removeAllViews()
        val items = TodoStorage.getAll(requireContext())
        for ((i, item) in items.withIndex()) {
            layoutTodoList.addView(createTodoItem(item, i))
        }
    }

    private fun createTodoItem(item: TodoItem, index: Int): View {
        val ctx = requireContext()
        val density = ctx.resources.displayMetrics.density

        // 主卡片容器
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.card_glass)
            elevation = 1f * density
            setPadding(16, 12, 16, 12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, (8 * density).toInt()) }
        }

        // 优先级色条（左侧竖条）
        card.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                (4 * density).toInt(), LinearLayout.LayoutParams.MATCH_PARENT
            ).apply { marginEnd = (12 * density).toInt() }
            // 颜色：默认蓝，带日期变橙/红
            setBackgroundColor(
                if (item.dueDate.isNotEmpty()) {
                    val daysLeft = daysUntil(item.dueDate)
                    when {
                        daysLeft < 0 -> 0xFFFF6B6B.toInt()   // 已过期 → 红
                        daysLeft <= 1 -> 0xFFFF9F0A.toInt()  // 今天/明天 → 橙
                        else -> 0xFF6B9FC7.toInt()           // 普通 → 蓝
                    }
                } else 0xFF6B9FC7.toInt()
            )
        })

        // 勾选
        val cb = CheckBox(ctx).apply {
            isChecked = item.isDone
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = (12 * density).toInt() }
        }
        card.addView(cb)

        // 文字区（纵向）
        val textArea = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
            setPadding(0, 2, 0, 2)
        }

        // 标题
        textArea.addView(TextView(ctx).apply {
            text = item.content
            textSize = 15f
            setTextColor(resources.getColor(R.color.text_primary, null))
            paint.isStrikeThruText = item.isDone
            alpha = if (item.isDone) 0.5f else 1f
        })

        // 副标题行（每日/截止日期）
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
                textSize = 12f
                setTextColor(resources.getColor(R.color.text_tertiary, null))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (2 * density).toInt() }
            })
        }

        card.addView(textArea)

        // 删除按钮
        card.addView(TextView(ctx).apply {
            text = "✕"
            textSize = 16f
            setTextColor(resources.getColor(R.color.text_tertiary, null))
            gravity = android.view.Gravity.CENTER
            setPadding((12 * density).toInt(), 0, 0, 0)
            setOnClickListener {
                val itemToDelete = TodoStorage.getAll(ctx).getOrNull(index)
                if (itemToDelete != null) TodoStorage.delete(ctx, itemToDelete.id)
                deleteAnimate(card) { refreshList() }
            }
        })

        // 勾选事件
        cb.setOnCheckedChangeListener { _, isChecked ->
            if (isAnimating) return@setOnCheckedChangeListener
            isAnimating = true
            val toggleItem = TodoStorage.getAll(ctx).getOrNull(index)
            if (toggleItem != null && !toggleItem.isDone) {
                TodoStorage.markDone(ctx, toggleItem.id)
            } else {
                isAnimating = false
                refreshList()
                return@setOnCheckedChangeListener
            }
            // 完成动画
            if (isChecked) {
                card.animate()
                    .alpha(0.3f)
                    .translationX(40f)
                    .setDuration(300)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction {
                        isAnimating = false
                        refreshList()
                    }
                    .start()
            } else {
                isAnimating = false
                refreshList()
            }
        }

        // 进场动画（每个卡片延迟递增）
        card.translationY = 30f
        card.alpha = 0f
        card.postDelayed({
            card.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(300)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }, (index * 50).toLong())

        return card
    }

    private fun deleteAnimate(view: View, onEnd: () -> Unit) {
        ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 0.8f).apply {
            duration = 150; start()
        }
        ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 0.8f).apply {
            duration = 150; start()
        }
        view.animate()
            .alpha(0f)
            .translationX(-200f)
            .setDuration(250)
            .withEndAction(onEnd)
            .start()
    }

    private fun showAddDialog() {
        val ctx = requireContext()
        val density = ctx.resources.displayMetrics.density

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
        }

        val etInput = android.widget.EditText(ctx).apply {
            hint = "添加待办事项..."
            textSize = 16f
            setPadding(0, 16, 0, 16)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        layout.addView(etInput)

        val cbDaily = CheckBox(ctx).apply {
            text = "每日重复"
            textSize = 14f
        }
        layout.addView(cbDaily)

        val tvPickDate = TextView(ctx).apply {
            text = "📅 设置截止日期"
            textSize = 14f
            setTextColor(resources.getColor(R.color.blue_primary, null))
            setPadding(0, 8, 0, 8)
            setOnClickListener {
                val cal = Calendar.getInstance()
                DatePickerDialog(ctx, { _, y, m, d ->
                    this.text = String.format("📅 %d-%02d-%02d", y, m + 1, d)
                    this.tag = String.format("%d-%02d-%02d", y, m + 1, d)
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
                    .show()
            }
        }
        layout.addView(tvPickDate)

        AlertDialog.Builder(ctx)
            .setTitle("添加待办")
            .setView(layout)
            .setPositiveButton("添加") { _, _ ->
                val text = etInput.text.toString().trim()
                if (text.isNotEmpty()) {
                    val due = tvPickDate.tag?.toString() ?: ""
                    TodoStorage.add(ctx, TodoItem(
                    id = System.currentTimeMillis(),
                    content = text,
                    isDaily = cbDaily.isChecked,
                    dueDate = due
                ))
                    refreshList()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun daysUntil(dateStr: String): Int {
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = fmt.parse(dateStr) ?: return 999
            val now = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            ((date.time - now.timeInMillis) / (1000 * 60 * 60 * 24)).toInt()
        } catch (_: Exception) { 999 }
    }
}
