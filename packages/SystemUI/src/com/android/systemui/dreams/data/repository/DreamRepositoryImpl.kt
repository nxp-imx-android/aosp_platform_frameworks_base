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

package com.android.systemui.dreams.data.repository

import android.app.DreamManager
import android.content.ComponentName
import android.os.RemoteException
import android.os.UserHandle
import android.service.dreams.DreamPlaylist
import android.util.Log
import com.android.app.tracing.FlowTracing.tracedAwaitClose
import com.android.app.tracing.FlowTracing.tracedConflatedCallbackFlow
import com.android.app.tracing.coroutines.flow.stateInTraced
import com.android.app.tracing.coroutines.withContextTraced
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dreams.shared.model.DreamPlaylistModel
import com.android.systemui.log.dagger.CommunalTableLog
import com.android.systemui.log.table.TableLogBuffer
import com.android.systemui.log.table.logDiffsForTable
import com.android.systemui.user.data.repository.UserRepository
import com.android.systemui.user.utils.UserScopedService
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@SysUISingleton
class DreamRepositoryImpl
@Inject
constructor(
    @param:Background private val bgDispatcher: CoroutineDispatcher,
    @param:Background private val bgScope: CoroutineScope,
    @param:CommunalTableLog private val tableLogBuffer: TableLogBuffer,
    private val userScopedDreamManager: UserScopedService<DreamManager>,
    userRepository: UserRepository,
) : DreamRepository {

    private val dreamManager: Flow<DreamManager> =
        userRepository.selectedUser
            .map { it.userInfo.userHandle }
            .distinctUntilChanged()
            .map(userScopedDreamManager::forUser)

    override val dreamState: Flow<DreamPlaylistModel> =
        dreamManager
            .flatMapLatest(::listenForDreamChanges)
            .logDiffsForTable(
                tableLogBuffer = tableLogBuffer,
                columnPrefix = "dreamState",
                initialValue = DreamPlaylistModel.EMPTY,
            )
            .stateInTraced(
                name = "$TAG#dreamState",
                scope = bgScope,
                started = SharingStarted.WhileSubscribed(5.seconds),
                initialValue = DreamPlaylistModel.EMPTY,
            )

    override suspend fun setActiveDream(componentName: ComponentName, user: UserHandle): Boolean =
        withContextTraced("$TAG#setActiveDream", bgDispatcher) {
            runCatchingRemote("Failed to set active dream", false) {
                userScopedDreamManager.forUser(user).setActiveDreamComponent(componentName)
            }
        }

    private fun listenForDreamChanges(manager: DreamManager): Flow<DreamPlaylistModel> =
        tracedConflatedCallbackFlow("$TAG#listenForDreamChanges") {
            var initialFetchDone = false
            val listener =
                object : DreamManager.DreamListener {
                    override fun onPlaylistChanged(playlist: DreamPlaylist) {
                        initialFetchDone = true
                        trySend(DreamPlaylistModel.from(playlist))
                    }
                }

            runCatchingRemote("Failed to register listener or fetch initial playlist", Unit) {
                manager.registerListener(bgDispatcher.asExecutor(), listener)
                if (!initialFetchDone) {
                    trySend(DreamPlaylistModel.from(manager.dreamPlaylist))
                }
                Unit
            }

            tracedAwaitClose("$TAG#listenForDreamChanges") {
                runCatchingRemote("Failed to unregister listener", Unit) {
                    manager.unregisterListener(listener)
                }
            }
        }

    private inline fun <T> runCatchingRemote(
        errorMessage: String,
        defaultValue: T,
        block: () -> T,
    ): T {
        return try {
            block()
        } catch (e: Exception) {
            if (e is RemoteException || e.cause is RemoteException) {
                Log.e(TAG, errorMessage, e)
                defaultValue
            } else {
                throw e
            }
        }
    }

    private companion object {
        const val TAG = "DreamRepository"
    }
}
