/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.systemui.underlay.data.repository

import android.app.smartspace.SmartspaceConfig
import android.app.smartspace.SmartspaceManager
import android.app.smartspace.SmartspaceSession.OnTargetsAvailableListener
import android.content.Context
import android.content.IntentFilter
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.android.systemui.broadcast.BroadcastDispatcher
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.res.R
import com.android.systemui.underlay.shared.model.ActionModel
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Source of truth for ambient actions and visibility of their system space. */
interface UnderlayRepository {
    /** Chips that should be visible on the UI. */
    val actions: StateFlow<List<ActionModel>>

    /** If window should be added to the navbar area or not. */
    val isAttached: StateFlow<Boolean>

    /** If hint (or chips list) should be visible. */
    val isVisible: MutableStateFlow<Boolean>
}

@SysUISingleton
class UnderlayRepositoryImpl
@Inject
constructor(
    @Background private val backgroundScope: CoroutineScope,
    broadcastDispatcher: BroadcastDispatcher,
    private val smartSpaceManager: SmartspaceManager?,
    @Background executor: Executor,
    @Application applicationContext: Context,
) : UnderlayRepository {
    private val debugBroadcastFlow: Flow<Boolean> =
        if (DEBUG) {
            broadcastDispatcher.broadcastFlow(
                filter =
                    IntentFilter().apply {
                        addAction(ACTION_CREATE_UNDERLAY)
                        addAction(ACTION_DESTROY_UNDERLAY)
                    }
            ) { intent, _ ->
                intent.action == ACTION_CREATE_UNDERLAY
            }
        } else {
            MutableStateFlow(false).asStateFlow()
        }

    override val actions: StateFlow<List<ActionModel>> =
        conflatedCallbackFlow {
                if (smartSpaceManager == null) {
                    Log.i(TAG, "Cannot connect to SmartSpaceManager, it's null.")
                    return@conflatedCallbackFlow
                }

                val session =
                    smartSpaceManager.createSmartspaceSession(
                        SmartspaceConfig.Builder(applicationContext, UNDERLAY_SURFACE).build()
                    )

                val smartSpaceListener = OnTargetsAvailableListener { targets ->
                    val actions =
                        targets
                            .filter { target -> target.featureType == AMBIENT_ACTION_FEATURE }
                            .filter { it.smartspaceTargetId == UNDERLAY_SURFACE }
                            .flatMap { target -> target.actionChips }
                            .map { chip ->
                                ActionModel(
                                    icon =
                                        chip.icon?.loadDrawable(applicationContext)
                                            ?: applicationContext.getDrawable(
                                                R.drawable.clipboard
                                            )!!,
                                    intent = chip.intent,
                                    label = chip.title.toString(),
                                    attribution = chip.subtitle.toString(),
                                )
                            }
                    if (DEBUG) {
                        Log.d(TAG, "SmartSpace OnTargetsAvailableListener $targets")
                        Log.d(TAG, "SmartSpace actions $actions")
                    }
                    trySend(actions)
                }

                session.addOnTargetsAvailableListener(executor, smartSpaceListener)
                awaitClose {
                    session.removeOnTargetsAvailableListener(smartSpaceListener)
                    session.close()
                }
            }
            .stateIn(
                scope = backgroundScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = emptyList(),
            )

    override val isAttached: StateFlow<Boolean> =
        combine(actions, debugBroadcastFlow) { actions, createdViaBroadcast ->
                actions.isNotEmpty() || createdViaBroadcast
            }
            .stateIn(
                scope = backgroundScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = false,
            )

    override val isVisible: MutableStateFlow<Boolean> = MutableStateFlow(false)

    companion object {
        // Privately defined card type, exclusive for ambient actions
        @VisibleForTesting const val AMBIENT_ACTION_FEATURE = 72
        // Surface that PCC wants to push cards into
        @VisibleForTesting const val UNDERLAY_SURFACE = "underlay"
        private const val TAG = "underlay"
        private const val DEBUG = false
        private const val ACTION_CREATE_UNDERLAY = "com.systemui.underlay.action.CREATE_UNDERLAY"
        private const val ACTION_DESTROY_UNDERLAY = "com.systemui.underlay.action.DESTROY_UNDERLAY"
    }
}
