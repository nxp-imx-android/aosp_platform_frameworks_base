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
import android.text.BidiFormatter
import android.text.TextUtils
import android.view.KeyEvent
import android.view.accessibility.AccessibilityManager
import com.android.internal.accessibility.common.ShortcutConstants
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.keyboard.shortcut.data.repository.ShortcutHelperKeys
import com.android.systemui.res.R
import com.android.systemui.settings.UserTracker
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Provides data related to first-time dialog for key gesture to enable accessibility services. */
interface AccessibilityShortcutsRepository {
    suspend fun getTitleToContentForKeyGestureDialog(
        keyGestureType: Int,
        metaState: Int,
        keyCode: Int,
        targetName: String,
    ): Pair<String, CharSequence>?

    fun getActionKeyIconResId(): Int

    fun enableShortcutsForTargets(targetName: String)
}

@SysUISingleton
class AccessibilityShortcutsRepositoryImpl
@Inject
constructor(
    private val context: Context,
    private val accessibilityManager: AccessibilityManager,
    private val packageManager: PackageManager,
    private val userTracker: UserTracker,
    @Main private val resources: Resources,
    @Background private val backgroundDispatcher: CoroutineDispatcher,
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

    override suspend fun getTitleToContentForKeyGestureDialog(
        keyGestureType: Int,
        metaState: Int,
        keyCode: Int,
        targetName: String,
    ): Pair<String, CharSequence>? {
        val featureNameToIntro = getFeatureNameToIntro(keyGestureType, targetName)

        // TODO: b/419026315 - Update the secondary modifier key label.
        val secondaryModifierLabel = ShortcutHelperKeys.modifierLabels[MODIFIER_KEY xor metaState]
        val keyCodeLabel = keyCodeMap[keyCode]
        if (featureNameToIntro == null || secondaryModifierLabel == null || keyCodeLabel == null) {
            return null
        }

        val title =
            resources.getString(
                R.string.accessibility_key_gesture_dialog_title,
                featureNameToIntro.first,
            )
        val contentText =
            TextUtils.expandTemplate(
                resources.getText(R.string.accessibility_key_gesture_dialog_content),
                secondaryModifierLabel.invoke(context),
                keyCodeLabel,
                featureNameToIntro.first,
                featureNameToIntro.second,
            )

        return Pair(title, contentText)
    }

    override fun getActionKeyIconResId(): Int {
        // TODO: b/419026315 - Update the modifier key icon res id based on keyboard device.
        return ShortcutHelperKeys.metaModifierIconResId
    }

    @SuppressLint("MissingPermission") // android.permission.MANAGE_ACCESSIBILITY
    override fun enableShortcutsForTargets(targetName: String) {
        accessibilityManager.enableShortcutsForTargets(
            /* enable= */ true,
            ShortcutConstants.UserShortcutType.KEY_GESTURE,
            setOf(targetName),
            userTracker.userId,
        )
    }

    private suspend fun getFeatureNameToIntro(
        keyGestureType: Int,
        targetName: String,
    ): Pair<CharSequence, CharSequence>? {
        return when (keyGestureType) {
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION ->
                Pair(
                    resources.getString(
                        com.android.settingslib.R.string.accessibility_screen_magnification_title
                    ),
                    resources.getString(R.string.accessibility_key_gesture_dialog_magnifier_intro),
                )
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_VOICE_ACCESS,
            KeyGestureEvent.KEY_GESTURE_TYPE_ACTIVATE_SELECT_TO_SPEAK,
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_SCREEN_READER -> {
                val accessibilityServiceInfo =
                    withContext(backgroundDispatcher) {
                        accessibilityManager.getInstalledServiceInfoWithComponentName(
                            ComponentName.unflattenFromString(targetName)
                        )
                    }

                if (accessibilityServiceInfo == null) {
                    null
                } else {
                    val featureName =
                        getFeatureName(
                            accessibilityServiceInfo.resolveInfo.loadLabel(packageManager)
                        )

                    val intro =
                        getFeatureIntro(
                            keyGestureType,
                            featureName,
                            accessibilityServiceInfo.loadIntro(packageManager),
                        )

                    Pair(featureName, intro)
                }
            }
            else -> null
        }
    }

    // Get the service name and bidi wrap it to protect from bidi side effects.
    private fun getFeatureName(label: CharSequence): CharSequence {
        val locale = context.resources.configuration.getLocales().get(0)
        return BidiFormatter.getInstance(locale).unicodeWrap(label)
    }

    /**
     * @param defaultIntro The intro we get from AccessibilityServiceInfo
     * @return A customize introduction
     */
    private fun getFeatureIntro(
        keyGestureType: Int,
        featureName: CharSequence,
        defaultIntro: CharSequence?,
    ): CharSequence {
        return when (keyGestureType) {
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_SCREEN_READER ->
                resources.getString(
                    R.string.accessibility_key_gesture_dialog_talkback_intro,
                    featureName,
                )

            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_VOICE_ACCESS ->
                resources.getString(R.string.accessibility_key_gesture_dialog_va_intro, featureName)

            else -> defaultIntro ?: ""
        }
    }
}
