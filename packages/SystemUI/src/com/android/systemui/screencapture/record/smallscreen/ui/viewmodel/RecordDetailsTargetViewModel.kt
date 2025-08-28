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

import androidx.annotation.StringRes
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.domain.interactor.ScreenCaptureRecentTaskInteractor
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

enum class RecordDetailsTargetItemViewModel(
    @StringRes val labelRes: Int,
    val isSelectable: Boolean,
) {

    EntireScreen(R.string.screen_record_entire_screen, true),
    SingleApp(R.string.screen_record_single_app, true),
    SingleAppNoRecents(R.string.screen_record_single_app_no_recents, false),
}

class RecordDetailsTargetViewModel
@AssistedInject
constructor(private val screenCaptureRecentTaskInteractor: ScreenCaptureRecentTaskInteractor) :
    HydratedActivatable() {

    private val _selectedIndex = MutableStateFlow(0)
    private val _items: MutableStateFlow<List<RecordDetailsTargetItemViewModel>?> =
        MutableStateFlow(null)
    val items: List<RecordDetailsTargetItemViewModel>? by _items.hydratedStateOf()

    val selectedIndex: Int by
        combine(_selectedIndex, _items) { idx, targets ->
                if (targets.isNullOrEmpty()) {
                    idx
                } else {
                    val result = idx.coerceIn(targets.indices)
                    if (!targets[result].isSelectable) {
                        RecordDetailsTargetItemViewModel.entries.indexOf(
                            RecordDetailsTargetItemViewModel.EntireScreen
                        )
                    } else {
                        result
                    }
                }
            }
            .hydratedStateOf(0)

    override suspend fun onActivated() {
        screenCaptureRecentTaskInteractor.recentTasks
            .map { tasks ->
                buildList {
                    add(RecordDetailsTargetItemViewModel.EntireScreen)
                    add(
                        if (tasks.isNullOrEmpty()) {
                            RecordDetailsTargetItemViewModel.SingleAppNoRecents
                        } else {
                            RecordDetailsTargetItemViewModel.SingleApp
                        }
                    )
                }
            }
            .collect(_items)
    }

    fun select(index: Int) {
        _selectedIndex.value = index
    }

    @AssistedFactory
    interface Factory {
        fun create(): RecordDetailsTargetViewModel
    }
}
