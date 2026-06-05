package com.example.studytimer

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

/**
 * 自定义扇形统计图（甜甜圈风格）
 * 用于直观显示各科目学习时长的占比
 */
class PieChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ==================== 渐变色调色板 ====================
    // 蓝→紫→粉→橙→绿：相邻色相渐变，视觉连贯
    private val colors = listOf(
        Color.parseColor("#FF6B9FC7"),  // 雾蓝
        Color.parseColor("#FF8B7EC8"),  // 靛紫
        Color.parseColor("#FFB07CC8"),  // 淡紫
        Color.parseColor("#FFD489B5"),  // 粉紫
        Color.parseColor("#FFE8A87C"),  // 暖橙
        Color.parseColor("#FF7DB89A"),  // 柔绿
        Color.parseColor("#FF6BB5B5"),  // 青绿
        Color.parseColor("#FFA8CBE3")   // 浅蓝
    )

    // ==================== 数据 ====================
    // 每个条目：科目名 -> 时长（秒）
    private var data: List<Pair<String, Long>> = emptyList()

    // ==================== 画笔 ====================
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val legendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 28f  // 图例文字大小（像素）
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
        color = Color.parseColor("#FF333333")
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    // ==================== 尺寸常量 ====================
    private val innerRadiusRatio = 0.55f  // 内圆半径占外圆的比例（甜甜圈效果）

    /**
     * 设置图表数据
     * @param items 科目名 -> 时长（秒）的列表
     */
    fun setData(items: List<Pair<String, Long>>) {
        data = items
        invalidate()  // 触发重新绘制
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (data.isEmpty()) return

        val w = width.toFloat()
        val h = height.toFloat()

        // ---------- 计算总时长 ----------
        val total = data.sumOf { it.second }.toFloat()
        if (total == 0f) return

        // ---------- 扇形图绘制区域（左边 60% 宽度） ----------
        val chartWidth = w * 0.55f
        val cx = chartWidth / 2f          // 圆心 X
        val cy = h / 2f                   // 圆心 Y
        val outerRadius = minOf(chartWidth, h) / 2f - 24f  // 外圆半径
        val innerRadius = outerRadius * innerRadiusRatio    // 内圆半径（甜甜圈空心）

        // ---------- 绘制扇形 ----------
        val oval = RectF(cx - outerRadius, cy - outerRadius, cx + outerRadius, cy + outerRadius)
        var startAngle = -90f  // 从 12 点钟方向开始

        for (i in data.indices) {
            val (_, value) = data[i]
            val sweepAngle = (value / total) * 360f  // 该扇形的角度

            // 设置颜色（循环使用调色板）
            arcPaint.color = colors[i % colors.size]

            // 画弧
            canvas.drawArc(oval, startAngle, sweepAngle, true, arcPaint)

            // 在扇形中心绘制百分比文字
            val midAngle = startAngle + sweepAngle / 2f
            val radians = Math.toRadians(midAngle.toDouble())
            val textRadius = (outerRadius + innerRadius) / 2f  // 文字放在扇环中间
            val tx = cx + textRadius * Math.cos(radians).toFloat()
            val ty = cy + textRadius * Math.sin(radians).toFloat()
            // 调整文字基线位置
            percentPaint.getTextBounds("100%", 0, 4, Rect())
            val percentText = "${(value / total * 100).toInt()}%"
            canvas.drawText(percentText, tx, ty + 8f, percentPaint)

            startAngle += sweepAngle
        }

        // ---------- 绘制中心白色圆（甜甜圈效果） ----------
        canvas.drawCircle(cx, cy, innerRadius, centerPaint)

        // ---------- 中心文字：总时长 ----------
        val totalHours = total / 3600f
        val centerText = if (totalHours >= 1) {
            "%.1f 小时".format(totalHours)
        } else {
            "${(total / 60).toInt()} 分钟"
        }
        titlePaint.textSize = 32f
        val textY = cy + 10f
        canvas.drawText(centerText, cx, textY, titlePaint)

        // ---------- 图例区域（右边 40% 宽度） ----------
        val legendX = chartWidth + 16f  // 图例起始 X
        var legendY = 40f               // 图例起始 Y
        val legendSpacing = 44f         // 图例行间距

        // 图例标题
        legendPaint.textSize = 32f
        legendPaint.color = Color.parseColor("#FF333333")
        legendPaint.isFakeBoldText = true
        canvas.drawText("科目占比", legendX, legendY, legendPaint)
        legendY += legendSpacing + 12f

        legendPaint.textSize = 25f  // 稍小字号，容纳更多文字
        legendPaint.color = Color.parseColor("#FF555555")
        legendPaint.isFakeBoldText = false

        for (i in data.indices) {
            val (name, value) = data[i]
            val percent = (value / total * 100).toInt()

            // 画颜色圆点
            legendDotPaint.color = colors[i % colors.size]
            canvas.drawCircle(legendX + 8f, legendY - 6f, 7f, legendDotPaint)

            // 画科目名（最多 10 字符，超出加省略号）
            val displayName = if (name.length > 10) name.take(10) + "…" else name
            canvas.drawText("$displayName  $percent%", legendX + 22f, legendY, legendPaint)

            legendY += legendSpacing
        }
    }
}
