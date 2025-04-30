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

import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.testing.AndroidTestingRunner
import android.testing.TestableLooper.RunWithLooper
import android.view.SurfaceControl
import androidx.test.filters.SmallTest
import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.modules.utils.testing.ExtendedMockitoRule
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayLayout
import com.android.wm.shell.common.FloatingContentCoordinator
import com.android.wm.shell.common.MultiDisplayDragMoveBoundsCalculator
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.common.pip.PipBoundsAlgorithm
import com.android.wm.shell.common.pip.PipBoundsState
import com.android.wm.shell.common.pip.PipDesktopState
import com.android.wm.shell.common.pip.PipDisplayLayoutState
import com.android.wm.shell.common.pip.PipPerfHintController
import com.android.wm.shell.common.pip.PipUiEventLogger
import com.android.wm.shell.common.pip.SizeSpecSource
import com.android.wm.shell.pip2.PipSurfaceTransactionHelper
import com.android.wm.shell.sysui.ShellCommandHandler
import com.android.wm.shell.sysui.ShellInit
import java.util.Optional
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit test against [PipTouchHandler].
 *
 * To run: atest WMShellUnitTests:com.android.wm.shell.pip2.phone.PipTouchHandlerTest
 */
@SmallTest
@RunWithLooper
@RunWith(AndroidTestingRunner::class)
class PipTouchHandlerTest : ShellTestCase() {
    private val mockShellInit = mock<ShellInit>()
    private val mockShellCommandHandler = mock<ShellCommandHandler>()
    private val mockMenuPhoneController = mock<PhonePipMenuController>()
    private val mockPipBoundsAlgorithm = mock<PipBoundsAlgorithm>()
    private val mockPipBoundsState = mock<PipBoundsState>()
    private val mockPipTransitionState = mock<PipTransitionState>()
    private val mockPipScheduler = mock<PipScheduler>()
    private val mockSizeSpecSource = mock<SizeSpecSource>()
    private val mockPipDisplayLayoutState = mock<PipDisplayLayoutState>()
    private val mockPipDesktopState = mock<PipDesktopState>()
    private val mockDisplayController = mock<DisplayController>()
    private val mockPipMotionHelper = mock<PipMotionHelper>()
    private val mockFloatingContentCoordinator = mock<FloatingContentCoordinator>()
    private val mockPipUiEventLogger = mock<PipUiEventLogger>()
    private val shellExecutor = mock<ShellExecutor>()
    private val mockPipPerfHintController = mock<PipPerfHintController>()
    private val mockPipDisplayTransferHandler = mock<PipDisplayTransferHandler>()
    private val pipTransitionState = mock<PipTransitionState>()
    private val pipTouchState = mock<PipTouchState>()
    private val mockMotionBoundsState = mock<PipBoundsState.MotionBoundsState>()
    private val mockLeash = mock<SurfaceControl>()
    private val mockDisplayLayout = mock<DisplayLayout>()
    private val mockTouchPosition = PointF()
    private val mockPipSurfaceTransactionHelper = mock<PipSurfaceTransactionHelper>()

    private lateinit var pipTouchHandler: PipTouchHandler
    private lateinit var pipTouchGesture: PipTouchGesture

    @JvmField
    @Rule
    val extendedMockitoRule =
        ExtendedMockitoRule.Builder(this)
            .mockStatic(MultiDisplayDragMoveBoundsCalculator::class.java)
            .build()!!

