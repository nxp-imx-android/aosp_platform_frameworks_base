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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.animation.scene.ElementContentScope
import com.android.compose.modifiers.padding
import com.android.systemui.customization.clocks.R as clocksR
import com.android.systemui.keyguard.ui.viewmodel.KeyguardClockViewModel
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElement
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementContext
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementFactory
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys.Clock
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys.Smartspace
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementProvider
import com.android.systemui.res.R
import com.android.systemui.shade.ShadeDisplayAware
import javax.inject.Inject
import kotlin.collections.List

/** Provides default clock regions, if not overridden by the clock itself */
class ClockRegionElementProvider
@Inject
constructor(
    @ShadeDisplayAware private val context: Context,
    private val keyguardClockViewModel: KeyguardClockViewModel,
) : LockscreenElementProvider {
    override val elements: List<LockscreenElement> by lazy {
        listOf(SmallClockRegionElement(), LargeClockRegionElement())
    }

    private inner class SmallClockRegionElement : LockscreenElement {
        override val key = LockscreenElementKeys.Region.Clock.Small
        override val context = this@ClockRegionElementProvider.context

        @Composable
        override fun ElementContentScope.LockscreenElement(
            factory: LockscreenElementFactory,
            context: LockscreenElementContext,
        ) {
            with(factory) {
                val shouldDateWeatherBeBelowSmallClock: Boolean by
                    keyguardClockViewModel.shouldDateWeatherBeBelowSmallClock
                        .collectAsStateWithLifecycle()

                // Horizontal Padding is handled internally within the SmartspaceCards element. This
                // makes the application here to other elements in the hierarchy slightly awkward.
                val xPadding = dimensionResource(clocksR.dimen.clock_padding_start)

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier.padding(horizontal = xPadding)
                                .padding(top = dimensionResource(R.dimen.keyguard_clock_top_margin)),
                    ) {
                        LockscreenElement(Clock.Small, context, Modifier)

                        if (!shouldDateWeatherBeBelowSmallClock) {
                            LockscreenElement(
                                Smartspace.DWA.SmallClock.Column,
                                context,
                                Modifier.padding(
                                    horizontal =
                                        dimensionResource(R.dimen.smartspace_padding_horizontal)
                                ),
                            )
                        }
                    }

                    if (shouldDateWeatherBeBelowSmallClock) {
                        LockscreenElement(
                            Smartspace.DWA.SmallClock.Row,
                            context,
                            Modifier.padding(horizontal = xPadding),
                        )
                    }

                    LockscreenElement(Smartspace.Cards, context, Modifier)
                }
            }
        }
    }

    private inner class LargeClockRegionElement : LockscreenElement {
        override val key = LockscreenElementKeys.Region.Clock.Large
        override val context = this@ClockRegionElementProvider.context

        @Composable
        override fun ElementContentScope.LockscreenElement(
            factory: LockscreenElementFactory,
            context: LockscreenElementContext,
        ) {
            with(factory) {
                val shouldDateWeatherBeBelowLargeClock: Boolean by
                    keyguardClockViewModel.shouldDateWeatherBeBelowLargeClock
                        .collectAsStateWithLifecycle()

                // Horizontal Padding is handled internally within the SmartspaceCards element. This
                // makes the application here to other elements in the hierarchy slightly awkward.
                val xPadding = dimensionResource(clocksR.dimen.clock_padding_start)

                Column(
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.Top,
                    modifier =
                        Modifier.fillMaxSize()
                            .padding(top = dimensionResource(R.dimen.keyguard_clock_top_margin)),
                ) {
                    Column(
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.Top,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (!shouldDateWeatherBeBelowLargeClock) {
                            LockscreenElement(
                                Smartspace.DWA.LargeClock.Above,
                                context,
                                Modifier.padding(horizontal = xPadding),
                            )
                        }

                        LockscreenElement(
                            Smartspace.Cards,
                            context,
                            // Always reserve space for smartspace cards, even if they're not
                            // visible. This keeps the clock position stable when smartspace
                            // enters/exits.
                            Modifier.heightIn(
                                min = dimensionResource(clocksR.dimen.enhanced_smartspace_height)
                            ),
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement =
                            Arrangement.spacedBy(
                                dimensionResource(R.dimen.smartspace_padding_vertical),
                                Alignment.CenterVertically,
                            ),
                        modifier = Modifier.padding(horizontal = xPadding).fillMaxWidth().weight(1f),
                    ) {
                        LockscreenElement(Clock.Large, context, Modifier)
                        if (shouldDateWeatherBeBelowLargeClock) {
                            LockscreenElement(Smartspace.DWA.LargeClock.Below, context, Modifier)
                        }
                    }
                }
            }
        }
    }
}
