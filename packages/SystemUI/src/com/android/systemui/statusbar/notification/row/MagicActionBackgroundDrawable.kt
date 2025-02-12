/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.notification.row

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import com.android.systemui.res.R
import com.android.wm.shell.shared.animation.Interpolators

class MagicActionBackgroundDrawable(
    context: Context,
) : Drawable() {

    private val cornerRadius = context.resources.getDimension(R.dimen.magic_action_button_corner_radius)
    private val outlineStrokeWidth = context.resources.getDimension(R.dimen.magic_action_button_outline_stroke_width)
    private val insetVertical = 8 * context.resources.displayMetrics.density

    private val buttonShape = Path()
    // Color and style
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        val bgColor =
            context.getColor(
                com.android.internal.R.color.materialColorPrimaryContainer
            )
        color = bgColor
        style = Paint.Style.FILL
    }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        val outlineColor =
            context.getColor(
                com.android.internal.R.color.materialColorOutlineVariant
            )
        color = outlineColor
        style = Paint.Style.STROKE
        strokeWidth = outlineStrokeWidth
    }
    private val outlineStartColor =
        context.getColor(
            com.android.internal.R.color.materialColorTertiaryContainer
        )
    private val outlineMiddleColor =
        context.getColor(
            com.android.internal.R.color.materialColorPrimaryFixedDim
        )
    private val outlineEndColor =
        context.getColor(
            com.android.internal.R.color.materialColorPrimary
        )
    // Animation
    private var gradientAnimator: ValueAnimator
    private var rotationAngle = 20f // Start rotation at 20 degrees

    init {
        gradientAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 5000 // 5 seconds
            interpolator = Interpolators.LINEAR
            repeatCount = 1
            addUpdateListener { animator ->
                val animatedValue = animator.animatedValue as Float
                rotationAngle = 20f + animatedValue * 360f // Rotate in a spiral
                invalidateSelf()
            }
            // TODO: Reset the outline color when animation ends.
            start()
        }
    }

    override fun draw(canvas: Canvas) {
        val boundsF = RectF(bounds)
        boundsF.inset(0f, insetVertical)
        buttonShape.reset()
        buttonShape.addRoundRect(boundsF, cornerRadius, cornerRadius, Path.Direction.CW)

        canvas.save()
        // Draw background
        canvas.clipPath(buttonShape)
        canvas.drawPath(buttonShape, bgPaint)
        // Apply gradient to outline
        canvas.drawPath(buttonShape, outlinePaint)
        updateGradient(boundsF)
        canvas.restore()
    }

    private fun updateGradient(boundsF: RectF) {
        val gradient = LinearGradient(
            boundsF.left, boundsF.top,
            boundsF.right, boundsF.bottom,
            intArrayOf(outlineStartColor, outlineMiddleColor, outlineEndColor),
            null,
            Shader.TileMode.CLAMP
        )
        // Create a rotation matrix for the spiral effect
        val matrix = Matrix()
        matrix.setRotate(rotationAngle, boundsF.centerX(), boundsF.centerY())
        gradient.setLocalMatrix(matrix)

        outlinePaint.shader = gradient
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        invalidateSelf() // Redraw when size changes
    }

    override fun setAlpha(alpha: Int) {
        bgPaint.alpha = alpha
        outlinePaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        bgPaint.colorFilter = colorFilter
        outlinePaint.colorFilter = colorFilter
        invalidateSelf()
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
