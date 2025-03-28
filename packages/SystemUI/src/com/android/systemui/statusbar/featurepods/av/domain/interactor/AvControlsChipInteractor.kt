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

package com.android.systemui.statusbar.featurepods.vc.domain.interactor

import com.android.systemui.Flags
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.privacy.PrivacyItem
import com.android.systemui.privacy.PrivacyType
import com.android.systemui.shade.data.repository.PrivacyChipRepository
import com.android.systemui.statusbar.featurepods.vc.shared.model.AvControlsChipModel
import com.android.systemui.statusbar.featurepods.vc.shared.model.SensorActivityModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

/**
 * Interactor for managing the state of the video conference privacy chip in the status bar.
 *
 * Provides a [Flow] of [AvControlsChipModel] representing the current state of the media control
 * chip.
 *
 * This functionality is only enabled on large screen devices.
 */
@SysUISingleton
class AvControlsChipInteractor @Inject constructor(privacyChipRepository: PrivacyChipRepository) {
    private val isEnabled = MutableStateFlow(false)

    val model =
        combine(isEnabled, privacyChipRepository.privacyItems) { isEnabled, privacyItems ->
            if (isEnabled) createModel(privacyItems)
            else AvControlsChipModel(sensorActivityModel = SensorActivityModel.Inactive())
        }

    private fun createModel(privacyItems: List<PrivacyItem>): AvControlsChipModel {
        return AvControlsChipModel(
            sensorActivityModel =
                createSensorActivityModel(
                    cameraActive = privacyItems.any { it.privacyType == PrivacyType.TYPE_CAMERA },
                    microphoneActive =
                        privacyItems.any { it.privacyType == PrivacyType.TYPE_MICROPHONE },
                )
        )
    }

    private fun createSensorActivityModel(
        cameraActive: Boolean,
        microphoneActive: Boolean,
    ): SensorActivityModel =
        when {
            !cameraActive && microphoneActive ->
                SensorActivityModel.Active(SensorActivityModel.Active.Sensors.MICROPHONE)
            cameraActive && !microphoneActive ->
                SensorActivityModel.Active(SensorActivityModel.Active.Sensors.CAMERA)
            cameraActive && microphoneActive ->
                SensorActivityModel.Active(SensorActivityModel.Active.Sensors.CAMERA_AND_MICROPHONE)
            else -> SensorActivityModel.Inactive()
        }

    /**
     * The VC/Privacy control chip may not be enabled on all form factors, so only the relevant form
     * factors should initialize the interactor. This must be called from a CoreStartable.
     */
    fun initialize() {
        isEnabled.value = Flags.expandedPrivacyIndicatorsOnLargeScreen()
    }
}
