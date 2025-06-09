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

package com.android.systemui.accessibility.data.repository

import android.hardware.input.KeyGestureEvent
import com.android.systemui.accessibility.data.model.KeyGestureConfirmInfo

class FakeAccessibilityShortcutsRepository : AccessibilityShortcutsRepository {
    override suspend fun getKeyGestureConfirmInfoByType(
        keyGestureType: Int,
        metaState: Int,
        keyCode: Int,
        targetName: String,
    ): KeyGestureConfirmInfo? {
        return when (keyGestureType) {
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION,
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_VOICE_ACCESS,
            KeyGestureEvent.KEY_GESTURE_TYPE_ACTIVATE_SELECT_TO_SPEAK,
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_SCREEN_READER -> {
                // return a fake data
                KeyGestureConfirmInfo("fakeTitle", "fakeContentText", targetName)
            }

            else -> null
        }
    }

    override fun enableShortcutsForTargets(targetName: String) {}
}
