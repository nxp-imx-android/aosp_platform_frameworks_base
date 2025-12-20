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

package com.android.systemui.statusbar.quickactions.av.ui.viewmodel

import androidx.compose.runtime.getValue
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.statusbar.quickactions.av.domain.interactor.AvControlsChipInteractor
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.map

class SensorActivityViewModel
@AssistedInject
constructor(
    private val avControlsChipInteractor: AvControlsChipInteractor,
    @Assisted private val setCurrentPage: (PageType) -> Unit,
) : HydratedActivatable() {

    fun returnToMainPage() {
        setCurrentPage(PageType.MAIN)
    }

    fun enterDedicatedPage() {
        setCurrentPage(PageType.SENSOR_ACTIVITY)
    }

    /** List of apps using camera or microphone. */
    val sensorAccessList by
        avControlsChipInteractor.model
            .map { it.sensorAccessList }
            .hydratedStateOf(initialValue = listOf())

    fun closeApp(packageName: String) {
        avControlsChipInteractor.closeApp(packageName)
    }

    fun manageApp(packageName: String) {
        avControlsChipInteractor.manageApp(packageName)
    }

    fun openPrivacyDashboard() {
        avControlsChipInteractor.openPrivacyDashboard()
    }

    /** A factory to be used to create view model instances. */
    @AssistedFactory
    interface Factory {
        fun create(setCurrentPage: (PageType) -> Unit): SensorActivityViewModel
    }
}
