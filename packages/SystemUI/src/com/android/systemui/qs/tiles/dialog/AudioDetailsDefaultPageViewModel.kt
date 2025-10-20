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

package com.android.systemui.qs.tiles.dialog

import android.media.AudioManager
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.android.settingslib.volume.shared.model.AudioStream
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.volume.panel.component.volume.domain.model.SliderType
import com.android.systemui.volume.panel.component.volume.slider.ui.viewmodel.AudioStreamSliderViewModel
import com.android.systemui.volume.panel.ui.viewmodel.ComponentState
import com.android.systemui.volume.panel.ui.viewmodel.VolumePanelViewModel
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AudioDetailsDefaultPageViewModel
@AssistedInject
constructor(
    private val volumePanelViewModelFactory: VolumePanelViewModel.Factory,
    private val audioStreamSliderViewModelFactory: AudioStreamSliderViewModel.Factory,
) : AudioDetailsViewModel.ContentViewModel, HydratedActivatable() {
    private var volumePanelViewModel: VolumePanelViewModel? by mutableStateOf(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val footerComponents: List<ComponentState>? by
        snapshotFlow { volumePanelViewModel }
            .flatMapLatest { it?.componentsLayout ?: flowOf(null) }
            .map { layout -> layout?.footerComponents }
            .hydratedStateOf(traceName = "footerComponents", initialValue = null)

    val volumeComponentsFactory by derivedStateOf { volumePanelViewModel?.componentsFactory }

    val volumePanelState by derivedStateOf { volumePanelViewModel?.volumePanelState }

    var volumeSliderViewModel: AudioStreamSliderViewModel? = null

    override suspend fun onActivated(): Nothing {
        coroutineScope {
            launch {
                if (volumePanelViewModel == null) {
                    volumePanelViewModel = volumePanelViewModelFactory.create(this)
                    volumeSliderViewModel =
                        audioStreamSliderViewModelFactory.create(
                            AudioStreamSliderViewModel.FactoryAudioStreamWrapper(
                                SliderType.Stream(AudioStream(AudioManager.STREAM_MUSIC)).stream
                            ),
                            this,
                        )
                }
            }
        }
        awaitCancellation()
    }

    @AssistedFactory
    fun interface Factory {
        fun create(): AudioDetailsDefaultPageViewModel
    }
}
