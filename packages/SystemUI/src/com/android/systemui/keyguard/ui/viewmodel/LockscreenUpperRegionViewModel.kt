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

package com.android.systemui.keyguard.ui.viewmodel

import androidx.compose.runtime.getValue
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryBypassInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardClockInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardTransitionInteractor
import com.android.systemui.keyguard.shared.model.ClockSize
import com.android.systemui.keyguard.shared.model.KeyguardState.AOD
import com.android.systemui.keyguard.ui.composable.layout.UnfoldTranslations
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.shade.domain.interactor.ShadeModeInteractor
import com.android.systemui.shade.shared.model.ShadeMode
import com.android.systemui.statusbar.notification.domain.interactor.ActiveNotificationsInteractor
import com.android.systemui.unfold.domain.interactor.UnfoldTransitionInteractor
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class LockscreenUpperRegionViewModel
@AssistedInject
constructor(
    private val clockInteractor: KeyguardClockInteractor,
    private val shadeModeInteractor: ShadeModeInteractor,
    private val keyguardTransitionInteractor: KeyguardTransitionInteractor,
    private val unfoldTransitionInteractor: UnfoldTransitionInteractor,
    private val deviceEntryBypassInteractor: DeviceEntryBypassInteractor,
    private val keyguardMediaViewModelFactory: KeyguardMediaViewModel.Factory,
    private val activeNotificationsInteractor: ActiveNotificationsInteractor,
) : ExclusiveActivatable() {
    private val hydrator = Hydrator("LockscreenUpperRegionViewModel.hydrator")
    private val keyguardMediaViewModel: KeyguardMediaViewModel by lazy {
        keyguardMediaViewModelFactory.create()
    }

    val isMediaVisible: Boolean
        get() = keyguardMediaViewModel.isMediaVisible

    val isNotificationsVisible: Boolean by
        hydrator.hydratedStateOf(
            traceName = "isNotificationsVisible",
            source = activeNotificationsInteractor.areAnyNotificationsPresent,
            initialValue = activeNotificationsInteractor.areAnyNotificationsPresentValue,
        )

    val isOnAOD: Boolean by
        hydrator.hydratedStateOf(
            traceName = "isOnAOD",
            source =
                keyguardTransitionInteractor
                    .transitionValue(AOD)
                    .map { it == 1f }
                    .distinctUntilChanged(),
            initialValue = false,
        )

    val unfoldTranslations: UnfoldTranslations =
        object : UnfoldTranslations {
            override val start: Float by
                hydrator.hydratedStateOf(
                    traceName = "unfoldTranslations.start",
                    initialValue = 0f,
                    source = unfoldTransitionInteractor.unfoldTranslationX(isOnStartSide = true),
                )

            override val end: Float by
                hydrator.hydratedStateOf(
                    traceName = "unfoldTranslations.end",
                    initialValue = 0f,
                    source = unfoldTransitionInteractor.unfoldTranslationX(isOnStartSide = false),
                )
        }

    val shadeMode: ShadeMode by
        hydrator.hydratedStateOf(
            traceName = "shadeMode",
            source = shadeModeInteractor.shadeMode,
            initialValue = shadeModeInteractor.shadeMode.value,
        )

    val clockSize: ClockSize by
        hydrator.hydratedStateOf(
            traceName = "clockSize",
            source = clockInteractor.clockSize,
            initialValue = clockInteractor.clockSize.value,
        )

    override suspend fun onActivated(): Nothing {
        coroutineScope {
            launch { hydrator.activate() }
            launch { keyguardMediaViewModel.activate() }
            awaitCancellation()
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(): LockscreenUpperRegionViewModel
    }
}
