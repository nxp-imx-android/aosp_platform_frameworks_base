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

package com.android.systemui.statusbar.ui.viewmodel

import com.android.systemui.Flags
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.desktop.domain.interactor.DesktopInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.scene.domain.interactor.SceneInteractor
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.statusbar.domain.interactor.KeyguardStatusBarInteractor
import com.android.systemui.statusbar.policy.BatteryController
import com.android.systemui.user.domain.interactor.UserLogoutInteractor
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * A view model for the status bar displayed on keyguard (lockscreen).
 *
 * Note: This view model is for the status bar view as a whole. Certain icons may have their own
 * individual view models, such as
 * [com.android.systemui.statusbar.pipeline.wifi.ui.viewmodel.WifiViewModel] or
 * [com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.MobileIconsViewModel].
 */
class KeyguardStatusBarViewModel
@Inject
constructor(
    @Application scope: CoroutineScope,
    desktopInteractor: DesktopInteractor,
    sceneInteractor: SceneInteractor,
    private val keyguardInteractor: KeyguardInteractor,
    keyguardStatusBarInteractor: KeyguardStatusBarInteractor,
    private val userLogoutInteractor: UserLogoutInteractor,
    batteryController: BatteryController,
) : HydratedActivatable(enableEnqueuedActivations = true) {
    /** True if this view should be visible and false otherwise. */
    val isVisible: StateFlow<Boolean> =
        combine(
                desktopInteractor.useDesktopStatusBar,
                sceneInteractor.currentScene,
                sceneInteractor.currentOverlays,
                keyguardInteractor.isDozing,
            ) { useDesktopStatusBar, currentScene, currentOverlays, isDozing ->
                !useDesktopStatusBar &&
                    (currentScene == Scenes.Lockscreen || currentScene == Scenes.Communal) &&
                    Overlays.NotificationsShade !in currentOverlays &&
                    Overlays.QuickSettingsShade !in currentOverlays &&
                    Overlays.Bouncer !in currentOverlays &&
                    !isDozing
            }
            .stateIn(scope, SharingStarted.WhileSubscribed(), false)

    /** True if we can show the user switcher on keyguard and false otherwise. */
    val isKeyguardUserSwitcherEnabled: Flow<Boolean> =
        keyguardStatusBarInteractor.isKeyguardUserSwitcherEnabled

    val isSignOutButtonEnabled: Boolean
        get() =
            Flags.signOutButtonOnKeyguardStatusBar() &&
                keyguardInteractor.isSignOutButtonOnStatusBarEnabled

    val isSignOutButtonVisible: Boolean by
        userLogoutInteractor.isLogoutToSystemUserEnabled.hydratedStateOf()

    fun onSignOut() {
        enqueueOnActivatedScope { userLogoutInteractor.logOutToSystemUser() }
    }
}
