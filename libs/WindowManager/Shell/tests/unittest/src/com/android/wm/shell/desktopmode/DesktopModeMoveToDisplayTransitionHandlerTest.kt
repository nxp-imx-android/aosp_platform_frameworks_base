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

import android.testing.AndroidTestingRunner
import android.testing.TestableLooper.RunWithLooper
import android.view.WindowManager
import android.window.TransitionInfo
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.util.StubTransaction
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@SmallTest
@RunWithLooper
@RunWith(AndroidTestingRunner::class)
class DesktopModeMoveToDisplayTransitionHandlerTest : ShellTestCase() {
    private lateinit var handler: DesktopModeMoveToDisplayTransitionHandler

    @Before
    fun setUp() {
        handler = DesktopModeMoveToDisplayTransitionHandler(StubTransaction())
    }

    @Test
    fun handleRequest_returnsNull() {
        assertNull(handler.handleRequest(mock(), mock()))
    }

    @Test
    fun startAnimation_changeWithinDisplay_returnsFalse() {
        val animates =
            handler.startAnimation(
                transition = mock(),
                info =
                    TransitionInfo(WindowManager.TRANSIT_CHANGE, /* flags= */ 0).apply {
                        addChange(
                            TransitionInfo.Change(mock(), mock()).apply { setDisplayId(1, 1) }
                        )
                    },
                startTransaction = StubTransaction(),
                finishTransaction = StubTransaction(),
                finishCallback = mock(),
            )

        assertFalse("Should not animate open transition", animates)
    }

    @Test
    fun startAnimation_changeMoveToDisplay_returnsTrue() {
        val animates =
            handler.startAnimation(
                transition = mock(),
                info =
                    TransitionInfo(WindowManager.TRANSIT_CHANGE, /* flags= */ 0).apply {
                        addChange(
                            TransitionInfo.Change(mock(), mock()).apply { setDisplayId(1, 2) }
                        )
                    },
                startTransaction = StubTransaction(),
                finishTransaction = StubTransaction(),
                finishCallback = mock(),
            )

        assertTrue("Should animate display change transition", animates)
    }
}
