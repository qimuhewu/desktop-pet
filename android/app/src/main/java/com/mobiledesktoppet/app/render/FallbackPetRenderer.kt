package com.mobiledesktoppet.app.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.View
import kotlin.math.abs
import kotlin.math.sin

/**
 * 兜底渲染实现：Canvas 程序绘制小动物（硬性约束 2：缺 SDK/缺模型时 App 不得崩溃）。
 *
 * - 自带一个透明的程序绘制 View（耳朵/眼睛/腮红/尾巴全部代码绘制）；
 * - 动画为时间驱动：待机呼吸、眨眼、走路摆动、开心/害羞/进食姿态；
 * - 帧率可通过 [setFrameRate] 控制（省电模式，P1-5）。
 */
class FallbackPetRenderer(context: Context) : PetRenderer {

    /** 姿态状态（对应动作名映射）。 */
    enum class Pose { IDLE, WALK, HAPPY, SHY, EAT }

    private inner class PetDrawingView(context: Context) : View(context) {

        private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BODY_COLOR }
        private val innerEarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INNER_EAR_COLOR }
        private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LINE_COLOR
            strokeWidth = 4f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        private val cheekPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CHEEK_COLOR }
        private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = HAPPY_COLOR }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            val now = System.currentTimeMillis()
            val t = now / 1000.0

            // 时间驱动的动画参数
            val breath = sin(t * 2.0) * 0.02                       // 呼吸缩放
            val blink = if ((now % BLINK_PERIOD_MS) < BLINK_MS) 1.0 else 0.0 // 周期眨眼
            val walkBob = if (pose == Pose.WALK) abs(sin(t * 6.0)).toFloat() * h * 0.04f else 0f
            val bob = sin(t * 1.6).toFloat() * h * 0.015f + walkBob
            val scale = (1.0 + breath).toFloat()

            canvas.save()
            canvas.translate(w / 2f, h / 2f + bob)
            canvas.scale(scale, scale)

            val cx = 0f
            val bodyRadius = minOf(w, h) * 0.32f

            // 耳朵（三角）
            val earPath = Path()
            val earHeight = bodyRadius * 1.1f
            earPath.moveTo(cx - bodyRadius * 0.75f, -bodyRadius * 0.55f)
            earPath.lineTo(cx - bodyRadius * 0.45f, -bodyRadius - earHeight * 0.6f)
            earPath.lineTo(cx - bodyRadius * 0.15f, -bodyRadius * 0.75f)
            earPath.close()
            earPath.moveTo(cx + bodyRadius * 0.15f, -bodyRadius * 0.75f)
            earPath.lineTo(cx + bodyRadius * 0.45f, -bodyRadius - earHeight * 0.6f)
            earPath.lineTo(cx + bodyRadius * 0.75f, -bodyRadius * 0.55f)
            earPath.close()
            canvas.drawPath(earPath, bodyPaint)
            // 内耳
            val innerEarPath = Path()
            innerEarPath.moveTo(cx - bodyRadius * 0.62f, -bodyRadius * 0.6f)
            innerEarPath.lineTo(cx - bodyRadius * 0.45f, -bodyRadius - earHeight * 0.28f)
            innerEarPath.lineTo(cx - bodyRadius * 0.28f, -bodyRadius * 0.72f)
            innerEarPath.close()
            innerEarPath.moveTo(cx + bodyRadius * 0.28f, -bodyRadius * 0.72f)
            innerEarPath.lineTo(cx + bodyRadius * 0.45f, -bodyRadius - earHeight * 0.28f)
            innerEarPath.lineTo(cx + bodyRadius * 0.62f, -bodyRadius * 0.6f)
            innerEarPath.close()
            canvas.drawPath(innerEarPath, innerEarPaint)

            // 身体（圆脸）
            canvas.drawCircle(cx, 0f, bodyRadius, bodyPaint)

            // 眼睛（眨眼时压扁）
            val eyeOffsetX = bodyRadius * 0.38f
            val eyeOffsetY = -bodyRadius * 0.1f
            val eyeRadiusY = bodyRadius * (0.16f - 0.13f * blink.toFloat())
            canvas.drawCircle(cx - eyeOffsetX, eyeOffsetY, bodyRadius * 0.14f, facePaint)
            canvas.drawCircle(cx + eyeOffsetX, eyeOffsetY, bodyRadius * 0.14f, facePaint)
            if (eyeRadiusY > 1f) {
                canvas.drawCircle(cx - eyeOffsetX, eyeOffsetY, eyeRadiusY * 0.5f, linePaint)
                canvas.drawCircle(cx + eyeOffsetX, eyeOffsetY, eyeRadiusY * 0.5f, linePaint)
            }

            // 嘴（姿态差异：害羞=波浪小嘴，进食=咀嚼，其他=微笑）
            val mouthY = bodyRadius * 0.35f
            val mouth = Path()
            when (pose) {
                Pose.EAT -> {
                    val chew = (sin(t * 10.0) * 0.5 + 0.5).toFloat()
                    mouth.addRoundRect(
                        RectF(cx - bodyRadius * 0.2f, mouthY, cx + bodyRadius * 0.2f, mouthY + bodyRadius * 0.1f + chew * bodyRadius * 0.12f),
                        8f, 8f, Path.Direction.CW
                    )
                    canvas.drawPath(mouth, linePaint)
                }
                else -> {
                    mouth.moveTo(cx - bodyRadius * 0.18f, mouthY)
                    mouth.quadTo(cx, mouthY + bodyRadius * 0.18f, cx + bodyRadius * 0.18f, mouthY)
                    canvas.drawPath(mouth, linePaint)
                }
            }

            // 腮红（害羞时更明显）
            cheekPaint.alpha = if (pose == Pose.SHY) 220 else 110
            canvas.drawCircle(cx - bodyRadius * 0.6f, bodyRadius * 0.22f, bodyRadius * 0.12f, cheekPaint)
            canvas.drawCircle(cx + bodyRadius * 0.6f, bodyRadius * 0.22f, bodyRadius * 0.12f, cheekPaint)

            // 开心姿态：头顶冒爱心粒子
            if (pose == Pose.HAPPY) {
                for (i in 0 until 3) {
                    val phase = (t * 0.8 + i * 0.33) % 1.0
                    val fy = (-bodyRadius - earHeight * 0.7f - (phase * bodyRadius * 0.8f)).toFloat()
                    val fx = (cx + sin(i * 2.1) * bodyRadius * 0.5f).toFloat()
                    val alpha = (255 * (1.0 - phase)).toInt()
                    bubblePaint.alpha = alpha.coerceIn(0, 255)
                    canvas.drawCircle(fx, fy, bodyRadius * 0.08f, bubblePaint)
                }
            }
            canvas.restore()

            // 下一帧调度（帧率由 frameIntervalMs 控制）
            handler?.postDelayed({ invalidate() }, frameIntervalMs)
        }
    }

    private val view: PetDrawingView = PetDrawingView(context)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var pose: Pose = Pose.IDLE

    @Volatile
    private var frameIntervalMs: Long = 33L // 默认 ~30fps

    @Volatile
    private var released = false

    override fun loadModel(path: String): Boolean {
        // 程序绘制不依赖外部模型；但仍报告「可渲染」，保证兜底路径被采用
        return true
    }

    override fun playMotion(name: String) {
        pose = when (name.lowercase()) {
            "walk" -> Pose.WALK
            "happy" -> Pose.HAPPY
            "shy" -> Pose.SHY
            "eat" -> Pose.EAT
            else -> Pose.IDLE
        }
        view.invalidate()
    }

    override fun setFrameRate(fps: Int) {
        // 合法范围保护：5fps ~ 60fps
        val safe = fps.coerceIn(5, 60)
        frameIntervalMs = (1000L / safe)
    }

    override fun pause() {
        view.pauseAnimation()
    }

    override fun resume() {
        if (!released) view.resumeAnimation()
    }

    override fun release() {
        released = true
        view.pauseAnimation()
    }

    override fun platformView(): View = view

    private fun View.pauseAnimation() {
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun View.resumeAnimation() {
        mainHandler.post { invalidate() }
    }

    companion object {
        // 配色常量（暖色系小猫）
        private const val BODY_COLOR = 0xFFF7D794.toInt()
        private const val INNER_EAR_COLOR = 0xFFF8A5C2.toInt()
        private const val LINE_COLOR = 0xFF5D4037.toInt()
        private const val CHEEK_COLOR = 0xFFF8A5C2.toInt()
        private const val HAPPY_COLOR = 0xFFF78FB3.toInt()
        private const val BLINK_PERIOD_MS = 3800L
        private const val BLINK_MS = 160L
    }
}
