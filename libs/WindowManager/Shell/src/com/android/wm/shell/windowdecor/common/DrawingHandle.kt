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

package com.android.wm.shell.windowdecor.common

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.DimenRes
import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import com.android.wm.shell.R
import com.android.wm.shell.windowdecor.extension.getDimensionPixelSize

/**
 * [View] for the handle at the top of fullscreen apps. Has custom hover and press handling to grow
 * the handle on hover enter and shrink the handle on hover exit and press. Draws itself in #onDraw
 * when animated.
 */
class DrawingHandle(context: Context?, attrs: AttributeSet?) :
    View(context, attrs), ColoredAppHandle {
    private val handleAnimator = ValueAnimator()

    /** Final horizontal padding for hover enter. */
    private val HANDLE_HEIGHT = loadDimensionPixelSize(R.dimen.app_handle_height).toFloat()

    private val HANDLE_WIDTH = loadDimensionPixelSize(R.dimen.app_handle_width).toFloat()

    private val HANDLE_LIGHT_COLOR = loadColor(R.color.desktop_mode_caption_handle_bar_light)

    private val HANDLE_DARK_COLOR = loadColor(R.color.desktop_mode_caption_handle_bar_dark)

    /** Final horizontal padding for hover enter. */
    private val HANDLE_HOVER_ENTER_PADDING =
        loadDimensionPixelSize(
            R.dimen.desktop_mode_fullscreen_decor_caption_horizontal_padding_hovered
        ) -
            loadDimensionPixelSize(
                R.dimen.desktop_mode_fullscreen_decor_caption_horizontal_padding_default
            )

    /** Final horizontal padding for press down. */
    private val HANDLE_PRESS_DOWN_PADDING =
        loadDimensionPixelSize(
            R.dimen.desktop_mode_fullscreen_decor_caption_horizontal_padding_touched
        ) -
            loadDimensionPixelSize(
                R.dimen.desktop_mode_fullscreen_decor_caption_horizontal_padding_default
            )

    @VisibleForTesting
    private val handlePaint: Paint =
        Paint().apply {
            flags = Paint.ANTI_ALIAS_FLAG
            style = Paint.Style.FILL
            color = HANDLE_DARK_COLOR
        }

    private val argbEvaluator: ArgbEvaluator = ArgbEvaluator.getInstance()
    private var currentHandleHeight = HANDLE_HEIGHT
    private var currentHandleWidth = HANDLE_WIDTH
    private var padding = 0
    @ColorInt private var handleColor = HANDLE_DARK_COLOR

    protected override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val handleLeft = (width - currentHandleWidth) / 2 + padding
        val handleRight = handleLeft + currentHandleWidth - 2 * padding
        val handleCenterY = height.toFloat() / 2
        val handleTop = (handleCenterY - currentHandleHeight / 2).toInt().toFloat()
        val handleBottom = handleTop + currentHandleHeight
        val cornerRadius = currentHandleHeight / 2
        canvas.drawRoundRect(
            handleLeft,
            handleTop,
            handleRight,
            handleBottom,
            cornerRadius,
            cornerRadius,
            handlePaint,
        )
    }

    /** Sets handle width, height and color. Does not change the layout properties */
    private fun setHandleProperties(width: Float, height: Float, @ColorInt color: Int) {
        currentHandleHeight = height
        currentHandleWidth = width
        handlePaint.setColor(color)
        invalidate()
    }

    /**
     * Updates the handle color.
     *
     * @param isRegionDark Whether the background behind the handle is dark, and thus the handle
     *   should be light (and vice versa).
     * @param animated Whether to animate the change, or apply it immediately.
     */
    fun updateHandleColor(isRegionDark: Boolean, animated: Boolean) {
        val newColor = if (isRegionDark) HANDLE_LIGHT_COLOR else HANDLE_DARK_COLOR
        if (newColor == handleColor) {
            return
        }
        handleColor = newColor
        setHandleColor(newColor)
    }

    private fun setHandleColor(color: Int) {
        handlePaint.setColor(color)
        invalidate()
    }

    /** Animates handle for the app handle menu. */
    fun animateHandleForMenu(
        progress: Float,
        widthDelta: Float,
        heightDelta: Float,
        menuColor: Int,
    ) {
        val currentWidth = HANDLE_WIDTH + widthDelta * progress
        val currentHeight = HANDLE_HEIGHT + heightDelta * progress
        val color = argbEvaluator.evaluate(progress, handleColor, menuColor) as Int
        setHandleProperties(currentWidth, currentHeight, color)
        translationY = getTranslationYValue(progress)
    }

    private fun getTranslationYValue(progress: Float): Float {
        return HANDLE_HEIGHT * progress * 2
    }

    override fun onHoverChanged(hovered: Boolean) {
        super.onHoverChanged(hovered)
        when {
            hovered -> animateHandle(HANDLE_HOVER_ANIM_DURATION, HANDLE_HOVER_ENTER_PADDING)
            !isPressed -> animateHandle(HANDLE_HOVER_ANIM_DURATION, HANDLE_DEFAULT_PADDING)
        }
    }

    override fun setPressed(pressed: Boolean) {
        if (isPressed != pressed) {
            super.setPressed(pressed)
            if (pressed) {
                animateHandle(HANDLE_PRESS_ANIM_DURATION, HANDLE_PRESS_DOWN_PADDING)
            } else {
                animateHandle(HANDLE_PRESS_ANIM_DURATION, HANDLE_DEFAULT_PADDING)
            }
        }
    }

    private fun animateHandle(duration: Long, endPadding: Int) {
        if (handleAnimator.isRunning) {
            handleAnimator.cancel()
            handleAnimator.removeAllListeners()
        }
        handleAnimator.duration = duration
        handleAnimator.setIntValues(paddingLeft, endPadding)
        handleAnimator.addUpdateListener { animator ->
            padding = animator.animatedValue as Int
            invalidate()
        }
        handleAnimator.start()
    }

    private fun loadDimensionPixelSize(@DimenRes resourceId: Int): Int {
        return context.resources.getDimensionPixelSize(resourceId, 0)
    }

    private fun loadColor(resourceId: Int): Int {
        if (resourceId == Resources.ID_NULL) {
            return 0
        }
        return ContextCompat.getColor(context, resourceId)
    }

    override fun tint(color: Int) {
        setHandleColor(color)
    }

    override fun asView(): View {
        return this
    }

    override fun getColor(): Int? {
        return handlePaint.color
    }

    companion object {
        /** The duration of animations related to hover state. */
        private const val HANDLE_HOVER_ANIM_DURATION = 300L
        /** The duration of animations related to pressed state. */
        private const val HANDLE_PRESS_ANIM_DURATION = 200L
        /** Default horizontal padding. */
        private const val HANDLE_DEFAULT_PADDING = 0
    }
}
