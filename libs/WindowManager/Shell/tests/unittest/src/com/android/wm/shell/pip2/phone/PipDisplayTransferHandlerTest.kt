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
package com.android.wm.shell.pip2.phone

import android.app.ActivityManager
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import android.testing.TestableLooper.RunWithLooper
import android.window.DisplayAreaInfo
import android.window.WindowContainerToken
import androidx.test.filters.SmallTest
import com.android.window.flags.Flags.FLAG_ENABLE_DRAGGING_PIP_ACROSS_DISPLAYS
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.pip.PipDisplayLayoutState
import com.android.wm.shell.desktopmode.DesktopRepository
import com.android.wm.shell.desktopmode.DesktopUserRepositories
import com.android.wm.shell.pip2.phone.PipTransitionState.SCHEDULED_BOUNDS_CHANGE
import com.android.wm.shell.pip2.phone.PipTransitionState.UNDEFINED
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.verify
import android.graphics.Rect
import android.os.Bundle
import android.view.SurfaceControl
import com.android.wm.shell.pip2.phone.PipDisplayTransferHandler.ORIGIN_DISPLAY_ID_KEY
import com.android.wm.shell.pip2.phone.PipDisplayTransferHandler.TARGET_DISPLAY_ID_KEY
import com.android.wm.shell.pip2.phone.PipTransition.PIP_DESTINATION_BOUNDS
import com.android.wm.shell.pip2.phone.PipTransition.PIP_START_TX
import com.android.wm.shell.pip2.phone.PipTransitionState.CHANGING_PIP_BOUNDS
import org.mockito.kotlin.eq

/**
 * Unit test against [PipDisplayTransferHandler].
 */
@SmallTest
@RunWithLooper
@RunWith(AndroidTestingRunner::class)
@EnableFlags(FLAG_ENABLE_DRAGGING_PIP_ACROSS_DISPLAYS)
class PipDisplayTransferHandlerTest : ShellTestCase() {
    private val mockPipDisplayLayoutState = mock<PipDisplayLayoutState>()
    private val mockDesktopUserRepositories = mock<DesktopUserRepositories>()
    private val mockDesktopRepository = mock<DesktopRepository>()
    private val mockPipTransitionState = mock<PipTransitionState>()
    private val mockPipScheduler = mock<PipScheduler>()
    private val mockRootTaskDisplayAreaOrganizer = mock<RootTaskDisplayAreaOrganizer>()
    private val mockTaskInfo = mock<ActivityManager.RunningTaskInfo>()
    private lateinit var defaultTda: DisplayAreaInfo
    private lateinit var pipDisplayTransferHandler: PipDisplayTransferHandler

    @Before
    fun setUp() {
        whenever(mockDesktopUserRepositories.current).thenReturn(mockDesktopRepository)
        whenever(mockTaskInfo.getDisplayId()).thenReturn(ORIGIN_DISPLAY_ID)
        whenever(mockPipDisplayLayoutState.displayId).thenReturn(ORIGIN_DISPLAY_ID)

        defaultTda =
            DisplayAreaInfo(mock<WindowContainerToken>(), ORIGIN_DISPLAY_ID, /* featureId = */ 0)
        whenever(mockRootTaskDisplayAreaOrganizer.getDisplayAreaInfo(ORIGIN_DISPLAY_ID)).thenReturn(
            defaultTda
        )

        pipDisplayTransferHandler =
            PipDisplayTransferHandler(mockPipTransitionState, mockPipScheduler)
    }

    @Test
    fun scheduleMovePipToDisplay_setsTransitionState() {
        pipDisplayTransferHandler.scheduleMovePipToDisplay(ORIGIN_DISPLAY_ID, TARGET_DISPLAY_ID)

        verify(mockPipTransitionState).setState(eq(SCHEDULED_BOUNDS_CHANGE), any())
    }

    @Test
    fun onPipTransitionStateChanged_schedulingBoundsChange_triggersPipScheduler() {
        val extra = Bundle()
        extra.putInt(ORIGIN_DISPLAY_ID_KEY, ORIGIN_DISPLAY_ID)
        extra.putInt(TARGET_DISPLAY_ID_KEY, TARGET_DISPLAY_ID)
        pipDisplayTransferHandler.onPipTransitionStateChanged(
            UNDEFINED,
            SCHEDULED_BOUNDS_CHANGE,
            extra
        )

        verify(mockPipScheduler).scheduleMoveToDisplay(eq(ORIGIN_DISPLAY_ID), eq(TARGET_DISPLAY_ID))
    }

    @Test
    fun onPipTransitionStateChanged_changingPipBounds_finishesChangingBounds() {
        val extra = Bundle()
        val destinationBounds = Rect(0, 0, 100, 100)
        extra.putParcelable(PIP_START_TX, SurfaceControl.Transaction())
        extra.putParcelable(PIP_DESTINATION_BOUNDS, destinationBounds)
        pipDisplayTransferHandler.mWaitingForDisplayTransfer = true

        pipDisplayTransferHandler.onPipTransitionStateChanged(
            UNDEFINED,
            CHANGING_PIP_BOUNDS,
            extra
        )

        verify(mockPipScheduler).scheduleFinishResizePip(eq(destinationBounds))
    }

    companion object {
        private const val ORIGIN_DISPLAY_ID = 0
        private const val TARGET_DISPLAY_ID = 1
    }
}