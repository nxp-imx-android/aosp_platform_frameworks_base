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

package com.android.wm.shell.pinnedlayer.phone

import android.app.TaskInfo
import android.content.res.Resources
import android.graphics.Rect
import android.platform.test.annotations.EnableFlags
import android.testing.TestableLooper
import android.util.DisplayMetrics
import android.view.Display
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.window.flags.Flags
import com.android.wm.shell.R
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestRunningTaskInfoBuilder
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayLayout
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when` as whenever
import org.mockito.MockitoAnnotations

/**
 * Unit tests against [PinnedLayerPresentationController]
 *
 * Build/Install/Run: atest WMShellUnitTests:PinnedLayerPresentationControllerTests
 */
@SmallTest
@TestableLooper.RunWithLooper
@EnableFlags(Flags.FLAG_ENABLE_INTERACTIVE_PICTURE_IN_PICTURE)
@RunWith(AndroidJUnit4::class)
class PinnedLayerPresentationControllerTests : ShellTestCase() {
    @Mock private lateinit var displayController: DisplayController
    @Mock private lateinit var displayLayout: DisplayLayout
    private lateinit var resources: Resources
    private lateinit var displayMetrics: DisplayMetrics

    private lateinit var controller: PinnedLayerPresentationController

    @Before
    fun setUp() {
        // Default display setup: 1080x1920, density 2.0, insets top=100 bottom=50
        // 10px padding for pinned window
        val testableResources = mContext.getOrCreateTestableResources()
        testableResources.addOverride(R.dimen.pinned_window_init_padding, 10)
        displayMetrics = DisplayMetrics()
        displayMetrics.density = 2.0f
        whenever(testableResources.resources.displayMetrics).thenReturn(displayMetrics)
        whenever(displayController.getDisplayLayout(Display.DEFAULT_DISPLAY))
            .thenReturn(displayLayout)
        whenever(displayController.getDisplayContext(Display.DEFAULT_DISPLAY)).thenReturn(context)
        whenever(displayLayout.width()).thenReturn(1080)
        whenever(displayLayout.height()).thenReturn(1920)
        whenever(displayLayout.stableInsets()).thenReturn(Rect(0, 100, 0, 50))

        controller = PinnedLayerPresentationController(context, displayController)
    }

    @Test
    fun getPinEntryDestinationBounds_taskWithinLimits_noScaling() {
        // minSize = 220dp * 2.0 = 440px
        // maxWidth = 1080 * 0.7 = 756px
        // maxHeight = 1920 * 0.7 = 1344px
        // Task size (500x600) is within these limits.
        val task = createTaskInfo(Rect(0, 0, 500, 600))

        val bounds = controller.getPinEntryDestinationBounds(task)

        // Expected position: bottom-right corner considering stable insets
        // right = 1080 - 0 (inset) - 10 (padding) = 1070
        // bottom = 1920 - 50 (inset) - 10 (padding) = 1860
        // left = 1070 - 500 = 570
        // top = 1860 - 600 = 1260
        val expected = Rect(570, 1260, 1070, 1860)
        assertThat(bounds).isEqualTo(expected)
    }

    @Test
    fun getPinEntryDestinationBounds_taskTooSmall_scalesUp() {
        // minSize = 220dp * 2.0 = 440px
        // Task size (200x300) is smaller than minSize.
        val task = createTaskInfo(Rect(0, 0, 200, 300))

        val bounds = controller.getPinEntryDestinationBounds(task)

        // w0=200, h0=300. minScale = max(440/200, 440/300) = 2.2
        // finalWidth = 200 * 2.2 = 440
        // finalHeight = 300 * 2.2 = 660
        // right = 1070, bottom = 1860
        // left = 1070 - 440 = 630
        // top = 1860 - 660 = 1200
        val expected = Rect(630, 1200, 1070, 1860)
        assertThat(bounds).isEqualTo(expected)
    }

    @Test
    fun getPinEntryDestinationBounds_taskTooLarge_scalesDown() {
        // maxWidth = 1080 * 0.7 = 756px
        // maxHeight = 1920 * 0.7 = 1344px
        // Task size (1000x1500) is larger than max size.
        val task = createTaskInfo(Rect(0, 0, 1000, 1500))

        val bounds = controller.getPinEntryDestinationBounds(task)

        // w0=1000, h0=1500. maxScale = min(756/1000, 1344/1500) = 0.756
        // finalWidth = 1000 * 0.756 = 756
        // finalHeight = 1500 * 0.756 = 1134
        // right = 1070, bottom = 1860
        // left = 1070 - 756 = 314
        // top = 1860 - 1134 = 726
        val expected = Rect(314, 726, 1070, 1860)
        assertThat(bounds).isEqualTo(expected)
    }

    @Test
    fun getPinEntryDestinationBounds_conflictingConstraints_prioritizesMaxSize() {
        // Setup a smaller display where min size in px is larger than max size in px
        whenever(displayLayout.width()).thenReturn(500)
        whenever(displayLayout.height()).thenReturn(800)
        whenever(displayLayout.stableInsets()).thenReturn(Rect()) // No insets

        // minSize = 220dp * 2.0 = 440px
        // maxWidth = 500 * 0.7 = 350px -> Conflict!
        // maxHeight = 800 * 0.7 = 560px
        val task = createTaskInfo(Rect(0, 0, 300, 400))

        val bounds = controller.getPinEntryDestinationBounds(task)

        // w0=300, h0=400
        // minScale = max(440/300, 440/400) = 1.466
        // maxScale = min(350/300, 560/400) = 1.166
        // minScale > maxScale, so scale should be maxScale.
        // finalWidth = 300 * 1.166... = 350
        // finalHeight = 400 * 1.166... = 466
        // right = 500 - 0 (inset) - 10 (padding) = 490
        // bottom = 800 - 0 (inset) - 10 (padding) = 790
        // left = 490 - 350 = 140
        // top = 790 - 466 = 324
        val expected = Rect(140, 324, 490, 790)
        assertThat(bounds).isEqualTo(expected)
    }

    @Test
    fun getPinEntryDestinationBounds_noDisplayLayout_returnsNull() {
        whenever(displayController.getDisplayLayout(Display.DEFAULT_DISPLAY)).thenReturn(null)
        val task = createTaskInfo(Rect(0, 0, 500, 600))

        val bounds = controller.getPinEntryDestinationBounds(task)

        assertThat(bounds).isNull()
    }

    @Test
    fun getPinEntryDestinationBounds_noContext_returnsNull() {
        whenever(displayController.getDisplayContext(Display.DEFAULT_DISPLAY)).thenReturn(null)
        val task = createTaskInfo(Rect(0, 0, 500, 600))

        val bounds = controller.getPinEntryDestinationBounds(task)

        assertThat(bounds).isNull()
    }

    @Test
    fun getPinEntryDestinationBounds_invalidTaskBounds_returnsNull() {
        val task = createTaskInfo(Rect(0, 0, 0, 100)) // Zero width

        val bounds = controller.getPinEntryDestinationBounds(task)

        assertThat(bounds).isNull()
    }

    private fun createTaskInfo(bounds: Rect): TaskInfo {
        return TestRunningTaskInfoBuilder()
            .setDisplayId(Display.DEFAULT_DISPLAY)
            .setBounds(bounds)
            .build()
    }
}
