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
package com.android.wm.shell.common

import android.app.ActivityManager.RunningTaskInfo
import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.RectF
import android.testing.TestableResources
import android.view.Display
import android.view.SurfaceControl
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestShellExecutor
import com.android.wm.shell.common.MultiDisplayTestUtil.TestDisplay
import java.util.function.Supplier
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever

/**
 * Tests for [MultiDisplayDragMoveIndicatorController].
 *
 * Build/Install/Run: atest WMShellUnitTests:MultiDisplayDragMoveIndicatorControllerTest
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class MultiDisplayDragMoveIndicatorControllerTest : ShellTestCase() {
    private val displayController = mock<DisplayController>()
    private val rootTaskDisplayAreaOrganizer = mock<RootTaskDisplayAreaOrganizer>()
    private val indicatorSurfaceFactory = mock<MultiDisplayDragMoveIndicatorSurface.Factory>()
    private val indicatorSurface0 = mock<MultiDisplayDragMoveIndicatorSurface>()
    private val indicatorSurface1 = mock<MultiDisplayDragMoveIndicatorSurface>()
    private val transaction = mock<SurfaceControl.Transaction>()
    private val transactionSupplier = mock<Supplier<SurfaceControl.Transaction>>()
    private val taskInfo = mock<RunningTaskInfo>()
    private val display0 = mock<Display>()
    private val display1 = mock<Display>()

    private lateinit var resources: TestableResources
    private val executor = TestShellExecutor()

    private lateinit var controller: MultiDisplayDragMoveIndicatorController

    @Before
    fun setUp() {
        resources = mContext.getOrCreateTestableResources()
        val resourceConfiguration = Configuration()
        resourceConfiguration.uiMode = 0
        resources.overrideConfiguration(resourceConfiguration)

        controller =
            MultiDisplayDragMoveIndicatorController(
                displayController,
                rootTaskDisplayAreaOrganizer,
                indicatorSurfaceFactory,
                executor,
            )

        val spyDisplayLayout0 = TestDisplay.DISPLAY_0.getSpyDisplayLayout(resources.resources)
        val spyDisplayLayout1 = TestDisplay.DISPLAY_1.getSpyDisplayLayout(resources.resources)

        taskInfo.taskId = TASK_ID
        whenever(displayController.getDisplayLayout(0)).thenReturn(spyDisplayLayout0)
        whenever(displayController.getDisplayLayout(1)).thenReturn(spyDisplayLayout1)
        whenever(displayController.getDisplayContext(any())).thenReturn(mContext)
        whenever(displayController.getDisplay(0)).thenReturn(display0)
        whenever(displayController.getDisplay(1)).thenReturn(display1)
        whenever(indicatorSurfaceFactory.create(eq(taskInfo), eq(display0), any()))
            .thenReturn(indicatorSurface0)
        whenever(indicatorSurfaceFactory.create(eq(taskInfo), eq(display1), any()))
            .thenReturn(indicatorSurface1)
        whenever(transactionSupplier.get()).thenReturn(transaction)
    }

    @Test
    fun onDrag_boundsNotIntersectWithDisplay_noIndicator() {
        controller.onDragMove(
            RectF(2000f, 2000f, 2100f, 2200f), // not intersect with any display
            currentDisplayId = 0,
            startDisplayId = 0,
            taskInfo,
            displayIds = setOf(0, 1),
        ) {
            transaction
        }
        executor.flushAll()

        verify(indicatorSurfaceFactory, never()).create(any(), any(), any())
    }

    @Test
    fun onDrag_boundsIntersectWithStartDisplay_noIndicator() {
        controller.onDragMove(
            RectF(100f, 100f, 200f, 200f), // intersect with display 0
            currentDisplayId = 0,
            startDisplayId = 0,
            taskInfo,
            displayIds = setOf(0, 1),
        ) {
            transaction
        }
        executor.flushAll()

        verify(indicatorSurfaceFactory, never()).create(any(), any(), any())
    }

    @Test
    fun onDrag_boundsIntersectWithNonStartDisplay_showAndDisposeIndicator() {
        controller.onDragMove(
            RectF(100f, -100f, 200f, 200f), // intersect with display 0 and 1
            currentDisplayId = 1,
            startDisplayId = 0,
            taskInfo,
            displayIds = setOf(0, 1),
        ) {
            transaction
        }
        executor.flushAll()

        verify(indicatorSurfaceFactory, times(1)).create(eq(taskInfo), eq(display1), any())
        verify(indicatorSurface1, times(1))
            .show(
                transaction,
                taskInfo,
                rootTaskDisplayAreaOrganizer,
                1,
                Rect(0, 1800, 200, 2400),
                MultiDisplayDragMoveIndicatorSurface.Visibility.VISIBLE,
            )

        controller.onDragMove(
            RectF(2000f, 2000f, 2100f, 2200f), // not intersect with display 1
            currentDisplayId = 0,
            startDisplayId = 0,
            taskInfo,
            displayIds = setOf(0, 1),
        ) {
            transaction
        }
        while (executor.callbacks.isNotEmpty()) {
            executor.flushAll()
        }

        verify(indicatorSurface1, times(1))
            .relayout(
                any(),
                eq(transaction),
                eq(MultiDisplayDragMoveIndicatorSurface.Visibility.INVISIBLE),
            )

        controller.onDragEnd(TASK_ID, { transaction })
        while (executor.callbacks.isNotEmpty()) {
            executor.flushAll()
        }

        verify(indicatorSurface1, times(1)).dispose(transaction)
    }

    companion object {
        private const val TASK_ID = 10
    }
}
