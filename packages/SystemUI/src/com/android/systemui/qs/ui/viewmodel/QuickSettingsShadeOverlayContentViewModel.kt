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

package com.android.systemui.qs.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.desktop.domain.interactor.DesktopInteractor
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.scene.domain.interactor.SceneInteractor
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.domain.interactor.ShadeInteractor
import com.android.systemui.shade.domain.interactor.ShadeModeInteractor
import com.android.systemui.shade.shared.model.ShadeMode
import com.android.systemui.statusbar.core.StatusBarForDesktop
import com.android.systemui.statusbar.notification.stack.domain.interactor.NotificationStackAppearanceInteractor
import com.android.systemui.statusbar.notification.stack.shared.model.ShadeScrimShape
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Models UI state used to render the content of the quick settings shade overlay.
 *
 * Different from [QuickSettingsShadeOverlayActionsViewModel], which only models user actions that
 * can be performed to navigate to other scenes.
 */
class QuickSettingsShadeOverlayContentViewModel
@AssistedInject
constructor(
    @Main private val mainDispatcher: CoroutineDispatcher,
    val desktopInteractor: DesktopInteractor,
    val shadeInteractor: ShadeInteractor,
    val shadeModeInteractor: ShadeModeInteractor,
    val sceneInteractor: SceneInteractor,
    val notificationStackAppearanceInteractor: NotificationStackAppearanceInteractor,
) : ExclusiveActivatable() {

    private val hydrator = Hydrator("QuickSettingsShadeOverlayContentViewModel.hydrator")

    /**
     * The Shade header can only be shown if desktop features are disabled. This is because the
     * status bar is always visible when desktop features are enabled.
     */
    val showHeader: Boolean by
        if (StatusBarForDesktop.isEnabled) {
            hydrator.hydratedStateOf(
                traceName = "showHeader",
                initialValue = !desktopInteractor.isDesktopFeatureSetEnabled.value,
                source = desktopInteractor.isDesktopFeatureSetEnabled.map { !it },
            )
        } else {
            mutableStateOf(true)
        }

    override suspend fun onActivated(): Nothing {
        coroutineScope {
            launch {
                shadeInteractor.isShadeTouchable
                    .distinctUntilChanged()
                    .filter { !it }
                    .collect {
                        withContext(mainDispatcher) {
                            shadeInteractor.collapseQuickSettingsShade(
                                loggingReason = "device became non-interactive"
                            )
                        }
                    }
            }

            launch {
                shadeModeInteractor.shadeMode.collect { shadeMode ->
                    withContext(mainDispatcher) {
                        val loggingReason = "Fold or rotate while on quick settings shade"
                        when (shadeMode) {
                            is ShadeMode.Single ->
                                sceneInteractor.snapToScene(Scenes.QuickSettings, loggingReason)

                            is ShadeMode.Split ->
                                sceneInteractor.snapToScene(Scenes.Shade, loggingReason)

                            is ShadeMode.Dual -> Unit // Standard case, nothing to do
                        }
                    }
                }
            }

            launch { hydrator.activate() }
        }

        awaitCancellation()
    }

    /** Notifies that the bounds of the QuickSettings panel have changed. */
    fun onPanelShapeChanged(shape: ShadeScrimShape?) {
        notificationStackAppearanceInteractor.sendQsPanelShape(shape)
    }

    fun onScrimClicked() {
        shadeInteractor.collapseQuickSettingsShade(loggingReason = "shade scrim clicked")
    }

    @AssistedFactory
    interface Factory {
        fun create(): QuickSettingsShadeOverlayContentViewModel
    }
}
