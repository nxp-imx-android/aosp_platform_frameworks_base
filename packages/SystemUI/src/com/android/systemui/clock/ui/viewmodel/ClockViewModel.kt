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

package com.android.systemui.clock.ui.viewmodel

import androidx.compose.runtime.getValue
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.systemui.clock.domain.interactor.ClockInteractor
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.mapLatest

/** Models UI state for the clock. */
@OptIn(ExperimentalCoroutinesApi::class)
class ClockViewModel @AssistedInject constructor(clockInteractor: ClockInteractor) :
    ExclusiveActivatable() {
    private val hydrator = Hydrator("ClockViewModel.hydrator")

    val clockText: String by
        hydrator.hydratedStateOf(
            traceName = "clockText",
            initialValue = clockInteractor.currentTime.value.toString(),
            source = clockInteractor.currentTime.mapLatest { time -> time.toString() },
        )

    override suspend fun onActivated(): Nothing {
        coroutineScope {
            launch { hydrator.activate() }

            awaitCancellation()
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(): ClockViewModel
    }
}
