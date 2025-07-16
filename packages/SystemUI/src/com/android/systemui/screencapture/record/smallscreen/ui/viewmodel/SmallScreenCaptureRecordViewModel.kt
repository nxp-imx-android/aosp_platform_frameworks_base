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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.app.tracing.coroutines.launchTraced
import com.android.systemui.lifecycle.HydratedActivatable
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.coroutineScope

class SmallScreenCaptureRecordViewModel
@AssistedInject
constructor(recordDetailsAppSelectorViewModelFactory: RecordDetailsAppSelectorViewModel.Factory) :
    HydratedActivatable() {

    val recordDetailsAppSelectorViewModel: RecordDetailsAppSelectorViewModel =
        recordDetailsAppSelectorViewModelFactory.create()

    var detailsPopup: RecordDetailsPopupType by mutableStateOf(RecordDetailsPopupType.Settings)
        private set

    override suspend fun onActivated() {
        coroutineScope {
            launchTraced("RecordDetailsAppSelectorViewModel#activate") {
                recordDetailsAppSelectorViewModel.activate()
            }
        }
    }

    fun showSettings() {
        detailsPopup = RecordDetailsPopupType.Settings
    }

    fun showAppSelector() {
        detailsPopup = RecordDetailsPopupType.AppSelector
    }

    fun showMarkupColorSelector() {
        detailsPopup = RecordDetailsPopupType.MarkupColorSelector
    }

    @AssistedFactory
    interface Factory {
        fun create(): SmallScreenCaptureRecordViewModel
    }
}
