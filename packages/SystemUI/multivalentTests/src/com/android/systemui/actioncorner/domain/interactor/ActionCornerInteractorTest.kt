/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.systemui.actioncorner.domain.interactor

import android.view.Display.DEFAULT_DISPLAY
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.LauncherProxyService
import com.android.systemui.SysuiTestCase
import com.android.systemui.actioncorner.data.model.ActionCornerRegion.BOTTOM_LEFT
import com.android.systemui.actioncorner.data.model.ActionCornerRegion.BOTTOM_RIGHT
import com.android.systemui.actioncorner.data.model.ActionCornerState
import com.android.systemui.actioncorner.data.repository.FakeActionCornerRepository
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.Kosmos.Fixture
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.shared.system.actioncorner.ActionCornerConstants.HOME
import com.android.systemui.shared.system.actioncorner.ActionCornerConstants.OVERVIEW
import com.android.systemui.testKosmos
import kotlin.test.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
class ActionCornerInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val Kosmos.actionCornerRepository by Fixture { FakeActionCornerRepository() }
    private val Kosmos.launcherProxyService by Fixture { mock<LauncherProxyService>() }
    private val Kosmos.underTest by Fixture {
        ActionCornerInteractor(kosmos.actionCornerRepository, kosmos.launcherProxyService)
    }

    @Before
    fun setUp() {
        kosmos.underTest.activateIn(kosmos.testScope)
    }

    @Test
    fun bottomLeftCornerActivated_notifyLauncherOfOverviewAction() =
        kosmos.runTest {
            actionCornerRepository.addState(
                ActionCornerState.ActiveActionCorner(BOTTOM_LEFT, DEFAULT_DISPLAY)
            )
            verify(launcherProxyService).onActionCornerActivated(OVERVIEW, DEFAULT_DISPLAY)
        }

    @Test
    fun bottomRightCornerActivated_notifyLauncherOfHomeAction() =
        kosmos.runTest {
            actionCornerRepository.addState(
                ActionCornerState.ActiveActionCorner(BOTTOM_RIGHT, DEFAULT_DISPLAY)
            )
            verify(launcherProxyService).onActionCornerActivated(HOME, DEFAULT_DISPLAY)
        }
}
