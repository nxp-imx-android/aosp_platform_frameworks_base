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
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.animation.scene.ContentScope
import com.android.compose.modifiers.padding
import com.android.systemui.keyguard.KeyguardUnlockAnimationController
import com.android.systemui.keyguard.ui.viewmodel.AodBurnInViewModel
import com.android.systemui.keyguard.ui.viewmodel.KeyguardSmartspaceViewModel
import com.android.systemui.plugins.clocks.LockscreenElement
import com.android.systemui.plugins.clocks.LockscreenElementContext
import com.android.systemui.plugins.clocks.LockscreenElementFactory
import com.android.systemui.plugins.clocks.LockscreenElementKeys
import com.android.systemui.plugins.clocks.LockscreenElementProvider
import com.android.systemui.res.R
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.statusbar.lockscreen.LockscreenSmartspaceController
import javax.inject.Inject

class SmartspaceElementProvider
@Inject
constructor(
    @ShadeDisplayAware private val context: Context,
    private val lockscreenSmartspaceController: LockscreenSmartspaceController,
    private val keyguardUnlockAnimationController: KeyguardUnlockAnimationController,
    private val keyguardSmartspaceViewModel: KeyguardSmartspaceViewModel,
    private val aodBurnInViewModel: AodBurnInViewModel,
) : LockscreenElementProvider {
    override val elements by lazy {
        listOf(
            dateElement,
            weatherElement,
            dateAndWeatherVerticalElement,
            dateAndWeatherHorizontalElement,
            cardsElement,
        )
    }

    private val dateElement =
        object : LockscreenElement {
            override val key = LockscreenElementKeys.SmartspaceDate
            override val context = this@SmartspaceElementProvider.context

            @Composable
            override fun ContentScope.LockscreenElement(
                factory: LockscreenElementFactory,
                context: LockscreenElementContext,
            ) {
                val isVisible =
                    keyguardSmartspaceViewModel.isDateVisible.collectAsStateWithLifecycle()
                if (!isVisible.value || !keyguardSmartspaceViewModel.isSmartspaceEnabled) {
                    return
                }

                AndroidView(
                    factory = { context ->
                        wrapView(context) { frame ->
                            lockscreenSmartspaceController.buildAndConnectDateView(frame, false)
                        }
                    }
                )
            }
        }

    private val weatherElement =
        object : LockscreenElement {
            override val key = LockscreenElementKeys.SmartspaceWeather
            override val context = this@SmartspaceElementProvider.context

            @Composable
            override fun ContentScope.LockscreenElement(
                factory: LockscreenElementFactory,
                context: LockscreenElementContext,
            ) {
                val isVisible =
                    keyguardSmartspaceViewModel.isWeatherVisible.collectAsStateWithLifecycle()
                if (!isVisible.value || !keyguardSmartspaceViewModel.isSmartspaceEnabled) {
                    return
                }

                AndroidView(
                    factory = { context ->
                        wrapView(context) { frame ->
                            lockscreenSmartspaceController.buildAndConnectWeatherView(frame, false)
                        }
                    }
                )
            }
        }

    private val dateAndWeatherHorizontalElement =
        object : LockscreenElement {
            override val key = LockscreenElementKeys.SmartspaceDateWeatherHorizontal
            override val context = this@SmartspaceElementProvider.context

            @Composable
            override fun ContentScope.LockscreenElement(
                factory: LockscreenElementFactory,
                context: LockscreenElementContext,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    factory.lockscreenElement(LockscreenElementKeys.SmartspaceDate, context)
                    factory.lockscreenElement(LockscreenElementKeys.SmartspaceWeather, context)
                }
            }
        }

    private val dateAndWeatherVerticalElement =
        object : LockscreenElement {
            override val key = LockscreenElementKeys.SmartspaceDateWeatherVertical
            override val context = this@SmartspaceElementProvider.context

            @Composable
            override fun ContentScope.LockscreenElement(
                factory: LockscreenElementFactory,
                context: LockscreenElementContext,
            ) {
                Column(
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    factory.lockscreenElement(LockscreenElementKeys.SmartspaceDate, context)
                    factory.lockscreenElement(LockscreenElementKeys.SmartspaceWeather, context)
                }
            }
        }

    private val cardsElement =
        object : LockscreenElement {
            override val key = LockscreenElementKeys.SmartspaceCards
            override val context = this@SmartspaceElementProvider.context

            @Composable
            override fun ContentScope.LockscreenElement(
                factory: LockscreenElementFactory,
                context: LockscreenElementContext,
            ) {
                if (!keyguardSmartspaceViewModel.isSmartspaceEnabled) {
                    return
                }

                // TODO(b/432451019): Reserve card space even if no cards are currently visible
                // TODO(b/432451019): Placement/positional modifiers need an implementation
                Column(
                    modifier =
                        Modifier
                            // .onTopPlacementChanged(onTopChanged)
                            .padding(
                                // top = { smartSpacePaddingTop(LocalResources.current) },
                                bottom =
                                    dimensionResource(R.dimen.keyguard_status_view_bottom_margin)
                            )
                ) {
                    AndroidView(
                        factory = { context ->
                            wrapView(context) { frame ->
                                val view = lockscreenSmartspaceController.buildAndConnectView(frame)
                                keyguardUnlockAnimationController.lockscreenSmartspace = view
                                view
                            }
                        },
                        onRelease = {
                            keyguardUnlockAnimationController.lockscreenSmartspace = null
                        },
                        modifier =
                            Modifier.fillMaxWidth()
                                .padding(
                                    horizontal = dimensionResource(R.dimen.below_clock_padding_end)
                                )
                                .then(context.burnInModifier),
                        // .onGloballyPositioned { coordinates ->
                        //    onBottomChanged?.invoke(coordinates.boundsInWindow().bottom)
                        // },
                    )
                }
            }
        }

    companion object {
        private fun wrapView(context: Context, builder: (FrameLayout) -> View?): View {
            return FrameLayout(context).apply {
                builder(this)?.let { view ->
                    view.layoutParams =
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                        )
                    addView(view)
                }
            }
        }
    }
}
