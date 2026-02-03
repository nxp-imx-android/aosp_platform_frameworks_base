/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.dreams.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel

/** Events that can be triggered from the dream setup screen. */
sealed interface DreamSetupEvent {
    /** Called when the setup flow is dismissed without taking action. */
    object Dismiss : DreamSetupEvent
    /** Called when the user chooses not to set up the dream at this time. */
    object NotNow : DreamSetupEvent
    /** Called when the user chooses to proceed with setting up the dream. */
    object SetUp : DreamSetupEvent
}

class DreamSetupViewModel : ViewModel() {
    fun onEvent(event: DreamSetupEvent) {
        when (event) {
            is DreamSetupEvent.Dismiss -> Log.d(TAG, "User dismissed the setup flow.")
            is DreamSetupEvent.NotNow -> Log.d(TAG, "User clicked 'Not now'.")
            is DreamSetupEvent.SetUp -> Log.d(TAG, "User clicked 'Set up'.")
        }
    }

    companion object {
        private const val TAG = "DreamSetupViewModel"
    }
}
