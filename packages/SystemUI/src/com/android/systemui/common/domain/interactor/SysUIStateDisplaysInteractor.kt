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

package com.android.systemui.common.domain.interactor

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.display.data.repository.PerDisplayRepository
import com.android.systemui.model.SysUiState
import javax.inject.Inject

/** Handles [SysUiState] changes between displays. */
@SysUISingleton
class SysUIStateDisplaysInteractor
@Inject
constructor(private val sysUIStateRepository: PerDisplayRepository<SysUiState>) {

    /**
     * Sets the flags on the given [targetDisplayId] based on the [stateChanges], while making sure
     * that those flags are not set in any other display.
     */
    fun setFlagsExclusivelyToDisplay(targetDisplayId: Int, stateChanges: StateChange) {
        sysUIStateRepository.forEachInstance { displayId, instance ->
            if (displayId == targetDisplayId) {
                stateChanges.applyTo(instance)
            } else {
                stateChanges.clearAllChangedFlagsIn(instance)
            }
        }
    }
}

/**
 * Represents a set of state changes. A bit can either be set to `true` or `false`.
 *
 * This is used in [SysUIStateDisplaysInteractor] to selectively change bits.
 */
class StateChange {
    private val changes = mutableMapOf<Long, Boolean>()

    /**
     * Sets the [state] of the given [bit].
     *
     * @return `this` for chaining purposes
     */
    fun setFlag(bit: Long, state: Boolean): StateChange {
        changes[bit] = state
        return this
    }

    /**
     * Gets the value of a given [bit] or false if not present.
     *
     * @param bit the bit to query
     * @return the value of the bit or false if not present.
     */
    fun get(bit: Long): Boolean = changes[bit] ?: false

    /** Applies all changed flags to [sysUiState]. */
    fun applyTo(sysUiState: SysUiState) {
        changes.forEach { (bit, state) -> sysUiState.setFlag(bit, state) }
        sysUiState.commitUpdate()
    }

    /** Clears all the flags changed in a [sysUiState] */
    fun clearAllChangedFlagsIn(sysUiState: SysUiState) {
        changes.forEach { (bit, _) -> sysUiState.setFlag(bit, false) }
        sysUiState.commitUpdate()
    }
}
