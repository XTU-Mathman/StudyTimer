package com.example.studytimer

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * 自定义扇形统计图（甜甜圈风格）— 带展开动画
 */
class PieChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val colors = listOf(
        Color.parseColor("#FF6B9FC7"),
        Color.parseColor("#FF8B7EC8"),
        Color.parseColor("#FFB07CC8"),
        Color.parseColor("#FFD489B5"),
        Color.parseColor("#FFE8A87C"),
        Color.parseColor("#FF7DB89A"),
        Color.parseColor("#FF6BB5B5"),
        Color.parseColor("#FFA8CBE3")
    )

    private var data: List<Pair<String, Long>> = emptyList()

    // 动画进度 0..1
    private var animProgress = 1f

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val legendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 28f
        color = Color.parseColor("#FF555555")
    }
    private val legendDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val percentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 32f
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 32f
        color = Color.parseColor("#FF2D2D2D")
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val innerRadiusRatio = 0.55f

    fun setData(items: List<Pair<String, Long>>, animate: Boolean = true) {
        data = items
        if (animate) {
            animProgress = 0f
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 600
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    animProgress = it.animatedFraction
                    invalidate()
                }
                start()
            }
        } else {
            animProgress = 1f
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (data.isEmpty()) return

        val w = width.toFloat()
        val h = height.toFloat()

        val total = data.sumOf { it.second }.toFloat()
        if (total == 0f) return

        val chartWidth = w * 0.55f
        val cx = chartWidth / 2f
        val cy = h / 2f
        val outerRadius = minOf(chartWidth, h) / 2f - 24f
        val innerRadius = outerRadius * innerRadiusRatio

        val oval = RectF(cx - outerRadius, cy - outerRadius, cx + outerRadius, cy + outerRadius)
        var startAngle = -90f

        // 动画阶段：先绘制到 animProgress
        var drawnSweep = 0f
        val totalSweep = 360f * animProgress

        for (i in data.indices) {
            val (_, value) = data[i]
            val fullSweep = (value / total) * 360f
            val available = totalSweep - drawnSweep
            if (available <= 0f) break

            val sweepAngle = minOf(fullSweep, available)  // 该扇区能绘制的角度
            arcPaint.color = colors[i % colors.size]
            canvas.drawArc(oval, startAngle, sweepAngle, true, arcPaint)

            // 百分比文字（只有在扇区足够大时才显示）
            if (sweepAngle > 15f) {
                val midAngle = startAngle + sweepAngle / 2f
                val radians = Math.toRadians(midAngle.toDouble())
                val textRadius = (outerRadius + innerRadius) / 2f
                val tx = cx + textRadius * Math.cos(radians).toFloat()
                val ty = cy + textRadius * Math.sin(radians).toFloat()
                val percentText = "${(value / total * 100).toInt()}%"
                canvas.drawText(percentText, tx, ty + 8f, percentPaint)
            }

            drawnSweep += fullSweep
            startAngle += fullSweep
        }

        // 中心白色圆
        canvas.drawCircle(cx, cy, innerRadius, centerPaint)

        // 中心文字：总时长
        val totalHours = total / 3600f
        val centerText = if (totalHours >= 1f) "%.1f 小时".format(totalHours)
            else "${(total / 60).toInt()} 分钟"
        titlePaint.textSize = 32f
        canvas.drawText(centerText, cx, cy + 10f, titlePaint)

        // 图例
        val legendX = chartWidth + 16f
        var legendY = 40f
        val legendSpacing = 44f

        legendPaint.textSize = 32f
        legendPaint.color = Color.parseColor("#FF2D2D2D")
        legendPaint.isFakeBoldText = true
        canvas.drawText("科目占比", legendX, legendY, legendPaint)
        legendY += legendSpacing + 12f

        legendPaint.textSize = 25f
        legendPaint.color = Color.parseColor("#FF8B8580")
        legendPaint.isFakeBoldText = false

        for (i in data.indices) {
            val (name, value) = data[i]
            val percent = (value / total * 100).toInt()
            legendDotPaint.color = colors[i % colors.size]
            canvas.drawCircle(legendX + 8f, legendY - 6f, 7f, legendDotPaint)
            val displayName = if (name.length > 10) name.take(10) + "…" else name
            canvas.drawText("$displayName  $percent%", legendX + 22f, legendY, legendPaint)
            legendY += legendSpacing
        }
    }
}
