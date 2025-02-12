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

import android.app.ActivityManager
import android.app.ActivityManager.RunningTaskInfo
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import android.testing.TestableLooper.RunWithLooper
import android.view.Display
import android.view.SurfaceControl
import android.view.SurfaceControlViewHost
import android.view.View
import androidx.test.filters.SmallTest
import com.android.window.flags.Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestRunningTaskInfoBuilder
import com.android.wm.shell.TestShellExecutor
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayLayout
import com.android.wm.shell.common.SyncTransactionQueue
import com.android.wm.shell.shared.bubbles.BubbleDropTargetBoundsProvider
import com.android.wm.shell.windowdecor.WindowDecoration.SurfaceControlViewHostFactory
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyZeroInteractions
import org.mockito.kotlin.whenever

/**
 * Test class for [VisualIndicatorViewContainer] and [VisualIndicatorAnimator]
 *
 * Usage: atest WMShellUnitTests:VisualIndicatorViewContainerTest
 */
@SmallTest
@RunWithLooper
@RunWith(AndroidTestingRunner::class)
@EnableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
class VisualIndicatorViewContainerTest : ShellTestCase() {
    @Mock private lateinit var view: View
    @Mock private lateinit var displayLayout: DisplayLayout
    @Mock private lateinit var displayController: DisplayController
    @Mock private lateinit var taskSurface: SurfaceControl
    @Mock private lateinit var syncQueue: SyncTransactionQueue
    @Mock private lateinit var mockSurfaceControlViewHostFactory: SurfaceControlViewHostFactory
    @Mock private lateinit var mockBackground: LayerDrawable
    @Mock private lateinit var bubbleDropTargetBoundsProvider: BubbleDropTargetBoundsProvider
    private val taskInfo: RunningTaskInfo = createTaskInfo()
    private val mainExecutor = TestShellExecutor()
    private val desktopExecutor = TestShellExecutor()

