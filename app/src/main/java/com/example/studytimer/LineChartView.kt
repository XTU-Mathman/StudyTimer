package com.example.studytimer

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
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
        color = Color.parseColor("#FF6BA4D1")
        strokeWidth = 4f * resources.displayMetrics.density
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF6BA4D1")
        style = Paint.Style.FILL
    }
    private val dotStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFCCCCCC")
        strokeWidth = 1f * resources.displayMetrics.density
        style = Paint.Style.STROKE
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFF0F0F0")
        strokeWidth = 1f * resources.displayMetrics.density
        style = Paint.Style.STROKE
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11f * resources.displayMetrics.scaledDensity
        color = Color.parseColor("#FF78716C")
        textAlign = Paint.Align.CENTER
    }
    private val yLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11f * resources.displayMetrics.scaledDensity
        color = Color.parseColor("#FF78716C")
        textAlign = Paint.Align.RIGHT
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 14f * resources.displayMetrics.scaledDensity
        color = Color.parseColor("#FF2D2D2D")
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val density = resources.displayMetrics.density
    private val marginLeft = 48f * density
    private val marginRight = 12f * density
    private val marginTop = 32f * density
    private val marginBottom = 40f * density

    // 触摸交互
    private var touchedIndex = -1
    private var touchX = 0f
    private var touchY = 0f
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF6BA4D1")
        style = Paint.Style.FILL
    }
    private val highlightTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12f * resources.displayMetrics.scaledDensity
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }

    private var cachedPoints = mutableListOf<Pair<Float, Float>>()
    private var cachedData = mutableListOf<Pair<String, Long>>()

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
        canvas.drawText("每日学习时长趋势", w / 2f, 22f * density, titlePaint)

        // Y 轴范围
        val maxSeconds = data.maxOf { it.second }.coerceAtLeast(60)
        val yMax = niceMax(maxSeconds)

        // 网格线
        val gridCount = 4
        for (i in 0..gridCount) {
            val y = chartBottom - (chartHeight * i / gridCount)
            val value = yMax * i / gridCount
            canvas.drawLine(chartLeft, y, chartRight, y, gridPaint)
            canvas.drawText("${value / 60}分", chartLeft - 6f * density, y + 6f * density, yLabelPaint)
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
        cachedPoints = points.toMutableList()
        cachedData = effectiveData.toMutableList()

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
                Color.parseColor("#406BA4D1"),
                Color.parseColor("#086BA4D1"),
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
        val dotRadius = 5f * density
        points.forEachIndexed { i, (px, py) ->
            if (i == touchedIndex) {
                // 高亮选中的点
                highlightPaint.color = Color.parseColor("#FF6BA4D1")
                canvas.drawCircle(px, py, dotRadius + 4f * density, highlightPaint)
                // 白色描边
                dotStrokePaint.strokeWidth = 3f * density
                canvas.drawCircle(px, py, dotRadius + 6f * density, dotStrokePaint)
            } else {
                canvas.drawCircle(px, py, dotRadius + 2f * density, dotStrokePaint)
                canvas.drawCircle(px, py, dotRadius, dotPaint)
            }
        }

        // 绘制选中标签
        if (touchedIndex in points.indices) {
            val (tx, ty) = points[touchedIndex]
            val label = effectiveData[touchedIndex].first
            val value = effectiveData[touchedIndex].second
            val hh = value / 3600; val mm = (value % 3600) / 60
            val text = "$label\n${hh}时${mm}分"

            // 背景框
            val bgRect = android.graphics.RectF()
            val tempPaint = Paint().apply { textSize = highlightTextPaint.textSize }
            val lines = text.split("\n")
            val maxW = lines.maxOf { tempPaint.measureText(it) } + 16f * density
            val bgH = lines.size * (highlightTextPaint.textSize + 4f * density) + 8f * density

            var bgX = tx - maxW / 2f
            val bgY = ty - 45f * density
            if (bgX < 4f * density) bgX = 4f * density
            if (bgX + maxW > width - 4f * density) bgX = width - maxW - 4f * density

            bgRect.set(bgX, bgY, bgX + maxW, bgY + bgH)
            highlightPaint.color = 0xDD1C1917.toInt()
            canvas.drawRoundRect(bgRect, 8f * density, 8f * density, highlightPaint)

            // 文字
            highlightTextPaint.color = Color.WHITE
            var lineY = bgY + highlightTextPaint.textSize + 6f * density
            for (line in lines) {
                canvas.drawText(line, bgX + maxW / 2f, lineY, highlightTextPaint)
                lineY += highlightTextPaint.textSize + 4f * density
            }
        }

        // X 轴标签
        val step = if (data.size <= 12) 1 else (data.size / 8).coerceAtLeast(1)
        for (i in effectiveData.indices step step) {
            val x = if (effectiveData.size == 1) chartLeft + chartWidth / 2f
                else chartLeft + chartWidth * i / (effectiveData.size - 1)
            val label = effectiveData[i].first
            val shortLabel = if (label.length > 5) label.take(5) + ".." else label
            canvas.drawText(shortLabel, x, chartBottom + 24f * density, labelPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (cachedPoints.isEmpty() || data.isEmpty()) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val ex = event.x
                val ey = event.y
                // 找最近的数据点
                var minDist = 100f * density
                var nearest = -1
                cachedPoints.forEachIndexed { i, (px, py) ->
                    val dist = Math.sqrt(((ex - px) * (ex - px) + (ey - py) * (ey - py)).toDouble()).toFloat()
                    if (dist < minDist) {
                        minDist = dist
                        nearest = i
                    }
                }
                if (nearest >= 0 && nearest != touchedIndex) {
                    touchedIndex = nearest
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // 延迟取消选中
                postDelayed({
                    touchedIndex = -1
                    invalidate()
                }, 2000)
            }
        }
        return true
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
