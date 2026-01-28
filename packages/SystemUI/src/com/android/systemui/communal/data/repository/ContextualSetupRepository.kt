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

package com.android.systemui.communal.data.repository

import com.android.systemui.dagger.SysUISingleton
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** A repository that manages the persistent state of contextual setup flows. */
interface ContextualSetupRepository {
    /** Emits the current [SetupState] for a given flow ID. */
    fun setupState(id: String): Flow<SetupState>

    /** Updates the state for a given flow ID. */
    suspend fun updateState(id: String, state: SetupState)

    /** Increments the failure count for a given flow ID. */
    suspend fun incrementFailureCount(id: String): Int
}

@SysUISingleton
class ContextualSetupRepositoryImpl @Inject constructor() : ContextualSetupRepository {
    /**
     * For now, this returns a hardcoded [SetupState.NotStarted] to unblock UI development.
     * TODO(b/473859719): Implement persistence using SharedPreferences.
     */
    override fun setupState(id: String): Flow<SetupState> {
        return flowOf(SetupState.NotStarted)
    }

    override suspend fun updateState(id: String, state: SetupState) {
        // No-op for now.
    }

    override suspend fun incrementFailureCount(id: String): Int {
        // No-op for now.
        return 0
    }
}

/** Represents the persistent state of a contextual setup flow. */
sealed interface SetupState {
    /** The flow is eligible to be shown. */
    data object NotStarted : SetupState

    /** The flow has been snoozed by the user and should not be shown until [expirationTimeMillis]. */
    data class Snoozed(val expirationTimeMillis: Long) : SetupState

    /** The flow has been successfully completed and should not be shown again. */
    data object Completed : SetupState

    /** The flow has been dismissed by the user and should not be shown again. */
    data object Dismissed : SetupState
}
