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

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.hardware.input.KeyGestureEvent
import android.os.Handler
import android.text.BidiFormatter
import android.text.TextUtils
import android.view.KeyEvent
import android.view.accessibility.AccessibilityManager
import com.android.hardware.input.Flags
import com.android.internal.accessibility.common.ShortcutConstants.UserShortcutType
import com.android.internal.accessibility.dialog.AccessibilityTarget
import com.android.internal.accessibility.dialog.AccessibilityTargetHelper
import com.android.internal.accessibility.util.FrameworkObjectProvider
import com.android.internal.accessibility.util.TtsPrompt
import com.android.systemui.accessibility.keygesture.shared.model.KeyGestureConfirmInfo
import com.android.systemui.accessibility.shortcutchooser.shared.model.AccessibilityTargetModel
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.keyboard.shortcut.data.repository.ShortcutHelperKeys
import com.android.systemui.res.R
import com.android.systemui.settings.UserTracker
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Provides data for enabling and triggering accessibility feature shortcuts. */
interface AccessibilityShortcutsRepository {
    suspend fun getKeyGestureConfirmInfo(
        keyGestureType: Int,
        metaState: Int,
        keyCode: Int,
        targetName: String,
        displayId: Int,
    ): KeyGestureConfirmInfo?

    fun createTtsPromptForText(text: CharSequence): TtsPrompt

    fun enableShortcutsForTargets(
        enable: Boolean,
        @UserShortcutType shortcutType: Int,
        targetName: String,
    )

    fun enableMagnificationAndZoomIn(displayId: Int)

    fun performAccessibilityShortcut(
        displayId: Int,
        @UserShortcutType shortcutType: Int,
        targetName: String,
    )

    /**
     * Returns list of [AccessibilityTargetModel] of the installed accessibility service,
     * accessibility activity, and allowlisting feature including accessibility feature's package
     * name, component id, etc.
     *
     * @param shortcutType The shortcut type.
     * @return The list of [AccessibilityTargetModel].
     */
    fun getAllAccessibilityTargetsInfo(
        @UserShortcutType shortcutType: Int
    ): List<AccessibilityTargetModel>

    /**
     * Returns list of [AccessibilityTargetModel] of assigned accessibility shortcuts from
     * [AccessibilityTargetHelper.getTargets] including accessibility feature's package name,
     * component id, etc.
     *
     * @param shortcutType The shortcut type.
     * @return The list of [AccessibilityTargetModel].
     */
    fun getSelectedAccessibilityTargetsInfo(
        @UserShortcutType shortcutType: Int
    ): List<AccessibilityTargetModel>
}

