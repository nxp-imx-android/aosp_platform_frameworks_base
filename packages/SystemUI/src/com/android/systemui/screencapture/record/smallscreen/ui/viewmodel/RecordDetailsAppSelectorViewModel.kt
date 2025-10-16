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

package com.android.systemui.screencapture.record.smallscreen.ui.viewmodel

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import com.android.app.tracing.coroutines.launchTraced
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.screencapture.common.domain.model.ScreenCaptureRecentTask
import com.android.systemui.screencapture.common.ui.viewmodel.RecentTaskViewModel
import com.android.systemui.screencapture.common.ui.viewmodel.RecentTasksViewModel
import com.android.systemui.screencapture.record.smallscreen.ui.SmallScreenPostRecordingActivity
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.coroutineScope

class RecordDetailsAppSelectorViewModel
@AssistedInject
constructor(private val recentTasksViewModel: RecentTasksViewModel) : HydratedActivatable() {

    val recentTasks: List<ScreenCaptureRecentTask>? by derivedStateOf {
        recentTasksViewModel.targets.value?.withoutPostRecordingActivity()
    }

    override suspend fun onActivated() {
        coroutineScope {
            launchTraced("RecordDetailsAppSelectorViewModel#recentTasksViewModel") {
                recentTasksViewModel.activate()
            }
        }
    }

    fun createTaskViewModel(task: ScreenCaptureRecentTask): RecentTaskViewModel =
        recentTasksViewModel.createViewModelFor(task) as RecentTaskViewModel

    private fun List<ScreenCaptureRecentTask>.withoutPostRecordingActivity():
        List<ScreenCaptureRecentTask> {
        return filter { task ->
            SmallScreenPostRecordingActivity::class.qualifiedName != task.component?.className
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(): RecordDetailsAppSelectorViewModel
    }
}
