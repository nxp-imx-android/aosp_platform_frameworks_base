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
import android.hardware.input.InputManager
import android.os.Binder
import android.os.Handler
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.annotations.UsesFlags
import android.platform.test.flag.junit.FlagsParameterization
import android.provider.Settings
import android.provider.Settings.Global.DEVELOPMENT_FORCE_DESKTOP_MODE_ON_EXTERNAL_DISPLAYS
import android.view.Display
import android.view.Display.DEFAULT_DISPLAY
import android.view.IWindowManager
import android.view.WindowManager.TRANSIT_CHANGE
import android.window.DisplayAreaInfo
import android.window.WindowContainerTransaction
import androidx.test.filters.SmallTest
import com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn
import com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession
import com.android.dx.mockito.inline.extended.ExtendedMockito.never
import com.android.dx.mockito.inline.extended.StaticMockitoSession
import com.android.server.display.feature.flags.Flags as DisplayFlags
import com.android.window.flags.Flags
import com.android.wm.shell.MockToken
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestRunningTaskInfoBuilder
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.desktopmode.desktopwallpaperactivity.DesktopWallpaperActivityTokenProvider
import com.android.wm.shell.shared.desktopmode.DesktopModeStatus
import com.android.wm.shell.transition.Transitions
import com.google.common.truth.Truth.assertThat
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import com.google.testing.junit.testparameterinjector.TestParameterValuesProvider
import org.junit.After
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
import org.mockito.quality.Strictness

/**
 * Test class for [DesktopDisplayModeController]
 *
 * Usage: atest WMShellUnitTests:DesktopDisplayModeControllerTest
 */
