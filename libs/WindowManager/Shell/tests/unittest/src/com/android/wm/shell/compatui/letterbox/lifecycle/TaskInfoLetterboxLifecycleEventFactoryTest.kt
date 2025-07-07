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

package com.android.wm.shell.compatui.letterbox.lifecycle

import android.graphics.Point
import android.graphics.Rect
import android.testing.AndroidTestingRunner
import android.view.SurfaceControl
import android.window.WindowContainerToken
import androidx.test.filters.SmallTest
import com.android.wm.shell.compatui.letterbox.config.LetterboxDependenciesHelper
import com.android.wm.shell.util.testLetterboxLifecycleEventFactory
import java.util.function.Consumer
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * Tests for [TaskInfoLetterboxLifecycleEventFactory].
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:TaskInfoLetterboxLifecycleEventFactoryTest
 */
@RunWith(AndroidTestingRunner::class)
@SmallTest
class TaskInfoLetterboxLifecycleEventFactoryTest {

    @Test
    fun `Change without TaskInfo cannot create the event and returns null`() {
        runTestScenario { r ->
            testLetterboxLifecycleEventFactory(r.getLetterboxLifecycleEventFactory()) {
                inputChange {
                    // Empty Change
                }
                validateCanHandle { canHandle ->
                    assertFalse(canHandle)
                }
                validateCreateLifecycleEvent { event ->
                    assertNull(event)
                }
            }
        }
    }

    @Test
    fun `With TaskInfo for Bubble a bubble event is returned`() {
        runTestScenario { r ->
            testLetterboxLifecycleEventFactory(r.getLetterboxLifecycleEventFactory()) {
                inputChange {
                    endAbsBounds = Rect(100, 200, 2000, 1000)
                    runningTaskInfo { ti ->
                        ti.isAppBubble = true
                    }
                    endAbsBounds = Rect(0, 0, 500, 1000)
                }
                validateCanHandle { canHandle ->
                    assertTrue(canHandle)
                }
                validateCreateLifecycleEvent { event ->
                    assertNotNull(event)
                    assertTrue(event.isBubble)
                }
            }
        }
    }

    @Test
    fun `With TaskInfo taskBounds are calculated from endAbsBounds`() {
        runTestScenario { r ->
            testLetterboxLifecycleEventFactory(r.getLetterboxLifecycleEventFactory()) {
                inputChange {
                    runningTaskInfo { }
                    endAbsBounds = Rect(100, 200, 2000, 1000)
                }
                validateCanHandle { canHandle ->
                    assertTrue(canHandle)
                }
                validateCreateLifecycleEvent { event ->
                    assertNotNull(event)
                    assertEquals(Rect(0, 0, 1900, 800), event.taskBounds)
                }
            }
        }
    }

    @Test
    fun `With TaskInfo letterboxBounds are null when Activity is not letterboxed`() {
        runTestScenario { r ->
            testLetterboxLifecycleEventFactory(r.getLetterboxLifecycleEventFactory()) {
                inputChange {
                    runningTaskInfo { ti ->
                        ti.appCompatTaskInfo.isTopActivityLetterboxed = false
                    }
                    endAbsBounds = Rect(0, 0, 500, 1000)
                }
                validateCanHandle { canHandle ->
                    assertTrue(canHandle)
                }
                validateCreateLifecycleEvent { event ->
                    assertNotNull(event)
                    assertNull(event.letterboxBounds)
                }
            }
        }
    }

    @Test
    fun `With TaskInfo letterboxBounds from appCompatTaskInfo when Activity is letterboxed`() {
        runTestScenario { r ->
            testLetterboxLifecycleEventFactory(r.getLetterboxLifecycleEventFactory()) {
                inputChange {
                    runningTaskInfo { ti ->
                        ti.appCompatTaskInfo.isTopActivityLetterboxed = true
                        ti.appCompatTaskInfo.topActivityLetterboxBounds = Rect(300, 200, 2300, 1200)
                    }
                    endAbsBounds = Rect(100, 50, 2500, 1500)
                }
                validateCanHandle { canHandle ->
                    assertTrue(canHandle)
                }
                validateCreateLifecycleEvent { event ->
                    assertNotNull(event)
                    assertEquals(Rect(200, 150, 2200, 1150), event.letterboxBounds)
                }
            }
        }
    }

    @Test
    fun `With TaskInfo leash from Change`() {
        runTestScenario { r ->
            testLetterboxLifecycleEventFactory(r.getLetterboxLifecycleEventFactory()) {
                val inputLeash = mock<SurfaceControl>()
                inputChange {
                    runningTaskInfo {}
                    endAbsBounds = Rect(0, 0, 500, 1000)
                    endRelOffset = Point(100, 200)
                    leash { inputLeash }
                }
                validateCanHandle { canHandle ->
                    assertTrue(canHandle)
                }
                validateCreateLifecycleEvent { event ->
                    assertNotNull(event)
                    assertEquals(inputLeash, event.taskLeash)
                }
            }
        }
    }

    @Test
    fun `supportsInput comes from LetterboxDependencyHelper`() {
        runTestScenario { r ->
            testLetterboxLifecycleEventFactory(r.getLetterboxLifecycleEventFactory()) {
                inputChange {
                    runningTaskInfo {}
                }

                r.shouldSupportInputSurface(shouldSupportInputSurface = true)
                validateCreateLifecycleEvent { event ->
                    assertNotNull(event)
                    assertTrue(event.supportsInput)
                }

                r.shouldSupportInputSurface(shouldSupportInputSurface = false)
                validateCreateLifecycleEvent { event ->
                    assertNotNull(event)
                    assertFalse(event.supportsInput)
                }
            }
        }
    }

    @Test
    fun `With TaskInfo token from TaskInfo`() {
        runTestScenario { r ->
            testLetterboxLifecycleEventFactory(r.getLetterboxLifecycleEventFactory()) {
                val inputToken = mock<WindowContainerToken>()
                inputChange {
                    runningTaskInfo { ti ->
                        ti.token = inputToken
                    }
                    endAbsBounds = Rect(0, 0, 500, 1000)
                    endRelOffset = Point(100, 200)
                }
                validateCanHandle { canHandle ->
                    assertTrue(canHandle)
                }
                validateCreateLifecycleEvent { event ->
                    assertNotNull(event)
                    assertEquals(inputToken, event.containerToken)
                }
            }
        }
    }

    /**
     * Runs a test scenario providing a Robot.
     */
    fun runTestScenario(consumer: Consumer<TaskInfoLetterboxLifecycleEventFactoryRobotTest>) {
        val robot = TaskInfoLetterboxLifecycleEventFactoryRobotTest()
        consumer.accept(robot)
    }

    /**
     * Robot contextual to [TaskInfoLetterboxLifecycleEventFactory].
     */
    class TaskInfoLetterboxLifecycleEventFactoryRobotTest {

        private val dependencyHelper: LetterboxDependenciesHelper =
            mock<LetterboxDependenciesHelper>()

        fun getLetterboxLifecycleEventFactory(): () -> LetterboxLifecycleEventFactory = {
            TaskInfoLetterboxLifecycleEventFactory(dependencyHelper)
        }

        fun shouldSupportInputSurface(shouldSupportInputSurface: Boolean) {
            doReturn(shouldSupportInputSurface).`when`(dependencyHelper)
                .shouldSupportInputSurface(any())
        }
    }
}
