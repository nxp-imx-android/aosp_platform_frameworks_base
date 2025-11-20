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

package com.android.systemui.statusbar.featurepods.ime.domain.interactor

import com.android.systemui.Flags
import com.android.systemui.dagger.SysUISingleton
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Interactor for managing the state of the IME indicator chip in the status bar. */
@SysUISingleton
class ImeIndicatorChipInteractor @Inject constructor() {
    private val isFeatureEnabled: Boolean
        get() = Flags.statusBarImeChip()

    // TODO(b/458558606): Add logic to show / hide chip depending on enabled IME subtypes.
    val isChipVisible: StateFlow<Boolean> = MutableStateFlow(isFeatureEnabled).asStateFlow()
}
