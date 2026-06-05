package com.example.studytimer

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

/**
 * 自定义折线统计图
 * 用于显示每天学习时长的变化趋势（每周/每月模式使用）
 */
class LineChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ==================== 数据 ====================
    // 每个条目：日期标签 -> 时长（秒）
    private var data: List<Pair<String, Long>> = emptyList()

    // ==================== 画笔 ====================
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF6B9FC7")  // 雾蓝折线
        strokeWidth = 6f
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
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
        color = Color.parseColor("#FF888888")
        textAlign = Paint.Align.CENTER
    }
    private val yLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 24f
        color = Color.parseColor("#FF888888")
        textAlign = Paint.Align.RIGHT
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 30f
        color = Color.parseColor("#FF333333")
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    // 边距（为坐标轴和标签留空间）
    private val marginLeft = 64f
    private val marginRight = 16f
    private val marginTop = 48f
    private val marginBottom = 56f

    /**
     * 设置图表数据
     * @param items 日期标签 -> 时长（秒）的列表
     */
    fun setData(items: List<Pair<String, Long>>) {
        data = items
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (data.isEmpty()) return

        val w = width.toFloat()
        val h = height.toFloat()

        // 绘图区域
        val chartLeft = marginLeft
        val chartRight = w - marginRight
        val chartTop = marginTop
        val chartBottom = h - marginBottom
        val chartWidth = chartRight - chartLeft
        val chartHeight = chartBottom - chartTop

        // ---------- 标题 ----------
        canvas.drawText("每日学习时长趋势", w / 2f, 34f, titlePaint)

        // ---------- 计算 Y 轴范围 ----------
        val maxSeconds = data.maxOf { it.second }.coerceAtLeast(60)  // 至少 1 分钟
        // 向上取整到合适的刻度（比如 10 分钟、30 分钟、1 小时等）
        val yMax = niceMax(maxSeconds)

        // ---------- 绘制横网格线 + Y 轴刻度 ----------
        val gridCount = 4
        for (i in 0..gridCount) {
            val y = chartBottom - (chartHeight * i / gridCount)
            val value = yMax * i / gridCount

            // 网格线
            canvas.drawLine(chartLeft, y, chartRight, y, gridPaint)
            // Y 轴标签（分钟数）
            val yLabel = "${value / 60}分"
            canvas.drawText(yLabel, chartLeft - 8f, y + 8f, yLabelPaint)
        }

        // ---------- 绘制 X 轴和 Y 轴基线 ----------
        canvas.drawLine(chartLeft, chartBottom, chartRight, chartBottom, axisPaint)
        canvas.drawLine(chartLeft, chartTop, chartLeft, chartBottom, axisPaint)

        // ---------- 计算数据点坐标 ----------
        val points = mutableListOf<Pair<Float, Float>>()
        for (i in data.indices) {
            val x = if (data.size == 1) {
                chartLeft + chartWidth / 2f
            } else {
                chartLeft + chartWidth * i / (data.size - 1)
            }
            val y = chartBottom - (data[i].second.toFloat() / yMax * chartHeight)
            points.add(x to y)
        }

        // ---------- 绘制渐变填充区域（折线下方） ----------
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
                Color.parseColor("#406B9FC7"),  // 蓝色半透明
                Color.parseColor("#086B9FC7"),  // 几乎透明
                Shader.TileMode.CLAMP
            )
            fillPaint.shader = gradient
            canvas.drawPath(fillPath, fillPaint)
        }

        // ---------- 绘制折线 ----------
        if (points.size >= 2) {
            val path = Path()
            path.moveTo(points[0].first, points[0].second)
            for (i in 1 until points.size) {
                path.lineTo(points[i].first, points[i].second)
            }
            canvas.drawPath(path, linePaint)
        }

        // ---------- 绘制数据点（实心圆 + 白边） ----------
        val dotRadius = 8f
        for ((x, y) in points) {
            canvas.drawCircle(x, y, dotRadius + 3f, dotStrokePaint)  // 白边
            canvas.drawCircle(x, y, dotRadius, dotPaint)              // 蓝心
        }

        // ---------- 绘制 X 轴标签 ----------
        // 如果标签太多，间隔显示
        val step = if (data.size <= 12) 1 else (data.size / 8).coerceAtLeast(1)
        for (i in data.indices step step) {
            val x = if (data.size == 1) {
                chartLeft + chartWidth / 2f
            } else {
                chartLeft + chartWidth * i / (data.size - 1)
            }
            val label = data[i].first
            // 避开太长的标签
            val shortLabel = if (label.length > 5) label.take(5) + ".." else label
            canvas.drawText(shortLabel, x, chartBottom + 36f, labelPaint)
        }

        // 如果没有数据点（全是0），显示空状态
        if (maxSeconds == 0L) {
            labelPaint.textSize = 28f
            labelPaint.color = Color.parseColor("#FFAAAAAA")
            canvas.drawText("暂无数据", chartLeft + chartWidth / 2f, chartTop + chartHeight / 2f, labelPaint)
        }
    }

    /**
     * 将最大值向上取整到美观的刻度
     * 例如：125秒 → 180秒（3分钟）， 3400秒 → 3600秒（1小时）
     */
    private fun niceMax(maxSeconds: Long): Long {
        val maxMinutes = ((maxSeconds + 59) / 60).toInt()  // 向上取整到分钟
        return when {
            maxMinutes <= 10 -> 600L          // 最多 10 分钟
            maxMinutes <= 30 -> 1800L         // 最多 30 分钟
            maxMinutes <= 60 -> 3600L         // 最多 1 小时
            maxMinutes <= 120 -> 7200L        // 最多 2 小时
            maxMinutes <= 300 -> 18000L       // 最多 5 小时
            else -> ((maxMinutes + 59) / 60 * 3600).toLong()  // 按小时取整
        }
    }
}
