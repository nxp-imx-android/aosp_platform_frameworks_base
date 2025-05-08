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

package com.android.systemui.ambientcue.data.repository

import android.app.smartspace.SmartspaceConfig
import android.app.smartspace.SmartspaceManager
import android.app.smartspace.SmartspaceSession.OnTargetsAvailableListener
import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.android.systemui.ambientcue.shared.model.ActionModel
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.res.R
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** Source of truth for ambient actions and visibility of their system space. */
interface AmbientCueRepository {
    /** Chips that should be visible on the UI. */
    val actions: StateFlow<List<ActionModel>>

    /** If hint (or chips list) should be visible. */
    val isVisible: MutableStateFlow<Boolean>

    /** If IME is visible or not. */
    val isImeVisible: MutableStateFlow<Boolean>
}

@SysUISingleton
class AmbientCueRepositoryImpl
@Inject
constructor(
    @Background private val backgroundScope: CoroutineScope,
    private val smartSpaceManager: SmartspaceManager?,
    @Background executor: Executor,
    @Application applicationContext: Context,
) : AmbientCueRepository {

    override val actions: StateFlow<List<ActionModel>> =
        conflatedCallbackFlow {
                if (smartSpaceManager == null) {
                    Log.i(TAG, "Cannot connect to SmartSpaceManager, it's null.")
                    return@conflatedCallbackFlow
                }

                val session =
                    smartSpaceManager.createSmartspaceSession(
                        SmartspaceConfig.Builder(applicationContext, AMBIENT_CUE_SURFACE).build()
                    )

                val smartSpaceListener = OnTargetsAvailableListener { targets ->
                    val actions =
                        targets
                            .filter { it.smartspaceTargetId == AMBIENT_CUE_SURFACE }
                            .flatMap { target -> target.actionChips }
                            .map { chip ->
                                ActionModel(
                                    icon =
                                        chip.icon?.loadDrawable(applicationContext)
                                            ?: applicationContext.getDrawable(
                                                R.drawable.ic_content_paste_spark
                                            )!!,
                                    intent = chip.intent,
                                    label = chip.title.toString(),
                                    attribution = chip.subtitle.toString(),
                                )
                            }
                    if (DEBUG) {
                        Log.d(TAG, "SmartSpace OnTargetsAvailableListener $targets")
                    }
                    Log.v(TAG, "SmartSpace actions $actions")
                    trySend(actions)
                }

                session.addOnTargetsAvailableListener(executor, smartSpaceListener)
                awaitClose {
                    session.removeOnTargetsAvailableListener(smartSpaceListener)
                    session.close()
                }
            }
            .onEach { actions -> isVisible.update { actions.isNotEmpty() } }
            .stateIn(
                scope = backgroundScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = emptyList(),
            )

    override val isVisible: MutableStateFlow<Boolean> = MutableStateFlow(false)

    override val isImeVisible: MutableStateFlow<Boolean> = MutableStateFlow(false)

    companion object {
        // Surface that PCC wants to push cards into
        @VisibleForTesting const val AMBIENT_CUE_SURFACE = "ambientcue"
        // Timeout to hide cuebar if it wasn't interacted with
        private const val TAG = "AmbientCueRepository"
        private const val DEBUG = false
    }
}
