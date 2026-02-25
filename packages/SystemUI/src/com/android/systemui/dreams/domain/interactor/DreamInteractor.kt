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

package com.android.systemui.dreams.domain.interactor

import android.content.ComponentName
import android.os.UserHandle
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dreams.data.repository.DreamRepository
import com.android.systemui.dreams.domain.model.DreamSwitcherDialogRequestModel
import com.android.systemui.dreams.shared.model.DreamPlaylistModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * The DreamInteractor provides a layer of business logic over the dream repository and is used to
 * interact with the current dream state.
 */
@SysUISingleton
class DreamInteractor @Inject constructor(private val repository: DreamRepository) {
    private val _switcherRequests = Channel<DreamSwitcherDialogRequestModel>(Channel.CONFLATED)
    /** A flow of [SwitcherRequest]s. */
    val switcherRequests: Flow<DreamSwitcherDialogRequestModel> = _switcherRequests.receiveAsFlow()

    /** Sends a request to show the switcher dialog. */
    fun showSwitcherDialog() {
        _switcherRequests.trySend(DreamSwitcherDialogRequestModel.Show)
    }

    /** Sends a request to dismiss the switcher dialog. */
    fun dismissSwitcherDialog() {
        _switcherRequests.trySend(DreamSwitcherDialogRequestModel.Dismiss)
    }

    /** Whether the dream switcher dialog is currently showing */
    val dreamSwitcherDialogShowing: StateFlow<Boolean> = repository.dreamSwitcherDialogShowing

    /** Updates whether the dream dialog is showing */
    fun setSwitcherDialogShowing(showing: Boolean) {
        repository.setSwitcherDialogShowing(showing)
    }

    /** The current dream playlist. */
    val dreamState: Flow<DreamPlaylistModel> = repository.dreamState

    /** Emits whether the user can switch between dreams. */
    val canSwitchDreams: Flow<Boolean> =
        dreamState
            .map { state ->
                state.dreams.size > 1 && (state.nextDream ?: state.previousDream) != null
            }
            .distinctUntilChanged()

    /**
     * Sets the active dream.
     *
     * @param componentName The dream to set as active.
     * @param user The user to set the active dream for.
     * @return true if the dream was successfully set, false otherwise.
     */
    suspend fun setActiveDream(componentName: ComponentName, user: UserHandle): Boolean {
        return repository.setActiveDream(componentName, user)
    }
}
