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

package com.android.systemui.qs.ui.viewmodel

import android.view.Display
import androidx.compose.runtime.getValue
import com.android.systemui.brightness.ui.viewmodel.BrightnessSliderViewModel
import com.android.systemui.display.data.repository.DisplayTypeRepository
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.media.controls.domain.pipeline.interactor.MediaCarouselInteractor
import com.android.systemui.media.controls.ui.controller.MediaHierarchyManager.Companion.LOCATION_QS
import com.android.systemui.media.remedia.ui.compose.MediaUiBehavior
import com.android.systemui.media.remedia.ui.viewmodel.MediaCarouselVisibility
import com.android.systemui.media.remedia.ui.viewmodel.MediaViewModel
import com.android.systemui.qs.panels.ui.viewmodel.DetailsViewModel
import com.android.systemui.qs.panels.ui.viewmodel.EditModeViewModel
import com.android.systemui.qs.panels.ui.viewmodel.MediaInRowInLandscapeViewModel
import com.android.systemui.qs.panels.ui.viewmodel.TileGridViewModel
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.shade.shared.flag.ShadeWindowGoesAround
import com.android.systemui.shade.ui.viewmodel.ShadeHeaderViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class QuickSettingsContainerViewModel
@AssistedInject
constructor(
    brightnessSliderViewModelFactory: BrightnessSliderViewModel.Factory,
    shadeHeaderViewModelFactory: ShadeHeaderViewModel.Factory,
    tileGridViewModelFactory: TileGridViewModel.Factory,
    @Assisted private val supportsBrightnessMirroring: Boolean,
    val editModeViewModel: EditModeViewModel,
    val detailsViewModel: DetailsViewModel,
    private val mediaCarouselInteractor: MediaCarouselInteractor,
    val mediaViewModelFactory: MediaViewModel.Factory,
    mediaInRowInLandscapeViewModelFactory: MediaInRowInLandscapeViewModel.Factory,
    @ShadeDisplayAware shadeDisplayTypeRepository: DisplayTypeRepository,
) : ExclusiveActivatable() {

    private val hydrator = Hydrator("QuickSettingsContainerViewModel.hydrator")

    val isBrightnessSliderVisible by
        hydrator.hydratedStateOf(
            traceName = "isBrightnessSliderVisible",
            initialValue = shadeDisplayTypeRepository.displayType.value == Display.TYPE_INTERNAL,
            source =
                if (ShadeWindowGoesAround.isEnabled) {
                    // The shade could be on an external display: in that case the slider shouldn't
                    // be visible.
                    shadeDisplayTypeRepository.displayType.map { it == Display.TYPE_INTERNAL }
                } else {
                    flowOf(true)
                },
        )

    val isEditing by
        hydrator.hydratedStateOf(source = editModeViewModel.isEditing, traceName = "isEditing")

    val brightnessSliderViewModel =
        brightnessSliderViewModelFactory.create(supportsBrightnessMirroring)

    val shadeHeaderViewModel = shadeHeaderViewModelFactory.create()

    val tileGridViewModel = tileGridViewModelFactory.create()

    val showMedia: Boolean by
        hydrator.hydratedStateOf(
            traceName = "showMedia",
            source = mediaCarouselInteractor.hasAnyMedia,
        )

    val showMediaInRow: Boolean
        get() = qsMediaInRowViewModel.shouldMediaShowInRow

    fun onMediaSwipeToDismiss() = mediaCarouselInteractor.onSwipeToDismiss()

    private val qsMediaInRowViewModel =
        mediaInRowInLandscapeViewModelFactory.create(LOCATION_QS, mediaUiBehavior)

    override suspend fun onActivated(): Nothing {
        coroutineScope {
            launch { hydrator.activate() }
            launch { brightnessSliderViewModel.activate() }
            launch { shadeHeaderViewModel.activate() }
            launch { tileGridViewModel.activate() }
            launch { qsMediaInRowViewModel.activate() }
            awaitCancellation()
        }
    }

    companion object {

        /** Behavior of the media carousel in quick settings */
        val mediaUiBehavior =
            MediaUiBehavior(
                isCarouselDismissible = false,
                carouselVisibility = MediaCarouselVisibility.WhenNotEmpty,
            )
    }

    @AssistedFactory
    interface Factory {
        fun create(supportsBrightnessMirroring: Boolean): QuickSettingsContainerViewModel
    }
}
