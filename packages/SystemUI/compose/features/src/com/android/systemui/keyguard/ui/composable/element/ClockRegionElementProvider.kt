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

package com.android.systemui.keyguard.ui.composable.element

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.animation.scene.ContentScope
import com.android.compose.modifiers.padding
import com.android.systemui.customization.clocks.R as clocksR
import com.android.systemui.keyguard.ui.viewmodel.KeyguardClockViewModel
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElement
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementContext
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementFactory
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementFactory.Companion.lockscreenElement
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys.Clock
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys.Smartspace
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementProvider
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
        listOf(smallClockRegionElement, largeClockRegionElement)
    }

    private val smallClockRegionElement =
        object : LockscreenElement {
            override val key = LockscreenElementKeys.Clock.Region.Small
            override val context = this@ClockRegionElementProvider.context

            @Composable
            override fun ContentScope.LockscreenElement(
                factory: LockscreenElementFactory,
                context: LockscreenElementContext,
            ) {
                val shouldDateWeatherBeBelowSmallClock: Boolean by
                    keyguardClockViewModel.shouldDateWeatherBeBelowSmallClock
                        .collectAsStateWithLifecycle()
                val paddingModifier =
                    Modifier.padding(
                        horizontal = dimensionResource(clocksR.dimen.clock_padding_start)
                    )

                Column {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = paddingModifier,
                    ) {
                        factory.lockscreenElement(Clock.Small, context)

                        if (!shouldDateWeatherBeBelowSmallClock) {
                            Column(
                                horizontalAlignment = Alignment.Start,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = context.burnInModifier,
                            ) {
                                factory.lockscreenElement(Smartspace.Date.SmallClock, context)
                                factory.lockscreenElement(Smartspace.Weather.SmallClock, context)
                            }
                        }
                    }

                    if (shouldDateWeatherBeBelowSmallClock) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = paddingModifier.then(context.burnInModifier),
                        ) {
                            factory.lockscreenElement(Smartspace.Date.SmallClock, context)
                            factory.lockscreenElement(Smartspace.Weather.SmallClock, context)
                        }
                    }

                    factory.lockscreenElement(Smartspace.Cards, context)
                }
            }
        }

    private val largeClockRegionElement =
        object : LockscreenElement {
            override val key = LockscreenElementKeys.Clock.Region.Large
            override val context = this@ClockRegionElementProvider.context

            @Composable
            override fun ContentScope.LockscreenElement(
                factory: LockscreenElementFactory,
                context: LockscreenElementContext,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                ) {
                    factory.lockscreenElement(Smartspace.Cards, context)
                    factory.lockscreenElement(Clock.Large, context)

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = context.burnInModifier,
                    ) {
                        factory.lockscreenElement(Smartspace.Date.LargeClock, context)
                        factory.lockscreenElement(Smartspace.Weather.LargeClock, context)
                    }
                }
            }
        }
}
