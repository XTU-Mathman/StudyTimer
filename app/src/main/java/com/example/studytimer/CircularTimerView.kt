package com.example.studytimer

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator

/**
 * 圆形计时进度环
 * - 外圈渐变弧（雾蓝→淡紫），随时间填充/消耗
 * - 弧线外发光晕（Aurora 效果）+ 呼吸脉动
 * - 中央超大时间数字
 * - 顶部科目标签、底部状态标签
 */
class CircularTimerView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(ctx, attrs, defStyleAttr) {

    // ============ 可设置属性 ============

    /** 当前进度 0..1（正计时从 0→1，倒计时从 1→0） */
    var progress: Float = 0f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }

    /** 显示的时间文本（如 "01:30:00"） */
    var timeText: String = "00:00:00"
        set(v) { field = v; invalidate() }

    /** 科目信息（显示在环上方） */
    var subjectText: String = ""
        set(v) { field = v; invalidate() }

    /** 状态文本（显示在环下方） */
    var statusText: String = ""
        set(v) { field = v; invalidate() }

    /** 是否倒计时模式（影响弧色） */
    var isCountdown: Boolean = false
        set(v) { field = v; invalidate() }

    /** 弧颜色数组 */
    var arcColors: IntArray = intArrayOf(
        0xFF6BA4D1.toInt(),  // 雾蓝
        0xFF8B82B8.toInt(),  // 靛紫
        0xFFA088B8.toInt()   // 淡紫
    )
        set(v) { field = v; invalidate() }

    // ============ 动画 ============

    private var animProgress = 0f
    private var animator: ValueAnimator? = null
    private var glowAlpha = 0.4f
    private var glowAnimator: ValueAnimator? = null

    /** 平滑过渡到目标进度 */
    fun animateTo(target: Float, duration: Long = 600L) {
        animator?.cancel()
        val start = animProgress
        animator = ValueAnimator.ofFloat(start, target).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                animProgress = it.animatedFraction
                progress = start + (target - start) * it.animatedFraction
            }
            start()
        }
    }

    /** 暂停环动画（微弹性回弹） */
    fun pulse() {
        animateTo(progress, 200L)
    }

    // ============ 画笔 ============

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val bgRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x18000000.toInt()
        strokeCap = Paint.Cap.ROUND
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        maskFilter = BlurMaskFilter(24f, BlurMaskFilter.Blur.NORMAL)
    }
    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF2D2D2D.toInt()
        textAlign = Paint.Align.CENTER
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF78716C.toInt()
        textAlign = Paint.Align.CENTER
    }
    private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFA8A29E.toInt()
        textAlign = Paint.Align.CENTER
    }

    private val arcRect = RectF()

    companion object {
        private const val RING_WIDTH_DP = 8f
        private const val GLOW_WIDTH_DP = 16f
        private const val TIME_TEXT_SIZE_SP = 48f
        private const val SUB_TEXT_SIZE_SP = 14f
        private const val STATUS_TEXT_SIZE_SP = 13f
        private const val ARC_GAP_DEG = 90f
        private const val ARC_START_DEG = 135f
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // 光晕呼吸动画
        glowAnimator = ValueAnimator.ofFloat(0.3f, 0.6f).apply {
            duration = 3000L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
            addUpdateListener {
                glowAlpha = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        glowAnimator?.cancel()
        glowAnimator = null
    }

    // ============ 绘制 ============

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val density = resources.displayMetrics.density
        val scaledDensity = resources.displayMetrics.scaledDensity

        val ringWidth = RING_WIDTH_DP * density
        val glowWidth = GLOW_WIDTH_DP * density
        val timeSize = TIME_TEXT_SIZE_SP * scaledDensity
        val subSize = SUB_TEXT_SIZE_SP * scaledDensity
        val statusSize = STATUS_TEXT_SIZE_SP * scaledDensity

        val diameter = minOf(width, height) * 0.72f
        val radius = diameter / 2f

        arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius)

        // ---- 1. 底色环 ----
        bgRingPaint.strokeWidth = ringWidth
        canvas.drawArc(arcRect, ARC_START_DEG, 360f - ARC_GAP_DEG, false, bgRingPaint)

        // ---- 2. 光晕层（Aurora 效果） ----
        if (progress > 0.01f) {
            val sweepAngle = progress * (360f - ARC_GAP_DEG)
            glowPaint.strokeWidth = glowWidth
            glowPaint.shader = SweepGradient(cx, cy, arcColors, null)
            glowPaint.alpha = (glowAlpha * 255).toInt()
            canvas.drawArc(arcRect, ARC_START_DEG, sweepAngle, false, glowPaint)
            glowPaint.shader = null
        }

        // ---- 3. 渐变弧（主环） ----
        ringPaint.strokeWidth = ringWidth
        ringPaint.shader = SweepGradient(cx, cy, arcColors, null)
        val sweepAngle = progress * (360f - ARC_GAP_DEG)
        canvas.drawArc(arcRect, ARC_START_DEG, sweepAngle, false, ringPaint)
        ringPaint.shader = null

        // ---- 4. 时间数字 ----
        timePaint.textSize = timeSize
        timePaint.color = if (isCountdown) 0xFFD4726A.toInt() else 0xFF1C1917.toInt()
        val timeY = cy + timePaint.textSize / 3f
        canvas.drawText(timeText, cx, timeY, timePaint)

        // ---- 5. 科目文本（环上方） ----
        subPaint.textSize = subSize
        val subY = cy - radius - ringWidth - subSize * 1.2f
        canvas.drawText(subjectText, cx, subY, subPaint)

        // ---- 6. 状态文本（环下方） ----
        statusPaint.textSize = statusSize
        val statusY = cy + radius + ringWidth + statusSize * 1.8f
        canvas.drawText(statusText, cx, statusY, statusPaint)
    }
}
