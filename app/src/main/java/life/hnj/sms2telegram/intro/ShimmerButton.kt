package life.hnj.sms2telegram.intro

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.animation.LinearInterpolator
import com.google.android.material.button.MaterialButton
import kotlin.math.min

class ShimmerButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : MaterialButton(context, attrs) {

    private val shimmerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        alpha = 160
    }
    private var shader: LinearGradient? = null
    private val shaderMatrix = Matrix()
    private val clipPath = Path()

    private var shimmerPhase: Float = 0f
    private var shimmerAnimator: ValueAnimator? = null
    private var pulseAnimator: ValueAnimator? = null

    fun startEffects() {
        if (shimmerAnimator != null) return

        shimmerAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1600L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                shimmerPhase = it.animatedValue as Float
                postInvalidateOnAnimation()
            }
            start()
        }

        pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1500L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
            addUpdateListener { a ->
                val t = a.animatedValue as Float
                val s = 1f + (t * 0.04f)
                scaleX = s
                scaleY = s
            }
            start()
        }
    }

    fun stopEffects() {
        shimmerAnimator?.cancel()
        shimmerAnimator = null
        pulseAnimator?.cancel()
        pulseAnimator = null
        scaleX = 1f
        scaleY = 1f
        invalidate()
    }

    override fun onDetachedFromWindow() {
        stopEffects()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) {
            shader = null
            return
        }
        val c1 = 0x00FFFFFF
        val c2 = 0xA0FFFFFF.toInt()
        val c3 = 0x00FFFFFF
        shader = LinearGradient(
            0f,
            0f,
            w.toFloat(),
            0f,
            intArrayOf(c1, c2, c3),
            floatArrayOf(0.35f, 0.5f, 0.65f),
            Shader.TileMode.CLAMP
        )
        shimmerPaint.shader = shader
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val sh = shader ?: return

        // Move the shimmer band across the button.
        val dx = (shimmerPhase * (w * 2f)) - w
        shaderMatrix.reset()
        shaderMatrix.setTranslate(dx, 0f)
        sh.setLocalMatrix(shaderMatrix)

        // Clip to rounded rect roughly matching MaterialButton shape.
        clipPath.reset()
        val r = min(w, h) * 0.22f
        clipPath.addRoundRect(0f, 0f, w, h, r, r, Path.Direction.CW)
        val save = canvas.save()
        canvas.clipPath(clipPath)
        canvas.drawRect(0f, 0f, w, h, shimmerPaint)
        canvas.restoreToCount(save)
    }
}

