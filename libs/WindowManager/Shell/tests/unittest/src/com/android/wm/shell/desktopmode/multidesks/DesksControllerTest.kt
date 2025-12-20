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

package com.android.wm.shell.desktopmode.multidesks

import android.testing.AndroidTestingRunner
import android.view.Display.DEFAULT_DISPLAY
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestShellExecutor
import com.android.wm.shell.desktopmode.DesktopUserRepositories
import com.android.wm.shell.desktopmode.data.DesktopRepository
import com.android.wm.shell.shared.desktopmode.FakeDesktopConfig
import com.android.wm.shell.shared.desktopmode.FakeDesktopState
import com.android.wm.shell.sysui.ShellController
import com.android.wm.shell.sysui.ShellInit
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.TestScope
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Test class for [DesksController].
 *
 * Usage: atest WMShellUnitTests:DesksControllerTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class DesksControllerTest : ShellTestCase() {

    private val mockShellController = mock<ShellController>()
    private lateinit var desktopState: FakeDesktopState
    private lateinit var desktopConfig: FakeDesktopConfig
    private lateinit var desktopUserRepositories: DesktopUserRepositories
    private val repository: DesktopRepository
        get() = desktopUserRepositories.current

    private val testScope = TestScope()
    private lateinit var controller: DesksController

    @Before
    fun setUp() {
        desktopState = FakeDesktopState()
        desktopConfig = FakeDesktopConfig()
        whenever(mockShellController.currentUserId).thenReturn(USER_ID_1)
        desktopUserRepositories =
            DesktopUserRepositories(
                ShellInit(TestShellExecutor()),
                /* shellController= */ mockShellController,
                /* persistentRepository= */ mock(),
                /* repositoryInitializer= */ mock(),
                testScope.backgroundScope,
                testScope.backgroundScope,
                /* userManager= */ mock(),
                desktopState,
                desktopConfig,
            )

        controller =
            DesksController(
                mockShellController,
                desktopUserRepositories,
                desktopConfig,
                desktopState,
            )
    }

    @Test
    fun testCanCreateDesks_noLimit_returnsTrue() {
        desktopConfig.maxDeskLimit = DESK_LIMIT_NO_LIMIT

        val canCreate = controller.canCreateDesks(USER_ID_1)

        assertThat(canCreate).isTrue()
    }

    @Test
    fun testCanCreateDesks_atLimit_returnsFalse() {
        desktopConfig.maxDeskLimit = 2
        // Add two desks to bring the number up to the limit.
        repository.addDesk(displayId = DEFAULT_DISPLAY, deskId = 2)
        repository.addDesk(displayId = DEFAULT_DISPLAY, deskId = 3)

        val canCreate = controller.canCreateDesks(USER_ID_1)

        assertThat(canCreate).isFalse()
    }

    @Test
    fun testCanCreateDesksInDisplay_desktopModeUnsupported_returnsFalse() {
        desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] = false
        desktopConfig.maxDeskLimit = DESK_LIMIT_NO_LIMIT

        val canCreate = controller.canCreateDeskInDisplay(DEFAULT_DISPLAY, USER_ID_1)

        assertThat(canCreate).isFalse()
    }

    @Test
    fun testCanCreateDesksInDisplay_atLimit_limitEnforced_returnsFalse() {
        desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] = true
        desktopConfig.maxDeskLimit = 2
        // Add two desks to bring the number up to the limit.
        repository.addDesk(displayId = DEFAULT_DISPLAY, deskId = 2)
        repository.addDesk(displayId = DEFAULT_DISPLAY, deskId = 3)

        val canCreate =
            controller.canCreateDeskInDisplay(
                displayId = DEFAULT_DISPLAY,
                userId = USER_ID_1,
                enforceDeskLimit = true,
            )

        assertThat(canCreate).isFalse()
    }

    @Test
    fun testCanCreateDesksInDisplay_atLimit_limitNotEnforced_returnsTrue() {
        desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] = true
        desktopConfig.maxDeskLimit = 2
        // Add two desks to bring the number up to the limit.
        repository.addDesk(displayId = DEFAULT_DISPLAY, deskId = 2)
        repository.addDesk(displayId = DEFAULT_DISPLAY, deskId = 3)

        val canCreate =
            controller.canCreateDeskInDisplay(
                displayId = DEFAULT_DISPLAY,
                userId = USER_ID_1,
                enforceDeskLimit = false,
            )

        assertThat(canCreate).isTrue()
    }

    private companion object {
        private const val USER_ID_1 = 6
        private const val DESK_LIMIT_NO_LIMIT = 0
    }
}
