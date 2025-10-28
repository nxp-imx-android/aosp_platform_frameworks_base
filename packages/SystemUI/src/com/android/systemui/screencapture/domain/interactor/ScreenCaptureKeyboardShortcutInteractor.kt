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

package com.android.systemui.screencapture.domain.interactor

import android.util.Log
import com.android.internal.logging.UiEventLogger
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.screencapture.ScreenCaptureEvent
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureUiParameters
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureUiParameters.Record.LargeScreenCaptureUiParameters
import com.android.systemui.screencapture.record.domain.interactor.ScreenCaptureRecordFeaturesInteractor
import com.android.systemui.screencapture.record.largescreen.shared.model.ScreenCaptureRegion
import com.android.systemui.screencapture.record.largescreen.shared.model.ScreenCaptureType as LargeScreenCaptureType
import com.android.systemui.user.data.repository.UserRepository
import com.android.systemui.user.domain.interactor.HeadlessSystemUserMode
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Handles the resulting actions of screen capture related keyboard shortcuts. */
@SysUISingleton
class ScreenCaptureKeyboardShortcutInteractor
@Inject
constructor(
    @Background private val backgroundScope: CoroutineScope,
    private val screenCaptureUiInteractor: ScreenCaptureUiInteractor,
    private val uiEventLogger: UiEventLogger,
    private val keyguardInteractor: KeyguardInteractor,
    private val userRepository: UserRepository,
    private val hsum: HeadlessSystemUserMode,
) {
    fun attemptPartialRegionScreenshot() {
        backgroundScope.launch {
            launchPreCaptureUi(
                defaultCaptureType = LargeScreenCaptureType.SCREENSHOT,
                defaultCaptureRegion = ScreenCaptureRegion.PARTIAL,
            )
        }
    }

    private suspend fun launchPreCaptureUi(
        defaultCaptureType: LargeScreenCaptureType,
        defaultCaptureRegion: ScreenCaptureRegion,
    ) {
        // TODO(b/420714826) Check if the large-screen screen capture UI is supported on this device
        // device's display (i.e. the focused display or external display). If not supported,
        // default to taking a fullscreen screenshot.
        if (!ScreenCaptureRecordFeaturesInteractor.isLargeScreenScreencaptureEnabled) {
            return
        }

        if (keyguardInteractor.isKeyguardCurrentlyShowing()) {
            Log.i(TAG, "Screen capture UI is disabled when keyguard is showing.")
            return
        }

        if (hsum.isHeadlessSystemUser(userRepository.getSelectedUserInfo().id)) {
            Log.i(TAG, "Screen capture UI is disabled for headless system user.")
            return
        }

        uiEventLogger.log(
            ScreenCaptureEvent.SCREEN_CAPTURE_LARGE_SCREEN_PARTIAL_SCREENSHOT_KEYBOARD_SHORTCUT
        )
        screenCaptureUiInteractor.show(
            ScreenCaptureUiParameters.Record(
                largeScreenParameters =
                    LargeScreenCaptureUiParameters(defaultCaptureType, defaultCaptureRegion)
            )
        )
    }

    private companion object {
        const val TAG = "ScreenCaptureKeyboardShortcutInteractor"
    }
}
