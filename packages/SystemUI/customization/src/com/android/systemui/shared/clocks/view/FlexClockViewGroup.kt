/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.shared.clocks.view

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Color
import android.icu.text.NumberFormat
import android.util.MathUtils.constrainedMap
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.withSave
import androidx.core.view.children
import com.android.app.animation.Interpolators
import com.android.systemui.customization.clocks.ClockContext
import com.android.systemui.customization.clocks.R
import com.android.systemui.customization.clocks.utils.CanvasUtils.translate
import com.android.systemui.customization.clocks.utils.ViewUtils.measuredSize
import com.android.systemui.customization.clocks.utils.ViewUtils.position
import com.android.systemui.customization.clocks.view.DigitalClockViewGroup
import com.android.systemui.plugins.keyguard.VPointF
import com.android.systemui.plugins.keyguard.VRectF
import com.android.systemui.plugins.keyguard.ui.clocks.ClockAxisStyle
import com.android.systemui.plugins.keyguard.ui.clocks.ClockPositionAnimationArgs
import com.android.systemui.plugins.keyguard.ui.clocks.ClockViewIds
import java.util.Locale
import kotlin.collections.map
import kotlin.collections.max

@SuppressLint("ViewConstructor")
class FlexClockViewGroup(clockCtx: ClockContext) :
    DigitalClockViewGroup<FlexClockTextView>(clockCtx) {
    override val children: Sequence<FlexClockTextView>
        get() = (this as ViewGroup).children.filterIsInstance<FlexClockTextView>()

    private var onAnimateDoze: (() -> Unit)? = null
    private var isDozeReadyToAnimate = false

    // Does the current language have mono vertical size when displaying numerals
    private var isMonoVerticalNumericLineSpacing = true
    private val digitOffsets = mutableMapOf<Int, Float>()

    init {
        setWillNotDraw(false)
        updateLocale(Locale.getDefault())
    }

    override fun calculateSize(widthMeasureSpec: Int, heightMeasureSpec: Int): VPointF {
        val xScale = if (children.count() < 4) 1f else 2f
        val yBuffer = context.resources.getDimensionPixelSize(R.dimen.clock_vertical_digit_buffer)
        return maxChildSize * VPointF(xScale, 2f) + VPointF(0f, yBuffer)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        isDozeReadyToAnimate = true
        onAnimateDoze?.invoke()
        onAnimateDoze = null
    }

    override fun getChildFrame(child: FlexClockTextView): VRectF {
        val yBuffer = context.resources.getDimensionPixelSize(R.dimen.clock_vertical_digit_buffer)
        var offset =
            maxChildSize.run {
                when (child.id) {
                    ClockViewIds.HOUR_DIGIT_PAIR -> VPointF.ZERO
                    ClockViewIds.HOUR_FIRST_DIGIT -> VPointF.ZERO
                    ClockViewIds.HOUR_SECOND_DIGIT -> VPointF(x, 0f)
                    // Add a small vertical buffer for second line views
                    ClockViewIds.MINUTE_DIGIT_PAIR -> VPointF(0f, y + yBuffer)
                    ClockViewIds.MINUTE_FIRST_DIGIT -> VPointF(0f, y + yBuffer)
                    ClockViewIds.MINUTE_SECOND_DIGIT -> VPointF(x, y + yBuffer)
                    else -> VPointF.ZERO
                }
            }

        val childSize = child.measuredSize

        // Horizontal offset to center each view in the available space
        val midX = if (children.count() < 4) measuredWidth / 2f else measuredWidth / 4f
        offset += VPointF(midX - childSize.x / 2f, 0f)

        return VRectF.fromTopLeft(offset, childSize)
    }

    override fun onDraw(canvas: Canvas) {
        logger.onDraw()
        children.forEach { child ->
            canvas.withSave {
                translate(digitOffsets.getOrDefault(child.id, 0f), 0f)
                translate(child.position)
                child.draw(this)
            }
        }
    }

    override fun refreshTime() {
        logger.refreshTime()
        children.forEach { textView -> textView.refreshText() }
    }

    fun onLocaleChanged(locale: Locale) {
        updateLocale(locale)
        requestLayout()
    }

    fun updateColor(lockscreenColor: Int, aodColor: Int = Color.WHITE) {
        children.forEach { view -> view.updateColor(lockscreenColor, aodColor) }
        invalidate()
    }

    fun updateAxes(axes: ClockAxisStyle, isAnimated: Boolean) {
        children.forEach { view -> view.updateAxes(axes, isAnimated) }
        requestLayout()
    }

    fun onFontSettingChanged(fontSizePx: Float) {
        children.forEach { view -> view.applyTextSize(fontSizePx, constrainedByHeight = false) }
    }

    fun animateDoze(isDozing: Boolean, isAnimated: Boolean) {
        fun executeDozeAnimation() {
            children.forEach { view -> view.animateDoze(isDozing, isAnimated) }
        }

        if (isDozeReadyToAnimate) executeDozeAnimation()
        else onAnimateDoze = { executeDozeAnimation() }
    }

    fun animateCharge() {
        children.forEach { view -> view.animateCharge() }
    }

    override fun animateFidget(pt: VPointF, enforceBounds: Boolean): Boolean {
        if (enforceBounds) {
            if (visibility != View.VISIBLE) {
                logger.animateFidget(pt, isSuppressed = true)
                return false
            }

            val bounds = VRectF(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
            if (!bounds.contains(pt)) {
                logger.animateFidget(pt, isSuppressed = true)
                return false
            }
        }

        children.forEach { it.animateFidget(pt, enforceBounds = false) }
        return true
    }

    private fun updateLocale(locale: Locale) {
        isMonoVerticalNumericLineSpacing =
            !NON_MONO_VERTICAL_NUMERIC_LINE_SPACING_LANGUAGES.any {
                val newLocaleNumberFormat =
                    NumberFormat.getInstance(locale).format(FORMAT_NUMBER.toLong())
                val nonMonoVerticalNumericLineSpaceNumberFormat =
                    NumberFormat.getInstance(Locale.forLanguageTag(it))
                        .format(FORMAT_NUMBER.toLong())
                newLocaleNumberFormat == nonMonoVerticalNumericLineSpaceNumberFormat
            }
    }

    /** Offsets the textViews of the clock for the step clock animation. */
    fun offsetGlyphsForStepClockAnimation(args: ClockPositionAnimationArgs) {
        val translation = left - args.fromLeft
        val isCentering = isLayoutRtl == (translation < 0)
        // A map of the delays for a given digit, keyed by digit index. Inverted for rtl motion.
        val delays = if (isLayoutRtl == isCentering) STEP_LEFT_DELAYS else STEP_RIGHT_DELAYS
        children.forEachIndexed { index, child ->
            val digitFraction =
                STEP_INTERPOLATOR.getInterpolation(
                    constrainedMap(
                        /* rangeMin= */ 0.0f,
                        /* rangeMax= */ 1.0f,
                        /* valueMin= */ delays[index],
                        /* valueMax= */ delays[index] + STEP_ANIMATION_TIME,
                        /* value= */ args.fraction,
                    )
                )
            digitOffsets[child.id] = translation * (digitFraction - 1)
        }
        invalidate()
    }

    fun resetGlyphsOffsets() {
        if (digitOffsets.isEmpty()) return
        digitOffsets.clear()
        invalidate()
    }

    /** Offsets the textViews of the clock for the compose version of the step clock animation. */
    fun offsetGlyphsForStepClockAnimation(
        startX: Float,
        currentX: Float,
        endX: Float,
        progress: Float,
    ) {
        if (progress <= 0f || progress >= 1f) {
            resetGlyphsOffsets()
            return
        }

        val translation = endX - startX
        // A map of the delays for a given digit, keyed by digit index
        val delays = if (translation > 0) STEP_RIGHT_DELAYS else STEP_LEFT_DELAYS
        children.forEachIndexed { index, child ->
            val digitFraction =
                constrainedMap(
                    /* rangeMin= */ 0.0f,
                    /* rangeMax= */ 1.0f,
                    /* valueMin= */ delays[index],
                    /* valueMax= */ delays[index] + STEP_ANIMATION_TIME,
                    /* value= */ progress,
                )

            val digitX = translation * digitFraction + startX
            digitOffsets[child.id] = (digitX - currentX)
        }
        invalidate()
    }

    companion object {
        const val FORMAT_NUMBER = 1234567890
        const val AOD_TRANSITION_DURATION = 800L

        private val STEP_INTERPOLATOR = Interpolators.EMPHASIZED
        // Measured as fraction of total animation duration
        private const val STEP_DIGIT_DELAY = 0.033f
        private val STEP_LEFT_DELAYS = listOf(0, 1, 2, 3).map { it * STEP_DIGIT_DELAY }
        private val STEP_RIGHT_DELAYS = listOf(1, 0, 3, 2).map { it * STEP_DIGIT_DELAY }
        private val STEP_ANIMATION_TIME = 1.0f - STEP_LEFT_DELAYS.max()

        /** Languages that do not have vertically mono spaced numerals */
        private val NON_MONO_VERTICAL_NUMERIC_LINE_SPACING_LANGUAGES = setOf("my" /* Burmese */)
    }
}
