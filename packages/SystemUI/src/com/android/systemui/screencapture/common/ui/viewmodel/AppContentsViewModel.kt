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

package com.android.systemui.screencapture.common.ui.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.screencapture.common.domain.interactor.ScreenCaptureAppContentInteractor
import com.android.systemui.screencapture.common.domain.interactor.ScreenCaptureRecentTaskInteractor
import com.android.systemui.screencapture.common.domain.model.ScreenCaptureAppContent
import com.android.systemui.utils.coroutines.flow.flatMapLatestConflated
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

/**
 * Interface for view models concerned with app content.
 *
 * Example Usage:
 * ```
 * class FooViewModel(
 *     factory: AppContentsViewModel.Factory,
 * ) : AppContentsViewModel, HydratedActivatable() {
 *
 *     private val appContentsViewModel = factory.create(200, 100)
 *
 *     override val targets = appContentsViewModel.targets
 *
 *     override suspend fun onActivated() {
 *         coroutineScope {
 *             launch { appContentsViewModel.activate() }
 *         }
 *     }
 * }
 * ```
 *
 * And then in compose:
 * ```
 * @Composable
 * fun Foo(
 *     viewModel: FooViewModel,
 *     modelFactory: AppContentViewModel.Factory,
 * ) {
 *     val appContents by viewModel.appContents
 *     LazyRow {
 *         appContents?.let {
 *             items(it) { appContent ->
 *                 val model by rememberViewModel("FooTraceName", appContent) {
 *                     modelFactory.create(appContent)
 *                 }
 *                 // ...
 *             }
 *         }
 *     }
 * }
 * ```
 */
interface AppContentsViewModel : TargetsViewModel<ScreenCaptureAppContent> {
    interface Factory {
        fun create(thumbnailWidthPx: Int, thumbnailHeightPx: Int): AppContentsViewModel
    }
}

class AppContentsViewModelImpl
@AssistedInject
constructor(
    private val appContentInteractor: ScreenCaptureAppContentInteractor,
    recentTaskInteractor: ScreenCaptureRecentTaskInteractor,
    private val appContentViewModelFactory: AppContentViewModel.Factory,
    drawableLoaderViewModel: DrawableLoaderViewModel,
    audioSwitchViewModel: AudioSwitchViewModel,
    @Assisted("thumbnailWidthPx") private val thumbnailWidthPx: Int,
    @Assisted("thumbnailHeightPx") private val thumbnailHeightPx: Int,
) :
    AppContentsViewModel,
    DrawableLoaderViewModel by drawableLoaderViewModel,
    AudioSwitchViewModel by audioSwitchViewModel,
    HydratedActivatable() {

    override val targets: State<List<ScreenCaptureAppContent>?> =
        recentTaskInteractor.recentTasks
            .flatMapLatestConflated { tasks ->
                appContentInteractor.appContentsFor(
                    packageNames = tasks.mapNotNull { it.component?.packageName },
                    thumbnailWidthPx = thumbnailWidthPx,
                    thumbnailHeightPx = thumbnailHeightPx,
                )
            }
            .hydratedStateOf("AppContentsViewModel#getAppContents", null)

    private val _selectedTarget = mutableStateOf<TargetViewModel<ScreenCaptureAppContent>?>(null)
    override val selectedTarget: State<TargetViewModel<ScreenCaptureAppContent>?> = _selectedTarget

    override fun setSelectedTarget(target: TargetViewModel<ScreenCaptureAppContent>?) {
        _selectedTarget.value = target
    }

    override fun createViewModelFor(
        target: ScreenCaptureAppContent
    ): TargetViewModel<ScreenCaptureAppContent> = appContentViewModelFactory.create(target)

    @AssistedFactory
    interface Factory : AppContentsViewModel.Factory {
        override fun create(
            @Assisted("thumbnailWidthPx") thumbnailWidthPx: Int,
            @Assisted("thumbnailHeightPx") thumbnailHeightPx: Int,
        ): AppContentsViewModelImpl
    }
}
