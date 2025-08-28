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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.window.core.layout.WindowSizeClass
import com.android.compose.animation.Easings
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.ElementContentScope
import com.android.compose.windowsizeclass.LocalWindowSizeClass
import com.android.systemui.keyguard.shared.model.ClockSize
import com.android.systemui.keyguard.ui.viewmodel.LockscreenUpperRegionViewModel
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.Logger
import com.android.systemui.log.dagger.KeyguardBlueprintLog
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElement
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementContext
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementFactory
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys.Clock
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys.MediaCarousel
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys.Notifications
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys.Region
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementProvider
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenSceneKeys.CenteredClockScene
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenSceneKeys.TwoColumnScene
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenScopeUtils.NestedScenes
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.shade.shared.model.ShadeMode
import com.android.systemui.statusbar.notification.promoted.PromotedNotificationUi
import javax.inject.Inject
import kotlin.collections.List

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

    private val wideLayout = WideLayout()
    private val narrowLayout = NarrowLayout()

    private inner class UpperRegionElement : LockscreenElement {
        override val key = LockscreenElementKeys.Region.Upper
        override val context = this@LockscreenUpperRegionElementProvider.context

        @Composable
        override fun ElementContentScope.LockscreenElement(
            factory: LockscreenElementFactory,
            context: LockscreenElementContext,
        ) {
            val viewModel = rememberViewModel("LockscreenUpperRegion") { viewModelFactory.create() }
            when (getLayoutType()) {
                LayoutType.WIDE -> with(wideLayout) { Layout(viewModel, factory, context) }
                LayoutType.NARROW -> with(narrowLayout) { Layout(viewModel, factory, context) }
            }
        }
    }

    /** The Narrow Layouts are intended for phones */
    private inner class NarrowLayout {
        @Composable
        fun ContentScope.Layout(
            viewModel: LockscreenUpperRegionViewModel,
            factory: LockscreenElementFactory,
            context: LockscreenElementContext,
            modifier: Modifier = Modifier,
        ) {
            when (viewModel.clockSize) {
                ClockSize.LARGE -> LargeClock(viewModel, factory, context, modifier)
                ClockSize.SMALL -> Content(viewModel, factory, context, modifier)
            }
        }

        @Composable
        private fun ContentScope.LargeClock(
            viewModel: LockscreenUpperRegionViewModel,
            factory: LockscreenElementFactory,
            context: LockscreenElementContext,
            modifier: Modifier = Modifier,
        ) {
            with(factory) { LockscreenElement(Region.Clock.Large, context, modifier) }
        }

        @Composable
        private fun ContentScope.Content(
            viewModel: LockscreenUpperRegionViewModel,
            factory: LockscreenElementFactory,
            context: LockscreenElementContext,
            modifier: Modifier = Modifier,
        ) {
            with(factory) {
                Column(modifier = modifier) {
                    LockscreenElement(Region.Clock.Small, context, Modifier)
                    LockscreenElement(MediaCarousel, context, Modifier)
                    Notifications(viewModel, factory, context)
                }
            }
        }
    }

    /** The wide layouts are intended for tablets / foldables */
    private inner class WideLayout {
        @Composable
        fun ContentScope.Layout(
            viewModel: LockscreenUpperRegionViewModel,
            factory: LockscreenElementFactory,
            context: LockscreenElementContext,
            modifier: Modifier = Modifier,
        ) {
            // TODO(b/441339360): Align w/ pre-flexi logic
            val isTwoColumn =
                when {
                    viewModel.clockSize == ClockSize.SMALL -> true
                    viewModel.isOnAOD -> false
                    viewModel.isNotificationsVisible -> true
                    viewModel.isMediaVisible -> true
                    else -> false
                }

            NestedScenes(
                sceneKey = if (isTwoColumn) TwoColumnScene else CenteredClockScene,
                transitions = {
                    from(from = CenteredClockScene, to = TwoColumnScene) {
                        spec = tween(ClockCenteringDurationMS, easing = Easings.Emphasized)
                    }
                    from(from = TwoColumnScene, to = CenteredClockScene) {
                        spec = tween(ClockCenteringDurationMS, easing = Easings.Emphasized)
                    }
                },
                modifier = modifier,
            ) {
                scene(CenteredClockScene) { LargeClockCentered(viewModel, factory, context) }
                scene(TwoColumnScene) {
                    when (viewModel.shadeMode) {
                        ShadeMode.Dual -> TwoColumnNotifStart(viewModel, factory, context)
                        ShadeMode.Split -> TwoColumnNotifEnd(viewModel, factory, context)
                        else -> logger.wtf("WideLayout state is invalid")
                    }
                }
            }
        }

        @Composable
        private fun ContentScope.LargeClockCentered(
            viewModel: LockscreenUpperRegionViewModel,
            factory: LockscreenElementFactory,
            context: LockscreenElementContext,
            modifier: Modifier = Modifier,
        ) {
            with(factory) { LockscreenElement(Region.Clock.Large, context, modifier) }
        }

        @Composable
        private fun ContentScope.TwoColumnNotifEnd(
            viewModel: LockscreenUpperRegionViewModel,
            factory: LockscreenElementFactory,
            context: LockscreenElementContext,
            modifier: Modifier = Modifier,
        ) {
            with(factory) {
                TwoColumn(
                    viewModel = viewModel,
                    modifier = modifier,
                    startContent = {
                        Column {
                            if (viewModel.clockSize == ClockSize.SMALL) {
                                LockscreenElement(Region.Clock.Small, context, Modifier)
                            }
                            LockscreenElement(MediaCarousel, context, Modifier)
                        }
                        if (viewModel.clockSize == ClockSize.LARGE) {
                            LockscreenElement(Region.Clock.Large, context, Modifier)
                        }
                    },
                    endContent = { Notifications(viewModel, factory, context) },
                )
            }
        }

        @Composable
        private fun ContentScope.TwoColumnNotifStart(
            viewModel: LockscreenUpperRegionViewModel,
            factory: LockscreenElementFactory,
            context: LockscreenElementContext,
            modifier: Modifier = Modifier,
        ) {
            with(factory) {
                TwoColumn(
                    viewModel = viewModel,
                    modifier = modifier,
                    startContent = {
                        Column {
                            if (viewModel.clockSize == ClockSize.SMALL) {
                                LockscreenElement(Region.Clock.Small, context, Modifier)
                            }
                            LockscreenElement(MediaCarousel, context, Modifier)
                            Notifications(viewModel, factory, context)
                        }
                    },
                    endContent = {
                        if (viewModel.clockSize == ClockSize.LARGE) {
                            LockscreenElement(Region.Clock.Large, context, Modifier)
                        }
                    },
                )
            }
        }

        @Composable
        private fun TwoColumn(
            viewModel: LockscreenUpperRegionViewModel,
            startContent: @Composable BoxScope.() -> Unit,
            endContent: @Composable BoxScope.() -> Unit,
            modifier: Modifier = Modifier,
        ) {
            Row(modifier = modifier) {
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
    private fun ContentScope.Notifications(
        viewModel: LockscreenUpperRegionViewModel,
        factory: LockscreenElementFactory,
        context: LockscreenElementContext,
        modifier: Modifier = Modifier,
    ) {
        with(factory) {
            AnimatedVisibility(viewModel.isNotificationsVisible) {
                Box(modifier = modifier.fillMaxHeight()) {
                    Column {
                        if (PromotedNotificationUi.isEnabled) {
                            LockscreenElement(Notifications.AOD.Promoted, context, Modifier)
                        }
                        LockscreenElement(Notifications.AOD.IconShelf, context, Modifier)
                    }
                    LockscreenElement(Notifications.Stack, context, Modifier)
                }
            }
        }
    }

    companion object {
        val ClockCenteringDurationMS = 1000

        enum class LayoutType {
            WIDE,
            NARROW,
        }

        @Composable
        fun getLayoutType(): LayoutType {
            with(LocalWindowSizeClass.current) {
                val isWindowLarge =
                    isAtLeastBreakpoint(
                        WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND,
                        WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND,
                    )
                return if (isWindowLarge) LayoutType.WIDE else LayoutType.NARROW
            }
        }
    }
}
