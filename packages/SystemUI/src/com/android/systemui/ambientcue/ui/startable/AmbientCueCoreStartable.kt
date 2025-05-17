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

package com.android.systemui.ambientcue.ui.startable

import android.util.Log
import android.view.WindowInsets.Type.ime
import android.view.WindowManager
import com.android.systemui.CoreStartable
import com.android.systemui.ambientcue.domain.interactor.AmbientCueInteractor
import com.android.systemui.ambientcue.shared.flag.AmbientCueFlag
import com.android.systemui.ambientcue.ui.view.AmbientCueUtils
import com.android.systemui.ambientcue.ui.view.AmbientCueWindowRootView
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Core startable for the AmbientCue.
 *
 * This is responsible for starting the AmbientCue and its dependencies.
 */
@SysUISingleton
class AmbientCueCoreStartable
@Inject
constructor(
    private val windowManager: WindowManager,
    private val ambientCueInteractor: AmbientCueInteractor,
    private val ambientCueWindowRootView: AmbientCueWindowRootView,
    @Application private val mainScope: CoroutineScope,
) : CoreStartable {

    override fun start() {
        if (!AmbientCueFlag.isEnabled) {
            Log.d(TAG, "AmbientCue flag is disabled, not starting.")
            return
        }

        Log.d(TAG, "start!")
        mainScope.launch { ambientCueInteractor.actions.collect() }
        mainScope.launch {
            ambientCueInteractor.isRootViewAttached.collectLatest { isAttached ->
                if (isAttached) {
                    createAmbientCueView()
                } else {
                    // Delay a while to ensure AmbientCue disappearing animation to show.
                    delay(DELAY_MS)
                    destroyAmbientCueView()
                }
            }
        }

        ambientCueWindowRootView.setOnApplyWindowInsetsListener { _, insets ->
            val imeVisible = insets.isVisible(ime())
            ambientCueInteractor.setImeVisible(imeVisible)
            insets
        }
    }

    private fun createAmbientCueView() {
        if (!ambientCueWindowRootView.isAttachedToWindow) {
            windowManager.addView(
                ambientCueWindowRootView,
                AmbientCueUtils.getAmbientCueLayoutParams(spyTouches = true),
            )
        }
    }

    private fun destroyAmbientCueView() {
        if (ambientCueWindowRootView.isAttachedToWindow) {
            windowManager.removeView(ambientCueWindowRootView)
        }
    }

    private companion object {
        const val TAG = "AmbientCueCoreStartable"
        const val DELAY_MS = 500L
    }
}
