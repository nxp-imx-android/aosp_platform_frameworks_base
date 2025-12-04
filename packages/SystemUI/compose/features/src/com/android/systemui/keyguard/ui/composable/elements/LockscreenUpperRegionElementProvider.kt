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

package com.android.systemui.keyguard.ui.composable.elements

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import com.android.compose.animation.Easings
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.ElementContentScope
import com.android.compose.animation.scene.PropertyTransformationBuilder
import com.android.compose.animation.scene.TransitionBuilder
import com.android.compose.windowsizeclass.LocalWindowSizeClass
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.keyguard.shared.model.ClockSize
import com.android.systemui.keyguard.ui.viewmodel.LockscreenUpperRegionViewModel
import com.android.systemui.keyguard.ui.viewmodel.LockscreenUpperRegionViewModel.Decision
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.Logger
import com.android.systemui.log.dagger.KeyguardBlueprintLog
import com.android.systemui.plugins.keyguard.ui.composable.elements.BaseLockscreenElement.ElementSource
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElement
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys.Clock
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys.MediaCarousel
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys.Notifications
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys.Region
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys.Smartspace
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementProvider
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenMovableParentKeys.UpperRegion.NarrowLayout as NarrowScenes
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenMovableParentKeys.UpperRegion.WideLayout as WideScenes
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenScope
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenScope.Companion.LockscreenElement
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenScope.Companion.NestedScenes
import com.android.systemui.res.R
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.shade.shared.model.ShadeMode
import com.google.errorprone.annotations.CompileTimeConstant
import javax.inject.Inject

