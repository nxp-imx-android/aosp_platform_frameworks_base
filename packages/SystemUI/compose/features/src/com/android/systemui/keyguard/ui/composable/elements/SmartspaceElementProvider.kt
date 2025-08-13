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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.ElementKey
import com.android.compose.modifiers.padding
import com.android.systemui.keyguard.KeyguardUnlockAnimationController
import com.android.systemui.keyguard.ui.viewmodel.AodBurnInViewModel
import com.android.systemui.keyguard.ui.viewmodel.KeyguardSmartspaceViewModel
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElement
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementContext
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementFactory
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys.Smartspace
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementProvider
import com.android.systemui.res.R
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.statusbar.lockscreen.LockscreenSmartspaceController
import javax.inject.Inject
import kotlin.collections.List

class SmartspaceElementProvider
@Inject
constructor(
    @ShadeDisplayAware private val context: Context,
    private val smartspaceController: LockscreenSmartspaceController,
    private val keyguardUnlockAnimationController: KeyguardUnlockAnimationController,
    private val keyguardSmartspaceViewModel: KeyguardSmartspaceViewModel,
    private val aodBurnInViewModel: AodBurnInViewModel,
) : LockscreenElementProvider {
    override val elements: List<LockscreenElement> by lazy {
        listOf(
            DateElement(Smartspace.Date.LargeClock, isLargeClock = true),
            DateElement(Smartspace.Date.SmallClock, isLargeClock = false),
            WeatherElement(Smartspace.Weather.LargeClock, isLargeClock = true),
            WeatherElement(Smartspace.Weather.SmallClock, isLargeClock = false),
            cardsElement,
        )
    }

    private inner class DateElement(
        override val key: ElementKey,
        private val isLargeClock: Boolean,
    ) : LockscreenElement {
        override val context = this@SmartspaceElementProvider.context

        @Composable
        override fun ContentScope.LockscreenElement(
            factory: LockscreenElementFactory,
            context: LockscreenElementContext,
        ) {
            val isDateEnabled = keyguardSmartspaceViewModel.isDateEnabled
            if (!keyguardSmartspaceViewModel.isSmartspaceEnabled || !isDateEnabled) {
                return
            }

            AndroidView(
                factory = { ctx ->
                    smartspaceController.buildAndConnectDateView(ctx, isLargeClock)!!
                }
            )
        }
    }

    private inner class WeatherElement(
        override val key: ElementKey,
        private val isLargeClock: Boolean,
    ) : LockscreenElement {
        override val context = this@SmartspaceElementProvider.context

        @Composable
        override fun ContentScope.LockscreenElement(
            factory: LockscreenElementFactory,
            context: LockscreenElementContext,
        ) {
            val isWeatherEnabled: Boolean by
                keyguardSmartspaceViewModel.isWeatherEnabled.collectAsStateWithLifecycle(false)
            if (!keyguardSmartspaceViewModel.isSmartspaceEnabled || !isWeatherEnabled) {
                return
            }

            AndroidView(
                factory = { ctx ->
                    smartspaceController.buildAndConnectWeatherView(ctx, isLargeClock)!!
                }
            )
        }
    }

    private val cardsElement =
        object : LockscreenElement {
            override val key = Smartspace.Cards
            override val context = this@SmartspaceElementProvider.context

            @Composable
            override fun ContentScope.LockscreenElement(
                factory: LockscreenElementFactory,
                context: LockscreenElementContext,
            ) {
                if (!keyguardSmartspaceViewModel.isSmartspaceEnabled) {
                    return
                }

                AndroidView(
                    factory = { ctx ->
                        val view = smartspaceController.buildAndConnectView(ctx)!!
                        keyguardUnlockAnimationController.lockscreenSmartspace = view
                        view
                    },
                    onRelease = { view ->
                        if (keyguardUnlockAnimationController.lockscreenSmartspace == view) {
                            keyguardUnlockAnimationController.lockscreenSmartspace = null
                        }
                    },
                    modifier =
                        Modifier
                            // TODO(b/432451019): Constrain in wide layout to half width
                            .fillMaxWidth()
                            .padding(
                                // Note: smartspace adds 16dp of start padding internally
                                start = 12.dp,
                                bottom =
                                    dimensionResource(R.dimen.keyguard_status_view_bottom_margin),
                            )
                            .then(context.burnInModifier),
                )
            }
        }
}
