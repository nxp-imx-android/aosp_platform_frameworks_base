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

package com.android.systemui.statusbar.featurepods.av.ui.viewmodel

import androidx.compose.runtime.getValue
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.statusbar.featurepods.popups.ui.model.ChipIcon
import com.android.systemui.statusbar.featurepods.popups.ui.model.PopupChipId
import com.android.systemui.statusbar.featurepods.popups.ui.model.PopupChipModel
import com.android.systemui.statusbar.featurepods.popups.ui.viewmodel.StatusBarPopupChipViewModel
import com.android.systemui.statusbar.featurepods.vc.domain.interactor.AvControlsChipInteractor
import com.android.systemui.statusbar.featurepods.vc.shared.model.AvControlsChipModel
import com.android.systemui.statusbar.featurepods.vc.shared.model.SensorActivityModel
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.map

/** ViewModel for the VC Privacy Chip */
class AvControlsChipViewModel
@AssistedInject
constructor(avControlsChipInteractor: AvControlsChipInteractor) :
    StatusBarPopupChipViewModel, ExclusiveActivatable() {
    private val hydrator: Hydrator = Hydrator("AvControlsChipViewModel.hydrator")

    override val chip: PopupChipModel by
        hydrator.hydratedStateOf(
            traceName = "chip",
            initialValue = PopupChipModel.Hidden(PopupChipId.AvControlsIndicator),
            source = avControlsChipInteractor.model.map { toPopupChipModel(it) },
        )

    override suspend fun onActivated(): Nothing {
        hydrator.activate()
    }

    private fun toPopupChipModel(avControlsChipModel: AvControlsChipModel): PopupChipModel {
        val chipId = PopupChipId.AvControlsIndicator
        val sensorActivityModel = avControlsChipModel.sensorActivityModel
        return when (sensorActivityModel) {
            is SensorActivityModel.Inactive -> PopupChipModel.Hidden(chipId)
            is SensorActivityModel.Active ->
                PopupChipModel.Shown(
                    // TODO: Pass in color when the api supports it
                    chipId = chipId,
                    icons = listOf(ChipIcon(icon(sensorActivityModel = sensorActivityModel))),
                    chipText = chipText(sensorActivityModel = sensorActivityModel),
                )
        }
    }

    private fun icon(sensorActivityModel: SensorActivityModel.Active): Icon {
        val imageRes =
            when (sensorActivityModel.sensors) {
                SensorActivityModel.Active.Sensors.CAMERA ->
                    com.android.internal.R.drawable.perm_group_camera
                SensorActivityModel.Active.Sensors.MICROPHONE ->
                    com.android.internal.R.drawable.perm_group_microphone
                // TODO(405903665): Pass both camera and microphone icons when it is supported.
                SensorActivityModel.Active.Sensors.CAMERA_AND_MICROPHONE ->
                    com.android.internal.R.drawable.perm_group_camera
            }
        return Icon.Resource(res = imageRes, contentDescription = null)
    }

    // TODO(405903665): Remove text after api change
    private fun chipText(sensorActivityModel: SensorActivityModel.Active): String {
        return when (sensorActivityModel.sensors) {
            SensorActivityModel.Active.Sensors.CAMERA_AND_MICROPHONE -> "Cam & Mic"
            SensorActivityModel.Active.Sensors.CAMERA -> "Camera"
            SensorActivityModel.Active.Sensors.MICROPHONE -> "Microphone"
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(): AvControlsChipViewModel
    }
}
