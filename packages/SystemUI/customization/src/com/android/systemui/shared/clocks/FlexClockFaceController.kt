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

package com.android.systemui.shared.clocks

import android.graphics.Rect
import android.icu.util.TimeZone
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import com.android.app.animation.Interpolators
import com.android.systemui.animation.GSFAxes
import com.android.systemui.customization.clocks.ClockContext
import com.android.systemui.customization.clocks.DefaultClockFaceLayout
import com.android.systemui.customization.clocks.DigitalTimeFormatter
import com.android.systemui.customization.clocks.DigitalTimespec
import com.android.systemui.customization.clocks.FontTextStyle
import com.android.systemui.customization.clocks.FontUtils.get
import com.android.systemui.customization.clocks.FontUtils.set
import com.android.systemui.customization.clocks.R
import com.android.systemui.customization.clocks.ViewUtils.computeLayoutDiff
import com.android.systemui.customization.clocks.view.DigitalAlignment
import com.android.systemui.customization.clocks.view.HorizontalAlignment
import com.android.systemui.customization.clocks.view.VerticalAlignment
import com.android.systemui.plugins.clocks.AlarmData
import com.android.systemui.plugins.clocks.ClockAnimations
import com.android.systemui.plugins.clocks.ClockAxisStyle
import com.android.systemui.plugins.clocks.ClockEvents
import com.android.systemui.plugins.clocks.ClockFaceConfig
import com.android.systemui.plugins.clocks.ClockFaceController
import com.android.systemui.plugins.clocks.ClockFaceEvents
import com.android.systemui.plugins.clocks.ClockFaceLayout
import com.android.systemui.plugins.clocks.ClockFontAxis.Companion.merge
import com.android.systemui.plugins.clocks.ClockPositionAnimationArgs
import com.android.systemui.plugins.clocks.ClockViewIds
import com.android.systemui.plugins.clocks.ThemeConfig
import com.android.systemui.plugins.clocks.TimeFormatKind
import com.android.systemui.plugins.clocks.VPointF
import com.android.systemui.plugins.clocks.VRectF
import com.android.systemui.plugins.clocks.WeatherData
import com.android.systemui.plugins.clocks.ZenData
import com.android.systemui.shared.clocks.FlexClockController.Companion.getDefaultAxes
import com.android.systemui.shared.clocks.view.FlexClockViewGroup
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

interface FlexClockViewController {
    val view: View
    val events: ClockEvents
    val animations: ClockAnimations
    val faceEvents: ClockFaceEvents
    val config: ClockFaceConfig

    var onViewBoundsChanged: ((VRectF) -> Unit)?
    var onViewMaxSizeChanged: ((VPointF) -> Unit)?
}

