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

import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.android.systemui.plugins.ActivityStarter
import javax.inject.Inject

/** Events that can be triggered from the dream setup screen. */
sealed interface DreamSetupEvent {
    /** Called when the setup flow is dismissed without taking action. */
    object Dismiss : DreamSetupEvent

    /** Called when the user chooses not to set up the dream at this time. */
    object NotNow : DreamSetupEvent

    /** Called when the user chooses to proceed with setting up the dream. */
    object SetUp : DreamSetupEvent
}

class DreamSetupViewModel(private val activityStarter: ActivityStarter) : ViewModel() {

    class Factory @Inject constructor(private val activityStarter: ActivityStarter) :
        ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(DreamSetupViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return DreamSetupViewModel(activityStarter) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
        }
    }

    fun onEvent(event: DreamSetupEvent) {
        when (event) {
            is DreamSetupEvent.Dismiss -> handleDismiss()
            is DreamSetupEvent.NotNow -> handleNotNow()
            is DreamSetupEvent.SetUp -> handleSetUp()
        }
    }

    private fun handleDismiss() {
        Log.d(TAG, "User dismissed the setup flow.")
    }

    private fun handleNotNow() {
        Log.d(TAG, "User clicked 'Not now'.")
    }

    private fun handleSetUp() {
        Log.d(TAG, "User clicked 'Set up'.")
        activityStarter.postStartActivityDismissingKeyguard(
            Intent(Settings.ACTION_DREAM_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            /* delay */ 0,
            /* animationController */ null,
        )
    }

    companion object {
        private const val TAG = "DreamSetupViewModel"
    }
}