@SysUISingleton
/** Provides a combined element for all lockscreen ui above the lock icon */
class LockscreenUpperRegionElementProvider
@Inject
constructor(
    @ShadeDisplayAware private val context: Context,
    @KeyguardBlueprintLog private val blueprintLog: LogBuffer,
    private val viewModelFactory: LockscreenUpperRegionViewModel.Factory,
) : LockscreenElementProvider {
    private val logger = Logger(blueprintLog, "LockscreenUpperRegionElementProvider")
    override val elements: List<LockscreenElement> by lazy { listOf(UpperRegionElement()) }

    private inner class UpperRegionElement : LockscreenElement {
        override val key = Region.Upper
        override val context = this@LockscreenUpperRegionElementProvider.context
        override val source = ElementSource.STANDARD

        @Composable
        override fun LockscreenScope<ElementContentScope>.LockscreenElement() {
            val viewModel = rememberViewModel("LockscreenUpperRegion") { viewModelFactory.create() }
            val layoutType = logDecision("LayoutType") { getLayoutDecision(viewModel.shadeMode) }
            val layout =
                remember(viewModel, layoutType) {
                    when (layoutType) {
                        LayoutType.WIDE -> WideLayout(viewModel)
                        LayoutType.NARROW -> NarrowLayout(viewModel)
                    }
                }

            with(layout) { Layout() }
        }
    }

    abstract inner class RegionLayout(val viewModel: LockscreenUpperRegionViewModel) {
        @Composable abstract fun LockscreenScope<ContentScope>.Layout(modifier: Modifier = Modifier)

        @Composable
        protected fun LockscreenScope<ContentScope>.Notifications(
            aodAlignment: Alignment,
            modifier: Modifier = Modifier,
        ) {
            Box(modifier = Modifier.fillMaxSize().then(context.burnInModifier).then(modifier)) {
                AODNotifications(Modifier.align(aodAlignment))
                // Make the Notification section overlap with the AOD icons,
                // to avoid jumps while animating them in.
                AnimatedVisibility(viewModel.isNotificationStackActive) {
                    LockscreenElement(Notifications.Stack)
                }
            }
        }

        @Composable
        protected fun LockscreenScope<ContentScope>.MediaCarousel(modifier: Modifier = Modifier) {
            val bottomPadding =
                dimensionResource(R.dimen.notification_section_divider_height_lockscreen)
            LockscreenElement(
                MediaCarousel,
                Modifier.padding(bottom = bottomPadding).then(modifier),
            )
        }

        @Composable
        protected fun LockscreenScope<ContentScope>.AODNotifications(
            modifier: Modifier = Modifier
        ) {
            Column(modifier) {
                LockscreenElement(Notifications.AOD.Promoted, Modifier.padding(bottom = 4.dp))
                LockscreenElement(Notifications.AOD.IconShelf)
            }
        }

        protected fun TransitionBuilder.configureClockCenteringTransition() {
            val duration =
                if (viewModel.shouldSkipTransition) 1 else CLOCK_CENTERING_DURATION_MILLIS
            spec = tween(duration, easing = Easings.Emphasized)
        }

        protected fun TransitionBuilder.configureClockSwitchTransition(
            enter: PropertyTransformationBuilder.() -> Unit,
            exit: PropertyTransformationBuilder.() -> Unit,
        ) {
            val duration = if (viewModel.shouldSkipTransition) 1 else 300
            spec = tween(duration, easing = Easings.Emphasized)

            // Since Smartspace cards are guaranteed to be shared between the small and large clock
            // regions, it's convenient to anchor the movement of the small clock elements to it.
            anchoredTranslate(Clock.Small, anchor = Smartspace.Cards)
            anchoredTranslate(Smartspace.DWA.SmallClock.Row, anchor = Smartspace.Cards)
            anchoredTranslate(Smartspace.DWA.SmallClock.Column, anchor = Smartspace.Cards)

            if (!viewModel.shouldSkipTransition) {
                timestampRange(endMillis = 133) { exit() }
                timestampRange(startMillis = 133, endMillis = 300) { enter() }
            }
        }

        protected fun PropertyTransformationBuilder.fadeLargeClock() {
            fade(Clock.Large)
            fade(Smartspace.DWA.LargeClock.Above)
            fade(Smartspace.DWA.LargeClock.Below)
        }

        protected fun PropertyTransformationBuilder.fadeSmallClock() {
            fade(Clock.Small)
            fade(Smartspace.DWA.SmallClock.Row)
            fade(Smartspace.DWA.SmallClock.Column)
        }
    }

    /** The Narrow Layouts are intended for phones */
    inner class NarrowLayout(viewModel: LockscreenUpperRegionViewModel) : RegionLayout(viewModel) {
        @Composable
        override fun LockscreenScope<ContentScope>.Layout(modifier: Modifier) {
            val clockSize =
                logDecision("NarrowLayout: ClockSize") {
                    viewModel.evaluateClockSize {
                        when {
                            viewModel.isNotificationStackActive ->
                                Decision(ClockSize.SMALL, "isNotificationStackActive")
                            viewModel.isMediaVisible -> Decision(ClockSize.SMALL, "isMediaVisible")
                            else -> Decision(ClockSize.LARGE, "Default Case")
                        }
                    }
                }

            NestedScenes(
                sceneKey =
                    when (clockSize) {
                        ClockSize.LARGE -> NarrowScenes.LargeClock
                        ClockSize.SMALL -> NarrowScenes.SmallClock
                    },
                transitions = {
                    from(from = NarrowScenes.SmallClock, to = NarrowScenes.LargeClock) {
                        configureClockSwitchTransition(
                            enter = { fadeLargeClock() },
                            exit = { fadeSmallClock() },
                        )
                    }
                    from(from = NarrowScenes.LargeClock, to = NarrowScenes.SmallClock) {
                        configureClockSwitchTransition(
                            enter = { fadeSmallClock() },
                            exit = { fadeLargeClock() },
                        )
                    }
                },
                modifier = modifier,
            ) {
                scene(NarrowScenes.LargeClock) { LockscreenElement(Region.Clock.Large) }
                scene(NarrowScenes.SmallClock) {
                    Column {
                        LockscreenElement(Region.Clock.Small)
                        MediaCarousel()
                        Notifications(aodAlignment = Alignment.TopStart)
                    }
                }
            }
        }
    }

    /** The wide layouts are intended for tablets / foldables */
    inner class WideLayout(viewModel: LockscreenUpperRegionViewModel) : RegionLayout(viewModel) {
        @Composable
        override fun LockscreenScope<ContentScope>.Layout(modifier: Modifier) {
            val clockSize =
                logDecision("WideLayout: ClockSize") {
                    viewModel.evaluateClockSize {
                        when {
                            viewModel.shadeMode == ShadeMode.Dual ->
                                Decision(ClockSize.LARGE, "shadeMode == ShadeMode.Dual")
                            viewModel.isMediaVisible -> Decision(ClockSize.SMALL, "isMediaVisible")
                            else -> Decision(ClockSize.LARGE, "Default Case")
                        }
                    }
                }

            val isTwoColumn =
                logDecision("WideLayout: TwoColumn") {
                    when {
                        clockSize == ClockSize.SMALL -> Decision(true, "clockSize == SMALL")
                        !viewModel.isDozing && viewModel.isNotificationStackActive ->
                            Decision(true, "!isDozing && isNotificationStackActive")
                        viewModel.isDozing && viewModel.isHeadsUpNotificationActive ->
                            Decision(true, "isDozing && isHeadsUpNotificationActive")
                        viewModel.isDozing && viewModel.isPromotedNotificationActive ->
                            Decision(true, "isDozing && isPromotedNotificationActive")
                        else -> Decision(false, "Default Case")
                    }
                }

            NestedScenes(
                sceneKey =
                    when {
                        !isTwoColumn -> WideScenes.CenteredClock
                        clockSize == ClockSize.LARGE -> WideScenes.TwoColumn.LargeClock
                        else -> WideScenes.TwoColumn.SmallClock
                    },
                transitions = {
                    from(from = WideScenes.CenteredClock, to = WideScenes.TwoColumn.LargeClock) {
                        configureClockCenteringTransition()
                    }
                    from(from = WideScenes.TwoColumn.LargeClock, to = WideScenes.CenteredClock) {
                        configureClockCenteringTransition()
                    }
                    from(from = WideScenes.CenteredClock, to = WideScenes.TwoColumn.SmallClock) {
                        configureClockSwitchTransition(
                            enter = { fadeSmallClock() },
                            exit = { fadeLargeClock() },
                        )
                    }
                    from(from = WideScenes.TwoColumn.SmallClock, to = WideScenes.CenteredClock) {
                        configureClockSwitchTransition(
                            enter = { fadeLargeClock() },
                            exit = { fadeSmallClock() },
                        )
                    }
                    from(
                        from = WideScenes.TwoColumn.LargeClock,
                        to = WideScenes.TwoColumn.SmallClock,
                    ) {
                        configureClockSwitchTransition(
                            enter = { fadeSmallClock() },
                            exit = { fadeLargeClock() },
                        )
                    }
                    from(
                        from = WideScenes.TwoColumn.SmallClock,
                        to = WideScenes.TwoColumn.LargeClock,
                    ) {
                        configureClockSwitchTransition(
                            enter = { fadeLargeClock() },
                            exit = { fadeSmallClock() },
                        )
                    }
                },
                modifier = modifier,
            ) {
                scene(WideScenes.CenteredClock) {
                    // Media is unsupported with centered large clock
                    LargeClockCenter_NotifsAlign(
                        notifAlignment =
                            when (viewModel.shadeMode) {
                                ShadeMode.Dual -> {
                                    if (viewModel.useDesktopStatusBar) Alignment.TopEnd
                                    else Alignment.TopStart
                                }
                                ShadeMode.Split -> Alignment.TopEnd
                                else -> {
                                    logger.wtf("WideLayout state is invalid")
                                    Alignment.TopCenter
                                }
                            }
                    )
                }
                scene(WideScenes.TwoColumn.LargeClock) {
                    when (viewModel.shadeMode) {
                        ShadeMode.Dual -> {
                            if (viewModel.useDesktopStatusBar) LargeClockStart_NotifsEnd_MediaEnd()
                            else LargeClockEnd_NotifsStart_MediaStart()
                        }
                        // Media is unsupported with large clock in split shade mode
                        ShadeMode.Split -> LargeClockStart_NotifsEnd()
                        else -> logger.wtf("WideLayout state is invalid")
                    }
                }
                scene(WideScenes.TwoColumn.SmallClock) {
                    when (viewModel.shadeMode) {
                        ShadeMode.Dual -> {
                            if (viewModel.useDesktopStatusBar) SmallClockStart_NotifsEnd_MediaEnd()
                            else SmallClockStart_NotifsStart_MediaStart()
                        }
                        ShadeMode.Split -> SmallClockStart_NotifsEnd_MediaStart()
                        else -> logger.wtf("WideLayout state is invalid")
                    }
                }
            }
        }

        @Composable
        private fun LockscreenScope<ContentScope>.LargeClockCenter_NotifsAlign(
            notifAlignment: Alignment,
            modifier: Modifier = Modifier,
        ) {
            // We overlap the notification stack with large clock region so that large clock is
            // horizontally centered as expected. Since this layout should only be used when the
            // all notifications are in the shelf, these elements won't overlap visually in
            // practice outside of momentarially during certain transitions.
            Box(
                modifier = Modifier.fillMaxSize().then(modifier),
                contentAlignment = Alignment.Center,
            ) {
                LockscreenElement(Region.Clock.Large)
                AODNotifications(context.burnInModifier.align(notifAlignment))
            }
        }

        @Composable
        private fun LockscreenScope<ContentScope>.LargeClockEnd_NotifsStart_MediaStart(
            modifier: Modifier = Modifier
        ) {
            TwoColumn(
                startContent = {
                    Column {
                        MediaCarousel()
                        Notifications(aodAlignment = Alignment.TopStart)
                    }
                },
                endContent = { LockscreenElement(Region.Clock.Large) },
                modifier = modifier,
            )
        }

        @Composable
        private fun LockscreenScope<ContentScope>.SmallClockStart_NotifsStart_MediaStart(
            modifier: Modifier = Modifier
        ) {
            TwoColumn(
                startContent = {
                    Column {
                        LockscreenElement(Region.Clock.Small)
                        MediaCarousel()
                        Notifications(aodAlignment = Alignment.TopStart)
                    }
                },
                modifier = modifier,
            )
        }

        @Composable
        private fun LockscreenScope<ContentScope>.LargeClockStart_NotifsEnd(
            modifier: Modifier = Modifier
        ) {
            TwoColumn(
                startContent = { LockscreenElement(Region.Clock.Large) },
                endContent = { Notifications(aodAlignment = Alignment.TopEnd) },
                modifier = modifier,
            )
        }

        @Composable
        private fun LockscreenScope<ContentScope>.LargeClockStart_NotifsEnd_MediaEnd(
            modifier: Modifier = Modifier
        ) {
            TwoColumn(
                startContent = { LockscreenElement(Region.Clock.Large) },
                endContent = {
                    Column {
                        MediaCarousel()
                        Notifications(aodAlignment = Alignment.TopEnd)
                    }
                },
                modifier = modifier,
            )
        }

        @Composable
        private fun LockscreenScope<ContentScope>.SmallClockStart_NotifsEnd_MediaStart(
            modifier: Modifier = Modifier
        ) {
            TwoColumn(
                startContent = {
                    Column {
                        LockscreenElement(Region.Clock.Small)
                        MediaCarousel()
                    }
                },
                endContent = { Notifications(aodAlignment = Alignment.TopEnd) },
                modifier = modifier,
            )
        }

        @Composable
        private fun LockscreenScope<ContentScope>.SmallClockStart_NotifsEnd_MediaEnd(
            modifier: Modifier = Modifier
        ) {
            TwoColumn(
                startContent = { LockscreenElement(Region.Clock.Small) },
                endContent = {
                    Column {
                        MediaCarousel()
                        Notifications(aodAlignment = Alignment.TopEnd)
                    }
                },
                modifier = modifier,
            )
        }

        @Composable
        private fun TwoColumn(
            modifier: Modifier = Modifier,
            startContent: @Composable BoxScope.() -> Unit = {},
            endContent: @Composable BoxScope.() -> Unit = {},
        ) {
            Row(modifier) {
                Box(
                    content = startContent,
                    modifier =
                        Modifier.fillMaxWidth(0.5f).fillMaxHeight().graphicsLayer {
                            translationX = viewModel.unfoldTranslations.start
                        },
                )
                Box(
                    content = endContent,
                    modifier =
                        Modifier.fillMaxWidth(1f).fillMaxHeight().graphicsLayer {
                            translationX = viewModel.unfoldTranslations.end
                        },
                )
            }
        }
    }

    @Composable
    fun <T> logDecision(
        @CompileTimeConstant prefix: String,
        func: @Composable () -> Decision<T>,
    ): T {
        val decision = func()
        logger.i({ "$prefix: decision=$str1; reason=$str2" }) {
            str1 = "${decision.choice}"
            str2 = decision.reason
        }
        return decision.choice
    }

    companion object {
        const val CLOCK_CENTERING_DURATION_MILLIS = 1000

        enum class LayoutType {
            WIDE,
            NARROW,
        }

        @Composable
        fun getLayoutDecision(shadeMode: ShadeMode): Decision<LayoutType> {
            return when (shadeMode) {
                ShadeMode.Single -> Decision(LayoutType.NARROW, "Single Shade")
                ShadeMode.Split -> Decision(LayoutType.WIDE, "Split Shade")
                ShadeMode.Dual -> {
                    with(LocalWindowSizeClass.current) {
                        val isWindowLarge =
                            isAtLeastBreakpoint(
                                WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND,
                                WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND,
                            )

                        if (isWindowLarge) {
                            Decision(LayoutType.WIDE, "Dual Shade && SizeClass >= Medium")
                        } else {
                            Decision(LayoutType.NARROW, "Dual Shade && SizeClass < Medium")
                        }
                    }
                }
            }
        }

        @Composable
        fun getLayoutType(shadeMode: ShadeMode): LayoutType {
            return getLayoutDecision(shadeMode).choice
        }
    }
}
