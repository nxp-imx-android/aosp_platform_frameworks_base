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

package com.android.systemui.dreams

import android.Manifest.permission.WRITE_DREAM_STATE
import android.app.DreamManager
import androidx.annotation.RequiresPermission
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.Logger
import com.android.systemui.log.dagger.DreamLog
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.util.kotlin.sample
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@SysUISingleton
class DreamStartable
@Inject
constructor(
    @Application private val applicationScope: CoroutineScope,
    private val deviceEntryInteractor: DeviceEntryInteractor,
    private val dreamManager: DreamManager,
    private val keyguardInteractor: KeyguardInteractor,
    @DreamLog private val logBuffer: LogBuffer,
) : CoreStartable {

    private val logger = Logger(logBuffer, TAG)

    @RequiresPermission(WRITE_DREAM_STATE)
    override fun start() {
        if (SceneContainerFlag.isEnabled) {
            handleStopDreamWhenUnlocked()
        }
    }

    /** Stops dream when device is entered */
    @RequiresPermission(WRITE_DREAM_STATE)
    private fun handleStopDreamWhenUnlocked() {
        applicationScope.launch {
            deviceEntryInteractor.isUnlocked
                .sample(keyguardInteractor.isDreaming, ::Pair)
                .collect { (isUnlocked, isDreaming) ->
                    if (isUnlocked && isDreaming) {
                        logger.i("Stopping dream due to device unlock")
                        dreamManager.stopDream()
                    }
                }
        }
    }

    private companion object {
        const val TAG = "DreamStartable"
    }
}
