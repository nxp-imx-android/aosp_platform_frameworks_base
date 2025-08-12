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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.animation.scene.ContentScope
import com.android.systemui.keyguard.ui.composable.LockscreenTouchHandling
import com.android.systemui.keyguard.ui.composable.element.AmbientIndicationElement
import com.android.systemui.keyguard.ui.composable.element.AodNotificationIconsElementProvider
import com.android.systemui.keyguard.ui.composable.element.AodPromotedNotificationAreaElementProvider
import com.android.systemui.keyguard.ui.composable.element.ClockRegionElementProvider
import com.android.systemui.keyguard.ui.composable.element.IndicationAreaElement
import com.android.systemui.keyguard.ui.composable.element.LockElement
import com.android.systemui.keyguard.ui.composable.element.LockscreenElementFactoryImpl
import com.android.systemui.keyguard.ui.composable.element.LockscreenElementFactoryImpl.Companion.createRemembered
import com.android.systemui.keyguard.ui.composable.element.LockscreenUpperRegionElementProvider
import com.android.systemui.keyguard.ui.composable.element.MediaElementProvider
import com.android.systemui.keyguard.ui.composable.element.NotificationStackElementProvider
import com.android.systemui.keyguard.ui.composable.element.SettingsMenuElement
import com.android.systemui.keyguard.ui.composable.element.ShortcutElement
import com.android.systemui.keyguard.ui.composable.element.SmartspaceElementProvider
import com.android.systemui.keyguard.ui.composable.element.StatusBarElement
import com.android.systemui.keyguard.ui.composable.layout.LockscreenSceneLayout
import com.android.systemui.keyguard.ui.composable.modifier.burnInAware
import com.android.systemui.keyguard.ui.viewmodel.AodBurnInViewModel
import com.android.systemui.keyguard.ui.viewmodel.KeyguardClockViewModel
import com.android.systemui.keyguard.ui.viewmodel.LockscreenContentViewModel
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementContext
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys
import java.util.Optional
import javax.inject.Inject

/** Renders the lockscreen scene when showing a standard phone or tablet layout */
class DefaultBlueprint
@Inject
constructor(
    private val keyguardClockViewModel: KeyguardClockViewModel,
    private val aodBurnInViewModel: AodBurnInViewModel,
    private val statusBarElement: StatusBarElement,
    private val upperRegionElementProvider: LockscreenUpperRegionElementProvider,
    private val lockElement: LockElement,
    private val ambientIndicationElementOptional: Optional<AmbientIndicationElement>,
    private val shortcutElement: ShortcutElement,
    private val indicationAreaElement: IndicationAreaElement,
    private val settingsMenuElement: SettingsMenuElement,
    private val notificationStackElementProvider: NotificationStackElementProvider,
    private val aodNotificationIconElementProvider: AodNotificationIconsElementProvider,
    private val aodPromotedNotificationElementProvider: AodPromotedNotificationAreaElementProvider,
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
                upperRegionElementProvider,
                mediaElementProvider,
                smartspaceElementProvider,
                clockRegionElementProvider,
                notificationStackElementProvider,
                aodNotificationIconElementProvider,
                aodPromotedNotificationElementProvider,
                currentClock?.smallClock?.layout,
                currentClock?.largeClock?.layout,
            )

        val burnIn = rememberBurnIn(keyguardClockViewModel)
        val elementContext =
            LockscreenElementContext(
                scope = this,
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
                    }
                },
            )

        LockscreenTouchHandling(
            viewModelFactory = viewModel.touchHandlingFactory,
            modifier = modifier,
        ) { onSettingsMenuPlaced ->
            LockscreenSceneLayout(
                viewModel = viewModel.layout,
                elementFactory = elementFactory,
                elementContext = elementContext,
                statusBar = {
                    with(statusBarElement) { StatusBar(modifier = Modifier.fillMaxWidth()) }
                },
                lockIcon = { with(lockElement) { LockIcon() } },
                startShortcut = {
                    with(shortcutElement) {
                        Shortcut(
                            isStart = true,
                            applyPadding = false,
                            onTopChanged = viewModel::setShortcutTop,
                        )
                    }
                },
                ambientIndication = {
                    if (ambientIndicationElementOptional.isPresent) {
                        with(ambientIndicationElementOptional.get()) {
                            AmbientIndication(modifier = Modifier.fillMaxWidth())
                        }
                    }
                },
                bottomIndication = {
                    with(indicationAreaElement) {
                        IndicationArea(modifier = Modifier.fillMaxWidth())
                    }
                },
                endShortcut = {
                    with(shortcutElement) {
                        Shortcut(
                            isStart = false,
                            applyPadding = false,
                            onTopChanged = viewModel::setShortcutTop,
                        )
                    }
                },
                settingsMenu = {
                    with(settingsMenuElement) { SettingsMenu(onPlaced = onSettingsMenuPlaced) }
                },
            )
        }
    }
}
