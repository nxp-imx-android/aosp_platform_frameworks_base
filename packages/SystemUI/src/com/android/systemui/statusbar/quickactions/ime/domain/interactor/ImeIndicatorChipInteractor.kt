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

package com.android.systemui.statusbar.quickactions.ime.domain.interactor

import com.android.systemui.Flags
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.inputmethod.data.repository.InputMethodRepository
import com.android.systemui.statusbar.quickactions.ime.shared.model.ImeIndicatorChipModel
import com.android.systemui.user.data.repository.UserRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Interactor for managing the state of the IME indicator chip in the status bar. */
@SysUISingleton
class ImeIndicatorChipInteractor
@Inject
constructor(
    @param:Background private val scope: CoroutineScope,
    private val inputMethodRepository: InputMethodRepository,
    private val userRepository: UserRepository,
) {
    private val isFeatureEnabled: Boolean
        get() = Flags.statusBarImeChip()

    /** The current state of the IME indicator chip. */
    val chipModel: StateFlow<ImeIndicatorChipModel> =
        if (isFeatureEnabled) {
                // TODO(b/458558606): Add logic to show / hide chip depending on enabled IME
                // subtypes.
                userRepository.selectedUserInfo
                    .flatMapLatest { userInfo ->
                        inputMethodRepository.selectedInputMethodSubtype(userInfo.userHandle)
                    }
                    .map { subtype ->
                        ImeIndicatorChipModel(isVisible = true, selectedSubtype = subtype)
                    }
            } else {
                flowOf(ImeIndicatorChipModel(isVisible = false, selectedSubtype = null))
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = ImeIndicatorChipModel(isFeatureEnabled, selectedSubtype = null),
            )

    fun showInputMethodPicker(displayId: Int) {
        scope.launch {
            inputMethodRepository.showInputMethodPicker(
                displayId = displayId,
                showAuxiliarySubtypes = true,
            )
        }
    }
}
