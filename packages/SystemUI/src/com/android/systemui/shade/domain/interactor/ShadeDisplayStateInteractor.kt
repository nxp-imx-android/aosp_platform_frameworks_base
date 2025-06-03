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

package com.android.systemui.shade.domain.interactor

import android.util.Log
import com.android.app.displaylib.PerDisplayRepository
import com.android.app.tracing.FlowTracing.traceEach
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent
import com.android.systemui.display.domain.interactor.DisplayStateInteractor
import com.android.systemui.shade.shared.flag.ShadeWindowGoesAround
import dagger.Lazy
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * Provides the bits of [DisplayStateInteractor] that are relevant for the shade.
 *
 * This is needed as the shade can change display, and we want to use the correct
 * [DisplayStateInteractor] (as there is a different instance per display).
 */
@SysUISingleton
class ShadeDisplayStateInteractor
@Inject
constructor(
    @Application private val applicationScope: CoroutineScope,
    private val displayStateInteractor: DisplayStateInteractor,
    shadeDisplaysInteractor: Lazy<ShadeDisplaysInteractor>,
    private val displaySubcomponentRepository: PerDisplayRepository<SystemUIDisplaySubcomponent>,
) {
    val isWideScreen: StateFlow<Boolean> =
        if (ShadeWindowGoesAround.isEnabled) {
            shadeDisplaysInteractor
                .get()
                .displayId
                .flatMapLatest { displayId -> getIsWideScreenFlowForDisplay(displayId) }
                .distinctUntilChanged()
                .traceEach("$TAG#isWideScreen", logcat = true)
                .stateIn(
                    applicationScope,
                    SharingStarted.Eagerly,
                    initialValue = displayStateInteractor.isWideScreen.value,
                )
        } else {
            displayStateInteractor.isWideScreen
        }

    private fun getIsWideScreenFlowForDisplay(displayId: Int): StateFlow<Boolean> {
        val displaySpecificInteractor =
            displaySubcomponentRepository[displayId]?.displayStateInteractor
        return if (displaySpecificInteractor != null) {
            displaySpecificInteractor.isWideScreen
        } else {
            Log.e(
                TAG,
                "Couldn't get displayStateInteractor for display $displayId. " +
                    "\"isWideScreen\" might be wrong.",
            )
            displayStateInteractor.isWideScreen
        }
    }

    private companion object {
        const val TAG = "ShadeDisplayStateInteractor"
    }
}
