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
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.privacy.PrivacyType
import com.android.systemui.shade.data.repository.PrivacyChipRepository
import com.android.systemui.statusbar.data.repository.StatusBarModeRepositoryStore
import com.android.systemui.statusbar.featurepods.vc.shared.model.AvControlsChipModel
import com.android.systemui.statusbar.featurepods.vc.shared.model.SensorActivityModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Interactor for managing the state of the video conference privacy chip in the status bar.
 *
 * Provides a [Flow] of [AvControlsChipModel] representing the current state of the media control
 * chip.
 *
 * This functionality is only enabled on large screen devices.
 */
interface AvControlsChipInteractor {
    /** Encodes whether the feature is enabled. */
    val isEnabled: StateFlow<Boolean>

    /** Chip updates. */
    val model: StateFlow<AvControlsChipModel>

    /** Whether the display of the privacy dot should be suppressed to avoid duplicity. */
    val isShowingAvChip: StateFlow<Boolean>

    /** Initializes the feature. */
    fun initialize()
}

class AvControlsChipInteractorImpl
@Inject
constructor(
    @Background backgroundScope: CoroutineScope,
    private val privacyChipRepository: PrivacyChipRepository,
    statusBarModeRepositoryStore: StatusBarModeRepositoryStore,
) : AvControlsChipInteractor {
    private val _isEnabled = MutableStateFlow(false)
    override val isEnabled = _isEnabled.asStateFlow()
    override val model: StateFlow<AvControlsChipModel> =
        isEnabled
            .flatMapLatest { isEnabled ->
                if (isEnabled) {
                    privacyChipRepository.privacyItems.map { privacyItems ->
                        AvControlsChipModel(
                            sensorActivityModel =
                                createSensorActivityModel(
                                    cameraActive =
                                        privacyItems.any {
                                            it.privacyType == PrivacyType.TYPE_CAMERA
                                        },
                                    microphoneActive =
                                        privacyItems.any {
                                            it.privacyType == PrivacyType.TYPE_MICROPHONE
                                        },
                                )
                        )
                    }
                } else {
                    flowOf(AvControlsChipModel(sensorActivityModel = SensorActivityModel.Inactive))
                }
            }
            .stateIn(
                scope = backgroundScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue =
                    AvControlsChipModel(sensorActivityModel = SensorActivityModel.Inactive),
            )

    override val isShowingAvChip: StateFlow<Boolean> =
        isEnabled
            .flatMapLatest { isEnabled ->
                if (isEnabled) {
                    combine(
                        model,
                        statusBarModeRepositoryStore.defaultDisplay.isInFullscreenMode,
                    ) { chipModel: AvControlsChipModel, isInFullscreenMode: Boolean ->
                        when (chipModel.sensorActivityModel) {
                            is SensorActivityModel.Inactive -> false
                            is SensorActivityModel.Active -> !isInFullscreenMode
                        }
                    }
                } else {
                    flowOf(false)
                }
            }
            .stateIn(
                scope = backgroundScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = false,
            )

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

            else -> SensorActivityModel.Inactive
        }

    /**
     * The VC/Privacy control chip may not be enabled on all form factors, so only the relevant form
     * factors should initialize the interactor. This must be called from a CoreStartable.
     */
    override fun initialize() {
        _isEnabled.value = Flags.expandedPrivacyIndicatorsOnLargeScreen()
    }
}
