/*
 * Copyright (C) 2026 The Android Open Source Project
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

import android.app.ActivityManager
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import android.view.Display.DEFAULT_DISPLAY
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestShellExecutor
import com.android.wm.shell.shared.desktopmode.DesktopScrimListener
import com.android.wm.shell.sysui.ShellController
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Test class for {@link DesktopScrimController}
 *
 * Usage: atest WMShellUnitTests:DesktopScrimControllerTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class DesktopScrimControllerTest : ShellTestCase() {
    private val DEFAULT_USER_ID = ActivityManager.getCurrentUser()
    private val desktopRemoteListener = mock<DesktopRemoteListener>()
    private val desktopScrimListener = mock<DesktopScrimListener>()
    private val desktopTasksController = mock<DesktopTasksController>()
    private val shellController = mock<ShellController>()

    private val shellExecutor = TestShellExecutor()
    private lateinit var controller: DesktopScrimController

    @Before
    fun setUp() {
        whenever(shellController.currentUserId).thenReturn(DEFAULT_USER_ID)
        controller =
            DesktopScrimController(desktopRemoteListener, desktopTasksController, shellController)
    }

    @Test
    @EnableFlags(com.android.systemui.Flags.FLAG_OPAQUE_STATUS_BAR)
    fun updateImmersiveAppearance_notifyDesktopScrimListener() {
        controller.addDesktopScrimListener(desktopScrimListener, shellExecutor)
        controller.updateDesktopScrim(DEFAULT_DISPLAY, true /* applyLightOutEffect */)
        shellExecutor.flushAll()
        verify(desktopScrimListener).onDesktopScrimEffectChanged(DEFAULT_DISPLAY, true)

        clearInvocations(desktopScrimListener)
        controller.removeDesktopScrimListener(desktopScrimListener)
        controller.updateDesktopScrim(DEFAULT_DISPLAY, true /* applyLightOutEffect */)
        shellExecutor.flushAll()
        verify(desktopScrimListener, never()).onDesktopScrimEffectChanged(DEFAULT_DISPLAY, true)
    }

    // TODO(b/489916353): Add tests to cover all other methods in DesktopScrimController
}
