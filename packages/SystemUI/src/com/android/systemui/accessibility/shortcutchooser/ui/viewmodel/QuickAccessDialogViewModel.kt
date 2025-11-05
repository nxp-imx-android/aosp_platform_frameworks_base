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

package com.android.systemui.accessibility.shortcutchooser.ui.viewmodel

import android.view.Display.INVALID_DISPLAY
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.android.app.tracing.coroutines.launchTraced
import com.android.systemui.accessibility.shortcutchooser.domain.interactor.QuickAccessDialogInteractor
import com.android.systemui.accessibility.shortcutchooser.shared.model.AccessibilityTargetModel
import com.android.systemui.accessibility.shortcutchooser.shared.model.QuickAccessDialogRequestModel
import com.android.systemui.lifecycle.HydratedActivatable
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect

class QuickAccessDialogViewModel
@AssistedInject
constructor(private val interactor: QuickAccessDialogInteractor) : HydratedActivatable() {

    override suspend fun onActivated() {
        coroutineScope {
            interactor.enableShortcutForAllTargets()
            launchTraced { interactor.dialogRequest.collect { onDialogRequest(it) } }
            awaitCancellation()
        }
    }

    fun performAccessibilityShortcut(targetName: String) {
        interactor.performAccessibilityShortcut(displayId, targetName)
    }

    fun dismissDialog() {
        _isDialogVisible.value = false
    }

    val accessibilityTargets: List<AccessibilityTargetModel> by
        interactor.accessibilityTargets.hydratedStateOf(emptyList())

    private val _isDialogVisible = MutableStateFlow<Boolean>(false)
    val isDialogVisible = _isDialogVisible.asStateFlow()

    private var displayId = INVALID_DISPLAY

    private fun onDialogRequest(requestModel: QuickAccessDialogRequestModel) {
        displayId = requestModel.displayId
        _isDialogVisible.value = true
    }

    @AssistedFactory
    interface Factory {
        fun create(): QuickAccessDialogViewModel
    }
}
