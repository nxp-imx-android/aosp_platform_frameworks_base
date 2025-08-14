/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.keyguard.ui.composable.blueprint

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.animation.scene.ContentScope
import com.android.systemui.keyguard.ui.composable.LockscreenTouchHandling
import com.android.systemui.keyguard.ui.composable.elements.AodNotificationIconsElementProvider
import com.android.systemui.keyguard.ui.composable.elements.AodPromotedNotificationAreaElementProvider
import com.android.systemui.keyguard.ui.composable.elements.ClockRegionElementProvider
import com.android.systemui.keyguard.ui.composable.elements.IndicationAreaElementProvider
import com.android.systemui.keyguard.ui.composable.elements.LockIconElementProvider
import com.android.systemui.keyguard.ui.composable.elements.LockscreenElementFactoryImpl
import com.android.systemui.keyguard.ui.composable.elements.LockscreenElementFactoryImpl.Companion.createRemembered
import com.android.systemui.keyguard.ui.composable.elements.LockscreenLowerRegionElementProvider
import com.android.systemui.keyguard.ui.composable.elements.LockscreenUpperRegionElementProvider
import com.android.systemui.keyguard.ui.composable.elements.MediaElementProvider
import com.android.systemui.keyguard.ui.composable.elements.NotificationStackElementProvider
import com.android.systemui.keyguard.ui.composable.elements.OEMElementProvider
import com.android.systemui.keyguard.ui.composable.elements.SettingsMenuElementProvider
import com.android.systemui.keyguard.ui.composable.elements.ShortcutElementProvider
import com.android.systemui.keyguard.ui.composable.elements.SmartspaceElementProvider
import com.android.systemui.keyguard.ui.composable.elements.StatusBarElementProvider
import com.android.systemui.keyguard.ui.composable.layout.LockscreenSceneLayout
import com.android.systemui.keyguard.ui.composable.modifier.burnInAware
import com.android.systemui.keyguard.ui.viewmodel.AodBurnInViewModel
import com.android.systemui.keyguard.ui.viewmodel.KeyguardClockViewModel
import com.android.systemui.keyguard.ui.viewmodel.LockscreenContentViewModel
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementContext
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys
import javax.inject.Inject

/** Renders the lockscreen scene when showing a standard phone or tablet layout */
class DefaultBlueprint
@Inject
constructor(
    private val keyguardClockViewModel: KeyguardClockViewModel,
    private val aodBurnInViewModel: AodBurnInViewModel,
    private val statusBarElementProvider: StatusBarElementProvider,
    private val upperRegionElementProvider: LockscreenUpperRegionElementProvider,
    private val notificationStackElementProvider: NotificationStackElementProvider,
    private val aodNotificationIconElementProvider: AodNotificationIconsElementProvider,
    private val aodPromotedNotificationElementProvider: AodPromotedNotificationAreaElementProvider,
    private val lowerRegionElementProvider: LockscreenLowerRegionElementProvider,
    private val lockIconElementProvider: LockIconElementProvider,
    private val oemElementProviders: Set<@JvmSuppressWildcards OEMElementProvider>,
    private val shortcutElementProvider: ShortcutElementProvider,
    private val indicationAreaElementProvider: IndicationAreaElementProvider,
    private val settingsMenuElementProvider: SettingsMenuElementProvider,
    private val smartspaceElementProvider: SmartspaceElementProvider,
    private val clockRegionElementProvider: ClockRegionElementProvider,
    private val mediaElementProvider: MediaElementProvider,
    private val elementFactoryBuilder: LockscreenElementFactoryImpl.Builder,
) : ComposableLockscreenSceneBlueprint {

    override val id: String = "default"

    @Composable
    override fun ContentScope.Content(viewModel: LockscreenContentViewModel, modifier: Modifier) {
        val currentClock by keyguardClockViewModel.currentClock.collectAsStateWithLifecycle()
        val elementFactory =
            elementFactoryBuilder.createRemembered(
                mediaElementProvider,
                lockIconElementProvider,
                shortcutElementProvider,
                statusBarElementProvider,
                smartspaceElementProvider,
                upperRegionElementProvider,
                lowerRegionElementProvider,
                clockRegionElementProvider,
                settingsMenuElementProvider,
                indicationAreaElementProvider,
                notificationStackElementProvider,
                aodNotificationIconElementProvider,
                aodPromotedNotificationElementProvider,
                currentClock?.smallClock?.layout,
                currentClock?.largeClock?.layout,
                *oemElementProviders.toTypedArray(),
            )

        val burnIn = rememberBurnIn(keyguardClockViewModel)

        LockscreenTouchHandling(
            viewModelFactory = viewModel.touchHandlingFactory,
            modifier = modifier,
        ) { onSettingsMenuPlaced ->
            val elementContext =
                LockscreenElementContext(
                    scope = this@Content,
                    burnInModifier =
                        Modifier.burnInAware(
                            viewModel = aodBurnInViewModel,
                            params = burnIn.parameters,
                        ),
                    onElementPositioned = { key, rect ->
                        when (key) {
                            LockscreenElementKeys.Clock.Small -> {
                                burnIn.onSmallClockTopChanged(rect.top)
                                viewModel.setSmallClockBottom(rect.bottom)
                            }
                            LockscreenElementKeys.Smartspace.Cards -> {
                                burnIn.onSmartspaceTopChanged(rect.top)
                                viewModel.setSmartspaceCardBottom(rect.bottom)
                            }
                            LockscreenElementKeys.MediaCarousel -> {
                                viewModel.setMediaPlayerBottom(rect.bottom)
                            }
                            LockscreenElementKeys.Shortcuts.Start -> {
                                viewModel.setShortcutTop(rect.top)
                            }
                            LockscreenElementKeys.Shortcuts.End -> {
                                viewModel.setShortcutTop(rect.top)
                            }
                            LockscreenElementKeys.SettingsMenu -> {
                                onSettingsMenuPlaced(rect)
                            }
                        }
                    },
                )

            LockscreenSceneLayout(elementFactory = elementFactory, elementContext = elementContext)
        }
    }
}
