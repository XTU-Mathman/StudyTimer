package com.example.studytimer

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Shader
import android.os.Build
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * 动态渐变背景 — 三色极淡缓慢漂移，模拟环境光
 * Android 12+ 支持真毛玻璃模糊效果
 * 自动适配暗色模式
 */
class DynamicBackgroundView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(ctx, attrs, defStyleAttr) {

    private val isDarkMode = (ctx.resources.configuration.uiMode and
        Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    // 日间：暖色系渐变 / 暗间：冷蓝紫调
    private val color1 = if (isDarkMode) 0x204A6FA5.toInt() else 0x35D4A07C.toInt()
    private val color2 = if (isDarkMode) 0x186A5B8A.toInt() else 0x28E8C8A0.toInt()
    private val color3 = if (isDarkMode) 0x158070A0.toInt() else 0x22F0D8B8.toInt()

    private var offsetX = 0f
    private var offsetY = 0f

    private var blurNode: RenderNode? = null
    private val blurRadius = 25f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 12000L
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.RESTART
        interpolator = LinearInterpolator()
        addUpdateListener {
            offsetX = it.animatedFraction * width.toFloat()
            offsetY = it.animatedFraction * height.toFloat() * 0.5f
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            blurNode = RenderNode("blur").apply {
                setPosition(0, 0, width, height)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator.cancel()
        blurNode = null
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurNode != null) {
            val node = blurNode!!
            node.setPosition(0, 0, width, height)
            val nodeCanvas = node.beginRecording()
            nodeCanvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            node.endRecording()

            val effect = RenderEffect.createBlurEffect(
                blurRadius, blurRadius, android.graphics.Shader.TileMode.CLAMP
            )
            node.setRenderEffect(effect)
            canvas.drawRenderNode(node)
        } else {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }
    }

    companion object {
        // paint 移到实例变量，避免多实例共享 shader 导致闪烁
    }

    private val paint = android.graphics.Paint().apply {
        isAntiAlias = true
    }
}
