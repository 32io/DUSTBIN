package com.example.smartbin

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

// NeonView.kt
class NeonView
@JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var glowColor: Int = Color.CYAN
    private var glowRadius: Float = 20f
    private var pulseAnimation: Boolean = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private var pulseAnimator: ValueAnimator? = null

    init {
        context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.NeonView,
            0, 0
        ).apply {
            try {
                glowColor = getColor(R.styleable.NeonView_glowColor, Color.CYAN)
                glowRadius = getDimension(R.styleable.NeonView_glowRadius, 20f)
                pulseAnimation = getBoolean(R.styleable.NeonView_pulseAnimation, false)
            } finally {
                recycle()
            }
        }

        if (pulseAnimation) {
            setupPulseAnimation()
        }
    }

    private fun setupPulseAnimation() {
        pulseAnimator = ValueAnimator.ofFloat(0.5f, 1f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()

            addUpdateListener { animator ->
                paint.alpha = (255 * animator.animatedValue as Float).toInt()
                invalidate()
            }
        }
        pulseAnimator?.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        paint.apply {
            color = glowColor
            maskFilter = BlurMaskFilter(glowRadius, BlurMaskFilter.Blur.OUTER)
        }

        // Draw outer glow
        canvas.drawRect(
            glowRadius,
            glowRadius,
            width - glowRadius,
            height - glowRadius,
            paint
        )

        // Draw inner stroke
        paint.apply {
            maskFilter = null
            alpha = 255
        }
        canvas.drawRect(
            glowRadius,
            glowRadius,
            width - glowRadius,
            height - glowRadius,
            paint
        )
    }

    override fun onDetachedFromWindow() {
        pulseAnimator?.cancel()
        super.onDetachedFromWindow()
    }
}