package com.example.studytimer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * GitHub 风格学习热力图
 * 显示最近 52 周（约一年）的学习强度，每天一格
 * 颜色深浅代表学习时长：无学习=灰色，30分钟以上=最深色
 */
class HeatmapCalendarView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(ctx, attrs, defStyleAttr) {

    // 数据：日期字符串(yyyy-MM-dd) → 分钟数
    private var data: Map<String, Int> = emptyMap()

    // 尺寸
    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity
    private val cellSize = 12f * density
    private val cellGap = 3f * density
    private val labelWidth = 36f * density
    private val topLabelHeight = 20f * scaledDensity

    // 颜色（莫兰迪色系，从浅到深）
    private val colorEmpty = 0x0D000000        // 无学习：极浅灰
    private val colorLevels = intArrayOf(
        0x33D6E8F0.toInt(),  // <10min: 极浅蓝
        0x66A8CBE3.toInt(),  // 10-30min: 浅蓝
        0x996BA4D1.toInt(),  // 30-60min: 中蓝
        0xCC4A8AB8.toInt(),  // 60-120min: 深蓝
        0xFF2D6E9E.toInt()   // 120min+: 最深蓝
    )

    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f * scaledDensity
        color = 0xFF78716C.toInt()
        textAlign = Paint.Align.CENTER
    }
    private val monthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f * scaledDensity
        color = 0xFF78716C.toInt()
        textAlign = Paint.Align.LEFT
    }

    // 月标签位置缓存
    private data class MonthLabel(val label: String, val x: Float)
    private val monthLabels = mutableListOf<MonthLabel>()

    // 星期标签
    private val weekLabels = arrayOf("", "一", "", "三", "", "五", "")

    /**
     * 设置热力图数据
     * @param dailyMinutes Map<yyyy-MM-dd, 学习分钟数>
     */
    fun setData(dailyMinutes: Map<String, Int>) {
        data = dailyMinutes
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // 固定高度：7行格子 + 顶部月份标签 + 底部图例
        val rows = 7
        val cols = 53  // 52周 + 1列余量
        val desiredWidth = (labelWidth + cols * (cellSize + cellGap) + cellGap).toInt()
        val desiredHeight = (topLabelHeight + rows * (cellSize + cellGap) + cellGap + 30f * density).toInt()

        val width = resolveSize(desiredWidth, widthMeasureSpec)
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0) return

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val monthFormat = SimpleDateFormat("M月", Locale.getDefault())

        // 计算起始日期：52周前的周日
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        // 回到本周日
        cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        // 再往前推52周
        cal.add(Calendar.WEEK_OF_YEAR, -52)

        val startDate = cal.clone() as Calendar
        monthLabels.clear()

        // 绘制星期标签
        for (row in 0..6) {
            if (weekLabels[row].isNotEmpty()) {
                val y = topLabelHeight + row * (cellSize + cellGap) + cellSize * 0.75f
                canvas.drawText(weekLabels[row], labelWidth * 0.5f, y, labelPaint)
            }
        }

        // 绘制格子
        var col = 0
        var prevMonth = -1
        val drawCal = startDate.clone() as Calendar

        while (col < 53) {
            for (row in 0..6) {
                // 只绘制有效日期（不超出今天）
                if (drawCal.after(Calendar.getInstance())) {
                    drawCal.add(Calendar.DAY_OF_YEAR, 1)
                    continue
                }

                val dayOfWeek = drawCal.get(Calendar.DAY_OF_WEEK)
                // Calendar: SUNDAY=1, MONDAY=2, ..., SATURDAY=7
                // 我们要: MONDAY=0, TUESDAY=1, ..., SUNDAY=6
                val actualRow = (dayOfWeek + 5) % 7

                if (actualRow == row) {
                    val dateStr = dateFormat.format(drawCal.time)
                    val minutes = data[dateStr] ?: 0

                    // 月份标签（每月第一格）
                    val month = drawCal.get(Calendar.MONTH)
                    if (month != prevMonth && row == 0) {
                        monthLabels.add(MonthLabel(monthFormat.format(drawCal.time), labelWidth + col * (cellSize + cellGap)))
                        prevMonth = month
                    }

                    // 选择颜色
                    cellPaint.color = when {
                        minutes == 0 -> colorEmpty
                        minutes < 10 -> colorLevels[0]
                        minutes < 30 -> colorLevels[1]
                        minutes < 60 -> colorLevels[2]
                        minutes < 120 -> colorLevels[3]
                        else -> colorLevels[4]
                    }

                    val x = labelWidth + col * (cellSize + cellGap)
                    val y = topLabelHeight + row * (cellSize + cellGap)
                    val rect = RectF(x, y, x + cellSize, y + cellSize)
                    canvas.drawRoundRect(rect, 2f * density, 2f * density, cellPaint)

                    drawCal.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            col++
        }

        // 绘制月份标签
        for (label in monthLabels) {
            canvas.drawText(label.label, label.x, topLabelHeight - 4f * density, monthPaint)
        }

        // 底部图例
        val legendY = topLabelHeight + 7 * (cellSize + cellGap) + 16f * density
        val legendStartX = labelWidth + 53 * (cellSize + cellGap) - 5 * (cellSize + cellGap) - 40f * density

        labelPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("少", legendStartX - 4f * density, legendY + cellSize * 0.75f, labelPaint)
        labelPaint.textAlign = Paint.Align.CENTER

        for (i in colorLevels.indices) {
            cellPaint.color = if (i == 0) colorEmpty else colorLevels[i]
            val x = legendStartX + i * (cellSize + cellGap)
            canvas.drawRoundRect(RectF(x, legendY, x + cellSize, legendY + cellSize), 2f * density, 2f * density, cellPaint)
        }

        labelPaint.textAlign = Paint.Align.LEFT
        val lastLegendX = legendStartX + colorLevels.size * (cellSize + cellGap) + 4f * density
        canvas.drawText("多", lastLegendX, legendY + cellSize * 0.75f, labelPaint)
        labelPaint.textAlign = Paint.Align.CENTER
    }
}
