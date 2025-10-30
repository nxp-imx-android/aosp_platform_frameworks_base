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
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.hardware.input.KeyGestureEvent
import android.os.Handler
import android.view.Display.DEFAULT_DISPLAY
import com.android.internal.accessibility.common.ShortcutConstants.UserShortcutType
import com.android.internal.accessibility.util.TtsPrompt
import com.android.systemui.accessibility.keygesture.shared.model.KeyGestureConfirmInfo
import com.android.systemui.accessibility.shortcutchooser.shared.model.AccessibilityTargetModel
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Main
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.mockito.Mockito.mock

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
    private var selectedTargetsList: List<AccessibilityTargetModel> = emptyList()

    var areShortcutsEnabled: Boolean = false
    var isMagnificationAndZoomInEnabled: Boolean = false
    var ttsPrompt: TtsPrompt? = null
    var ttsText: CharSequence = ""
    var shortcutTypeAssigned: Int = 0
    var enabledShortcutTargetName = ""
    var performedShortcutTargetName = ""

    override suspend fun getKeyGestureConfirmInfo(
        keyGestureType: Int,
        metaState: Int,
        keyCode: Int,
        targetName: String,
        displayId: Int,
    ): KeyGestureConfirmInfo? =
        when (keyGestureType) {
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION,
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_VOICE_ACCESS,
            KeyGestureEvent.KEY_GESTURE_TYPE_ACTIVATE_SELECT_TO_SPEAK,
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_SCREEN_READER -> {
                val featureNameForTest = featureNameTestMap[keyGestureType] ?: ""
                val ttsText =
                    if (keyGestureType == KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_SCREEN_READER) {
                        "Press Action + Alt + T again to enable Screen Reader"
                    } else {
                        null
                    }
                // return a fake data
                KeyGestureConfirmInfo(
                    keyGestureType,
                    "$featureNameForTest fakeTitle",
                    "$featureNameForTest fakeContentText",
                    emptyList(),
                    targetName,
                    0,
                    DEFAULT_DISPLAY,
                    ttsText,
                )
            }

            else -> null
        }

    override fun enableShortcutsForTargets(
        enable: Boolean,
        @UserShortcutType shortcutType: Int,
        targetName: String,
    ) {
        areShortcutsEnabled = enable
        shortcutTypeAssigned = shortcutType
        enabledShortcutTargetName = targetName
    }

    override fun enableMagnificationAndZoomIn(displayId: Int) {
        isMagnificationAndZoomInEnabled = true
    }

    override fun createTtsPromptForText(text: CharSequence): TtsPrompt {
        ttsText = text
        return mock(TtsPrompt::class.java).also { ttsPrompt = it }
    }

    override fun performAccessibilityShortcut(
        displayId: Int,
        @UserShortcutType shortcutType: Int,
        targetName: String,
    ) {
        shortcutTypeAssigned = shortcutType
        performedShortcutTargetName = targetName
    }

    override fun getAllAccessibilityTargetsInfo(
        @UserShortcutType shortcutType: Int
    ): List<AccessibilityTargetModel> {
        shortcutTypeAssigned = shortcutType

        // Helper Drawable for the fake models
        val fakeIcon = ColorDrawable(Color.BLACK)

        return listOf(
            AccessibilityTargetModel(
                shortcutType = shortcutType,
                targetName = "fakeTargetNameForTalkBack",
                featureName = "Screen Reader",
                icon = fakeIcon,
                isAssigned = false,
                isToggleable = true,
                isToggleOn = false,
            ),
            AccessibilityTargetModel(
                shortcutType = shortcutType,
                targetName = "fakeTargetNameForMagnification",
                featureName = "Magnification",
                icon = fakeIcon,
                isAssigned = false,
                isToggleable = false,
                isToggleOn = false,
            ),
            AccessibilityTargetModel(
                shortcutType = shortcutType,
                targetName = "fakeTargetNameForVoiceAccess",
                featureName = "Voice Access",
                icon = fakeIcon,
                isAssigned = false,
                isToggleable = true,
                isToggleOn = false,
            ),
        )
    }

    override fun getAllAccessibilityTargets(
        @UserShortcutType shortcutType: Int
    ): Flow<List<AccessibilityTargetModel>> = flow {
        emit(getAllAccessibilityTargetsInfo(shortcutType))
    }

    override fun getSelectedAccessibilityTargetsInfo(
        @UserShortcutType shortcutType: Int
    ): List<AccessibilityTargetModel> {
        shortcutTypeAssigned = shortcutType

        return selectedTargetsList
    }

    fun setSelectedAccessibilityTargetsList(list: List<AccessibilityTargetModel>) {
        selectedTargetsList = list.toList()
    }
}
