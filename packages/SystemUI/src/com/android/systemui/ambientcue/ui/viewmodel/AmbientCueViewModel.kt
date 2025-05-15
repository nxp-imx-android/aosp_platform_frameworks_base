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

package com.android.systemui.ambientcue.ui.viewmodel

import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.android.app.tracing.coroutines.coroutineScopeTraced
import com.android.systemui.ambientcue.domain.interactor.AmbientCueInteractor
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AmbientCueViewModel
@AssistedInject
constructor(private val ambientCueInteractor: AmbientCueInteractor) : ExclusiveActivatable() {
    private val hydrator = Hydrator("AmbientCueViewModel.hydrator")

    private val isRootViewAttached: Boolean by
        hydrator.hydratedStateOf(
            traceName = "isRootViewAttached",
            initialValue = false,
            source = ambientCueInteractor.isRootViewAttached,
        )

    private val isImeVisible: Boolean by
        hydrator.hydratedStateOf(
            traceName = "isImeVisible",
            initialValue = false,
            source = ambientCueInteractor.isImeVisible,
        )

    val isVisible: Boolean
        get() = isRootViewAttached && !isImeVisible

    var isExpanded: Boolean by mutableStateOf(false)
        private set

    val pillStyle: PillStyleViewModel by
        hydrator.hydratedStateOf(
            traceName = "pillStyle",
            initialValue = PillStyleViewModel.Uninitialized,
            source =
                combine(ambientCueInteractor.isGestureNav, ambientCueInteractor.isTaskBarVisible) {
                    isGestureNav,
                    isTaskBarVisible ->
                    if (isGestureNav && !isTaskBarVisible) {
                        PillStyleViewModel.NavBarPillStyle
                    } else {
                        val position =
                            if (isGestureNav) {
                                null
                            } else {
                                // TODO: b/415914083 Overview button position should come from SysUI
                                Rect(Offset.Zero, Size(100f, 100f))
                            }
                        PillStyleViewModel.ShortPillStyle(position)
                    }
                },
        )

    val actions: List<ActionViewModel> by
        hydrator.hydratedStateOf(
            traceName = "actions",
            initialValue = listOf(),
            source =
                ambientCueInteractor.actions.map { actions ->
                    actions.map { action ->
                        ActionViewModel(
                            icon = action.icon,
                            label = action.label,
                            attribution = action.attribution,
                            onClick = {
                                action.onPerformAction()
                                collapse()
                            },
                        )
                    }
                },
        )

    fun show() {
        isExpanded = false
    }

    fun expand() {
        isExpanded = true
    }

    fun collapse() {
        isExpanded = false
    }

    fun hide() {
        ambientCueInteractor.setDeactivated(true)
        isExpanded = false
    }

    private var deactivateCueBarJob: Job? = null

    fun cancelDeactivation() {
        deactivateCueBarJob?.cancel()
    }

    suspend fun delayAndDeactivateCueBar() {
        deactivateCueBarJob?.cancel()

        coroutineScopeTraced("AmbientCueViewModel") {
            deactivateCueBarJob = launch {
                delay(AMBIENT_CUE_TIMEOUT_SEC)
                ambientCueInteractor.setDeactivated(true)
            }
        }
    }

    override suspend fun onActivated(): Nothing {
        coroutineScopeTraced("AmbientCueViewModel") {
            launch { hydrator.activate() }
            launch {
                // Hide the UI if the user doesn't interact with it after N seconds
                ambientCueInteractor.isRootViewAttached.collectLatest { isAttached ->
                    if (!isAttached) {
                        cancelDeactivation()
                        return@collectLatest
                    }
                    delayAndDeactivateCueBar()
                }
            }
            awaitCancellation()
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(): AmbientCueViewModel
    }

    companion object {
        private const val TAG = "AmbientCueViewModel"
        @VisibleForTesting val AMBIENT_CUE_TIMEOUT_SEC = 15.seconds
    }
}
