package com.example.ada.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class JarvisFaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
        color = Color.CYAN
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(10f)
        color = Color.argb(40, 0, 255, 255)
    }

    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(220, 0, 255, 255)
    }

    private val mouthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(4f)
        color = Color.argb(230, 0, 255, 255)
        strokeCap = Paint.Cap.ROUND
    }

    private val scanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        color = Color.argb(120, 0, 255, 255)
    }

    private val mouthPath = Path()

    private var uiState: String = "OFFLINE"
    private var phase: Float = 0f

    private var animator: ValueAnimator? = null

    fun setState(stateName: String) {
        uiState = stateName
        updateAnimation()
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAnimation()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val size = min(w, h)
        val cx = w / 2f
        val cy = h / 2f

        val baseRadius = size * 0.35f
        val pulse = when (uiState.uppercase()) {
            "SPEAKING" -> 1f + 0.06f * sin(phase * 2f)
            "THINKING" -> 1f + 0.04f * sin(phase * 1.3f)
            "LISTENING" -> 1f + 0.03f * sin(phase)
            else -> 1f
        }

        val alphaMul = when (uiState.uppercase()) {
            "OFFLINE" -> 0.25f
            "ERROR" -> 0.45f
            else -> 1f
        }

        val ringAlpha = (255 * alphaMul).toInt().coerceIn(0, 255)
        ringPaint.alpha = ringAlpha
        mouthPaint.alpha = ringAlpha
        eyePaint.alpha = (220 * alphaMul).toInt().coerceIn(0, 255)
        glowPaint.alpha = (40 * alphaMul).toInt().coerceIn(0, 255)
        scanPaint.alpha = (120 * alphaMul).toInt().coerceIn(0, 255)

        val r = baseRadius * pulse
        canvas.drawCircle(cx, cy, r + dp(12f), glowPaint)
        canvas.drawCircle(cx, cy, r, ringPaint)

        val eyeOffsetX = r * 0.35f
        val eyeOffsetY = r * 0.18f
        val eyeR = r * 0.06f
        canvas.drawCircle(cx - eyeOffsetX, cy - eyeOffsetY, eyeR, eyePaint)
        canvas.drawCircle(cx + eyeOffsetX, cy - eyeOffsetY, eyeR, eyePaint)

        drawMouth(canvas, cx, cy, r)

        if (uiState.uppercase() == "THINKING") {
            val sweep = ((phase / (Math.PI.toFloat() * 2f)) % 1f)
            val ang = sweep * (Math.PI.toFloat() * 2f)
            val x1 = cx + (r * 0.15f) * cos(ang)
            val y1 = cy + (r * 0.15f) * sin(ang)
            val x2 = cx + (r * 0.95f) * cos(ang)
            val y2 = cy + (r * 0.95f) * sin(ang)
            canvas.drawLine(x1, y1, x2, y2, scanPaint)
        }
    }

    private fun drawMouth(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val mouthY = cy + r * 0.30f
        val mouthW = r * 0.42f
        val t = phase

        val openness = when (uiState.uppercase()) {
            "SPEAKING" -> 0.12f + 0.10f * (0.5f + 0.5f * sin(t * 3.2f))
            "LISTENING" -> 0.06f + 0.04f * (0.5f + 0.5f * sin(t * 1.2f))
            "THINKING" -> 0.03f
            else -> 0.01f
        }

        val curve = r * openness
        val leftX = cx - mouthW
        val rightX = cx + mouthW

        mouthPath.reset()
        mouthPath.moveTo(leftX, mouthY)
        mouthPath.quadTo(cx, mouthY + curve, rightX, mouthY)
        canvas.drawPath(mouthPath, mouthPaint)
    }

    private fun updateAnimation() {
        val shouldRun = uiState.uppercase() != "OFFLINE"
        if (!shouldRun) {
            animator?.cancel()
            animator = null
            phase = 0f
            invalidate()
            return
        }

        if (animator != null) return

        animator = ValueAnimator.ofFloat(0f, (Math.PI.toFloat() * 2f)).apply {
            duration = 1200L
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                phase = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun dp(v: Float): Float {
        return v * resources.displayMetrics.density
    }
}
