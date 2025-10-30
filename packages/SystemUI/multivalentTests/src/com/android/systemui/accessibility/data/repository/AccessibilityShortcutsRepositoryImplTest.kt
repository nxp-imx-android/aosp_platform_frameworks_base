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

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.packageManager
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.content.res.mainResources
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.hardware.input.KeyGestureEvent
import android.os.Build
import android.os.fakeExecutorHandler
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.provider.Settings
import android.text.Annotation
import android.text.Spanned
import android.view.Display.DEFAULT_DISPLAY
import android.view.KeyEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.accessibilityManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.hardware.input.Flags
import com.android.internal.accessibility.AccessibilityShortcutController.MAGNIFICATION_CONTROLLER_NAME
import com.android.internal.accessibility.common.ShortcutConstants
import com.android.systemui.SysuiTestCase
import com.android.systemui.accessibility.shortcutchooser.shared.model.AccessibilityTargetModel
import com.android.systemui.concurrency.fakeExecutor
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.settings.userTracker
import com.android.systemui.testKosmosNew
import com.android.systemui.util.settings.fakeSettings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.spy
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class AccessibilityShortcutsRepositoryImplTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()
    private val accessibilityManager = kosmos.accessibilityManager
    private val packageManager = kosmos.packageManager
    private val userTracker = kosmos.userTracker
    private val secureSettings = kosmos.fakeSettings
    private val resources = kosmos.mainResources
    private val testScope = kosmos.testScope

    @get:Rule val setFlagsRule = SetFlagsRule()

    private lateinit var underTest: AccessibilityShortcutsRepositoryImpl

    @Before
    fun setUp() {
        underTest =
            AccessibilityShortcutsRepositoryImpl(
                context.apply {
                    addMockSystemService(AccessibilityManager::class.java, accessibilityManager)
                },
                accessibilityManager,
                packageManager,
                userTracker,
                secureSettings,
                resources,
                kosmos.testDispatcher,
                kosmos.fakeExecutorHandler,
            )

        whenever(packageManager.getDrawable(anyString(), anyInt(), any()))
            .thenReturn(ColorDrawable(Color.RED))
    }

    @Test
    fun getKeyGestureConfirmInfo_nonExistTypeReceived_isNull() {
        testScope.runTest {
            // Just test a random non-accessibility service type
            val info =
                underTest.getKeyGestureConfirmInfo(
                    KeyGestureEvent.KEY_GESTURE_TYPE_HOME,
                    0,
                    0,
                    "empty",
                    DEFAULT_DISPLAY,
                )

            assertThat(info).isNull()
        }
    }

    @EnableFlags(Flags.FLAG_ENABLE_MAGNIFY_MAGNIFICATION_KEY_GESTURE_DIALOG)
    @Test
    fun getKeyGestureConfirmInfo_onMagnificationTypeReceived_doNotEnableShortcut_getExpectedInfo() {
        testScope.runTest {
            val metaState = KeyEvent.META_META_ON or KeyEvent.META_ALT_ON

            val info =
                underTest.getKeyGestureConfirmInfo(
                    KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION,
                    metaState,
                    KeyEvent.KEYCODE_M,
                    getTargetNameByType(KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION),
                    DEFAULT_DISPLAY,
                )

            assertThat(info).isNotNull()
            assertThat(info!!.title).isEqualTo("Magnification keyboard shortcut turned on")
            val contentText = info.contentText
            assertThat(hasExpectedAnnotation(contentText)).isTrue()
            // `contentText` here is an instance of SpannableStringBuilder, so we only need to
            // compare its value here.
            assertThat(contentText.toString())
                .isEqualTo(
                    "Action icon + Alt + M is the keyboard shortcut to use Magnification, an" +
                        " accessibility feature. This allows you to quickly zoom in on the screen" +
                        " to make content larger. Once magnification is on, press Action icon +" +
                        " Alt and \"+\" or \"-\" to adjust zoom."
                )
        }
    }

    @DisableFlags(Flags.FLAG_ENABLE_MAGNIFY_MAGNIFICATION_KEY_GESTURE_DIALOG)
    @Test
    fun getKeyGestureConfirmInfo_onMagnificationTypeReceived_enableShortcut_getExpectedInfo() {
        testScope.runTest {
            val metaState = KeyEvent.META_META_ON or KeyEvent.META_ALT_ON

            val info =
                underTest.getKeyGestureConfirmInfo(
                    KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION,
                    metaState,
                    KeyEvent.KEYCODE_M,
                    getTargetNameByType(KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION),
                    DEFAULT_DISPLAY,
                )

            assertThat(info).isNotNull()
            assertThat(info!!.title).isEqualTo("Turn on Magnification keyboard shortcut?")
            val contentText = info.contentText
            assertThat(hasExpectedAnnotation(contentText)).isTrue()
            // `contentText` here is an instance of SpannableStringBuilder, so we only need to
            // compare its value here.
            assertThat(contentText.toString())
                .isEqualTo(
                    "Action icon + Alt + M is the keyboard shortcut to use Magnification, an" +
                        " accessibility feature. This allows you to quickly zoom in on the screen" +
                        " to make content larger. Once magnification is on, press Action icon +" +
                        " Alt and \"+\" or \"-\" to adjust zoom."
                )
        }
    }

    @Test
    fun getKeyGestureConfirmInfo_serviceUninstalled_isNull() {
        testScope.runTest {
            val metaState = KeyEvent.META_META_ON or KeyEvent.META_ALT_ON
            // If voice access isn't installed on device.
            whenever(accessibilityManager.getInstalledServiceInfoWithComponentName(anyOrNull()))
                .thenReturn(null)

            val info =
                underTest.getKeyGestureConfirmInfo(
                    KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_VOICE_ACCESS,
                    metaState,
                    KeyEvent.KEYCODE_V,
                    getTargetNameByType(KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_VOICE_ACCESS),
                    DEFAULT_DISPLAY,
                )

            assertThat(info).isNull()
        }
    }

    @Test
    fun getKeyGestureConfirmInfo_onVoiceAccessTypeReceived_getExpectedInfo() {
        testScope.runTest {
            val metaState = KeyEvent.META_META_ON or KeyEvent.META_ALT_ON
            val type = KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_VOICE_ACCESS

            val a11yServiceInfo = spy(getMockAccessibilityServiceInfo("Voice Access"))
            whenever(
                    accessibilityManager.getInstalledServiceInfoWithComponentName(
                        ComponentName.unflattenFromString(getTargetNameByType(type))
                    )
                )
                .thenReturn(a11yServiceInfo)

            val info =
                underTest.getKeyGestureConfirmInfo(
                    type,
                    metaState,
                    KeyEvent.KEYCODE_V,
                    getTargetNameByType(type),
                    DEFAULT_DISPLAY,
                )

            assertThat(info).isNotNull()
            assertThat(info!!.title).isEqualTo("Turn on Voice Access keyboard shortcut?")
            val contentText = info.contentText
            assertThat(hasExpectedAnnotation(contentText)).isTrue()
            // The intro should be the string below instead of the intro from
            // AccessibilityServiceInfo.
            assertThat(contentText.toString())
                .isEqualTo(
                    "Pressing Action icon + Alt + V turns on Voice Access, an accessibility" +
                        " feature. This lets you control your device hands-free."
                )
        }
    }

    @Test
    fun getTitleToContentForKeyGestureDialog_onScreenReaderTypeReceived_getExpectedInfo() {
        testScope.runTest {
            val metaState = KeyEvent.META_META_ON or KeyEvent.META_ALT_ON
            val type = KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_SCREEN_READER

            val a11yServiceInfo = spy(getMockAccessibilityServiceInfo("TalkBack"))
            whenever(
                    accessibilityManager.getInstalledServiceInfoWithComponentName(
                        ComponentName.unflattenFromString(getTargetNameByType(type))
                    )
                )
                .thenReturn(a11yServiceInfo)

            val info =
                underTest.getKeyGestureConfirmInfo(
                    type,
                    metaState,
                    KeyEvent.KEYCODE_T,
                    getTargetNameByType(type),
                    DEFAULT_DISPLAY,
                )

            assertThat(info).isNotNull()
            assertThat(info!!.title).isEqualTo("Turn on TalkBack keyboard shortcut?")
            val contentText = info.contentText
            assertThat(hasExpectedAnnotation(contentText)).isTrue()
            assertThat(contentText.toString())
                .isEqualTo(
                    "Action icon + Alt + T is the keyboard shortcut to use TalkBack. TalkBack is a" +
                        " screen reader that allows you to hear items spoken aloud. It can be" +
                        " helpful for people who have difficulty seeing the screen. This may" +
                        " change how your device works."
                )
            assertThat(info.contentSections).hasSize(4)
            assertThat(info.contentSections[0].heading)
                .isEqualTo(
                    "By turning on the keyboard shortcut, you will allow TalkBack to have full control of your device."
                )
            assertThat(info.contentSections[0].message).isNull()
            assertThat(info.contentSections[1].heading).isNull()
            assertThat(info.contentSections[1].message)
                .isEqualTo(
                    "Full control is appropriate for apps that help you with accessibility needs," +
                        " but not for most apps."
                )
            assertThat(info.contentSections[2].heading).isEqualTo("View and control screen")
            assertThat(info.contentSections[2].message)
                .isEqualTo(
                    "It can read all content on the screen and display content over other apps."
                )
            assertThat(info.contentSections[3].heading).isEqualTo("View and perform actions")
            assertThat(info.contentSections[3].message)
                .isEqualTo(
                    "It can track your interactions with an app or a hardware sensor, and" +
                        " interact with apps on your behalf."
                )
        }
    }

    @Test
    fun enableShortcutsForTargets_targetNameForMagnification_enabled() {
        val targetName = getTargetNameByType(KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION)

        underTest.enableShortcutsForTargets(
            enable = true,
            ShortcutConstants.UserShortcutType.KEY_GESTURE,
            targetName,
        )

        verify(accessibilityManager)
            .enableShortcutsForTargets(
                eq(true),
                eq(ShortcutConstants.UserShortcutType.KEY_GESTURE),
                eq(setOf(targetName)),
                anyInt(),
            )
    }

    @Test
    fun enableShortcutsForTargets_targetNameForS2S_enabled() {
        val targetName =
            getTargetNameByType(KeyGestureEvent.KEY_GESTURE_TYPE_ACTIVATE_SELECT_TO_SPEAK)

        underTest.enableShortcutsForTargets(
            enable = true,
            ShortcutConstants.UserShortcutType.KEY_GESTURE,
            targetName,
        )

        verify(accessibilityManager)
            .enableShortcutsForTargets(
                eq(true),
                eq(ShortcutConstants.UserShortcutType.KEY_GESTURE),
                eq(setOf(targetName)),
                anyInt(),
            )
    }

    @Test
    fun enableShortcutsForTargets_targetNameForVoiceAccess_enabled() {
        val targetName = getTargetNameByType(KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_VOICE_ACCESS)

        underTest.enableShortcutsForTargets(
            enable = true,
            ShortcutConstants.UserShortcutType.KEY_GESTURE,
            targetName,
        )

        verify(accessibilityManager)
            .enableShortcutsForTargets(
                eq(true),
                eq(ShortcutConstants.UserShortcutType.KEY_GESTURE),
                eq(setOf(targetName)),
                anyInt(),
            )
    }

    @Test
    fun enableShortcutsForTargets_targetNameForTalkBack_enabled() {
        val targetName = getTargetNameByType(KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_SCREEN_READER)

        underTest.enableShortcutsForTargets(
            enable = true,
            ShortcutConstants.UserShortcutType.KEY_GESTURE,
            targetName,
        )

        verify(accessibilityManager)
            .enableShortcutsForTargets(
                eq(true),
                eq(ShortcutConstants.UserShortcutType.KEY_GESTURE),
                eq(setOf(targetName)),
                anyInt(),
            )
    }

    @Test
    fun performAccessibilityShortcut_topRowKey_targetNameForTalkBack_performed() {
        val targetName = getTargetNameByType(KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_SCREEN_READER)

        underTest.performAccessibilityShortcut(
            DEFAULT_DISPLAY,
            ShortcutConstants.UserShortcutType.TOP_ROW_KEY,
            targetName,
        )

        verify(accessibilityManager)
            .performAccessibilityShortcut(
                eq(DEFAULT_DISPLAY),
                eq(ShortcutConstants.UserShortcutType.TOP_ROW_KEY),
                eq(targetName),
            )
    }

    @Test
    fun getAllAccessibilityTargets_returnsAccessibilityTargetModels() {
        testScope.runTest {
            whenever(accessibilityManager.getInstalledAccessibilityServiceList())
                .thenReturn(listOf(getMockAccessibilityServiceInfo("Test Service")))

            val targets =
                underTest
                    .getAllAccessibilityTargets(ShortcutConstants.UserShortcutType.HARDWARE)
                    .first()

            assertThat(targets.any { it.featureName == "Test Service" }).isTrue()
            assertThat(targets.any { it.featureName == "Magnification" }).isTrue()
            assertThat(targets.any { it.featureName == "Mouse keys" }).isTrue()
        }
    }

    @Test
    fun getAllAccessibilityTargets_whenAccessibilityServiceStateChanges_emitsUpdatedList() {
        testScope.runTest {
            val serviceName = "Test Service"
            val a11yServices = listOf(getMockAccessibilityServiceInfo(serviceName))
            whenever(accessibilityManager.getInstalledAccessibilityServiceList())
                .thenReturn(a11yServices)

            val emissions = mutableListOf<List<AccessibilityTargetModel>>()
            val job = launch {
                underTest
                    .getAllAccessibilityTargets(ShortcutConstants.UserShortcutType.HARDWARE)
                    .collect { emissions.add(it) }
            }
            testScheduler.advanceUntilIdle()

            val listenerCaptor =
                argumentCaptor<AccessibilityManager.AccessibilityServicesStateChangeListener>()
            verify(accessibilityManager)
                .addAccessibilityServicesStateChangeListener(listenerCaptor.capture())

            assertThat(emissions).hasSize(1)
            assertThat(emissions.last().any { it.featureName == serviceName && !it.isToggleOn })
                .isTrue()

            // Simulate a service state change.
            whenever(accessibilityManager.getEnabledAccessibilityServiceList(anyInt()))
                .thenReturn(a11yServices)
            listenerCaptor.firstValue.onAccessibilityServicesStateChanged(accessibilityManager)
            testScheduler.advanceUntilIdle()

            assertThat(emissions).hasSize(2)
            assertThat(emissions.last().any { it.featureName == serviceName && it.isToggleOn })
                .isTrue()

            job.cancel()

            verify(accessibilityManager)
                .removeAccessibilityServicesStateChangeListener(listenerCaptor.firstValue)
        }
    }

    @Test
    fun getAllAccessibilityTargets_whenSystemFeatureStateChanges_emitsUpdatedList() {
        testScope.runTest {
            val mouseKeysSettingsKey = Settings.Secure.ACCESSIBILITY_MOUSE_KEYS_ENABLED
            val mouseKeysFeatureName = "Mouse keys"
            val getContentObservers = {
                secureSettings.getContentObservers(
                    secureSettings.getUriFor(mouseKeysSettingsKey),
                    secureSettings.userId,
                )
            }

            assertThat(getContentObservers()).isEmpty()

            val emissions = mutableListOf<List<AccessibilityTargetModel>>()
            val job = launch {
                underTest
                    .getAllAccessibilityTargets(ShortcutConstants.UserShortcutType.HARDWARE)
                    .collect { emissions.add(it) }
            }
            testScheduler.advanceUntilIdle()

            assertThat(emissions).hasSize(1)
            assertThat(emissions.last().any { it.featureName == mouseKeysFeatureName }).isTrue()
            assertThat(getContentObservers()).hasSize(1)

            // Simulate a settings change.
            secureSettings.putBool(mouseKeysSettingsKey, true)
            kosmos.fakeExecutor.runAllReady()
            testScheduler.advanceUntilIdle()

            assertThat(emissions).hasSize(2)
            assertThat(emissions.last().any { it.featureName == mouseKeysFeatureName }).isTrue()

            job.cancel()

            assertThat(getContentObservers()).isEmpty()
        }
    }

    private fun getMockAccessibilityServiceInfo(featureName: String): AccessibilityServiceInfo {
        val packageName = "com.android.test"
        val className = featureName.replace(" ", "")
        val componentName = ComponentName(packageName, "$packageName.$className")
        val iconResId = 1

        return AccessibilityServiceInfo().apply {
            this.componentName = componentName
            this.resolveInfo =
                ResolveInfo().apply {
                    this.serviceInfo =
                        ServiceInfo().apply {
                            this.applicationInfo =
                                ApplicationInfo().apply {
                                    this.packageName = packageName
                                    this.icon = iconResId
                                    this.targetSdkVersion = Build.VERSION_CODES.BAKLAVA
                                }
                            this.name = className
                            this.packageName = packageName
                            this.icon = iconResId
                        }
                    this.nonLocalizedLabel = featureName
                    this.icon = iconResId
                    this.iconResourceId = iconResId
                }
        }
    }

    private fun getTargetNameByType(keyGestureType: Int): String {
        return when (keyGestureType) {
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION -> MAGNIFICATION_CONTROLLER_NAME
            KeyGestureEvent.KEY_GESTURE_TYPE_ACTIVATE_SELECT_TO_SPEAK ->
                resources.getString(
                    com.android.internal.R.string.config_defaultSelectToSpeakService
                )

            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_VOICE_ACCESS ->
                resources.getString(com.android.internal.R.string.config_defaultVoiceAccessService)

            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_SCREEN_READER ->
                resources.getString(
                    com.android.internal.R.string.config_defaultAccessibilityService
                )

            else -> ""
        }
    }

    // Return true if the text contains the expected annotation.
    private fun hasExpectedAnnotation(text: CharSequence?): Boolean {
        if (text == null || text !is Spanned) {
            return false
        }

        val annotations = text.getSpans(0, text.length, Annotation::class.java)
        for (annotation in annotations) {
            if (annotation.key == "id" && annotation.value == "action_key_icon") {
                return true
            }
        }
        return false
    }
}
