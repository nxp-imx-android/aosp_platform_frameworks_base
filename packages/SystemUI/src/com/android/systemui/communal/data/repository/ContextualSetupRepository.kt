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

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.domain.interactor.SharedPreferencesInteractor
import com.android.systemui.util.kotlin.SharedPreferencesExt.observeString
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
class ContextualSetupRepositoryImpl
@Inject
constructor(
    private val sharedPreferencesInteractor: SharedPreferencesInteractor,
    @Background private val backgroundDispatcher: CoroutineDispatcher,
) : ContextualSetupRepository {

    private val failureCountMutex = Mutex()

    private suspend fun getPrefs() =
        sharedPreferencesInteractor
            .sharedPreferences(CONTEXTUAL_SETUP_PREFS, Context.MODE_PRIVATE)
            .first()

    override fun setupState(id: String): Flow<SetupState> {
        return sharedPreferencesInteractor
            .sharedPreferences(CONTEXTUAL_SETUP_PREFS, Context.MODE_PRIVATE)
            .flatMapLatest { prefs ->
                prefs.observeString(stateKey(id), "").map { value ->
                    SetupState.deserialize(value)
                }
            }
            .flowOn(backgroundDispatcher)
    }

    override suspend fun updateState(id: String, state: SetupState) {
        withContext(backgroundDispatcher) {
            getPrefs().edit {
                val key = stateKey(id)
                val value = state.toStorageValue()
                if (value != null) {
                    putString(key, value)
                } else {
                    remove(key)
                }
            }
        }
    }

    override suspend fun incrementFailureCount(id: String): Int {
        return withContext(backgroundDispatcher) {
            failureCountMutex.withLock {
                val key = failureCountKey(id)
                getPrefs().run {
                    val newCount = getInt(key, 0) + 1
                    edit { putInt(key, newCount) }
                    newCount
                }
            }
        }
    }

    private fun failureCountKey(id: String) = "failure_count_$id"

    private fun stateKey(id: String) = "state_$id"

    companion object {
        const val CONTEXTUAL_SETUP_PREFS = "contextual_setup_prefs"
    }
}

/** Represents the persistent state of a contextual setup flow. */
sealed interface SetupState {
    /** The flow is eligible to be shown. */
    data object NotStarted : SetupState

    /**
     * The flow has been snoozed by the user and should not be shown until [expirationTimeMillis].
     */
    data class Snoozed(val expirationTimeMillis: Long) : SetupState

    /** The flow has been successfully completed and should not be shown again. */
    data object Completed : SetupState

    /** The flow has been dismissed by the user and should not be shown again. */
    data object Dismissed : SetupState

    fun toStorageValue(): String? =
        when (this) {
            is NotStarted -> null
            is Snoozed -> "$SNOOZED_PREFIX$expirationTimeMillis"
            is Completed -> COMPLETED
            is Dismissed -> DISMISSED
        }

    companion object {
        private const val SNOOZED_PREFIX = "SNOOZED:"
        private const val COMPLETED = "COMPLETED"
        private const val DISMISSED = "DISMISSED"

        fun deserialize(value: String): SetupState {
            return if (value.startsWith(SNOOZED_PREFIX)) {
                val expirationTime = value.removePrefix(SNOOZED_PREFIX).toLongOrNull() ?: 0L
                Snoozed(expirationTime)
            } else {
                when (value) {
                    COMPLETED -> Completed
                    DISMISSED -> Dismissed
                    else -> NotStarted
                }
            }
        }
    }
}
