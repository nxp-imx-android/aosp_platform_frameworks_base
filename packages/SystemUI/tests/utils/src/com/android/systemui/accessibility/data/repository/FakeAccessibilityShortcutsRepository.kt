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

import android.content.Context
import android.hardware.input.KeyGestureEvent
import android.os.Handler
import com.android.internal.accessibility.util.FrameworkObjectProvider
import com.android.internal.accessibility.util.TtsPrompt
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Main

class FakeAccessibilityShortcutsRepository(
    @param:Application private val context: Context,
    @param:Main private val handler: Handler,
) : AccessibilityShortcutsRepository {
    private val featureNameTestMap =
        mapOf(
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION to "Magnification",
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_SCREEN_READER to "Screen Reader",
            KeyGestureEvent.KEY_GESTURE_TYPE_ACTIVATE_SELECT_TO_SPEAK to "Select to Speak",
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_VOICE_ACCESS to "Voice Access",
        )

    var areShortcutsEnabled: Boolean = false
    var isMagnificationAndZoomInEnabled: Boolean = false
    var ttsPrompt: TtsPrompt? = null
    var ttsText: CharSequence = ""

    override suspend fun getTitleToContentForKeyGestureDialog(
        keyGestureType: Int,
        metaState: Int,
        keyCode: Int,
        targetName: String,
    ): Pair<String, CharSequence>? =
        when (keyGestureType) {
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION,
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_VOICE_ACCESS,
            KeyGestureEvent.KEY_GESTURE_TYPE_ACTIVATE_SELECT_TO_SPEAK,
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_SCREEN_READER -> {
                val featureNameForTest = featureNameTestMap[keyGestureType] ?: ""
                // return a fake data
                "$featureNameForTest fakeTitle" to "$featureNameForTest fakeContentText"
            }

            else -> null
        }

    override fun getActionKeyIconResId(): Int {
        return 0
    }

    override fun enableShortcutsForTargets(enable: Boolean, targetName: String) {
        areShortcutsEnabled = enable
    }

    override fun enableMagnificationAndZoomIn(displayId: Int) {
        isMagnificationAndZoomInEnabled = true
    }

    override fun createTtsPromptForText(text: CharSequence): TtsPrompt {
        ttsText = text
        ttsPrompt = TtsPrompt(context, handler, FrameworkObjectProvider(), text)
        return ttsPrompt as TtsPrompt
    }
}
