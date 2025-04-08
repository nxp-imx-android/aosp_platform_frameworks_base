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
import android.graphics.Rect
import android.testing.AndroidTestingRunner
import android.view.Display
import android.view.Display.DEFAULT_DISPLAY
import android.view.SurfaceControl
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.shared.R
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for [MultiDisplayDragMoveIndicatorSurface].
 *
 * Build/Install/Run: atest WMShellUnitTests:MultiDisplayDragMoveIndicatorSurfaceTest
 */
@RunWith(AndroidTestingRunner::class)
class MultiDisplayDragMoveIndicatorSurfaceTest : ShellTestCase() {
    private lateinit var display: Display
    private val mockTaskInfo = mock<RunningTaskInfo>()
    private val mockSurfaceControlBuilderFactory =
        mock<MultiDisplayDragMoveIndicatorSurface.Factory.SurfaceControlBuilderFactory>()
    private val mockSurfaceControlBuilder = mock<SurfaceControl.Builder>()
    private val mockVeilSurface = mock<SurfaceControl>()
    private val mockTransaction = mock<SurfaceControl.Transaction>()
    private val mockRootTaskDisplayAreaOrganizer = mock<RootTaskDisplayAreaOrganizer>()

    private lateinit var dragIndicatorSurface: MultiDisplayDragMoveIndicatorSurface

    @Before
    fun setUp() {
        display = mContext.display
        mockTaskInfo.taskId = TASK_ID
        whenever(
                mContext.orCreateTestableResources.resources.getDimensionPixelSize(
                    R.dimen.desktop_windowing_freeform_rounded_corner_radius
                )
            )
            .thenReturn(CORNER_RADIUS)
        whenever(mockSurfaceControlBuilderFactory.create(any()))
            .thenReturn(mockSurfaceControlBuilder)
        whenever(mockSurfaceControlBuilder.setColorLayer()).thenReturn(mockSurfaceControlBuilder)
        whenever(mockSurfaceControlBuilder.setCallsite(any())).thenReturn(mockSurfaceControlBuilder)
        whenever(mockSurfaceControlBuilder.setHidden(any())).thenReturn(mockSurfaceControlBuilder)
        whenever(mockSurfaceControlBuilder.build()).thenReturn(mockVeilSurface)
        whenever(mockTransaction.remove(any())).thenReturn(mockTransaction)
        whenever(mockTransaction.setCrop(any(), any())).thenReturn(mockTransaction)
        whenever(mockTransaction.show(any())).thenReturn(mockTransaction)
        whenever(mockTransaction.setColor(any(), any())).thenReturn(mockTransaction)

        dragIndicatorSurface =
            MultiDisplayDragMoveIndicatorSurface(
                mContext,
                mockTaskInfo,
                display,
                mockSurfaceControlBuilderFactory,
            )
    }

    @Test
    fun init_createsVeilSurfaceWithCorrectProperties() {
        verify(mockSurfaceControlBuilderFactory).create(any())
        verify(mockSurfaceControlBuilder).setColorLayer()
        verify(mockSurfaceControlBuilder).setCallsite(any())
        verify(mockSurfaceControlBuilder).setHidden(eq(true))
        verify(mockSurfaceControlBuilder).build()
    }

    @Test
    fun disposeSurface_removesVeilSurface() {
        dragIndicatorSurface.disposeSurface(mockTransaction)

        verify(mockTransaction).remove(eq(mockVeilSurface))
    }

    @Test
    fun disposeSurface_doesNothingIfAlreadyDisposed() {
        dragIndicatorSurface.disposeSurface(mockTransaction)
        clearInvocations(mockTransaction)

        dragIndicatorSurface.disposeSurface(mockTransaction)

        verify(mockTransaction, never()).remove(any())
    }

    @Test
    fun show_reparentsSetsCropShowsSetsColorAppliesTransaction() {
        dragIndicatorSurface.show(
            mockTransaction,
            mockTaskInfo,
            mockRootTaskDisplayAreaOrganizer,
            DEFAULT_DISPLAY,
            BOUNDS,
        )

        verify(mockRootTaskDisplayAreaOrganizer)
            .reparentToDisplayArea(eq(DEFAULT_DISPLAY), eq(mockVeilSurface), eq(mockTransaction))
        verify(mockTransaction).setCrop(eq(mockVeilSurface), eq(BOUNDS))
        verify(mockTransaction).setCornerRadius(eq(mockVeilSurface), eq(CORNER_RADIUS.toFloat()))
        verify(mockTransaction).show(eq(mockVeilSurface))
        verify(mockTransaction).setColor(eq(mockVeilSurface), any<FloatArray>())
        verify(mockTransaction).apply()
    }

    @Test
    fun relayout_whenVisibleAndShouldBeVisible_setsCrop() {
        dragIndicatorSurface.show(
            mockTransaction,
            mockTaskInfo,
            mockRootTaskDisplayAreaOrganizer,
            DEFAULT_DISPLAY,
            BOUNDS,
        )
        clearInvocations(mockTransaction)

        dragIndicatorSurface.relayout(NEW_BOUNDS, mockTransaction, shouldBeVisible = true)

        verify(mockTransaction).setCrop(eq(mockVeilSurface), eq(NEW_BOUNDS))
    }

    @Test
    fun relayout_whenVisibleAndShouldBeInvisible_setsCrop() {
        dragIndicatorSurface.show(
            mockTransaction,
            mockTaskInfo,
            mockRootTaskDisplayAreaOrganizer,
            DEFAULT_DISPLAY,
            BOUNDS,
        )
        clearInvocations(mockTransaction)
        dragIndicatorSurface.relayout(NEW_BOUNDS, mockTransaction, shouldBeVisible = false)

        verify(mockTransaction).setCrop(eq(mockVeilSurface), eq(NEW_BOUNDS))
    }

    @Test
    fun relayout_whenInvisibleAndShouldBeVisible_setsCrop() {
        dragIndicatorSurface.relayout(NEW_BOUNDS, mockTransaction, shouldBeVisible = true)

        verify(mockTransaction).setCrop(eq(mockVeilSurface), eq(NEW_BOUNDS))
    }

    @Test
    fun relayout_whenInvisibleAndShouldBeInvisible_doesNotSetCrop() {
        dragIndicatorSurface.relayout(NEW_BOUNDS, mockTransaction, shouldBeVisible = false)

        verify(mockTransaction, never()).setCrop(any(), any())
    }

    companion object {
        private const val TASK_ID = 10
        private const val CORNER_RADIUS = 32
        private val BOUNDS = Rect(10, 20, 100, 200)
        private val NEW_BOUNDS = Rect(50, 50, 150, 250)
    }
}
