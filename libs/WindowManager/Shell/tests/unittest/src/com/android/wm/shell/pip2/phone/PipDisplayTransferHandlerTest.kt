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
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.pip.PipBoundsState
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
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.testing.TestableResources
import android.util.ArrayMap
import android.view.Display
import android.view.SurfaceControl
import com.android.modules.utils.testing.ExtendedMockitoRule
import com.android.wm.shell.pip2.PipSurfaceTransactionHelper
import com.android.wm.shell.pip2.phone.PipDisplayTransferHandler.ORIGIN_DISPLAY_ID_KEY
import com.android.wm.shell.pip2.phone.PipDisplayTransferHandler.TARGET_DISPLAY_ID_KEY
import com.android.wm.shell.pip2.phone.PipTransition.PIP_DESTINATION_BOUNDS
import com.android.wm.shell.pip2.phone.PipTransition.PIP_START_TX
import com.android.wm.shell.pip2.phone.PipTransitionState.CHANGING_PIP_BOUNDS
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import com.google.common.truth.Truth.assertThat
import com.android.wm.shell.R
import com.android.wm.shell.common.DisplayLayout
import com.android.wm.shell.common.MultiDisplayTestUtil
import org.junit.Rule
import org.mockito.kotlin.never

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
    private val mockPipBoundsState = mock<PipBoundsState>()
    private val mockTaskInfo = mock<ActivityManager.RunningTaskInfo>()
    private val mockDisplayController = mock<DisplayController>()
    private val mockTransaction = mock<SurfaceControl.Transaction>()
    private val mockLeash = mock<SurfaceControl>()
    private val mockFactory = mock<PipSurfaceTransactionHelper.SurfaceControlTransactionFactory>()

    private lateinit var testableResources: TestableResources
    private lateinit var resources: Resources
    private lateinit var defaultTda: DisplayAreaInfo
    private lateinit var pipDisplayTransferHandler: PipDisplayTransferHandler

    private lateinit var displayLayout0: DisplayLayout
    private lateinit var displayLayout1: DisplayLayout
    private lateinit var displayLayout2: DisplayLayout

    private val display0 = mock<Display>()
    private val display1 = mock<Display>()
    private val display2 = mock<Display>()

    @JvmField
    @Rule
    val extendedMockitoRule =
        ExtendedMockitoRule.Builder(this)
            .mockStatic(SurfaceControl::class.java)
            .build()!!

    @Before
    fun setUp() {
        testableResources = mContext.getOrCreateTestableResources()
        val resourceConfiguration = Configuration()
        resourceConfiguration.uiMode = 0
        testableResources.overrideConfiguration(resourceConfiguration)
        resources = testableResources.resources

        whenever(resources.getDimensionPixelSize(R.dimen.pip_corner_radius)).thenReturn(
            TEST_CORNER_RADIUS
        )
        whenever(resources.getDimensionPixelSize(R.dimen.pip_shadow_radius)).thenReturn(
            TEST_SHADOW_RADIUS
        )
        whenever(SurfaceControl.mirrorSurface(any())).thenReturn(mockLeash)
        whenever(mockPipTransitionState.pinnedTaskLeash).thenReturn(mockLeash)
        whenever(mockDesktopUserRepositories.current).thenReturn(mockDesktopRepository)
        whenever(mockTaskInfo.getDisplayId()).thenReturn(ORIGIN_DISPLAY_ID)
        whenever(mockPipDisplayLayoutState.displayId).thenReturn(ORIGIN_DISPLAY_ID)
        whenever(mockTransaction.remove(any())).thenReturn(mockTransaction)
        whenever(mockTransaction.show(any())).thenReturn(mockTransaction)
        whenever(mockFactory.transaction).thenReturn(mockTransaction)

        defaultTda =
            DisplayAreaInfo(mock<WindowContainerToken>(), ORIGIN_DISPLAY_ID, /* featureId = */ 0)
        whenever(mockRootTaskDisplayAreaOrganizer.getDisplayAreaInfo(ORIGIN_DISPLAY_ID)).thenReturn(
            defaultTda
        )
        whenever(mockRootTaskDisplayAreaOrganizer.displayIds).thenReturn(
            intArrayOf(
                ORIGIN_DISPLAY_ID,
                TARGET_DISPLAY_ID,
                SECONDARY_DISPLAY_ID
            )
        )

        displayLayout0 =
            MultiDisplayTestUtil.createSpyDisplayLayout(
                MultiDisplayTestUtil.DISPLAY_GLOBAL_BOUNDS_0,
                MultiDisplayTestUtil.DISPLAY_DPI_0,
                resources,
            )
        displayLayout1 =
            MultiDisplayTestUtil.createSpyDisplayLayout(
                MultiDisplayTestUtil.DISPLAY_GLOBAL_BOUNDS_1,
                MultiDisplayTestUtil.DISPLAY_DPI_1,
                resources,
            )
        displayLayout2 =
            MultiDisplayTestUtil.createSpyDisplayLayout(
                MultiDisplayTestUtil.DISPLAY_GLOBAL_BOUNDS_2,
                MultiDisplayTestUtil.DISPLAY_DPI_2,
                resources,
            )

        whenever(mockDisplayController.getDisplay(0)).thenReturn(display0)
        whenever(mockDisplayController.getDisplay(1)).thenReturn(display1)
        whenever(mockDisplayController.getDisplay(2)).thenReturn(display2)

        whenever(mockDisplayController.getDisplayLayout(0)).thenReturn(displayLayout0)
        whenever(mockDisplayController.getDisplayLayout(1)).thenReturn(displayLayout1)
        whenever(mockDisplayController.getDisplayLayout(2)).thenReturn(displayLayout2)

        pipDisplayTransferHandler =
            PipDisplayTransferHandler(
                mContext, mockPipTransitionState, mockPipScheduler,
                mockRootTaskDisplayAreaOrganizer, mockPipBoundsState, mockDisplayController
            )
        pipDisplayTransferHandler.setSurfaceControlTransactionFactory(mockFactory)
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

        verify(mockPipScheduler).scheduleFinishPipBoundsChange(eq(destinationBounds))
    }

    @Test
    fun showDragMirrorOnConnectedDisplays_hasNotLeftOriginDisplay_shouldNotCreateMirrors() {
        pipDisplayTransferHandler.showDragMirrorOnConnectedDisplays(
            ORIGIN_DISPLAY_ID, ORIGIN_DISPLAY_ID,
            START_DRAG_COORDINATES, PointF(150f, 150f),
            PIP_BOUNDS
        )

        verify(mockRootTaskDisplayAreaOrganizer, never()).reparentToDisplayArea(
            eq(TARGET_DISPLAY_ID),
            any(),
            any()
        )
        assertThat(pipDisplayTransferHandler.mOnDragMirrorPerDisplayId.isEmpty()).isTrue()
        verify(mockPipScheduler, never()).setPipTransformations(any(), any(), any(), any())
        verify(mockTransaction, never()).show(any())
        verify(mockTransaction, times(1)).apply()
    }

    @Test
    fun showDragMirrorOnConnectedDisplays_movedToAnotherDisplay_createsOneMirror() {
        pipDisplayTransferHandler.showDragMirrorOnConnectedDisplays(
            ORIGIN_DISPLAY_ID, TARGET_DISPLAY_ID,
            START_DRAG_COORDINATES, MultiDisplayTestUtil.DISPLAY_GLOBAL_BOUNDS_1.center(),
            PIP_BOUNDS
        )

        verify(mockRootTaskDisplayAreaOrganizer).reparentToDisplayArea(
            eq(TARGET_DISPLAY_ID),
            any(),
            any()
        )
        assertThat(pipDisplayTransferHandler.mOnDragMirrorPerDisplayId.size).isEqualTo(1)
        assertThat(pipDisplayTransferHandler.mOnDragMirrorPerDisplayId.containsKey(TARGET_DISPLAY_ID)).isTrue()
        verify(mockPipScheduler).setPipTransformations(any(), any(), any(), any())
        verify(mockTransaction, times(1)).show(any())
        verify(mockTransaction, times(1)).apply()
    }

    @Test
    fun showDragMirrorOnConnectedDisplays_inBetweenThreeDisplays_createsTwoMirrors() {
        pipDisplayTransferHandler.showDragMirrorOnConnectedDisplays(
            ORIGIN_DISPLAY_ID, TARGET_DISPLAY_ID,
            START_DRAG_COORDINATES, PointF(1000f, -100f),
            PIP_BOUNDS
        )

        verify(mockRootTaskDisplayAreaOrganizer).reparentToDisplayArea(
            eq(SECONDARY_DISPLAY_ID),
            any(),
            any()
        )
        verify(mockRootTaskDisplayAreaOrganizer).reparentToDisplayArea(
            eq(TARGET_DISPLAY_ID),
            any(),
            any()
        )
        assertThat(pipDisplayTransferHandler.mOnDragMirrorPerDisplayId.size).isEqualTo(2)
        assertThat(
            pipDisplayTransferHandler.mOnDragMirrorPerDisplayId.containsKey(
                SECONDARY_DISPLAY_ID
            )
        ).isTrue()
        assertThat(pipDisplayTransferHandler.mOnDragMirrorPerDisplayId.containsKey(TARGET_DISPLAY_ID)).isTrue()
        verify(mockPipScheduler, times(2)).setPipTransformations(any(), any(), any(), any())
        verify(mockTransaction, times(2)).show(any())
        verify(mockTransaction, times(1)).apply()
    }

    @Test
    fun removeMirrors_removesAllMirrorsAndAppliesTransactionOnce() {
        pipDisplayTransferHandler.mOnDragMirrorPerDisplayId = ArrayMap()
        pipDisplayTransferHandler.mOnDragMirrorPerDisplayId.apply {
            put(0, mockLeash)
            put(1, mockLeash)
            put(2, mockLeash)
        }

        pipDisplayTransferHandler.removeMirrors()

        verify(mockTransaction, times(3)).remove(any())
        verify(mockTransaction, times(1)).apply()
        assertThat(pipDisplayTransferHandler.mOnDragMirrorPerDisplayId.isEmpty()).isTrue()
    }

    fun RectF.center(): PointF = PointF(this.centerX(), this.centerY())

    companion object {
        const val ORIGIN_DISPLAY_ID = 0
        const val TARGET_DISPLAY_ID = 1
        const val SECONDARY_DISPLAY_ID = 2
        const val TEST_CORNER_RADIUS = 5
        const val TEST_SHADOW_RADIUS = 5
        val START_DRAG_COORDINATES = PointF(100f, 100f)
        val PIP_BOUNDS = Rect(0, 0, 700, 700)
    }
}