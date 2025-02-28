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

package com.android.systemui.statusbar.core

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.display.data.repository.DisplayRepository
import com.android.systemui.display.data.repository.DisplayScopeRepository
import com.android.systemui.display.data.repository.PerDisplayStoreImpl
import com.android.systemui.statusbar.data.repository.StatusBarModeRepositoryStore
import com.android.systemui.statusbar.phone.AutoHideControllerStore
import com.android.systemui.statusbar.window.StatusBarWindowControllerStore
import com.android.systemui.statusbar.window.data.repository.StatusBarWindowStateRepositoryStore
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

/** [PerDisplayStoreImpl] for providing display specific [StatusBarOrchestrator]. */
@SysUISingleton
class MultiDisplayStatusBarOrchestratorStore
@Inject
constructor(
    @Background backgroundApplicationScope: CoroutineScope,
    displayRepository: DisplayRepository,
    private val factory: StatusBarOrchestrator.Factory,
    private val statusBarWindowControllerStore: StatusBarWindowControllerStore,
    private val statusBarModeRepositoryStore: StatusBarModeRepositoryStore,
    private val initializerStore: StatusBarInitializerStore,
    private val autoHideControllerStore: AutoHideControllerStore,
    private val displayScopeRepository: DisplayScopeRepository,
    private val statusBarWindowStateRepositoryStore: StatusBarWindowStateRepositoryStore,
) : PerDisplayStoreImpl<StatusBarOrchestrator>(backgroundApplicationScope, displayRepository) {

    init {
        StatusBarConnectedDisplays.assertInNewMode()
    }

    override fun createInstanceForDisplay(displayId: Int): StatusBarOrchestrator? {
        val statusBarModeRepository =
            statusBarModeRepositoryStore.forDisplay(displayId) ?: return null
        val statusBarInitializer = initializerStore.forDisplay(displayId) ?: return null
        val statusBarWindowController =
            statusBarWindowControllerStore.forDisplay(displayId) ?: return null
        val autoHideController = autoHideControllerStore.forDisplay(displayId) ?: return null
        return factory.create(
            displayId,
            // TODO: b/398825844 - Handle nullness to prevent leaking CoroutineScope.
            displayScopeRepository.scopeForDisplay(displayId),
            statusBarWindowStateRepositoryStore.forDisplay(displayId),
            statusBarModeRepository,
            statusBarInitializer,
            statusBarWindowController,
            autoHideController,
        )
    }

    override val instanceClass = StatusBarOrchestrator::class.java

    override suspend fun onDisplayRemovalAction(instance: StatusBarOrchestrator) {
        instance.stop()
    }
}
