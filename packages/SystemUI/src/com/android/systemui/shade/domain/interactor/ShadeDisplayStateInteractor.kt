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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Provides the bits of [DisplayStateInteractor] that are relevant for the shade.
 *
 * This is needed as the shade can change display, and we want to use the correct
 * [DisplayStateInteractor] (as there is a different instance per display).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@SysUISingleton
class ShadeDisplayStateInteractor
@Inject
constructor(
    @Application applicationScope: CoroutineScope,
    displayStateInteractor: DisplayStateInteractor,
    shadeDisplaysInteractor: Lazy<ShadeDisplaysInteractor>,
    private val displaySubcomponentRepository: PerDisplayRepository<SystemUIDisplaySubcomponent>,
) {
    private val shadeDisplayInteractor: Flow<DisplayStateInteractor> =
        if (ShadeWindowGoesAround.isEnabled) {
            shadeDisplaysInteractor
                .get()
                .displayId
                .map { displayId ->
                    val displaySpecificInteractor =
                        displaySubcomponentRepository[displayId]?.displayStateInteractor
                    if (displaySpecificInteractor != null) {
                        displaySpecificInteractor
                    } else {
                        Log.e(TAG, "Couldn't get displayStateInteractor for display $displayId. ")
                        displayStateInteractor
                    }
                }
                .traceEach("$TAG#shadeDisplayInteractor", logcat = true)
        } else {
            flowOf(displayStateInteractor)
        }

    /** @see DisplayStateInteractor.isWideScreen */
    val isWideScreen: StateFlow<Boolean> =
        shadeDisplayInteractor
            .flatMapLatest { it.isWideScreen }
            .traceEach("$TAG#isWideScreen", logcat = true)
            .stateIn(
                applicationScope,
                SharingStarted.Eagerly,
                initialValue = displayStateInteractor.isWideScreen.value,
            )

    /** @see DisplayStateInteractor.isLargeScreen */
    val isLargeScreen: StateFlow<Boolean> =
        shadeDisplayInteractor
            .flatMapLatest { it.isLargeScreen }
            .traceEach("$TAG#isLargeScreen", logcat = true)
            .stateIn(
                applicationScope,
                SharingStarted.Eagerly,
                initialValue = displayStateInteractor.isLargeScreen.value,
            )

    private companion object {
        const val TAG = "ShadeDisplayStateInteractor"
    }
}
