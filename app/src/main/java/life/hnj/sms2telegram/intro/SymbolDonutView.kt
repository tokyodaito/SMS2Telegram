package life.hnj.sms2telegram.intro

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class SymbolDonutView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xD0FFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        isSubpixelText = true
    }

    private val symbols: CharArray =
        ("!@#$%^&*()_+{}[]<>?/\\\\|~.,;:=-0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ").toCharArray()

    private var phase: Float = 0f
    private var animator: ValueAnimator? = null

    fun start() {
        if (animator != null) return
        val a = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 9000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                phase = it.animatedValue as Float
                postInvalidateOnAnimation()
            }
        }
        animator = a
        a.start()
    }

    fun stop() {
        animator?.cancel()
        animator = null
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w / 2f
        val cy = h / 2f
        val r = min(w, h) * 0.35f
        val n = min(symbols.size, 64)
        val baseAngle = (phase * 2f * PI).toFloat()

        paint.textSize = min(w, h) * 0.055f

        for (i in 0 until n) {
            val a = baseAngle + (i.toFloat() / n.toFloat()) * (2f * PI).toFloat()
            val x = cx + cos(a.toDouble()).toFloat() * r
            val y = cy + sin(a.toDouble()).toFloat() * r

            // Small breathing alpha gradient around the ring (no allocations, just math).
            val local = ((i.toFloat() / n.toFloat()) + phase) % 1f
            val alpha = (140 + (115f * (1f - kotlin.math.abs(local - 0.5f) * 2f))).toInt()
            paint.alpha = alpha.coerceIn(40, 255)

            // Avoid per-frame allocations (no String creation).
            canvas.drawText(symbols, i, 1, x, y, paint)
        }
    }
}