@SysUISingleton
class AccessibilityShortcutsRepositoryImpl
@Inject
constructor(
    @param:Application private val context: Context,
    private val accessibilityManager: AccessibilityManager,
    private val packageManager: PackageManager,
    private val userTracker: UserTracker,
    @Main private val resources: Resources,
    @Background private val backgroundDispatcher: CoroutineDispatcher,
    @param:Main private val handler: Handler,
) : AccessibilityShortcutsRepository {
    // Action key
    private val MODIFIER_KEY = KeyEvent.META_META_ON

    private val keyCodeMap =
        mapOf(
            KeyEvent.KEYCODE_M to "M",
            KeyEvent.KEYCODE_T to "T",
            KeyEvent.KEYCODE_S to "S",
            KeyEvent.KEYCODE_V to "V",
        )

    override suspend fun getKeyGestureConfirmInfo(
        keyGestureType: Int,
        metaState: Int,
        keyCode: Int,
        targetName: String,
        displayId: Int,
    ): KeyGestureConfirmInfo? {
        // TODO: b/419026315 - Update the secondary modifier key label.
        val secondaryModifierLabel =
            ShortcutHelperKeys.modifierLabels[MODIFIER_KEY xor metaState] ?: return null
        val keyCodeLabel = keyCodeMap[keyCode] ?: return null

        when (keyGestureType) {
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION,
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_SCREEN_READER,
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_VOICE_ACCESS -> {
                val featureName = getFeatureName(keyGestureType, targetName) ?: return null
                val title = getDialogTitle(keyGestureType, featureName) ?: return null
                val content =
                    getDialogContent(
                        keyGestureType,
                        secondaryModifierLabel.invoke(context),
                        keyCodeLabel,
                        featureName,
                    ) ?: return null

                val ttsText =
                    if (keyGestureType == KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_SCREEN_READER) {
                        resources.getString(
                            R.string.accessibility_key_gesture_dialog_screen_reader_tts,
                            secondaryModifierLabel.invoke(context),
                            keyCodeLabel,
                            featureName,
                        )
                    } else {
                        null
                    }

                return KeyGestureConfirmInfo(
                    keyGestureType,
                    title,
                    content,
                    targetName,
                    getActionKeyIconResId(),
                    displayId,
                    ttsText,
                )
            }
            else -> {
                val featureNameToIntro =
                    getFeatureNameToIntro(keyGestureType, targetName) ?: return null
                val title =
                    resources.getString(
                        R.string.accessibility_key_gesture_shortcut_not_yet_enabled_dialog_title,
                        featureNameToIntro.first,
                    )
                val content =
                    TextUtils.expandTemplate(
                        resources.getText(R.string.accessibility_key_gesture_dialog_content),
                        secondaryModifierLabel.invoke(context),
                        keyCodeLabel,
                        featureNameToIntro.first,
                        featureNameToIntro.second,
                    )

                return KeyGestureConfirmInfo(
                    keyGestureType,
                    title,
                    content,
                    targetName,
                    getActionKeyIconResId(),
                    displayId,
                    null,
                )
            }
        }
    }

    override fun createTtsPromptForText(text: CharSequence): TtsPrompt {
        return TtsPrompt(context, handler, FrameworkObjectProvider(), text)
    }

    @SuppressLint("MissingPermission") // android.permission.MANAGE_ACCESSIBILITY
    override fun enableShortcutsForTargets(
        enable: Boolean,
        @UserShortcutType shortcutType: Int,
        targetName: String,
    ) {
        accessibilityManager.enableShortcutsForTargets(
            enable,
            shortcutType,
            setOf(targetName),
            userTracker.userId,
        )
    }

    @SuppressLint("MissingPermission") // android.permission.MANAGE_ACCESSIBILITY
    override fun enableMagnificationAndZoomIn(displayId: Int) {
        accessibilityManager.enableMagnificationAndZoomIn(displayId)
    }

    @SuppressLint("MissingPermission") // android.permission.MANAGE_ACCESSIBILITY
    override fun performAccessibilityShortcut(
        displayId: Int,
        @UserShortcutType shortcutType: Int,
        targetName: String,
    ) {
        accessibilityManager.performAccessibilityShortcut(displayId, shortcutType, targetName)
    }

    override fun getAllAccessibilityTargetsInfo(
        @UserShortcutType shortcutType: Int
    ): List<AccessibilityTargetModel> =
        AccessibilityTargetHelper.getInstalledTargets(context, shortcutType).map {
            it.toAccessibilityTargetModel(shortcutType)
        }

    override fun getSelectedAccessibilityTargetsInfo(
        @UserShortcutType shortcutType: Int
    ): List<AccessibilityTargetModel> =
        AccessibilityTargetHelper.getTargets(context, shortcutType).map {
            it.toAccessibilityTargetModel(shortcutType)
        }

    private fun AccessibilityTarget.toAccessibilityTargetModel(
        @UserShortcutType shortcutType: Int
    ): AccessibilityTargetModel =
        AccessibilityTargetModel(
            shortcutType,
            targetName = id,
            featureName = label.toString(),
            icon = icon,
            isAssigned = isShortcutEnabled,
            isToggleable = isToggleable,
            isToggleOn = if (isToggleable) isStateOn else null,
        )

    private suspend fun getFeatureName(keyGestureType: Int, targetName: String): CharSequence? {
        return when (keyGestureType) {
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION ->
                resources.getString(
                    com.android.settingslib.R.string.accessibility_screen_magnification_title
                )
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_SCREEN_READER,
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_VOICE_ACCESS -> {
                val componentName = ComponentName.unflattenFromString(targetName)
                withContext(backgroundDispatcher) {
                    accessibilityManager
                        .getInstalledServiceInfoWithComponentName(componentName)
                        ?.resolveInfo
                        ?.loadLabel(packageManager)
                        ?.let { formatFeatureName(it) }
                }
            }
            else -> null
        }
    }

    private suspend fun getDialogTitle(keyGestureType: Int, featureName: CharSequence): String? {
        return when (keyGestureType) {
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION -> {
                if (Flags.enableMagnifyMagnificationKeyGestureDialog()) {
                    resources.getString(
                        R.string.accessibility_key_gesture_magnification_dialog_title,
                        featureName,
                    )
                } else {
                    resources.getString(
                        R.string.accessibility_key_gesture_shortcut_not_yet_enabled_dialog_title,
                        featureName,
                    )
                }
            }
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_SCREEN_READER,
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_VOICE_ACCESS -> {
                resources.getString(
                    R.string.accessibility_key_gesture_shortcut_not_yet_enabled_dialog_title,
                    featureName,
                )
            }
            else -> null
        }
    }

    private fun getDialogContent(
        keyGestureType: Int,
        secondaryModifierLabel: String,
        keyCodeLabel: String,
        featureName: CharSequence,
    ): CharSequence? {
        val contentTemplateResId: Int? =
            when (keyGestureType) {
                KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION ->
                    R.string.accessibility_key_gesture_magnification_dialog_content
                KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_SCREEN_READER ->
                    R.string.accessibility_key_gesture_screen_reader_dialog_content
                KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_VOICE_ACCESS ->
                    R.string.accessibility_key_gesture_voice_access_dialog_content
                else -> null
            }

        return contentTemplateResId?.let { resId ->
            val contentTemplate = resources.getText(resId)
            TextUtils.expandTemplate(
                contentTemplate,
                secondaryModifierLabel,
                keyCodeLabel,
                featureName,
            )
        }
    }

    private suspend fun getFeatureNameToIntro(
        keyGestureType: Int,
        targetName: String,
    ): Pair<CharSequence, CharSequence>? {
        return when (keyGestureType) {
            KeyGestureEvent.KEY_GESTURE_TYPE_ACTIVATE_SELECT_TO_SPEAK -> {
                val accessibilityServiceInfo =
                    withContext(backgroundDispatcher) {
                        accessibilityManager.getInstalledServiceInfoWithComponentName(
                            ComponentName.unflattenFromString(targetName)
                        )
                    } ?: return null

                val featureName =
                    formatFeatureName(
                        accessibilityServiceInfo.resolveInfo.loadLabel(packageManager)
                    )

                val intro = accessibilityServiceInfo.loadIntro(packageManager) ?: ""

                Pair(featureName, intro)
            }
            else -> null
        }
    }

    // Get the service name and bidi wrap it to protect from bidi side effects.
    private fun formatFeatureName(label: CharSequence): CharSequence {
        val locale = context.resources.configuration.getLocales().get(0)
        return BidiFormatter.getInstance(locale).unicodeWrap(label)
    }

    private fun getActionKeyIconResId(): Int {
        // TODO: b/419026315 - Update the modifier key icon res id based on keyboard device.
        return ShortcutHelperKeys.metaModifierIconResId
    }
}
