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

package com.android.wm.shell.gamecontrols

import android.app.TaskInfo
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.wm.shell.R
import com.android.wm.shell.ShellTestCase
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Test class for [GameControlsHelper].
 *
 * Usage: atest WMShellUnitTests:GameControlsHelperTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
@EnableFlags(com.android.window.flags.Flags.FLAG_ENABLE_GAME_CONTROLS_ENTRY_IN_HANDLE_MENU)
class GameControlsHelperTest : ShellTestCase() {

    private val mockContext = mock<Context>()
    private val mockPackageManager = mock<PackageManager>()
    private val mockResources = mock<Resources>()
    private val mockTaskInfo = mock<TaskInfo>()
    private val mockActivityInfo = mock<ActivityInfo>()
    private val mockApplicationInfo = mock<ApplicationInfo>()
    private var intentCaptor = argumentCaptor<Intent>()

    @Before
    fun setUp() {
        whenever(mockContext.packageManager).thenReturn(mockPackageManager)
        whenever(mockContext.resources).thenReturn(mockResources)
        mockTaskInfo.topActivityInfo = mockActivityInfo
        mockActivityInfo.applicationInfo = mockApplicationInfo
    }

    @Test
    fun shouldShowGameControlsButton_returnsTrue_whenAllConditionsMet() {
        mockApplicationInfo.category = ApplicationInfo.CATEGORY_GAME
        whenever(mockResources.getString(R.string.config_gameControlsSystemFeature))
            .thenReturn("some.feature")
        whenever(mockPackageManager.hasSystemFeature("some.feature")).thenReturn(true)

        assertThat(GameControlsHelper.shouldShowGameControlsButton(mockContext, mockTaskInfo))
            .isTrue()
    }

    @Test
    fun shouldShowGameControlsButton_returnsFalse_whenNotGame() {
        mockApplicationInfo.category = ApplicationInfo.CATEGORY_UNDEFINED
        whenever(mockResources.getString(R.string.config_gameControlsSystemFeature))
            .thenReturn("some.feature")
        whenever(mockPackageManager.hasSystemFeature("some.feature")).thenReturn(true)

        assertThat(GameControlsHelper.shouldShowGameControlsButton(mockContext, mockTaskInfo))
            .isFalse()
    }

    @Test
    fun shouldShowGameControlsButton_returnsFalse_whenNoSystemFeature() {
        mockApplicationInfo.category = ApplicationInfo.CATEGORY_GAME
        whenever(mockResources.getString(R.string.config_gameControlsSystemFeature))
            .thenReturn("some.feature")
        whenever(mockPackageManager.hasSystemFeature("some.feature")).thenReturn(false)

        assertThat(GameControlsHelper.shouldShowGameControlsButton(mockContext, mockTaskInfo))
            .isFalse()
    }

    @Test
    fun shouldShowGameControlsButton_returnsFalse_whenSystemFeatureIsEmpty() {
        mockApplicationInfo.category = ApplicationInfo.CATEGORY_GAME
        whenever(mockResources.getString(R.string.config_gameControlsSystemFeature)).thenReturn("")

        assertThat(GameControlsHelper.shouldShowGameControlsButton(mockContext, mockTaskInfo))
            .isFalse()
    }

    @Test
    fun shouldShowGameControlsButton_returnsFalse_whenAppInfoIsNull() {
        mockTaskInfo.topActivityInfo = null

        whenever(mockResources.getString(R.string.config_gameControlsSystemFeature))
            .thenReturn("some.feature")
        whenever(mockPackageManager.hasSystemFeature("some.feature")).thenReturn(true)

        assertThat(GameControlsHelper.shouldShowGameControlsButton(mockContext, mockTaskInfo))
            .isFalse()
    }

    @Test
    fun onLaunchGameControls_sendsBroadcast() {
        whenever(mockResources.getString(R.string.config_gameControlsIntentReceiverPackage))
            .thenReturn("com.test.package")
        whenever(mockResources.getString(R.string.config_gameControlsIntentAction))
            .thenReturn("com.test.ACTION")

        GameControlsHelper.onLaunchGameControls(mockContext, mockTaskInfo)

        verify(mockContext, times(1)).sendBroadcast(intentCaptor.capture())
        val intent = intentCaptor.firstValue
        assertThat(intent.action).isEqualTo("com.test.ACTION")
        assertThat(intent.`package`).isEqualTo("com.test.package")
    }

    @Test
    fun onLaunchGameControls_noBroadcast_whenPackageEmpty() {
        whenever(mockResources.getString(R.string.config_gameControlsIntentReceiverPackage))
            .thenReturn("")
        whenever(mockResources.getString(R.string.config_gameControlsIntentAction))
            .thenReturn("com.test.ACTION")

        GameControlsHelper.onLaunchGameControls(mockContext, mockTaskInfo)

        verify(mockContext, never()).sendBroadcast(any())
    }

    @Test
    fun onLaunchGameControls_noBroadcast_whenActionEmpty() {
        whenever(mockResources.getString(R.string.config_gameControlsIntentReceiverPackage))
            .thenReturn("com.test.package")
        whenever(mockResources.getString(R.string.config_gameControlsIntentAction)).thenReturn("")

        GameControlsHelper.onLaunchGameControls(mockContext, mockTaskInfo)

        verify(mockContext, never()).sendBroadcast(any())
    }
}
