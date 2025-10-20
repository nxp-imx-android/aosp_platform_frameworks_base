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

package com.android.systemui.statusbar.featurepods.assistant.domain.interactor

import android.content.res.Resources
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryInteractor
import com.android.systemui.res.R
import com.android.systemui.statusbar.featurepods.assistant.data.repository.AssistantRepository
import com.android.systemui.statusbar.featurepods.assistant.shared.model.AssistantIconSharedModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * An interactor that provides information about the assistant icon, such as whether it should be
 * shown and details about the current assistant application.
 */
interface AssistantIconInteractor {
    /**
     * A [StateFlow] that provides the [AssistantIconSharedModel] which contains information
     * necessary to display the assistant icon.
     *
     * The model is updated based on whether the device is entered and the current
     * [android.content.ComponentName] of the primary assistant app.
     */
    val assistantIconSharedModel: StateFlow<AssistantIconSharedModel>

    /** This invokes the assistant app. */
    fun startAssistant()
}

@SysUISingleton
class AssistantIconInteractorImpl
@Inject
constructor(
    @Main private val resources: Resources,
    @Background private val scope: CoroutineScope,
    deviceEntryInteractor: DeviceEntryInteractor,
    private val assistantRepository: AssistantRepository,
) : AssistantIconInteractor {
    override val assistantIconSharedModel: StateFlow<AssistantIconSharedModel> =
        combine(deviceEntryInteractor.isDeviceEntered, assistantRepository.assistInfo) {
                isDeviceEntered,
                assistInfo ->
                if (!isDeviceEntered || assistInfo == null || assistInfo.packageName == "") {
                    defaultSharedModel
                } else {
                    AssistantIconSharedModel(
                        assistInfo,
                        isStatusBarAssistantPackage =
                            assistInfo.packageName ==
                                resources.getString(R.string.config_statusBarAssistantPackage),
                        isAssistShown = false,
                    )
                }
            }
            .stateIn(
                scope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = defaultSharedModel,
            )

    override fun startAssistant() {
        assistantRepository.startAssistant()
    }

    private companion object {
        private val defaultSharedModel =
            AssistantIconSharedModel(
                assistInfo = null,
                isStatusBarAssistantPackage = false,
                isAssistShown = false,
            )
    }
}