class FlexClockFaceController(
    private val clockCtx: ClockContext,
    private val isLargeClock: Boolean,
) : ClockFaceController {
    override val view: View
        get() = layerController.view

    override val config = ClockFaceConfig(hasCustomPositionUpdatedAnimation = true)

    override var theme = ThemeConfig(true, clockCtx.settings.seedColor)

    private val keyguardLargeClockTopMargin =
        clockCtx.resources.getDimensionPixelSize(R.dimen.keyguard_large_clock_top_margin)
    private val timeFormatter =
        DigitalTimeFormatter("h:mm", clockCtx.timeKeeper, enableContentDescription = true)
    val layerController: FlexClockViewController

    init {
        layerController =
            if (isLargeClock) {
                FlexClockViewGroupController(clockCtx)
            } else {
                val cfg = SMALL_LAYER_CONFIG.copy(timeFormatter = timeFormatter)
                FlexClockTextViewController(clockCtx, cfg, isLargeClock)
            }
        layerController.view.layoutParams =
            FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT).apply { gravity = Gravity.CENTER }
    }

    override val layout: ClockFaceLayout =
        DefaultClockFaceLayout(view).apply {
            views[0].id =
                if (isLargeClock) ClockViewIds.LOCKSCREEN_CLOCK_VIEW_LARGE
                else ClockViewIds.LOCKSCREEN_CLOCK_VIEW_SMALL
        }

    override val events = FlexClockFaceEvents()

    // TODO(b/364680879): Remove ClockEvents
    inner class FlexClockFaceEvents : ClockEvents, ClockFaceEvents {
        override var isReactiveTouchInteractionEnabled = false
            get() = field
            set(value) {
                field = value
                layerController.events.isReactiveTouchInteractionEnabled = value
            }

        override fun onTimeTick() {
            clockCtx.timeKeeper.updateTime()
            view.contentDescription = timeFormatter.getContentDescription()
            layerController.faceEvents.onTimeTick()
        }

        override fun onTimeZoneChanged(timeZone: TimeZone) {
            clockCtx.timeKeeper.timeZone = timeZone
            layerController.events.onTimeZoneChanged(timeZone)
        }

        override fun onTimeFormatChanged(formatKind: TimeFormatKind) {
            timeFormatter.formatKind = formatKind
            layerController.events.onTimeFormatChanged(formatKind)
        }

        override fun onLocaleChanged(locale: Locale) {
            timeFormatter.locale = locale
            layerController.events.onLocaleChanged(locale)
        }

        override fun onFontSettingChanged(fontSizePx: Float) {
            layerController.faceEvents.onFontSettingChanged(fontSizePx)
            view.requestLayout()
        }

        override fun onThemeChanged(theme: ThemeConfig) {
            this@FlexClockFaceController.theme = theme
            layerController.faceEvents.onThemeChanged(theme)
        }

        /**
         * targetRegion passed to all customized clock applies counter translationY of Keyguard and
         * keyguard_large_clock_top_margin from default clock
         */
        override fun onTargetRegionChanged(targetRegion: Rect?) {
            var maxWidth = 0f
            var maxHeight = 0f

            layerController.faceEvents.onTargetRegionChanged(targetRegion)
            maxWidth = max(maxWidth, view.layoutParams.width.toFloat())
            maxHeight = max(maxHeight, view.layoutParams.height.toFloat())

            val lp =
                if (maxHeight <= 0 || maxWidth <= 0 || targetRegion == null) {
                    // No specified width/height. Just match parent size.
                    FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                } else {
                    // Scale to fit in targetRegion based on largest child elements.
                    val ratio = maxWidth / maxHeight
                    val targetRatio = targetRegion.width() / targetRegion.height().toFloat()
                    val scale =
                        if (ratio > targetRatio) targetRegion.width() / maxWidth
                        else targetRegion.height() / maxHeight

                    FrameLayout.LayoutParams(
                        (maxWidth * scale).roundToInt(),
                        (maxHeight * scale).roundToInt(),
                    )
                }

            lp.gravity = Gravity.CENTER
            view.layoutParams = lp
            targetRegion?.let {
                val diff = view.computeLayoutDiff(it, isLargeClock)
                view.translationX = diff.x
                view.translationY = diff.y
            }
        }

        override fun onSecondaryDisplayChanged(onSecondaryDisplay: Boolean) {}

        override fun onWeatherDataChanged(data: WeatherData) {
            layerController.events.onWeatherDataChanged(data)
        }

        override fun onAlarmDataChanged(data: AlarmData) {
            layerController.events.onAlarmDataChanged(data)
        }

        override fun onZenDataChanged(data: ZenData) {
            layerController.events.onZenDataChanged(data)
        }
    }

    override val animations =
        object : ClockAnimations {
            override fun enter() {
                layerController.animations.enter()
            }

            override fun doze(fraction: Float) {
                layerController.animations.doze(fraction)
            }

            override fun fold(fraction: Float) {
                layerController.animations.fold(fraction)
            }

            override fun charge() {
                layerController.animations.charge()
            }

            override fun onPickerCarouselSwiping(swipingFraction: Float) {
                if (isLargeClock) {
                    view.translationY = keyguardLargeClockTopMargin / 2F * swipingFraction
                }
                layerController.animations.onPickerCarouselSwiping(swipingFraction)
                view.invalidate()
            }

            override fun onPositionAnimated(args: ClockPositionAnimationArgs) {
                layerController.animations.onPositionAnimated(args)
                if (isLargeClock) {
                    (view as? FlexClockViewGroup)?.offsetGlyphsForStepClockAnimation(args)
                }
            }

            override fun onFidgetTap(x: Float, y: Float) {
                layerController.animations.onFidgetTap(x, y)
            }

            override fun onFontAxesChanged(style: ClockAxisStyle) {
                var axes = ClockAxisStyle(getDefaultAxes(clockCtx.settings).merge(style))
                if (!isLargeClock && axes[GSFAxes.WIDTH] > SMALL_CLOCK_MAX_WDTH) {
                    axes[GSFAxes.WIDTH] = SMALL_CLOCK_MAX_WDTH
                }

                layerController.animations.onFontAxesChanged(axes)
            }
        }

    companion object {
        val SMALL_CLOCK_MAX_WDTH = 120f

        val SMALL_LAYER_CONFIG =
            LayerConfig(
                style = FontTextStyle(fontSizeScale = 0.98f),
                aodStyle =
                    FontTextStyle(
                        transitionInterpolator = Interpolators.EMPHASIZED,
                        transitionDuration = FlexClockViewGroup.AOD_TRANSITION_DURATION,
                    ),
                alignment = DigitalAlignment(HorizontalAlignment.START, VerticalAlignment.CENTER),
                timespec = DigitalTimespec.TIME_FULL_FORMAT,
                timeFormatter = null, // Placeholder
            )
    }
}