    @Before
    fun setUp() {
        whenever(displayController.getDisplayLayout(anyInt())).thenReturn(displayLayout)
        whenever(displayLayout.getStableBounds(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(DISPLAY_BOUNDS)
        }
        whenever(mockSurfaceControlViewHostFactory.create(any(), any(), any()))
            .thenReturn(mock(SurfaceControlViewHost::class.java))
    }

    @Test
    fun testTransitionIndicator_sameTypeReturnsEarly() {
        val spyViewContainer = setupSpyViewContainer()
        // Test early return on startType == endType.
        spyViewContainer.transitionIndicator(
            taskInfo,
            displayController,
            DesktopModeVisualIndicator.IndicatorType.TO_FULLSCREEN_INDICATOR,
            DesktopModeVisualIndicator.IndicatorType.TO_FULLSCREEN_INDICATOR,
        )
        desktopExecutor.flushAll()
        verify(spyViewContainer)
            .transitionIndicator(
                eq(taskInfo),
                eq(displayController),
                eq(DesktopModeVisualIndicator.IndicatorType.TO_FULLSCREEN_INDICATOR),
                eq(DesktopModeVisualIndicator.IndicatorType.TO_FULLSCREEN_INDICATOR),
            )
        // Assert fadeIn, fadeOut, and animateIndicatorType were not called.
        verifyZeroInteractions(spyViewContainer)
    }

    @Test
    fun testTransitionIndicator_firstTypeNoIndicator_callsFadeIn() {
        val spyViewContainer = setupSpyViewContainer()
        spyViewContainer.transitionIndicator(
            taskInfo,
            displayController,
            DesktopModeVisualIndicator.IndicatorType.NO_INDICATOR,
            DesktopModeVisualIndicator.IndicatorType.TO_FULLSCREEN_INDICATOR,
        )
        desktopExecutor.flushAll()
        verify(spyViewContainer).fadeInIndicator(any(), any())
    }

    @Test
    fun testTransitionIndicator_secondTypeNoIndicator_callsFadeOut() {
        val spyViewContainer = setupSpyViewContainer()
        spyViewContainer.transitionIndicator(
            taskInfo,
            displayController,
            DesktopModeVisualIndicator.IndicatorType.TO_FULLSCREEN_INDICATOR,
            DesktopModeVisualIndicator.IndicatorType.NO_INDICATOR,
        )
        desktopExecutor.flushAll()
        verify(spyViewContainer)
            .fadeOutIndicator(
                any(),
                eq(DesktopModeVisualIndicator.IndicatorType.TO_FULLSCREEN_INDICATOR),
                anyOrNull(),
            )
    }

    @Test
    fun testTransitionIndicator_differentTypes_callsTransitionIndicator() {
        val spyViewContainer = setupSpyViewContainer()
        spyViewContainer.transitionIndicator(
            taskInfo,
            displayController,
            DesktopModeVisualIndicator.IndicatorType.TO_FULLSCREEN_INDICATOR,
            DesktopModeVisualIndicator.IndicatorType.TO_SPLIT_LEFT_INDICATOR,
        )
        desktopExecutor.flushAll()
        verify(spyViewContainer)
            .transitionIndicator(
                any(),
                any(),
                eq(DesktopModeVisualIndicator.IndicatorType.TO_FULLSCREEN_INDICATOR),
                eq(DesktopModeVisualIndicator.IndicatorType.TO_SPLIT_LEFT_INDICATOR),
            )
    }

    @Test
    fun testFadeInBoundsCalculation() {
        val spyIndicator = setupSpyViewContainer()
        val animator =
            spyIndicator.indicatorView?.let {
                VisualIndicatorViewContainer.VisualIndicatorAnimator.fadeBoundsIn(
                    it,
                    DesktopModeVisualIndicator.IndicatorType.TO_FULLSCREEN_INDICATOR,
                    displayLayout,
                    bubbleDropTargetBoundsProvider,
                )
            }
        assertThat(animator?.indicatorStartBounds).isEqualTo(Rect(15, 15, 985, 985))
        assertThat(animator?.indicatorEndBounds).isEqualTo(Rect(0, 0, 1000, 1000))
    }

    @Test
    fun testFadeOutBoundsCalculation() {
        val spyIndicator = setupSpyViewContainer()
        val animator =
            spyIndicator.indicatorView?.let {
                VisualIndicatorViewContainer.VisualIndicatorAnimator.fadeBoundsOut(
                    it,
                    DesktopModeVisualIndicator.IndicatorType.TO_FULLSCREEN_INDICATOR,
                    displayLayout,
                    bubbleDropTargetBoundsProvider,
                )
            }
        assertThat(animator?.indicatorStartBounds).isEqualTo(Rect(0, 0, 1000, 1000))
        assertThat(animator?.indicatorEndBounds).isEqualTo(Rect(15, 15, 985, 985))
    }

    @Test
    fun testChangeIndicatorTypeBoundsCalculation() {
        // Test fullscreen to split-left bounds.
        var animator =
            VisualIndicatorViewContainer.VisualIndicatorAnimator.animateIndicatorType(
                view,
                displayLayout,
                DesktopModeVisualIndicator.IndicatorType.TO_FULLSCREEN_INDICATOR,
                DesktopModeVisualIndicator.IndicatorType.TO_SPLIT_LEFT_INDICATOR,
                bubbleDropTargetBoundsProvider,
            )
        // Test desktop to split-right bounds.
        animator =
            VisualIndicatorViewContainer.VisualIndicatorAnimator.animateIndicatorType(
                view,
                displayLayout,
                DesktopModeVisualIndicator.IndicatorType.TO_DESKTOP_INDICATOR,
                DesktopModeVisualIndicator.IndicatorType.TO_SPLIT_RIGHT_INDICATOR,
                bubbleDropTargetBoundsProvider,
            )
    }

    private fun setupSpyViewContainer(): VisualIndicatorViewContainer {
        val viewContainer =
            VisualIndicatorViewContainer(
                desktopExecutor,
                mainExecutor,
                SurfaceControl.Builder(),
                syncQueue,
                mockSurfaceControlViewHostFactory,
                bubbleDropTargetBoundsProvider,
            )
        viewContainer.createView(
            context,
            mock(Display::class.java),
            displayLayout,
            taskInfo,
            taskSurface,
        )
        desktopExecutor.flushAll()
        viewContainer.indicatorView?.background = mockBackground
        whenever(mockBackground.findDrawableByLayerId(anyInt()))
            .thenReturn(mock(Drawable::class.java))
        return spy(viewContainer)
    }

    private fun createTaskInfo(): RunningTaskInfo {
        val taskDescriptionBuilder = ActivityManager.TaskDescription.Builder()
        return TestRunningTaskInfoBuilder()
            .setDisplayId(Display.DEFAULT_DISPLAY)
            .setTaskDescriptionBuilder(taskDescriptionBuilder)
            .setVisible(true)
            .build()
    }

    companion object {
        private val DISPLAY_BOUNDS = Rect(0, 0, 1000, 1000)
    }
}
