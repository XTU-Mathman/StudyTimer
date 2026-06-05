package com.example.studytimer

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * 动态渐变背景 — 三色极淡缓慢漂移，模拟环境光
 * 挂载在 Fragment 根布局底层，alpha ~8% 不影响前景可读
 */
class DynamicBackgroundView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(ctx, attrs, defStyleAttr) {

    // 三色：暖琥珀 → 杏桃 → 淡金 — 更明显的暖色渐变
    private val color1 = 0x30D4A07C.toInt()  // 琥珀
    private val color2 = 0x25E8C8A0.toInt()  // 杏桃
    private val color3 = 0x20F0D8B8.toInt()  // 淡金

    private var offsetX = 0f
    private var offsetY = 0f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 8000L
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.RESTART
        interpolator = LinearInterpolator()
        addUpdateListener {
            offsetX = it.animatedFraction * width.toFloat()
            offsetY = it.animatedFraction * height.toFloat() * 0.6f
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        val shader = LinearGradient(
            -offsetX, -offsetY,
            width + offsetX * 0.5f, height - offsetY * 0.3f,
            intArrayOf(color1, color2, color3),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.MIRROR
        )
        paint.shader = shader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    companion object {
        private val paint = android.graphics.Paint().apply {
            isAntiAlias = true
        }
    }
}
