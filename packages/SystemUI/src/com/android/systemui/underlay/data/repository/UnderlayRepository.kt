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

import android.content.IntentFilter
import com.android.systemui.broadcast.BroadcastDispatcher
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.underlay.shared.model.ActionModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@SysUISingleton
class UnderlayRepository
@Inject
constructor(
    @Background private val backgroundScope: CoroutineScope,
    broadcastDispatcher: BroadcastDispatcher,
) {
    val isUnderlayAttached: StateFlow<Boolean> =
        broadcastDispatcher
            .broadcastFlow(
                filter =
                    IntentFilter().apply {
                        addAction(ACTION_CREATE_UNDERLAY)
                        addAction(ACTION_DESTROY_UNDERLAY)
                    }
            ) { intent, _ ->
                intent.action == ACTION_CREATE_UNDERLAY
            }
            .stateIn(
                scope = backgroundScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = false,
            )

    val isOverlayVisible: MutableStateFlow<Boolean> = MutableStateFlow(false)

    val actions: MutableStateFlow<List<ActionModel>> = MutableStateFlow(listOf())

    companion object {
        const val ACTION_CREATE_UNDERLAY = "com.systemui.underlay.action.CREATE_UNDERLAY"
        const val ACTION_DESTROY_UNDERLAY = "com.systemui.underlay.action.DESTROY_UNDERLAY"
    }
}
