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
import android.hardware.input.KeyGestureEvent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.accessibility.AccessibilityShortcutController.MAGNIFICATION_CONTROLLER_NAME
import com.android.internal.accessibility.common.ShortcutConstants
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.settings.userTracker
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class AccessibilityShortcutsRepositoryImplTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val packageManager = kosmos.packageManager
    private val userTracker = kosmos.userTracker
    private val resources = kosmos.mainResources
    private val testScope = kosmos.testScope

    // mocks
    private val accessibilityManager: AccessibilityManager = mock(AccessibilityManager::class.java)

    private lateinit var underTest: AccessibilityShortcutsRepositoryImpl

    @Before
    fun setUp() {
        underTest =
            AccessibilityShortcutsRepositoryImpl(
                context,
                accessibilityManager,
                packageManager,
                userTracker,
                resources,
                kosmos.testDispatcher,
            )
    }

    @Test
    fun getKeyGestureConfirmInfo_nonExistTypeReceived_isNull() {
        testScope.runTest {
            // Just test a random non-accessibility service type
            val keyGestureConfirmInfo =
                underTest.getKeyGestureConfirmInfoByType(
                    KeyGestureEvent.KEY_GESTURE_TYPE_HOME,
                    0,
                    0,
                    "empty",
                )

            assertThat(keyGestureConfirmInfo).isNull()
        }
    }

    @Test
    fun getKeyGestureConfirmInfo_onMagnificationTypeReceived_getExpectedInfo() {
        testScope.runTest {
            val metaState = KeyEvent.META_META_ON or KeyEvent.META_ALT_ON

            val keyGestureConfirmInfo =
                underTest.getKeyGestureConfirmInfoByType(
                    KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION,
                    metaState,
                    KeyEvent.KEYCODE_M,
                    getTargetNameByType(KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION),
                )

            assertThat(keyGestureConfirmInfo).isNotNull()
            assertThat(keyGestureConfirmInfo?.title).isEqualTo("Turn on Magnification?")
            assertThat(keyGestureConfirmInfo?.contentText)
                .isEqualTo(
                    "Action + Alt + M is the keyboard shortcut to use Magnification. " +
                        "This allows you to quickly zoom in on the screen to make content larger."
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

            val keyGestureConfirmInfo =
                underTest.getKeyGestureConfirmInfoByType(
                    KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_VOICE_ACCESS,
                    metaState,
                    KeyEvent.KEYCODE_V,
                    getTargetNameByType(KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_VOICE_ACCESS),
                )

            assertThat(keyGestureConfirmInfo).isNull()
        }
    }

    @Test
    fun getKeyGestureConfirmInfo_onVoiceAccessTypeReceived_getExpectedInfo() {
        testScope.runTest {
            val metaState = KeyEvent.META_META_ON or KeyEvent.META_ALT_ON
            val type = KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_VOICE_ACCESS

            val a11yServiceInfo = spy(getMockAccessibilityServiceInfo("Voice access"))
            whenever(a11yServiceInfo.loadIntro(any())).thenReturn("Voice access Intro.")
            whenever(
                    accessibilityManager.getInstalledServiceInfoWithComponentName(
                        ComponentName.unflattenFromString(getTargetNameByType(type))
                    )
                )
                .thenReturn(a11yServiceInfo)

            val keyGestureConfirmInfo =
                underTest.getKeyGestureConfirmInfoByType(
                    type,
                    metaState,
                    KeyEvent.KEYCODE_V,
                    getTargetNameByType(type),
                )

            assertThat(keyGestureConfirmInfo).isNotNull()
            assertThat(keyGestureConfirmInfo?.title).isEqualTo("Turn on Voice access?")
            assertThat(keyGestureConfirmInfo?.contentText)
                .isEqualTo(
                    "Action + Alt + V is the keyboard shortcut to use Voice access. " +
                        "Voice access Intro."
                )
        }
    }

    @Test
    fun enableShortcutsForTargets_targetNameForMagnification_enabled() {
        val targetName = getTargetNameByType(KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION)

        underTest.enableShortcutsForTargets(targetName)

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

        underTest.enableShortcutsForTargets(targetName)

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

        underTest.enableShortcutsForTargets(targetName)

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

        underTest.enableShortcutsForTargets(targetName)

        verify(accessibilityManager)
            .enableShortcutsForTargets(
                eq(true),
                eq(ShortcutConstants.UserShortcutType.KEY_GESTURE),
                eq(setOf(targetName)),
                anyInt(),
            )
    }

    private fun getMockAccessibilityServiceInfo(featureName: String): AccessibilityServiceInfo {
        val packageName = "com.android.test"
        val componentName = ComponentName(packageName, "$packageName.test_a11y_service")

        val applicationInfo = mock(ApplicationInfo::class.java)
        applicationInfo.packageName = componentName.packageName

        val serviceInfo = spy(ServiceInfo())
        serviceInfo.packageName = componentName.packageName
        serviceInfo.name = componentName.className
        serviceInfo.applicationInfo = applicationInfo

        val resolveInfo = mock(ResolveInfo::class.java)
        resolveInfo.serviceInfo = serviceInfo
        whenever(resolveInfo.loadLabel(any())).thenReturn(featureName)

        val a11yServiceInfo = AccessibilityServiceInfo(resolveInfo, context)
        a11yServiceInfo.componentName = componentName
        return a11yServiceInfo
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
}
