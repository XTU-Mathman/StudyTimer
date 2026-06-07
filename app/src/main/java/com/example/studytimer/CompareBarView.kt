package com.example.studytimer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * 科目对比条形图
 * 显示每个科目当前周期 vs 上一周期的并排双条
 * 右侧显示增减百分比
 */
class CompareBarView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(ctx, attrs, defStyleAttr) {

    data class CompareItem(
        val name: String,
        val currentSeconds: Long,
        val previousSeconds: Long,
        val color: Int
    )

    private var items: List<CompareItem> = emptyList()

    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity

    private val barHeight = 14f * density
    private val barGap = 4f * density
    private val rowHeight = 60f * density
    private val labelWidth = 100f * density
    private val percentWidth = 60f * density

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12f * scaledDensity
        color = 0xFF1C1917.toInt()
    }
    private val percentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11f * scaledDensity
        textAlign = Paint.Align.RIGHT
    }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x0D000000
    }
    private val legendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f * scaledDensity
        color = 0xFF78716C.toInt()
    }

    fun setData(data: List<CompareItem>) {
        items = data
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = if (items.isEmpty()) 0 else (items.size * rowHeight + 30f * density).toInt()
        setMeasuredDimension(resolveSize(0, widthMeasureSpec), resolveSize(h, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (items.isEmpty() || width == 0) return

        val maxSeconds = items.maxOf { maxOf(it.currentSeconds, it.previousSeconds) }.coerceAtLeast(1)
        val barAreaWidth = width - labelWidth - percentWidth - 16f * density

        // 图例
        val legendY = 12f * density
        val legendX = labelWidth
        barPaint.color = 0x666BA4D1.toInt()
        canvas.drawRect(RectF(legendX, legendY, legendX + 12f * density, legendY + 10f * density), barPaint)
        canvas.drawText("上期", legendX + 16f * density, legendY + 9f * density, legendPaint)

        barPaint.color = 0xFF6BA4D1.toInt()
        val lx2 = legendX + 60f * density
        canvas.drawRect(RectF(lx2, legendY, lx2 + 12f * density, legendY + 10f * density), barPaint)
        canvas.drawText("本期", lx2 + 16f * density, legendY + 9f * density, legendPaint)

        for (i in items.indices) {
            val item = items[i]
            val y = 28f * density + i * rowHeight

            // 科目名
            val shortName = if (item.name.length > 8) item.name.take(8) + "…" else item.name
            canvas.drawText(shortName, 4f * density, y + barHeight, labelPaint)

            val barLeft = labelWidth

            // 上期条（灰色/淡色）
            val prevWidth = (item.previousSeconds.toFloat() / maxSeconds * barAreaWidth).coerceAtLeast(0f)
            barPaint.color = 0x33000000
            canvas.drawRoundRect(
                RectF(barLeft, y, barLeft + prevWidth, y + barHeight),
                3f * density, 3f * density, barPaint
            )

            // 本期条（科目色）
            val curWidth = (item.currentSeconds.toFloat() / maxSeconds * barAreaWidth).coerceAtLeast(0f)
            barPaint.color = item.color
            canvas.drawRoundRect(
                RectF(barLeft, y + barHeight + barGap, barLeft + curWidth, y + 2 * barHeight + barGap),
                3f * density, 3f * density, barPaint
            )

            // 增减百分比
            val changeText = if (item.previousSeconds == 0L) {
                if (item.currentSeconds > 0) "新" else "—"
            } else {
                val pct = ((item.currentSeconds - item.previousSeconds).toFloat() / item.previousSeconds * 100).toInt()
                if (pct > 0) "+${pct}%" else "${pct}%"
            }

            percentPaint.color = when {
                item.previousSeconds == 0L && item.currentSeconds > 0 -> 0xFF6DB89A.toInt()
                item.currentSeconds > item.previousSeconds -> 0xFF6DB89A.toInt()  // 增长=绿
                item.currentSeconds < item.previousSeconds -> 0xFFD4726A.toInt()  // 下降=红
                else -> 0xFF78716C.toInt()  // 持平=灰
            }
            canvas.drawText(changeText, width - 4f * density, y + barHeight + barGap + 4f * density, percentPaint)
        }
    }
}
