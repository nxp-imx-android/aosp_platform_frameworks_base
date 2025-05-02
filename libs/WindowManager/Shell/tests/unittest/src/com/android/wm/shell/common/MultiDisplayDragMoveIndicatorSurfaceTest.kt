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

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.testing.AndroidTestingRunner
import android.view.Display
import android.view.Display.DEFAULT_DISPLAY
import android.view.SurfaceControl
import android.view.SurfaceControlViewHost
import android.view.WindowlessWindowManager
import com.android.wm.shell.R
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestRunningTaskInfoBuilder
import com.android.wm.shell.shared.R as sharedR
import com.android.wm.shell.windowdecor.WindowDecoration
import com.android.wm.shell.windowdecor.common.WindowDecorTaskResourceLoader
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
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
    private val taskInfo = TestRunningTaskInfoBuilder().build()
    private val mockSurfaceControlBuilderFactory =
        mock<MultiDisplayDragMoveIndicatorSurface.Factory.SurfaceControlBuilderFactory>()
    private val spyVeilSurfaceControlBuilder = spy<SurfaceControl.Builder>()
    private val spyBackgroundSurfaceControlBuilder = spy<SurfaceControl.Builder>()
    private val spyIconSurfaceControlBuilder = spy<SurfaceControl.Builder>()
    private val mockVeilSurface = mock<SurfaceControl>()
    private val mockBackgroundSurface = mock<SurfaceControl>()
    private val mockIconSurface = mock<SurfaceControl>()
    private val mockTransaction = mock<SurfaceControl.Transaction>()
    private val mockRootTaskDisplayAreaOrganizer = mock<RootTaskDisplayAreaOrganizer>()
    private val mockAppIcon = mock<Bitmap>()
    private val mockTaskResourceLoader = mock<WindowDecorTaskResourceLoader>()
    private val mockSurfaceControlViewHost = mock<SurfaceControlViewHost>()
    private val mockSurfaceControlViewHostFactory =
        mock<WindowDecoration.SurfaceControlViewHostFactory>()

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = StandardTestDispatcher(testScheduler)
    private val testScope = TestScope(testDispatcher)

    private lateinit var dragIndicatorSurface: MultiDisplayDragMoveIndicatorSurface

    @Before
    fun setUp() {
        display = mContext.display
        whenever(
                mContext.orCreateTestableResources.resources.getDimensionPixelSize(
                    sharedR.dimen.desktop_windowing_freeform_rounded_corner_radius
                )
            )
            .thenReturn(CORNER_RADIUS)
        whenever(
                mContext.orCreateTestableResources.resources.getDimensionPixelSize(
                    R.dimen.desktop_mode_resize_veil_icon_size
                )
            )
            .thenReturn(ICON_SIZE)

        val displayId = display.displayId
        whenever(
                mockSurfaceControlBuilderFactory.create(
                    eq("Drag indicator veil of Task=${taskInfo.taskId} Display=$displayId")
                )
            )
            .thenReturn(spyVeilSurfaceControlBuilder)
        whenever(
                mockSurfaceControlBuilderFactory.create(
                    eq("Drag indicator background of Task=${taskInfo.taskId} Display=$displayId")
                )
            )
            .thenReturn(spyBackgroundSurfaceControlBuilder)
        whenever(
                mockSurfaceControlBuilderFactory.create(
                    eq("Drag indicator icon of Task=${taskInfo.taskId} Display=$displayId")
                )
            )
            .thenReturn(spyIconSurfaceControlBuilder)

        doReturn(mockVeilSurface).whenever(spyVeilSurfaceControlBuilder).build()
        doReturn(mockBackgroundSurface).whenever(spyBackgroundSurfaceControlBuilder).build()
        doReturn(mockIconSurface).whenever(spyIconSurfaceControlBuilder).build()

        whenever(mockSurfaceControlViewHostFactory.create(any(), any(), any(), any()))
            .thenReturn(mockSurfaceControlViewHost)
        whenever(mockTaskResourceLoader.getVeilIcon(eq(taskInfo))).thenReturn(mockAppIcon)

        whenever(mockTransaction.remove(any())).thenReturn(mockTransaction)
        whenever(mockTransaction.setCrop(any(), any())).thenReturn(mockTransaction)
        whenever(mockTransaction.setCornerRadius(any(), any())).thenReturn(mockTransaction)
        whenever(mockTransaction.setCornerRadius(any(), any())).thenReturn(mockTransaction)
        whenever(mockTransaction.show(any())).thenReturn(mockTransaction)
        whenever(mockTransaction.hide(any())).thenReturn(mockTransaction)
        whenever(mockTransaction.setColor(any(), any())).thenReturn(mockTransaction)
        whenever(mockTransaction.setLayer(any(), any())).thenReturn(mockTransaction)
        whenever(mockTransaction.setAlpha(any(), any())).thenReturn(mockTransaction)

        dragIndicatorSurface =
            MultiDisplayDragMoveIndicatorSurface(
                mContext,
                taskInfo,
                display,
                mockSurfaceControlBuilderFactory,
                mockTaskResourceLoader,
                testDispatcher,
                testScope,
                mockSurfaceControlViewHostFactory,
            )
    }

    @Test
    fun init_createsVeilSurfaceWithCorrectProperties() =
        runTest(testDispatcher) {
            verify(mockSurfaceControlBuilderFactory, times(3)).create(any())
            verify(spyVeilSurfaceControlBuilder).build()
            verify(spyBackgroundSurfaceControlBuilder).build()
            verify(spyIconSurfaceControlBuilder).build()
            verify(mockSurfaceControlViewHostFactory)
                .create(eq(mContext), eq(display), any<WindowlessWindowManager>(), any())
            verify(mockTaskResourceLoader).getVeilIcon(eq(taskInfo))
            assertThat((dragIndicatorSurface.iconView.drawable as BitmapDrawable).bitmap)
                .isEqualTo(mockAppIcon)
        }

    @Test
    fun dispose_removesVeil() {
        dragIndicatorSurface.dispose(mockTransaction)

        verify(mockSurfaceControlViewHost).release()
        verify(mockTransaction).remove(eq(mockVeilSurface))
        verify(mockTransaction).remove(eq(mockBackgroundSurface))
        verify(mockTransaction).remove(eq(mockIconSurface))
    }

    @Test
    fun dispose_doesNothingIfAlreadyDisposed() {
        dragIndicatorSurface.dispose(mockTransaction)
        clearInvocations(mockTransaction)

        dragIndicatorSurface.dispose(mockTransaction)

        verify(mockTransaction, never()).remove(any())
    }

    @Test
    fun show_reparentsSetsCropShowsSetsColorAppliesTransaction() {
        val expectedX = BOUNDS.left + BOUNDS.width().toFloat() / 2 - ICON_SIZE.toFloat() / 2
        val expectedY = BOUNDS.top + BOUNDS.height().toFloat() / 2 - ICON_SIZE.toFloat() / 2

        dragIndicatorSurface.show(
            mockTransaction,
            taskInfo,
            mockRootTaskDisplayAreaOrganizer,
            DEFAULT_DISPLAY,
            BOUNDS,
            MultiDisplayDragMoveIndicatorSurface.Visibility.VISIBLE,
        )

        verify(mockRootTaskDisplayAreaOrganizer)
            .reparentToDisplayArea(eq(DEFAULT_DISPLAY), eq(mockVeilSurface), eq(mockTransaction))
        verify(mockTransaction).setCrop(eq(mockVeilSurface), eq(BOUNDS))
        verify(mockTransaction).setCornerRadius(eq(mockVeilSurface), eq(CORNER_RADIUS.toFloat()))
        verify(mockTransaction).setPosition(eq(mockIconSurface), eq(expectedX), eq(expectedY))
        verify(mockTransaction).show(eq(mockVeilSurface))
        verify(mockTransaction).show(eq(mockBackgroundSurface))
        verify(mockTransaction).show(eq(mockIconSurface))
        verify(mockTransaction).apply()
    }

    @Test
    fun relayout_whenVisibleAndDoesntChangeVisibility_setsCropAndPosition() {
        val expectedX = NEW_BOUNDS.left + NEW_BOUNDS.width().toFloat() / 2 - ICON_SIZE.toFloat() / 2
        val expectedY = NEW_BOUNDS.top + NEW_BOUNDS.height().toFloat() / 2 - ICON_SIZE.toFloat() / 2

        dragIndicatorSurface.show(
            mockTransaction,
            taskInfo,
            mockRootTaskDisplayAreaOrganizer,
            DEFAULT_DISPLAY,
            BOUNDS,
            MultiDisplayDragMoveIndicatorSurface.Visibility.VISIBLE,
        )
        clearInvocations(mockTransaction)

        dragIndicatorSurface.relayout(
            NEW_BOUNDS,
            mockTransaction,
            MultiDisplayDragMoveIndicatorSurface.Visibility.VISIBLE,
        )

        verify(mockTransaction).setCrop(eq(mockVeilSurface), eq(NEW_BOUNDS))
        verify(mockTransaction).setPosition(eq(mockIconSurface), eq(expectedX), eq(expectedY))
    }

    @Test
    fun relayout_whenVisibleAndShouldBeInvisible_setsCropAndPosition() {
        val expectedX = NEW_BOUNDS.left + NEW_BOUNDS.width().toFloat() / 2 - ICON_SIZE.toFloat() / 2
        val expectedY = NEW_BOUNDS.top + NEW_BOUNDS.height().toFloat() / 2 - ICON_SIZE.toFloat() / 2

        dragIndicatorSurface.show(
            mockTransaction,
            taskInfo,
            mockRootTaskDisplayAreaOrganizer,
            DEFAULT_DISPLAY,
            BOUNDS,
            MultiDisplayDragMoveIndicatorSurface.Visibility.VISIBLE,
        )
        clearInvocations(mockTransaction)
        dragIndicatorSurface.relayout(
            NEW_BOUNDS,
            mockTransaction,
            MultiDisplayDragMoveIndicatorSurface.Visibility.INVISIBLE,
        )

        verify(mockTransaction).setCrop(eq(mockVeilSurface), eq(NEW_BOUNDS))
        verify(mockTransaction).setPosition(eq(mockIconSurface), eq(expectedX), eq(expectedY))
    }

    @Test
    fun relayout_whenInvisibleAndShouldBeVisible_setsCropAndPosition() {
        val expectedX = NEW_BOUNDS.left + NEW_BOUNDS.width().toFloat() / 2 - ICON_SIZE.toFloat() / 2
        val expectedY = NEW_BOUNDS.top + NEW_BOUNDS.height().toFloat() / 2 - ICON_SIZE.toFloat() / 2

        dragIndicatorSurface.relayout(
            NEW_BOUNDS,
            mockTransaction,
            MultiDisplayDragMoveIndicatorSurface.Visibility.VISIBLE,
        )

        verify(mockTransaction).setCrop(eq(mockVeilSurface), eq(NEW_BOUNDS))
        verify(mockTransaction).setPosition(eq(mockIconSurface), eq(expectedX), eq(expectedY))
    }

    @Test
    fun relayout_whenInvisibleAndShouldBeInvisible_doesNotSetCropOrPosition() {
        dragIndicatorSurface.relayout(
            NEW_BOUNDS,
            mockTransaction,
            MultiDisplayDragMoveIndicatorSurface.Visibility.INVISIBLE,
        )

        verify(mockTransaction, never()).setCrop(any(), any())
        verify(mockTransaction, never()).setPosition(any(), any(), any())
    }

    @Test
    fun relayout_whenVisibleAndShouldBeTranslucent_setAlpha() {
        dragIndicatorSurface.show(
            mockTransaction,
            taskInfo,
            mockRootTaskDisplayAreaOrganizer,
            DEFAULT_DISPLAY,
            BOUNDS,
            MultiDisplayDragMoveIndicatorSurface.Visibility.VISIBLE,
        )
        clearInvocations(mockTransaction)
        dragIndicatorSurface.relayout(
            NEW_BOUNDS,
            mockTransaction,
            MultiDisplayDragMoveIndicatorSurface.Visibility.TRANSLUCENT,
        )

        verify(mockTransaction).setAlpha(eq(mockVeilSurface), eq(ALPHA_FOR_TRANSLUCENT))
        verify(mockTransaction).setAlpha(eq(mockIconSurface), eq(ALPHA_FOR_TRANSLUCENT))
    }

    @Test
    fun relayout_whenTranslucentAndShouldBeVisible_setAlpha() {
        dragIndicatorSurface.show(
            mockTransaction,
            taskInfo,
            mockRootTaskDisplayAreaOrganizer,
            DEFAULT_DISPLAY,
            BOUNDS,
            MultiDisplayDragMoveIndicatorSurface.Visibility.TRANSLUCENT,
        )
        clearInvocations(mockTransaction)
        dragIndicatorSurface.relayout(
            NEW_BOUNDS,
            mockTransaction,
            MultiDisplayDragMoveIndicatorSurface.Visibility.VISIBLE,
        )

        verify(mockTransaction).setAlpha(eq(mockVeilSurface), eq(ALPHA_FOR_VISIBLE))
        verify(mockTransaction).setAlpha(eq(mockIconSurface), eq(ALPHA_FOR_VISIBLE))
    }

    companion object {
        private const val CORNER_RADIUS = 32
        private const val ICON_SIZE = 48
        private val BOUNDS = Rect(10, 20, 100, 200)
        private val NEW_BOUNDS = Rect(50, 50, 150, 250)
        private val ALPHA_FOR_TRANSLUCENT = 0.8f
        private val ALPHA_FOR_VISIBLE = 1.0f
    }
}
