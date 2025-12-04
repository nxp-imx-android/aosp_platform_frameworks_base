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
import com.android.internal.accessibility.common.ShortcutConstants.UserShortcutType
import com.android.internal.accessibility.util.TtsPrompt
import com.android.systemui.accessibility.keygesture.shared.model.KeyGestureConfirmInfo
import com.android.systemui.accessibility.shortcutchooser.shared.model.AccessibilityTargetModel
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Main
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.mockito.kotlin.mock

class FakeAccessibilityShortcutsRepository(
    @param:Application private val context: Context,
    @param:Main private val handler: Handler,
) : AccessibilityShortcutsRepository {
    companion object {
        const val FAKE_COLOR_INVERSION_TARGET_NAME = "com.android.test/.FakeColorInversion"
        const val FAKE_MAGNIFICATION_TARGET_NAME = "com.android.test/.FakeMagnification"
        const val FAKE_TALKBACK_TARGET_NAME = "com.android.test/.FakeTalkBack"
        const val FAKE_VOICE_ACCESS_TARGET_NAME = "com.android.test/.FakeVoiceAccess"
    }

    private data class TargetInfo(
        val targetName: String,
        val featureName: String,
        val isToggleable: Boolean,
    )

    private val allTargets =
        listOf(
            TargetInfo(FAKE_TALKBACK_TARGET_NAME, "Screen Reader", true),
            TargetInfo(FAKE_MAGNIFICATION_TARGET_NAME, "Magnification", false),
            TargetInfo(FAKE_VOICE_ACCESS_TARGET_NAME, "Voice Access", true),
        )

    // Target names existing in the set means they are assigned.
    private val assignedTargetNamesByShortcutType = mutableMapOf<Int, MutableSet<String>>()
    // Target names existing in the set means they are turned on.
    private val enabledTargetNamesByShortcutType = mutableMapOf<Int, MutableSet<String>>()

    private val featureNameTestMap =
        mapOf(
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_DISPLAY_COLOR_INVERSION to "Color Inversion",
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION to "Magnification",
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_SCREEN_READER to "Screen Reader",
            KeyGestureEvent.KEY_GESTURE_TYPE_ACTIVATE_SELECT_TO_SPEAK to "Select to Speak",
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_VOICE_ACCESS to "Voice Access",
        )

    var ttsPrompt: TtsPrompt? = null
    var ttsText: CharSequence = ""

    override suspend fun getKeyGestureConfirmInfo(
        keyGestureType: Int,
        metaState: Int,
        keyCode: Int,
        targetName: String,
        displayId: Int,
    ): KeyGestureConfirmInfo? =
        when (keyGestureType) {
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_DISPLAY_COLOR_INVERSION,
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
                    displayId,
                    ttsText,
                )
            }

            else -> null
        }

    override fun enableShortcutsForTargets(
        enable: Boolean,
        @UserShortcutType shortcutType: Int,
        targetNames: Set<String>,
    ) {
        targetNames
            .filter { allTargets.map { it.targetName }.contains(it) }
            .forEach { targetName ->
                with(assignedTargetNamesByShortcutType) {
                    if (enable) {
                        add(shortcutType, targetName)
                    } else {
                        remove(shortcutType, targetName)
                    }
                }
            }
    }

    override fun enableMagnificationAndZoomIn(displayId: Int) {
        enabledTargetNamesByShortcutType.add(
            UserShortcutType.KEY_GESTURE,
            FAKE_MAGNIFICATION_TARGET_NAME,
        )
    }

    override fun createTtsPromptForText(text: CharSequence): TtsPrompt =
        mock<TtsPrompt>().also {
            ttsPrompt = it
            ttsText = text
        }

    override fun performAccessibilityShortcut(
        displayId: Int,
        @UserShortcutType shortcutType: Int,
        targetName: String,
    ) {
        if (allTargets.map { it.targetName }.contains(targetName)) {
            with(enabledTargetNamesByShortcutType) {
                if (contains(shortcutType, targetName)) {
                    remove(shortcutType, targetName)
                } else {
                    add(shortcutType, targetName)
                }
            }
        }
    }

    override fun getAllAccessibilityTargetsInfo(
        @UserShortcutType shortcutType: Int
    ): List<AccessibilityTargetModel> = allTargets.map { it.toTargetModel(shortcutType) }

    override fun getAllAccessibilityTargets(
        @UserShortcutType shortcutType: Int
    ): Flow<List<AccessibilityTargetModel>> = flow {
        emit(getAllAccessibilityTargetsInfo(shortcutType))
    }

    override fun getSelectedAccessibilityTargetsInfo(
        @UserShortcutType shortcutType: Int
    ): List<AccessibilityTargetModel> =
        allTargets.map { it.toTargetModel(shortcutType) }.filter { it.isAssigned }

    override fun getSelectedAccessibilityTargets(
        @UserShortcutType shortcutType: Int
    ): Flow<List<AccessibilityTargetModel>> = flow {
        emit(getSelectedAccessibilityTargetsInfo(shortcutType))
    }

    private fun TargetInfo.toTargetModel(
        @UserShortcutType shortcutType: Int
    ): AccessibilityTargetModel =
        AccessibilityTargetModel(
            shortcutType = shortcutType,
            targetName = targetName,
            featureName = featureName,
            icon = ColorDrawable(Color.RED),
            isAssigned = assignedTargetNamesByShortcutType.contains(shortcutType, targetName),
            isToggleable = isToggleable,
            isToggleOn = enabledTargetNamesByShortcutType.contains(shortcutType, targetName),
        )

    private fun MutableMap<Int, MutableSet<String>>.add(
        @UserShortcutType shortcutType: Int,
        targetName: String,
    ) = getOrPut(shortcutType) { mutableSetOf() }.add(targetName)

    private fun MutableMap<Int, MutableSet<String>>.remove(
        @UserShortcutType shortcutType: Int,
        targetName: String,
    ) = getOrPut(shortcutType) { mutableSetOf() }.remove(targetName)

    private fun Map<Int, MutableSet<String>>.contains(
        @UserShortcutType shortcutType: Int,
        targetName: String,
    ) = get(shortcutType)?.contains(targetName) ?: false
}
