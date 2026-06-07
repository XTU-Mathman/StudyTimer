package com.example.studytimer

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * 自定义扇形统计图（甜甜圈风格）— 带展开动画 + 触摸高亮
 */
class PieChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val colors = listOf(
        Color.parseColor("#FF6BA4D1"),
        Color.parseColor("#FF8B82B8"),
        Color.parseColor("#FFA088B8"),
        Color.parseColor("#FFC496A8"),
        Color.parseColor("#FFD4A574"),
        Color.parseColor("#FF6DB89A"),
        Color.parseColor("#FF6BA8A8"),
        Color.parseColor("#FFA8CBE3")
    )

    private var data: List<Pair<String, Long>> = emptyList()
    private var animProgress = 1f
    private var selectedIndex = -1  // 触摸高亮的扇区索引
    private var highlightProgress = 0f  // 高亮弹出动画 0..1

    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val arcShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x20000000
        maskFilter = BlurMaskFilter(8f * density, BlurMaskFilter.Blur.NORMAL)
    }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val legendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 13f * scaledDensity
    }
    private val legendDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val percentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12f * scaledDensity
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 15f * scaledDensity
        color = Color.parseColor("#FF1C1917")
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val innerRadiusRatio = 0.55f
    private val explodeDistance = 8f * density  // 高亮弹出像素

    private var highlightAnimator: ValueAnimator? = null
    private var highlightRunnable: Runnable? = null

    fun setData(items: List<Pair<String, Long>>, animate: Boolean = true) {
        data = items
        selectedIndex = -1
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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (data.isEmpty()) return super.onTouchEvent(event)

        val w = width.toFloat()
        val h = height.toFloat()
        val chartWidth = w * 0.55f
        val cx = chartWidth / 2f
        val cy = h / 2f
        val outerRadius = minOf(chartWidth, h) / 2f - 16f * density

        val dx = event.x - cx
        val dy = event.y - cy
        val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()

        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                if (dist in (outerRadius * innerRadiusRatio)..outerRadius) {
                    // 在环形区域内，计算角度
                    var angle = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                    angle = (angle + 90f + 360f) % 360f  // 从顶部开始

                    val total = data.sumOf { it.second }.toFloat()
                    var cumAngle = 0f
                    var found = -1
                    for (i in data.indices) {
                        val sweep = (data[i].second / total) * 360f
                        if (angle in cumAngle..(cumAngle + sweep)) {
                            found = i
                            break
                        }
                        cumAngle += sweep
                    }
                    if (found != selectedIndex) {
                        selectedIndex = found
                        animateHighlight()
                    }
                } else {
                    if (selectedIndex != -1) {
                        selectedIndex = -1
                        animateHighlight()
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                highlightRunnable?.let { removeCallbacks(it) }
                highlightRunnable = Runnable {
                    if (selectedIndex != -1) {
                        selectedIndex = -1
                        animateHighlight()
                    }
                }
                postDelayed(highlightRunnable!!, 800)
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        highlightAnimator?.cancel()
        highlightRunnable?.let { removeCallbacks(it) }
    }

    private fun animateHighlight() {
        highlightAnimator?.cancel()
        val target = if (selectedIndex >= 0) 1f else 0f
        highlightAnimator = ValueAnimator.ofFloat(highlightProgress, target).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                highlightProgress = it.animatedValue as Float
                invalidate()
            }
            start()
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
        val outerRadius = minOf(chartWidth, h) / 2f - 16f * density
        val innerRadius = outerRadius * innerRadiusRatio

        val oval = RectF(cx - outerRadius, cy - outerRadius, cx + outerRadius, cy + outerRadius)
        var startAngle = -90f

        var drawnSweep = 0f
        val totalSweep = 360f * animProgress

        for (i in data.indices) {
            val (_, value) = data[i]
            val fullSweep = (value / total) * 360f
            val available = totalSweep - drawnSweep
            if (available <= 0f) break

            val sweepAngle = minOf(fullSweep, available)
            arcPaint.color = colors[i % colors.size]

            // 高亮弹出
            if (i == selectedIndex && highlightProgress > 0f) {
                val midAngle = startAngle + sweepAngle / 2f
                val radians = Math.toRadians(midAngle.toDouble())
                val offset = explodeDistance * highlightProgress
                val dx = offset * Math.cos(radians).toFloat()
                val dy = offset * Math.sin(radians).toFloat()

                // 阴影
                val shadowOval = RectF(oval).apply { offset(dx * 0.5f, dy * 0.5f) }
                canvas.drawArc(shadowOval, startAngle, sweepAngle, true, arcShadowPaint)

                val highlightOval = RectF(oval).apply { offset(dx, dy) }
                canvas.drawArc(highlightOval, startAngle, sweepAngle, true, arcPaint)
            } else {
                canvas.drawArc(oval, startAngle, sweepAngle, true, arcPaint)
            }

            // 百分比文字
            if (sweepAngle > 15f) {
                val midAngle = startAngle + sweepAngle / 2f
                val radians = Math.toRadians(midAngle.toDouble())
                val textRadius = (outerRadius + innerRadius) / 2f
                val offsetX = if (i == selectedIndex) explodeDistance * highlightProgress * Math.cos(radians).toFloat() else 0f
                val offsetY = if (i == selectedIndex) explodeDistance * highlightProgress * Math.sin(radians).toFloat() else 0f
                val tx = cx + textRadius * Math.cos(radians).toFloat() + offsetX
                val ty = cy + textRadius * Math.sin(radians).toFloat() + offsetY
                val percentText = "${(value / total * 100).toInt()}%"
                canvas.drawText(percentText, tx, ty + 5f * density, percentPaint)
            }

            drawnSweep += fullSweep
            startAngle += fullSweep
        }

        // 中心白色圆
        canvas.drawCircle(cx, cy, innerRadius, centerPaint)

        // 中心文字
        val totalHours = total / 3600f
        val centerText = if (totalHours >= 1f) "%.1f 小时".format(totalHours)
            else "${(total / 60).toInt()} 分钟"
        titlePaint.textSize = 15f * scaledDensity
        canvas.drawText(centerText, cx, cy + 6f * density, titlePaint)

        // 图例
        val legendX = chartWidth + 12f * density
        var legendY = 28f * density
        val legendSpacing = 30f * density

        legendPaint.textSize = 14f * scaledDensity
        legendPaint.color = Color.parseColor("#FF1C1917")
        legendPaint.isFakeBoldText = true
        canvas.drawText("科目占比", legendX, legendY, legendPaint)
        legendY += legendSpacing + 8f * density

        legendPaint.textSize = 11f * scaledDensity
        legendPaint.color = Color.parseColor("#FF78716C")
        legendPaint.isFakeBoldText = false

        for (i in data.indices) {
            val (name, value) = data[i]
            val percent = (value / total * 100).toInt()
            legendDotPaint.color = colors[i % colors.size]
            canvas.drawCircle(legendX + 6f * density, legendY - 4f * density, 5f * density, legendDotPaint)
            val displayName = if (name.length > 10) name.take(10) + "…" else name
            canvas.drawText("$displayName  $percent%", legendX + 16f * density, legendY, legendPaint)
            legendY += legendSpacing
        }
    }
}
