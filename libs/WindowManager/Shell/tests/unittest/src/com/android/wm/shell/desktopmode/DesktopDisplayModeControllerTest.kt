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

package com.android.wm.shell.desktopmode

import android.app.ActivityManager.RunningTaskInfo
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED
import android.content.ContentResolver
import android.os.Binder
import android.provider.Settings
import android.provider.Settings.Global.DEVELOPMENT_FORCE_DESKTOP_MODE_ON_EXTERNAL_DISPLAYS
import android.testing.AndroidTestingRunner
import android.view.Display.DEFAULT_DISPLAY
import android.view.IWindowManager
import android.view.WindowManager.TRANSIT_CHANGE
import android.window.DisplayAreaInfo
import android.window.WindowContainerTransaction
import androidx.test.filters.SmallTest
import com.android.dx.mockito.inline.extended.ExtendedMockito.never
import com.android.wm.shell.MockToken
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestRunningTaskInfoBuilder
import com.android.wm.shell.desktopmode.desktopwallpaperactivity.DesktopWallpaperActivityTokenProvider
import com.android.wm.shell.transition.Transitions
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Test class for [DesktopDisplayModeController]
 *
 * Usage: atest WMShellUnitTests:DesktopDisplayModeControllerTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class DesktopDisplayModeControllerTest : ShellTestCase() {
    private val transitions = mock<Transitions>()
    private val rootTaskDisplayAreaOrganizer = mock<RootTaskDisplayAreaOrganizer>()
    private val mockWindowManager = mock<IWindowManager>()
    private val shellTaskOrganizer = mock<ShellTaskOrganizer>()
    private val desktopWallpaperActivityTokenProvider =
        mock<DesktopWallpaperActivityTokenProvider>()

    private lateinit var controller: DesktopDisplayModeController

    private val runningTasks = mutableListOf<RunningTaskInfo>()
    private val freeformTask =
        TestRunningTaskInfoBuilder().setWindowingMode(WINDOWING_MODE_FREEFORM).build()
    private val fullscreenTask =
        TestRunningTaskInfoBuilder().setWindowingMode(WINDOWING_MODE_FULLSCREEN).build()
    private val defaultTDA = DisplayAreaInfo(MockToken().token(), DEFAULT_DISPLAY, 0)
    private val wallpaperToken = MockToken().token()

    @Before
    fun setUp() {
        whenever(transitions.startTransition(anyInt(), any(), isNull())).thenReturn(Binder())
        whenever(rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(DEFAULT_DISPLAY))
            .thenReturn(defaultTDA)
        controller =
            DesktopDisplayModeController(
                context,
                transitions,
                rootTaskDisplayAreaOrganizer,
                mockWindowManager,
                shellTaskOrganizer,
                desktopWallpaperActivityTokenProvider,
            )
        runningTasks.add(freeformTask)
        runningTasks.add(fullscreenTask)
        whenever(shellTaskOrganizer.getRunningTasks(anyInt())).thenReturn(ArrayList(runningTasks))
        whenever(desktopWallpaperActivityTokenProvider.getToken()).thenReturn(wallpaperToken)
    }

    private fun testDisplayWindowingModeSwitch(
        defaultWindowingMode: Int,
        extendedDisplayEnabled: Boolean,
        expectTransition: Boolean,
    ) {
        defaultTDA.configuration.windowConfiguration.windowingMode = defaultWindowingMode
        whenever(mockWindowManager.getWindowingMode(anyInt())).thenReturn(defaultWindowingMode)
        val settingsSession =
            ExtendedDisplaySettingsSession(
                context.contentResolver,
                if (extendedDisplayEnabled) 1 else 0,
            )

        settingsSession.use {
            connectExternalDisplay()
            defaultTDA.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_FREEFORM
            disconnectExternalDisplay()

            if (expectTransition) {
                val arg = argumentCaptor<WindowContainerTransaction>()
                verify(transitions, times(2))
                    .startTransition(eq(TRANSIT_CHANGE), arg.capture(), isNull())
                assertThat(arg.firstValue.changes[defaultTDA.token.asBinder()]?.windowingMode)
                    .isEqualTo(WINDOWING_MODE_FREEFORM)
                assertThat(arg.firstValue.changes[wallpaperToken.asBinder()]?.windowingMode)
                    .isEqualTo(WINDOWING_MODE_FULLSCREEN)
                assertThat(arg.secondValue.changes[defaultTDA.token.asBinder()]?.windowingMode)
                    .isEqualTo(defaultWindowingMode)
                assertThat(arg.secondValue.changes[wallpaperToken.asBinder()]?.windowingMode)
                    .isEqualTo(WINDOWING_MODE_FULLSCREEN)
            } else {
                verify(transitions, never()).startTransition(eq(TRANSIT_CHANGE), any(), isNull())
            }
        }
    }

    @Test
    fun displayWindowingModeSwitchOnDisplayConnected_extendedDisplayDisabled() {
        testDisplayWindowingModeSwitch(
            defaultWindowingMode = WINDOWING_MODE_FULLSCREEN,
            extendedDisplayEnabled = false,
            expectTransition = false,
        )
    }

    @Test
    fun displayWindowingModeSwitchOnDisplayConnected_fullscreenDisplay() {
        testDisplayWindowingModeSwitch(
            defaultWindowingMode = WINDOWING_MODE_FULLSCREEN,
            extendedDisplayEnabled = true,
            expectTransition = true,
        )
    }

    @Test
    fun displayWindowingModeSwitchOnDisplayConnected_freeformDisplay() {
        testDisplayWindowingModeSwitch(
            defaultWindowingMode = WINDOWING_MODE_FREEFORM,
            extendedDisplayEnabled = true,
            expectTransition = false,
        )
    }

    @Test
    fun displayWindowingModeSwitch_existingTasksOnConnected() {
        defaultTDA.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_FULLSCREEN
        whenever(mockWindowManager.getWindowingMode(anyInt())).thenReturn(WINDOWING_MODE_FULLSCREEN)

        ExtendedDisplaySettingsSession(context.contentResolver, 1).use {
            connectExternalDisplay()

            val arg = argumentCaptor<WindowContainerTransaction>()
            verify(transitions, times(1))
                .startTransition(eq(TRANSIT_CHANGE), arg.capture(), isNull())
            assertThat(arg.firstValue.changes[freeformTask.token.asBinder()]?.windowingMode)
                .isEqualTo(WINDOWING_MODE_UNDEFINED)
            assertThat(arg.firstValue.changes[fullscreenTask.token.asBinder()]?.windowingMode)
                .isEqualTo(WINDOWING_MODE_FULLSCREEN)
        }
    }

    @Test
    fun displayWindowingModeSwitch_existingTasksOnDisconnected() {
        defaultTDA.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_FREEFORM
        whenever(mockWindowManager.getWindowingMode(anyInt())).thenAnswer {
            WINDOWING_MODE_FULLSCREEN
        }

        ExtendedDisplaySettingsSession(context.contentResolver, 1).use {
            disconnectExternalDisplay()

            val arg = argumentCaptor<WindowContainerTransaction>()
            verify(transitions, times(1))
                .startTransition(eq(TRANSIT_CHANGE), arg.capture(), isNull())
            assertThat(arg.firstValue.changes[freeformTask.token.asBinder()]?.windowingMode)
                .isEqualTo(WINDOWING_MODE_FREEFORM)
            assertThat(arg.firstValue.changes[fullscreenTask.token.asBinder()]?.windowingMode)
                .isEqualTo(WINDOWING_MODE_UNDEFINED)
        }
    }

    private fun connectExternalDisplay() {
        whenever(rootTaskDisplayAreaOrganizer.getDisplayIds())
            .thenReturn(intArrayOf(DEFAULT_DISPLAY, EXTERNAL_DISPLAY_ID))
        controller.refreshDisplayWindowingMode()
    }

    private fun disconnectExternalDisplay() {
        whenever(rootTaskDisplayAreaOrganizer.getDisplayIds())
            .thenReturn(intArrayOf(DEFAULT_DISPLAY))
        controller.refreshDisplayWindowingMode()
    }

    private class ExtendedDisplaySettingsSession(
        private val contentResolver: ContentResolver,
        private val overrideValue: Int,
    ) : AutoCloseable {
        private val settingName = DEVELOPMENT_FORCE_DESKTOP_MODE_ON_EXTERNAL_DISPLAYS
        private val initialValue = Settings.Global.getInt(contentResolver, settingName, 0)

        init {
            Settings.Global.putInt(contentResolver, settingName, overrideValue)
        }

        override fun close() {
            Settings.Global.putInt(contentResolver, settingName, initialValue)
        }
    }

    private companion object {
        const val EXTERNAL_DISPLAY_ID = 100
    }
}