    @Before
    fun setUp() {
        pipTouchHandler = PipTouchHandler(
            mContext, mockPipSurfaceTransactionHelper, mockShellInit, mockShellCommandHandler,
            mockMenuPhoneController, mockPipBoundsAlgorithm, mockPipBoundsState,
            mockPipTransitionState, mockPipScheduler, mockSizeSpecSource, mockPipDisplayLayoutState,
            mockPipDesktopState, mockDisplayController, mockPipMotionHelper,
            mockFloatingContentCoordinator, mockPipUiEventLogger, shellExecutor,
            Optional.of(mockPipPerfHintController), mockPipDisplayTransferHandler
        )
        pipTouchGesture = pipTouchHandler.touchGesture
        pipTouchHandler.setPipTouchState(pipTouchState)

        whenever(pipTouchState.downTouchPosition).thenReturn(mockTouchPosition)
        whenever(pipTouchState.velocity).thenReturn(mockTouchPosition)
        whenever(pipTouchState.lastTouchPosition).thenReturn(mockTouchPosition)
        whenever(pipTouchState.lastTouchDisplayId).thenReturn(ORIGIN_DISPLAY_ID)
        whenever(pipTouchState.lastTouchDelta).thenReturn(mockTouchPosition)
        whenever(pipTransitionState.pinnedTaskLeash).thenReturn(mockLeash)
        whenever(mockPipBoundsState.movementBounds).thenReturn(PIP_BOUNDS)
        whenever(mockPipBoundsState.motionBoundsState).thenReturn(mockMotionBoundsState)
        whenever(pipTouchHandler.possiblyMotionBounds).thenReturn(PIP_BOUNDS)
        whenever(mockDisplayController.getDisplayLayout(anyInt()))
            .thenReturn(mockDisplayLayout)
        whenever(
            MultiDisplayDragMoveBoundsCalculator.convertGlobalDpToLocalPxForRect(any(), any())
        ).thenReturn(PIP_BOUNDS)
        whenever(
            MultiDisplayDragMoveBoundsCalculator.calculateGlobalDpBoundsForDrag(
                any(), any(), any(), any(), any(), any()
            )
        ).thenReturn(GLOBAL_BOUNDS)
    }

    @Test
    fun pipTouchGesture_crossDisplayDragFlagEnabled_onMove_showsMirrors() {
        whenever(mockPipDesktopState.isDraggingPipAcrossDisplaysEnabled()).thenReturn(true)
        whenever(pipTouchState.isUserInteracting).thenReturn(true)
        whenever(pipTouchState.isDragging).thenReturn(true)
        // Initialize variables that are set on down
        pipTouchGesture.onDown(pipTouchState)

        pipTouchGesture.onMove(pipTouchState)

        ExtendedMockito.verify {
            MultiDisplayDragMoveBoundsCalculator.calculateGlobalDpBoundsForDrag(
                any(), any(), any(), any(), any(), any()
            )
        }
        ExtendedMockito.verify {
            MultiDisplayDragMoveBoundsCalculator.convertGlobalDpToLocalPxForRect(
                eq(GLOBAL_BOUNDS),
                eq(mockDisplayLayout)
            )
        }
        verify(mockPipDisplayTransferHandler).showDragMirrorOnConnectedDisplays(
             eq(GLOBAL_BOUNDS), eq(ORIGIN_DISPLAY_ID))
        verify(mockPipMotionHelper).movePip(eq(PIP_BOUNDS), eq(true), eq(ORIGIN_DISPLAY_ID))
    }

    @Test
    fun pipTouchGesture_crossDisplayDragFlagEnabled_onUpOnADifferentDisplay_schedulesMovePip() {
        whenever(mockPipDesktopState.isDraggingPipAcrossDisplaysEnabled()).thenReturn(true)
        whenever(pipTouchState.isUserInteracting).thenReturn(true)
        pipTouchGesture.onDown(pipTouchState)

        pipTouchHandler.mEnableStash = false
        whenever(pipTouchState.isDragging).thenReturn(true)
        whenever(pipTouchState.lastTouchDisplayId).thenReturn(TARGET_DISPLAY_ID)
        pipTouchGesture.onUp(pipTouchState)

        verify(mockPipDisplayTransferHandler).scheduleMovePipToDisplay(
            eq(ORIGIN_DISPLAY_ID),
            eq(TARGET_DISPLAY_ID),
            eq(PIP_BOUNDS)
        )
    }

    @Test
    fun pipTouchGesture_crossDisplayDragFlagEnabled_onUp_removesMirrors() {
        whenever(mockPipDesktopState.isDraggingPipAcrossDisplaysEnabled()).thenReturn(true)

        pipTouchGesture.onUp(pipTouchState)

        verify(mockPipDisplayTransferHandler).removeMirrors()
    }

    private companion object {
        const val ORIGIN_DISPLAY_ID = 0
        const val TARGET_DISPLAY_ID = 1
        val PIP_BOUNDS = Rect(0, 0, 700, 700)
        val GLOBAL_BOUNDS = RectF(0f, 0f, 400f, 400f)
    }
}