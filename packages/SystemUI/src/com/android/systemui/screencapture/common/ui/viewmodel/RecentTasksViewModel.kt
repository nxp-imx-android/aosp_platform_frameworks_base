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
import com.android.systemui.screencapture.common.domain.interactor.ScreenCaptureRecentTaskInteractor
import com.android.systemui.screencapture.common.domain.model.ScreenCaptureRecentTask
import javax.inject.Inject

/**
 * Interface for view models concerned with recent tasks.
 *
 * Example usage in a [HydratedActivatable]:
 * ```
 * class FooViewModel(
 *     viewModelProvider: Provider<RecentTasksViewModel>,
 * ) : HydratedActivatable() {
 *
 *     private val viewModel = viewModelProvider.get()
 *
 *     override suspend fun onActivated() {
 *         coroutineScope {
 *             launchTraced("FooTraceName") { viewModel.activate() }
 *         }
 *     }
 * }
 * ```
 *
 * Example usage in a [Composable][androidx.compose.runtime.Composable]
 *
 * ```
 * @Composable
 * fun Foo(viewModelProvider: Provider<RecentTasksViewModel>) {
 *     val viewModel = rememberViewModel("FooTraceName") { viewModelProvider.get() }
 * }
 * ```
 */
interface RecentTasksViewModel : TargetsViewModel<ScreenCaptureRecentTask>

/** The default implementation of [RecentTasksViewModel]. */
class RecentTasksViewModelImpl
@Inject
constructor(
    interactor: ScreenCaptureRecentTaskInteractor,
    private val recentTaskViewModelFactory: RecentTaskViewModel.Factory,
    drawableLoaderViewModel: DrawableLoaderViewModel,
    audioSwitchViewModel: AudioSwitchViewModel,
) :
    RecentTasksViewModel,
    DrawableLoaderViewModel by drawableLoaderViewModel,
    AudioSwitchViewModel by audioSwitchViewModel,
    HydratedActivatable() {

    override val targets: State<List<ScreenCaptureRecentTask>?> =
        interactor.recentTasks.hydratedStateOf("RecentTasksViewModel#recentTasks", null)

    private val _selectedTarget = mutableStateOf<TargetViewModel<ScreenCaptureRecentTask>?>(null)
    override val selectedTarget: State<TargetViewModel<ScreenCaptureRecentTask>?> = _selectedTarget

    override fun setSelectedTarget(target: TargetViewModel<ScreenCaptureRecentTask>?) {
        _selectedTarget.value = target
    }

    override fun createViewModelFor(
        target: ScreenCaptureRecentTask
    ): TargetViewModel<ScreenCaptureRecentTask> = recentTaskViewModelFactory.create(target)
}
