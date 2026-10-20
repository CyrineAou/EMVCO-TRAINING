package com.example.nexgointegration.ui.payment

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Illustration animée, dessinée à la main (aucune image à embarquer).
 *  - TAP        : une carte qui flotte, des ondes sans contact qui montent
 *  - PROCESSING : un anneau qui tourne
 *  - SUCCESS    : un cercle vert et une coche qui se trace
 *  - FAILURE    : un cercle rouge, une croix qui se trace, une petite secousse
 */
class StatusIllustrationView(context: Context) : View(context) {

    enum class Mode { TAP, PROCESSING, SUCCESS, FAILURE }

    private var mode = Mode.TAP
    private var progress = 0f          // 0..1, avance avec l'animation
    private var animator: ValueAnimator? = null

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()

    fun show(newMode: Mode) {
        if (newMode == mode && animator?.isRunning == true) return
        mode = newMode
        animator?.cancel()

        val loops = newMode == Mode.TAP || newMode == Mode.PROCESSING
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (loops) 1_800 else 700
            interpolator = if (loops) LinearInterpolator() else DecelerateInterpolator()
            if (loops) repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) / 2f

        when (mode) {
            Mode.TAP -> drawTap(canvas, cx, cy, radius)
            Mode.PROCESSING -> drawProcessing(canvas, cx, cy, radius)
            Mode.SUCCESS -> drawSuccess(canvas, cx, cy, radius)
            Mode.FAILURE -> drawFailure(canvas, cx, cy, radius)
        }
    }

    // ---------------------------------------------------------
    // Les quatre dessins
    // ---------------------------------------------------------

    private fun drawTap(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        // Trois ondes décalées : chacune grandit puis s'efface.
        val waveOriginY = cy + radius * 0.05f
        repeat(3) { i ->
            val phase = (progress + i / 3f) % 1f
            val r = radius * (0.35f + 0.6f * phase)
            useStroke(PaymentTheme.ACCENT, 6f, alpha = ((1f - phase) * 255).toInt())
            bounds.set(cx - r, waveOriginY - r, cx + r, waveOriginY + r)
            canvas.drawArc(bounds, 225f, 90f, false, stroke)   // arc orienté vers le haut
        }

        // La carte flotte doucement.
        val bob = sin(progress * 2 * PI).toFloat() * dp(4f)
        val cardW = radius * 1.0f
        val cardH = radius * 0.62f
        val top = cy + radius * 0.25f + bob
        bounds.set(cx - cardW / 2, top, cx + cardW / 2, top + cardH)

        fill.color = PaymentTheme.CARD
        canvas.drawRoundRect(bounds, dp(12f), dp(12f), fill)

        fill.color = PaymentTheme.CHIP
        bounds.set(
            cx - cardW / 2 + dp(14f), top + cardH * 0.3f,
            cx - cardW / 2 + dp(40f), top + cardH * 0.3f + dp(20f)
        )
        canvas.drawRoundRect(bounds, dp(4f), dp(4f), fill)
    }

    private fun drawProcessing(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val r = radius * 0.7f

        useStroke(PaymentTheme.TEXT, 6f, alpha = 40)           // piste
        canvas.drawCircle(cx, cy, r, stroke)

        useStroke(PaymentTheme.ACCENT, 6f)                     // arc qui tourne
        bounds.set(cx - r, cy - r, cx + r, cy + r)
        canvas.drawArc(bounds, progress * 360f, 110f, false, stroke)
    }

    private fun drawSuccess(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        fill.color = PaymentTheme.SUCCESS
        canvas.drawCircle(cx, cy, radius * 0.8f * progress, fill)

        // La coche commence à se tracer quand le cercle est bien formé.
        if (progress > 0.4f) {
            val check = Path().apply {
                moveTo(cx - radius * 0.30f, cy)
                lineTo(cx - radius * 0.08f, cy + radius * 0.25f)
                lineTo(cx + radius * 0.32f, cy - radius * 0.22f)
            }
            useStroke(PaymentTheme.TEXT, 9f)
            drawPartially(canvas, check, (progress - 0.4f) / 0.6f)
        }
    }

    private fun drawFailure(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        canvas.save()
        // Petite secousse qui s'éteint : « non, non ».
        canvas.translate(sin(progress * PI * 8).toFloat() * (1f - progress) * dp(10f), 0f)

        fill.color = PaymentTheme.FAILURE
        canvas.drawCircle(cx, cy, radius * 0.8f * progress, fill)

        if (progress > 0.3f) {
            val p = (progress - 0.3f) / 0.7f
            val d = radius * 0.25f
            useStroke(PaymentTheme.TEXT, 9f)
            drawPartially(canvas, Path().apply { moveTo(cx - d, cy - d); lineTo(cx + d, cy + d) }, p)
            drawPartially(canvas, Path().apply { moveTo(cx + d, cy - d); lineTo(cx - d, cy + d) }, p)
        }
        canvas.restore()
    }

    // ---------------------------------------------------------
    // Petits outils
    // ---------------------------------------------------------

    private fun useStroke(color: Int, widthDp: Float, alpha: Int = 255) {
        stroke.color = color
        stroke.alpha = alpha
        stroke.strokeWidth = dp(widthDp)
    }

    /** Dessine seulement la fraction [fraction] (0..1) d'un tracé : effet « on écrit à la main ». */
    private fun drawPartially(canvas: Canvas, path: Path, fraction: Float) {
        val measure = PathMeasure(path, false)
        val piece = Path()
        measure.getSegment(0f, measure.length * fraction.coerceIn(0f, 1f), piece, true)
        canvas.drawPath(piece, stroke)
    }
}