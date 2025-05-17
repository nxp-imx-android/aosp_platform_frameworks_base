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

package com.android.systemui.actioncorner.domain.interactor

import com.android.systemui.LauncherProxyService
import com.android.systemui.actioncorner.data.model.ActionCornerRegion.BOTTOM_LEFT
import com.android.systemui.actioncorner.data.model.ActionCornerRegion.BOTTOM_RIGHT
import com.android.systemui.actioncorner.data.model.ActionCornerRegion.TOP_LEFT
import com.android.systemui.actioncorner.data.model.ActionCornerRegion.TOP_RIGHT
import com.android.systemui.actioncorner.data.model.ActionCornerState
import com.android.systemui.actioncorner.data.repository.ActionCornerRepository
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.shade.domain.interactor.ShadeInteractor
import com.android.systemui.shade.domain.interactor.ShadeModeInteractor
import com.android.systemui.shade.shared.model.ShadeMode.Dual
import com.android.systemui.shared.system.actioncorner.ActionCornerConstants.HOME
import com.android.systemui.shared.system.actioncorner.ActionCornerConstants.OVERVIEW
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.withContext

@SysUISingleton
class ActionCornerInteractor
@Inject
constructor(
    @Main private val mainThreadContext: CoroutineContext,
    private val repository: ActionCornerRepository,
    private val launcherProxyService: LauncherProxyService,
    private val shadeModeInteractor: ShadeModeInteractor,
    private val shadeInteractor: ShadeInteractor,
) : ExclusiveActivatable() {

    override suspend fun onActivated(): Nothing {
        repository.actionCornerState
            .filterIsInstance<ActionCornerState.ActiveActionCorner>()
            .collect {
                // TODO(b/410791828): Read corresponding action from Action Corner Setting page
                when (it.region) {
                    TOP_LEFT -> {
                        if (isDualShadeEnabled()) {
                            withContext(mainThreadContext) {
                                if (shadeInteractor.isShadeAnyExpanded.value) {
                                    shadeInteractor.collapseNotificationsShade(LOGGING_REASON)
                                } else {
                                    shadeInteractor.expandNotificationsShade(LOGGING_REASON)
                                }
                            }
                        }
                    }
                    TOP_RIGHT -> {
                        if (isDualShadeEnabled()) {
                            withContext(mainThreadContext) {
                                if (shadeInteractor.isQsExpanded.value) {
                                    shadeInteractor.collapseQuickSettingsShade(LOGGING_REASON)
                                } else {
                                    shadeInteractor.expandQuickSettingsShade(LOGGING_REASON)
                                }
                            }
                        }
                    }
                    BOTTOM_LEFT ->
                        launcherProxyService.onActionCornerActivated(OVERVIEW, it.displayId)
                    BOTTOM_RIGHT -> launcherProxyService.onActionCornerActivated(HOME, it.displayId)
                }
            }
        awaitCancellation()
    }

    private fun isDualShadeEnabled(): Boolean {
        return SceneContainerFlag.isEnabled && shadeModeInteractor.shadeMode.value == Dual
    }

    companion object {
        private const val LOGGING_REASON = "Active action corner"
    }
}
