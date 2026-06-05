package com.example.studytimer

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * 自定义折线统计图 — 带逐段绘制动画
 */
class LineChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var data: List<Pair<String, Long>> = emptyList()
    private var animProgress = 1f  // 0..1

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF6B9FC7")
        strokeWidth = 6f
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF6B9FC7")
        style = Paint.Style.FILL
    }
    private val dotStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFCCCCCC")
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFF0F0F0")
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 24f
        color = Color.parseColor("#FF8B8580")
        textAlign = Paint.Align.CENTER
    }
    private val yLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 24f
        color = Color.parseColor("#FF8B8580")
        textAlign = Paint.Align.RIGHT
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 30f
        color = Color.parseColor("#FF2D2D2D")
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val marginLeft = 64f
    private val marginRight = 16f
    private val marginTop = 48f
    private val marginBottom = 56f

    fun setData(items: List<Pair<String, Long>>, animate: Boolean = true) {
        data = items
        if (animate) {
            animProgress = 0f
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 800
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

        val chartLeft = marginLeft
        val chartRight = w - marginRight
        val chartTop = marginTop
        val chartBottom = h - marginBottom
        val chartWidth = chartRight - chartLeft
        val chartHeight = chartBottom - chartTop

        // 标题
        canvas.drawText("每日学习时长趋势", w / 2f, 34f, titlePaint)

        // Y 轴范围
        val maxSeconds = data.maxOf { it.second }.coerceAtLeast(60)
        val yMax = niceMax(maxSeconds)

        // 网格线
        val gridCount = 4
        for (i in 0..gridCount) {
            val y = chartBottom - (chartHeight * i / gridCount)
            val value = yMax * i / gridCount
            canvas.drawLine(chartLeft, y, chartRight, y, gridPaint)
            canvas.drawText("${value / 60}分", chartLeft - 8f, y + 8f, yLabelPaint)
        }

        // X 轴 / Y 轴基线
        canvas.drawLine(chartLeft, chartBottom, chartRight, chartBottom, axisPaint)
        canvas.drawLine(chartLeft, chartTop, chartLeft, chartBottom, axisPaint)

        // 数据点坐标
        val points = mutableListOf<Pair<Float, Float>>()
        val visibleCount = (data.size * animProgress).toInt().coerceAtLeast(1)
        val effectiveData = data.take(visibleCount)

        for (i in effectiveData.indices) {
            val x = if (effectiveData.size == 1) chartLeft + chartWidth / 2f
                else chartLeft + chartWidth * i / (effectiveData.size - 1)
            val y = chartBottom - (effectiveData[i].second.toFloat() / yMax * chartHeight)
            points.add(x to y)
        }

        if (points.isEmpty()) return

        // 渐变填充
        if (points.size >= 2) {
            val fillPath = Path()
            fillPath.moveTo(points[0].first, chartBottom)
            fillPath.lineTo(points[0].first, points[0].second)
            for (i in 1 until points.size) {
                fillPath.lineTo(points[i].first, points[i].second)
            }
            fillPath.lineTo(points.last().first, chartBottom)
            fillPath.close()

            val gradient = LinearGradient(
                0f, chartTop, 0f, chartBottom,
                Color.parseColor("#406B9FC7"),
                Color.parseColor("#086B9FC7"),
                Shader.TileMode.CLAMP
            )
            fillPaint.shader = gradient
            canvas.drawPath(fillPath, fillPaint)
        }

        // 折线
        if (points.size >= 2) {
            val path = Path()
            path.moveTo(points[0].first, points[0].second)
            for (i in 1 until points.size) {
                path.lineTo(points[i].first, points[i].second)
            }
            canvas.drawPath(path, linePaint)
        }

        // 数据点
        val dotRadius = 8f
        for ((x, y) in points) {
            canvas.drawCircle(x, y, dotRadius + 3f, dotStrokePaint)
            canvas.drawCircle(x, y, dotRadius, dotPaint)
        }

        // X 轴标签
        val step = if (data.size <= 12) 1 else (data.size / 8).coerceAtLeast(1)
        for (i in effectiveData.indices step step) {
            val x = if (effectiveData.size == 1) chartLeft + chartWidth / 2f
                else chartLeft + chartWidth * i / (effectiveData.size - 1)
            val label = effectiveData[i].first
            val shortLabel = if (label.length > 5) label.take(5) + ".." else label
            canvas.drawText(shortLabel, x, chartBottom + 36f, labelPaint)
        }
    }

    private fun niceMax(maxSeconds: Long): Long {
        val maxMinutes = ((maxSeconds + 59) / 60).toInt()
        return when {
            maxMinutes <= 10 -> 600L
            maxMinutes <= 30 -> 1800L
            maxMinutes <= 60 -> 3600L
            maxMinutes <= 120 -> 7200L
            maxMinutes <= 300 -> 18000L
            else -> ((maxMinutes + 59) / 60 * 3600).toLong()
        }
    }
}
