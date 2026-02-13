package life.hnj.sms2telegram.intro

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class ParticleFieldView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private data class Particle(
        var x: Float = 0f,
        var y: Float = 0f,
        var vx: Float = 0f,
        var vy: Float = 0f,
        var age: Float = 0f,
        var life: Float = 1f,
        var size: Float = 1f,
        var alpha: Int = 255,
        var color: Int = 0xFFFFFFFF.toInt(),
        var active: Boolean = false,
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Fixed pool to avoid GC churn.
    private val poolSize = 700
    private val particles = Array(poolSize) { Particle() }
    private var nextIdx = 0

    private var emitterL = 0f
    private var emitterT = 0f
    private var emitterR = 0f
    private var emitterB = 0f

    private var running = false
    private var lastFrameNs: Long = 0L
    private var spawnAccumulator = 0f

    private val frameCallback = Choreographer.FrameCallback { nowNs ->
        if (!running) return@FrameCallback
        if (lastFrameNs == 0L) lastFrameNs = nowNs
        val dt = ((nowNs - lastFrameNs).coerceAtMost(50_000_000L)).toFloat() / 1_000_000_000f
        lastFrameNs = nowNs

        step(dt)
        postInvalidateOnAnimation()
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    fun setEmitterRect(l: Float, t: Float, r: Float, b: Float) {
        emitterL = l
        emitterT = t
        emitterR = r
        emitterB = b
    }

    fun start() {
        if (running) return
        running = true
        lastFrameNs = 0L
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    fun stop() {
        if (!running) return
        running = false
        lastFrameNs = 0L
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        // No need to clear particles; they won't draw when not invalidated.
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (p in particles) {
            if (!p.active) continue
            paint.color = p.color
            paint.alpha = p.alpha
            canvas.drawCircle(p.x, p.y, p.size, paint)
        }
    }

    private fun step(dt: Float) {
        if (dt <= 0f) return

        // Spawn rate: high, but capped by pool size and dt clamp above.
        val spawnPerSec = 220f
        spawnAccumulator += spawnPerSec * dt
        val toSpawn = spawnAccumulator.toInt().coerceAtMost(35)
        spawnAccumulator -= toSpawn.toFloat()
        repeat(toSpawn) { spawnOne() }

        val gravity = min(height.toFloat(), width.toFloat()) * 0.55f
        for (p in particles) {
            if (!p.active) continue
            p.age += dt
            if (p.age >= p.life) {
                p.active = false
                continue
            }
            p.vy += gravity * dt
            p.x += p.vx * dt
            p.y += p.vy * dt

            val t = (p.age / p.life).coerceIn(0f, 1f)
            p.alpha = (255f * (1f - t) * (1f - t)).toInt().coerceIn(0, 255)
        }
    }

    private fun spawnOne() {
        if (width == 0 || height == 0) return
        val cx = (emitterL + emitterR) * 0.5f
        val cy = (emitterT + emitterB) * 0.5f
        if (cx == 0f && cy == 0f) return

        val p = particles[nextIdx]
        nextIdx = (nextIdx + 1) % poolSize

        val angle = Random.nextFloat() * (2f * PI.toFloat())
        val speed = (min(width, height) * 0.20f) * (0.35f + Random.nextFloat())
        val jitter = min(emitterR - emitterL, emitterB - emitterT) * 0.12f

        p.x = cx + (Random.nextFloat() - 0.5f) * jitter
        p.y = cy + (Random.nextFloat() - 0.5f) * jitter
        p.vx = cos(angle) * speed
        p.vy = sin(angle) * speed - (speed * 0.35f)
        p.age = 0f
        p.life = 0.8f + Random.nextFloat() * 0.9f
        p.size = 2.0f + Random.nextFloat() * 4.5f
        p.color = pickColor()
        p.alpha = 255
        p.active = true
    }

    private fun pickColor(): Int {
        // Bright palette; keep it simple and cheap.
        return when (Random.nextInt(6)) {
            0 -> 0xFFFFF1A8.toInt()
            1 -> 0xFFA8FFE8.toInt()
            2 -> 0xFFA8C7FF.toInt()
            3 -> 0xFFFFA8D8.toInt()
            4 -> 0xFFFFDCA8.toInt()
            else -> 0xFFFFFFFF.toInt()
        }
    }
}
