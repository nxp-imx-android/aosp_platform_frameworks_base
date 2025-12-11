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

package com.android.wm.shell.transition
import android.app.ActivityManager.RunningTaskInfo
import android.os.Binder
import android.view.SurfaceControl
import android.view.SurfaceControl.Transaction
import android.view.WindowManager.TRANSIT_CHANGE
import android.view.WindowManager.TRANSIT_OPEN
import android.window.TransitionInfo.FLAG_IS_DISPLAY
import android.window.TransitionInfo.FLAG_MOVED_TO_TOP
import android.window.WindowContainerToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.testing.wm.util.TransitionInfoBuilder
import com.android.wm.shell.ShellTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify

/**
 * Tests for the transition leash manager that sets up and tears down inner and handler-specific
 * leashes.
 *
 * Build/Install/Run: atest WMShellUnitTests:TransitionLeashManagerTest
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class TransitionLeashManagerTest : ShellTestCase() {
    private val underTest = TransitionLeashManager()

    @Test
    fun testHandlerLeashLifecycle() {
        val taskInfo = RunningTaskInfo().apply { token = mock<WindowContainerToken>() }
        val info =
            TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_CHANGE)
                .addChange(TRANSIT_OPEN, 0 /* flags */, taskInfo)
                .addChange(TRANSIT_CHANGE)
                .build()

        val displayChange = info.changes[0]
        val displayChangeSurface = displayChange.leash
        displayChange.flags = displayChange.flags or FLAG_IS_DISPLAY or FLAG_MOVED_TO_TOP

        val independentChange = info.changes[1]
        val independentChangeSurface = independentChange.leash

        val dependentChange = info.changes[2]
        val dependentChangeSurface = dependentChange.leash
        dependentChange.parent = independentChange.container

        val token = Binder()

        val transaction = mock<Transaction>()

        underTest.setUpLeashes(token, info, transaction)
        // Only the independent change should have a handler leash.
        val leashCaptor = argumentCaptor<SurfaceControl>()
        verify(transaction, never()).reparent(eq(dependentChangeSurface), any())
        assertEquals(dependentChangeSurface, dependentChange.leash)
        verify(transaction, never()).reparent(eq(displayChangeSurface), any())
        assertEquals(displayChangeSurface, displayChange.leash)
        verify(transaction).reparent(eq(independentChangeSurface), leashCaptor.capture())
        val leash = leashCaptor.firstValue
        verify(transaction).reparent(eq(leash), eq(info.getRoot(0).leash))
        assertEquals(leash, independentChange.leash)
        reset(transaction)

        underTest.detachLeashes(token, info, transaction)
        // The change with a parent has its leash reparented to it. The independent change has its
        // original leash reparented to the root. All changes are updated with the original leashes.
        verify(transaction).reparent(eq(dependentChangeSurface), eq(independentChangeSurface))
        assertEquals(dependentChangeSurface, dependentChange.leash)
        verify(transaction, never()).reparent(eq(displayChangeSurface), any())
        assertEquals(displayChangeSurface, displayChange.leash)
        verify(transaction, never()).reparent(eq(leash), any())
        verify(transaction).reparent(eq(independentChangeSurface), eq(info.getRoot(0).leash))
        assertEquals(independentChangeSurface, independentChange.leash)

        underTest.cleanUp(token)
        // The handler leash is released.
        assertFalse(leash.isValid)
    }
}
