package com.example.studytimer

import android.content.Context
import android.graphics.*
import android.text.TextPaint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 交互式学习日历 — 点击某天查看当日记录
 */
class CalendarDetailView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(ctx, attrs, defStyleAttr) {

    private var data: Map<String, Int> = emptyMap() // yyyy-MM-dd -> minutes
    private var currentMonth = Calendar.getInstance()
    private var selectedDate: String? = null
    var onDateSelected: ((date: String, minutes: Int) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity

    private val cellSize = 40f * density
    private val headerHeight = 50f * density
    private val dayLabelHeight = 30f * density

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = 0xFF6BA4D1.toInt()
    }

    private val morandiLevels = intArrayOf(
        0x0D000000, 0x33D6E8F0.toInt(), 0x66A8CBE3.toInt(),
        0x996BA4D1.toInt(), 0xCC4A8AB8.toInt(), 0xFF2D6E9E.toInt()
    )

    fun setData(dailyMinutes: Map<String, Int>) {
        data = dailyMinutes
        invalidate()
    }

    fun setMonth(year: Int, month: Int) {
        currentMonth = Calendar.getInstance().apply { set(year, month, 1) }
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = resolveSize((cellSize * 7 + 8 * density).toInt(), widthMeasureSpec)
        val h = (headerHeight + dayLabelHeight + cellSize * 6 + 8 * density).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val col = ((event.x - 4 * density) / cellSize).toInt()
            val row = ((event.y - headerHeight - dayLabelHeight) / cellSize).toInt()
            if (col in 0..6 && row in 0..5) {
                val cal = currentMonth.clone() as Calendar
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                val offset = (firstDayOfWeek + 5) % 7 // 周一=0
                val day = row * 7 + col - offset + 1
                if (day in 1..cal.getActualMaximum(Calendar.DAY_OF_MONTH)) {
                    cal.set(Calendar.DAY_OF_MONTH, day)
                    val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val dateStr = dateFmt.format(cal.time)
                    selectedDate = dateStr
                    val minutes = data[dateStr] ?: 0
                    onDateSelected?.invoke(dateStr, minutes)
                    invalidate()
                    return true
                }
            }
        }
        return false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val monthFmt = SimpleDateFormat("yyyy年MM月", Locale.getDefault())

        // 月份标题
        textPaint.textSize = 16f * scaledDensity
        textPaint.color = 0xFF1C1917.toInt()
        textPaint.isFakeBoldText = true
        canvas.drawText(monthFmt.format(currentMonth.time), width / 2f, headerHeight * 0.6f, textPaint)

        // 星期标签
        val days = arrayOf("一", "二", "三", "四", "五", "六", "日")
        textPaint.textSize = 12f * scaledDensity
        textPaint.isFakeBoldText = false
        textPaint.color = 0xFF78716C.toInt()
        for (i in 0..6) {
            val x = 4 * density + i * cellSize + cellSize / 2f
            canvas.drawText(days[i], x, headerHeight + dayLabelHeight * 0.7f, textPaint)
        }

        // 绘制格子
        val cal = currentMonth.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val offset = (firstDayOfWeek + 5) % 7
        val maxDay = currentMonth.getActualMaximum(Calendar.DAY_OF_MONTH)

        val todayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayStr = todayFmt.format(Calendar.getInstance().time)

        for (day in 1..maxDay) {
            cal.set(Calendar.DAY_OF_MONTH, day)
            val row = (offset + day - 1) / 7
            val col = (offset + day - 1) % 7

            val cx = 4 * density + col * cellSize + cellSize / 2f
            val cy = headerHeight + dayLabelHeight + row * cellSize + cellSize / 2f
            val dateStr = dateFmt.format(cal.time)
            val minutes = data[dateStr] ?: 0

            // 背景色
            val level = when {
                minutes == 0 -> 0
                minutes < 10 -> 1
                minutes < 30 -> 2
                minutes < 60 -> 3
                minutes < 120 -> 4
                else -> 5
            }
            bgPaint.color = morandiLevels[level]
            canvas.drawRoundRect(
                cx - cellSize / 2f + 2f * density,
                cy - cellSize / 2f + 2f * density,
                cx + cellSize / 2f - 2f * density,
                cy + cellSize / 2f - 2f * density,
                6f * density, 6f * density, bgPaint
            )

            // 选中边框
            if (dateStr == selectedDate) {
                canvas.drawRoundRect(
                    cx - cellSize / 2f + 1f * density,
                    cy - cellSize / 2f + 1f * density,
                    cx + cellSize / 2f - 1f * density,
                    cy + cellSize / 2f - 1f * density,
                    6f * density, 6f * density, selectedPaint
                )
            }

            // 如果是今天，画一个圆点
            if (dateStr == todayStr) {
                bgPaint.color = 0xFF6BA4D1.toInt()
                canvas.drawCircle(cx, cy - cellSize / 2f + 4f * density, 3f * density, bgPaint)
            }
        }
    }

    fun getSelectedDate(): String? = selectedDate
}