@SmallTest
@RunWith(TestParameterInjector::class)
@UsesFlags(com.android.server.display.feature.flags.Flags::class)
class DesktopDisplayModeControllerTest(
    @TestParameter(valuesProvider = FlagsParameterizationProvider::class)
    flags: FlagsParameterization
) : ShellTestCase() {
    private val transitions = mock<Transitions>()
    private val rootTaskDisplayAreaOrganizer = mock<RootTaskDisplayAreaOrganizer>()
    private val mockWindowManager = mock<IWindowManager>()
    private val shellTaskOrganizer = mock<ShellTaskOrganizer>()
    private val desktopWallpaperActivityTokenProvider =
        mock<DesktopWallpaperActivityTokenProvider>()
    private val inputManager = mock<InputManager>()
    private val displayController = mock<DisplayController>()
    private val mainHandler = mock<Handler>()

    private lateinit var controller: DesktopDisplayModeController

    private val runningTasks = mutableListOf<RunningTaskInfo>()
    private val freeformTask =
        TestRunningTaskInfoBuilder().setWindowingMode(WINDOWING_MODE_FREEFORM).build()
    private val fullscreenTask =
        TestRunningTaskInfoBuilder().setWindowingMode(WINDOWING_MODE_FULLSCREEN).build()
    private val defaultTDA = DisplayAreaInfo(MockToken().token(), DEFAULT_DISPLAY, 0)
    private val wallpaperToken = MockToken().token()
    private val externalDisplay = mock<Display>()

    private lateinit var extendedDisplaySettingsRestoreSession:
        ExtendedDisplaySettingsRestoreSession

    private lateinit var mockitoSession: StaticMockitoSession

    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    @Before
    fun setUp() {
        mockitoSession =
            mockitoSession()
                .strictness(Strictness.LENIENT)
                .spyStatic(DesktopModeStatus::class.java)
                .startMocking()
        extendedDisplaySettingsRestoreSession =
            ExtendedDisplaySettingsRestoreSession(context.contentResolver)
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
                inputManager,
                displayController,
                mainHandler,
            )
        runningTasks.add(freeformTask)
        runningTasks.add(fullscreenTask)
        whenever(shellTaskOrganizer.getRunningTasks(anyInt())).thenReturn(ArrayList(runningTasks))
        whenever(desktopWallpaperActivityTokenProvider.getToken()).thenReturn(wallpaperToken)
        whenever(displayController.getDisplay(EXTERNAL_DISPLAY_ID)).thenReturn(externalDisplay)
        setTabletModeStatus(SwitchState.UNKNOWN)
    }

    @After
    fun tearDown() {
        extendedDisplaySettingsRestoreSession.restore()
        mockitoSession.finishMocking()
    }

    private fun testDisplayWindowingModeSwitchOnDisplayConnected(expectToSwitch: Boolean) {
        defaultTDA.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_FULLSCREEN
        whenever(mockWindowManager.getWindowingMode(anyInt())).thenReturn(WINDOWING_MODE_FULLSCREEN)
        setExtendedMode(true)

        connectExternalDisplay()
        if (expectToSwitch) {
            // Assumes [connectExternalDisplay] properly triggered the switching transition.
            // Will verify the transition later along with [disconnectExternalDisplay].
            defaultTDA.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_FREEFORM
        }
        disconnectExternalDisplay()

        if (expectToSwitch) {
            val arg = argumentCaptor<WindowContainerTransaction>()
            verify(transitions, times(2))
                .startTransition(eq(TRANSIT_CHANGE), arg.capture(), isNull())
            assertThat(arg.firstValue.changes[defaultTDA.token.asBinder()]?.windowingMode)
                .isEqualTo(WINDOWING_MODE_FREEFORM)
            assertThat(arg.firstValue.changes[wallpaperToken.asBinder()]?.windowingMode)
                .isEqualTo(WINDOWING_MODE_FULLSCREEN)
            assertThat(arg.secondValue.changes[defaultTDA.token.asBinder()]?.windowingMode)
                .isEqualTo(WINDOWING_MODE_FULLSCREEN)
            assertThat(arg.secondValue.changes[wallpaperToken.asBinder()]?.windowingMode)
                .isEqualTo(WINDOWING_MODE_FULLSCREEN)
        } else {
            verify(transitions, never()).startTransition(eq(TRANSIT_CHANGE), any(), isNull())
        }
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_DISPLAY_WINDOWING_MODE_SWITCHING)
    fun displayWindowingModeSwitchOnDisplayConnected_flagDisabled() {
        // When the flag is disabled, never switch.
        testDisplayWindowingModeSwitchOnDisplayConnected(/* expectToSwitch= */ false)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DISPLAY_WINDOWING_MODE_SWITCHING)
    fun displayWindowingModeSwitchOnDisplayConnected() {
        testDisplayWindowingModeSwitchOnDisplayConnected(/* expectToSwitch= */ true)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DISPLAY_WINDOWING_MODE_SWITCHING)
    @DisableFlags(Flags.FLAG_FORM_FACTOR_BASED_DESKTOP_FIRST_SWITCH)
    fun testTargetWindowingMode_formfactorDisabled(
        @TestParameter param: ExternalDisplayBasedTargetModeTestCase,
        @TestParameter tabletModeStatus: SwitchState,
    ) {
        whenever(mockWindowManager.getWindowingMode(anyInt()))
            .thenReturn(param.defaultWindowingMode)
        if (param.hasExternalDisplay) {
            connectExternalDisplay()
        } else {
            disconnectExternalDisplay()
        }
        setTabletModeStatus(tabletModeStatus)
        setExtendedMode(param.extendedDisplayEnabled)

        assertThat(controller.getTargetWindowingModeForDefaultDisplay())
            .isEqualTo(param.expectedWindowingMode)
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DISPLAY_WINDOWING_MODE_SWITCHING,
        Flags.FLAG_FORM_FACTOR_BASED_DESKTOP_FIRST_SWITCH,
    )
    fun testTargetWindowingMode(@TestParameter param: FormFactorBasedTargetModeTestCase) {
        if (param.hasExternalDisplay) {
            connectExternalDisplay()
        } else {
            disconnectExternalDisplay()
        }
        setTabletModeStatus(param.tabletModeStatus)
        setExtendedMode(param.extendedDisplayEnabled)

        assertThat(controller.getTargetWindowingModeForDefaultDisplay())
            .isEqualTo(param.expectedWindowingMode)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DISPLAY_WINDOWING_MODE_SWITCHING)
    fun displayWindowingModeSwitch_existingTasksOnConnected() {
        defaultTDA.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_FULLSCREEN
        whenever(mockWindowManager.getWindowingMode(anyInt())).thenReturn(WINDOWING_MODE_FULLSCREEN)
        setExtendedMode(true)

        connectExternalDisplay()

        val arg = argumentCaptor<WindowContainerTransaction>()
        verify(transitions, times(1)).startTransition(eq(TRANSIT_CHANGE), arg.capture(), isNull())
        assertThat(arg.firstValue.changes[freeformTask.token.asBinder()]?.windowingMode)
            .isEqualTo(WINDOWING_MODE_UNDEFINED)
        assertThat(arg.firstValue.changes[fullscreenTask.token.asBinder()]?.windowingMode)
            .isEqualTo(WINDOWING_MODE_FULLSCREEN)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DISPLAY_WINDOWING_MODE_SWITCHING)
    fun displayWindowingModeSwitch_existingTasksOnDisconnected() {
        defaultTDA.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_FREEFORM
        whenever(mockWindowManager.getWindowingMode(anyInt())).thenAnswer {
            WINDOWING_MODE_FULLSCREEN
        }
        setExtendedMode(true)

        disconnectExternalDisplay()

        val arg = argumentCaptor<WindowContainerTransaction>()
        verify(transitions, times(1)).startTransition(eq(TRANSIT_CHANGE), arg.capture(), isNull())
        assertThat(arg.firstValue.changes[freeformTask.token.asBinder()]?.windowingMode)
            .isEqualTo(WINDOWING_MODE_FREEFORM)
        assertThat(arg.firstValue.changes[fullscreenTask.token.asBinder()]?.windowingMode)
            .isEqualTo(WINDOWING_MODE_UNDEFINED)
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

    private fun setTabletModeStatus(status: SwitchState) {
        whenever(inputManager.isInTabletMode()).thenReturn(status.value)
    }

    private fun setExtendedMode(enabled: Boolean) {
        if (DisplayFlags.enableDisplayContentModeManagement()) {
            doReturn(enabled).`when` {
                DesktopModeStatus.isDesktopModeSupportedOnDisplay(context, externalDisplay)
            }
        } else {
            Settings.Global.putInt(
                context.contentResolver,
                DEVELOPMENT_FORCE_DESKTOP_MODE_ON_EXTERNAL_DISPLAYS,
                if (enabled) 1 else 0,
            )
        }
    }

    private class ExtendedDisplaySettingsRestoreSession(
        private val contentResolver: ContentResolver
    ) {
        private val settingName = DEVELOPMENT_FORCE_DESKTOP_MODE_ON_EXTERNAL_DISPLAYS
        private val initialValue = Settings.Global.getInt(contentResolver, settingName, 0)

        fun restore() {
            Settings.Global.putInt(contentResolver, settingName, initialValue)
        }
    }

    private class FlagsParameterizationProvider : TestParameterValuesProvider() {
        override fun provideValues(
            context: TestParameterValuesProvider.Context
        ): List<FlagsParameterization> {
            return FlagsParameterization.allCombinationsOf(
                Flags.FLAG_FORM_FACTOR_BASED_DESKTOP_FIRST_SWITCH,
                DisplayFlags.FLAG_ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT,
            )
        }
    }

    companion object {
        const val EXTERNAL_DISPLAY_ID = 100

        enum class SwitchState(val value: Int) {
            UNKNOWN(InputManager.SWITCH_STATE_UNKNOWN),
            ON(InputManager.SWITCH_STATE_ON),
            OFF(InputManager.SWITCH_STATE_OFF),
        }

        enum class ExternalDisplayBasedTargetModeTestCase(
            val defaultWindowingMode: Int,
            val hasExternalDisplay: Boolean,
            val extendedDisplayEnabled: Boolean,
            val expectedWindowingMode: Int,
        ) {
            FREEFORM_EXTERNAL_EXTENDED(
                defaultWindowingMode = WINDOWING_MODE_FREEFORM,
                hasExternalDisplay = true,
                extendedDisplayEnabled = true,
                expectedWindowingMode = WINDOWING_MODE_FREEFORM,
            ),
            FULLSCREEN_EXTERNAL_EXTENDED(
                defaultWindowingMode = WINDOWING_MODE_FULLSCREEN,
                hasExternalDisplay = true,
                extendedDisplayEnabled = true,
                expectedWindowingMode = WINDOWING_MODE_FREEFORM,
            ),
            FREEFORM_NO_EXTERNAL_EXTENDED(
                defaultWindowingMode = WINDOWING_MODE_FREEFORM,
                hasExternalDisplay = false,
                extendedDisplayEnabled = true,
                expectedWindowingMode = WINDOWING_MODE_FREEFORM,
            ),
            FULLSCREEN_NO_EXTERNAL_EXTENDED(
                defaultWindowingMode = WINDOWING_MODE_FULLSCREEN,
                hasExternalDisplay = false,
                extendedDisplayEnabled = true,
                expectedWindowingMode = WINDOWING_MODE_FULLSCREEN,
            ),
            FREEFORM_EXTERNAL_MIRROR(
                defaultWindowingMode = WINDOWING_MODE_FREEFORM,
                hasExternalDisplay = true,
                extendedDisplayEnabled = false,
                expectedWindowingMode = WINDOWING_MODE_FREEFORM,
            ),
            FULLSCREEN_EXTERNAL_MIRROR(
                defaultWindowingMode = WINDOWING_MODE_FULLSCREEN,
                hasExternalDisplay = true,
                extendedDisplayEnabled = false,
                expectedWindowingMode = WINDOWING_MODE_FULLSCREEN,
            ),
            FREEFORM_NO_EXTERNAL_MIRROR(
                defaultWindowingMode = WINDOWING_MODE_FREEFORM,
                hasExternalDisplay = false,
                extendedDisplayEnabled = false,
                expectedWindowingMode = WINDOWING_MODE_FREEFORM,
            ),
            FULLSCREEN_NO_EXTERNAL_MIRROR(
                defaultWindowingMode = WINDOWING_MODE_FULLSCREEN,
                hasExternalDisplay = false,
                extendedDisplayEnabled = false,
                expectedWindowingMode = WINDOWING_MODE_FULLSCREEN,
            ),
        }

        enum class FormFactorBasedTargetModeTestCase(
            val hasExternalDisplay: Boolean,
            val extendedDisplayEnabled: Boolean,
            val tabletModeStatus: SwitchState,
            val expectedWindowingMode: Int,
        ) {
            EXTERNAL_EXTENDED_TABLET(
                hasExternalDisplay = true,
                extendedDisplayEnabled = true,
                tabletModeStatus = SwitchState.ON,
                expectedWindowingMode = WINDOWING_MODE_FREEFORM,
            ),
            NO_EXTERNAL_EXTENDED_TABLET(
                hasExternalDisplay = false,
                extendedDisplayEnabled = true,
                tabletModeStatus = SwitchState.ON,
                expectedWindowingMode = WINDOWING_MODE_FULLSCREEN,
            ),
            EXTERNAL_MIRROR_TABLET(
                hasExternalDisplay = true,
                extendedDisplayEnabled = false,
                tabletModeStatus = SwitchState.ON,
                expectedWindowingMode = WINDOWING_MODE_FULLSCREEN,
            ),
            NO_EXTERNAL_MIRROR_TABLET(
                hasExternalDisplay = false,
                extendedDisplayEnabled = false,
                tabletModeStatus = SwitchState.ON,
                expectedWindowingMode = WINDOWING_MODE_FULLSCREEN,
            ),
            EXTERNAL_EXTENDED_CLAMSHELL(
                hasExternalDisplay = true,
                extendedDisplayEnabled = true,
                tabletModeStatus = SwitchState.OFF,
                expectedWindowingMode = WINDOWING_MODE_FREEFORM,
            ),
            NO_EXTERNAL_EXTENDED_CLAMSHELL(
                hasExternalDisplay = false,
                extendedDisplayEnabled = true,
                tabletModeStatus = SwitchState.OFF,
                expectedWindowingMode = WINDOWING_MODE_FREEFORM,
            ),
            EXTERNAL_MIRROR_CLAMSHELL(
                hasExternalDisplay = true,
                extendedDisplayEnabled = false,
                tabletModeStatus = SwitchState.OFF,
                expectedWindowingMode = WINDOWING_MODE_FREEFORM,
            ),
            NO_EXTERNAL_MIRROR_CLAMSHELL(
                hasExternalDisplay = false,
                extendedDisplayEnabled = false,
                tabletModeStatus = SwitchState.OFF,
                expectedWindowingMode = WINDOWING_MODE_FREEFORM,
            ),
            EXTERNAL_EXTENDED_UNKNOWN(
                hasExternalDisplay = true,
                extendedDisplayEnabled = true,
                tabletModeStatus = SwitchState.UNKNOWN,
                expectedWindowingMode = WINDOWING_MODE_FREEFORM,
            ),
            NO_EXTERNAL_EXTENDED_UNKNOWN(
                hasExternalDisplay = false,
                extendedDisplayEnabled = true,
                tabletModeStatus = SwitchState.UNKNOWN,
                expectedWindowingMode = WINDOWING_MODE_FULLSCREEN,
            ),
            EXTERNAL_MIRROR_UNKNOWN(
                hasExternalDisplay = true,
                extendedDisplayEnabled = false,
                tabletModeStatus = SwitchState.UNKNOWN,
                expectedWindowingMode = WINDOWING_MODE_FULLSCREEN,
            ),
            NO_EXTERNAL_MIRROR_UNKNOWN(
                hasExternalDisplay = false,
                extendedDisplayEnabled = false,
                tabletModeStatus = SwitchState.UNKNOWN,
                expectedWindowingMode = WINDOWING_MODE_FULLSCREEN,
            ),
        }
    }
}
