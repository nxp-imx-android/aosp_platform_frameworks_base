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
package com.android.systemui.dreams.touch

import android.content.res.Resources
import android.util.Log
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.systemui.ambient.touch.TouchHandler
import com.android.systemui.ambient.touch.TouchHandler.TouchSession
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.dreams.DreamOverlayContainerView
import com.android.systemui.dreams.domain.interactor.DreamInteractor
import com.android.systemui.res.R
import com.android.systemui.statusbar.VibratorHelper
import javax.inject.Inject
import kotlinx.coroutines.launch

/** [TouchHandler] for long press gestures. */
class LongPressTouchHandler
@Inject
constructor(
    private val vibratorHelper: VibratorHelper,
    private val containerView: DreamOverlayContainerView,
    private val dreamInteractor: DreamInteractor,
    lifecycle: Lifecycle,
    @param:Main private val resources: Resources,
) : TouchHandler {
    private companion object {
        const val TAG = "LongPressTouchHandler"
    }

    private var accessibilityActionId: Int? = null
    private var canSwitchDreams: Boolean = false

    init {
        lifecycle.coroutineScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                dreamInteractor.canSwitchDreams.collect { canSwitch ->
                    canSwitchDreams = canSwitch
                    updateAccessibilityAction(canSwitch)
                }
            }
        }
    }

    private fun updateAccessibilityAction(canSwitch: Boolean) {
        accessibilityActionId?.let {
            ViewCompat.removeAccessibilityAction(containerView, it)
            accessibilityActionId = null
        }

        if (canSwitch) {
            accessibilityActionId =
                ViewCompat.addAccessibilityAction(
                    containerView,
                    resources.getString(R.string.dreams_switcher_accessibility_action),
                ) { _, _ ->
                    dreamInteractor.showSwitcherDialog()
                    true
                }
        }
    }

    override fun onSessionStart(session: TouchSession) {
        if (!canSwitchDreams) {
            return
        }

        session.registerGestureListener {
            if (!canSwitchDreams) {
                Log.d(TAG, "ignoring long press since we cannot switch dreams")
                return@registerGestureListener
            }

            Log.d(TAG, "long press detected")
            dreamInteractor.showSwitcherDialog()
            vibratorHelper.performHapticFeedback(containerView, HapticFeedbackConstants.LONG_PRESS)
        }
    }

    override fun onDestroy() {
        updateAccessibilityAction(false)
    }
}

private fun TouchSession.registerGestureListener(onLongPress: (MotionEvent) -> Unit) {
    registerGestureListener(
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) = onLongPress(e)
        }
    )
}
