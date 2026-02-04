/*
 * Copyright (C) 2026 The Android Open Source Project
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

import android.graphics.Rect
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.window.flags.Flags
import com.android.wm.shell.R
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.desktopmode.DesktopTaskPosition.Center
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test class for {@link DesktopTaskPosition#cascadeWindowStepped}
 *
 * Usage: atest WMShellUnitTests:SteppedCascadingTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class SteppedCascadingTest : ShellTestCase() {

    private fun getCascadingOffset(): Int =
        mContext.resources.getDimensionPixelSize(R.dimen.desktop_mode_cascading_offset)

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_STEPPED_CASCADING)
    fun cascade_fromCenter() {
        val prevBounds = getDefaultBounds()
        val newBounds = getDefaultBounds()

        cascadeWindowStepped(mContext.resources, FRAME, prevBounds, newBounds, false)

        val offset = getCascadingOffset()
        assertThat(newBounds.left).isEqualTo(prevBounds.left + offset)
        assertThat(newBounds.top).isEqualTo(prevBounds.top + offset)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_STEPPED_CASCADING)
    fun cascade_differentSizes_resetsToCenter() {
        val prev = getDefaultBounds().apply { offset(100, 100) }
        val dest =
            Rect(0, 0, DEFAULT_WIDTH / 2, DEFAULT_HEIGHT / 2).apply {
                offsetTo(prev.left, prev.top)
            }

        cascadeWindowStepped(mContext.resources, FRAME, prev, dest, false)

        val centerPos =
            Center.getTopLeftCoordinates(FRAME, Rect(0, 0, DEFAULT_WIDTH / 2, DEFAULT_HEIGHT / 2))
        assertThat(dest.left).isEqualTo(centerPos.x)
        assertThat(dest.top).isEqualTo(centerPos.y)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_REMEMBERED_BOUNDS)
    @EnableFlags(Flags.FLAG_ENABLE_STEPPED_CASCADING)
    fun cascade_rememberedBounds_collision_cascades() {
        // Exact overlap
        val prev = getDefaultBounds()
        val dest = getDefaultBounds()

        cascadeWindowStepped(mContext.resources, FRAME, prev, dest, true)

        val offset = getCascadingOffset()
        assertThat(dest.left).isEqualTo(prev.left + offset)
        assertThat(dest.top).isEqualTo(prev.top + offset)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_REMEMBERED_BOUNDS)
    @EnableFlags(Flags.FLAG_ENABLE_STEPPED_CASCADING)
    fun cascade_rememberedBounds_outOfBounds_fallbackToOriginal() {
        // On the bottom edge, which requires screen wrapping for the next cascading.
        val prev = getDefaultBounds().apply { offsetTo(0, FRAME.bottom - height()) }
        val dest = Rect(prev)

        cascadeWindowStepped(mContext.resources, FRAME, prev, dest, true)

        assertThat(dest).isEqualTo(dest)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_REMEMBERED_BOUNDS)
    @EnableFlags(Flags.FLAG_ENABLE_STEPPED_CASCADING)
    fun cascade_rememberedBounds_maximized_fallbackToOriginal() {
        // Maximized bounds.
        val prev = Rect(FRAME)
        val dest = Rect(FRAME)

        cascadeWindowStepped(mContext.resources, FRAME, prev, dest, true)

        assertThat(dest).isEqualTo(dest)
    }

    private companion object {
        private const val DEFAULT_WIDTH = 1600
        private const val DEFAULT_HEIGHT = 800
        private val FRAME = Rect(0, 0, 2000, 1000)

        private fun getDefaultBounds(): Rect {
            return Rect(0, 0, DEFAULT_WIDTH, DEFAULT_HEIGHT).apply {
                val centerPosPrev = Center.getTopLeftCoordinates(FRAME, this)
                offsetTo(centerPosPrev.x, centerPosPrev.y)
            }
        }
    }
}
