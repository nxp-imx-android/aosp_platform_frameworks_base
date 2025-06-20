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

import android.graphics.Rect
import android.testing.AndroidTestingRunner
import android.view.SurfaceControl
import android.window.WindowContainerToken
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.compatui.letterbox.state.LetterboxTaskInfoRepository
import com.android.wm.shell.compatui.letterbox.state.LetterboxTaskInfoState
import com.android.wm.shell.util.testLetterboxLifecycleEventFactory
import java.util.function.Consumer
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

/**
 * Tests for [ActivityLetterboxLifecycleEventFactory].
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:ActivityLetterboxLifecycleEventFactoryTest
 */
@RunWith(AndroidTestingRunner::class)
@SmallTest
class ActivityLetterboxLifecycleEventFactoryTest : ShellTestCase() {

    @Test
    fun `Change without ActivityTransitionInfo cannot create the event`() {
        runTestScenario { r ->
            testLetterboxLifecycleEventFactory(r.getLetterboxLifecycleEventFactory()) {
                inputChange {
                    // Empty Change
                }
                validateCanHandle { canHandle ->
                    assert(canHandle == false)
                }
            }
        }
    }

    @Test
    fun `Read Task bounds from endAbsBounds in Change`() {
        runTestScenario { r ->
            testLetterboxLifecycleEventFactory(r.getLetterboxLifecycleEventFactory()) {
                inputChange {
                    endAbsBounds = Rect(100, 50, 2000, 1500)
                }
                validateCanHandle { canHandle ->
                    assert(canHandle == false)
                }
                validateCreateLifecycleEvent { event ->
                    assert(event?.taskBounds == Rect(0, 0, 1900, 1450))
                }
            }
        }
    }

    @Test
    fun `Read Letterbox bounds from activityTransitionInfo and endAbsBounds in Change`() {
        runTestScenario { r ->
            testLetterboxLifecycleEventFactory(r.getLetterboxLifecycleEventFactory()) {
                inputChange {
                    endAbsBounds = Rect(100, 50, 2000, 1500)
                    activityTransitionInfo {
                        appCompatTransitionInfo {
                            letterboxBounds = Rect(500, 50, 1500, 800)
                        }
                    }
                }
                validateCanHandle { canHandle ->
                    assert(canHandle == false)
                }
                validateCreateLifecycleEvent { event ->
                    assert(event?.taskBounds == Rect(0, 0, 1900, 1450))
                    assert(event?.letterboxBounds == Rect(400, 0, 1400, 750))
                }
            }
        }
    }

    @Test
    fun `Uses leash and token from the repository`() {
        runTestScenario { r ->
            testLetterboxLifecycleEventFactory(r.getLetterboxLifecycleEventFactory()) {
                val testLeash = mock<SurfaceControl>()
                val testToken = mock<WindowContainerToken>()
                r.addToTaskRepository(10, LetterboxTaskInfoState(testToken, testLeash))
                inputChange {
                    leash { testLeash }
                    token { testToken }
                    runningTaskInfo { ti ->
                        ti.taskId = 10
                    }
                }
                validateCanHandle { canHandle ->
                    assert(canHandle == false)
                }
                validateCreateLifecycleEvent { event ->
                    assert(event?.taskLeash == testLeash)
                    assert(event?.containerToken == testToken)
                }
            }
        }
    }

    @Test
    fun `Event is null if repository has no task data`() {
        runTestScenario { r ->
            testLetterboxLifecycleEventFactory(r.getLetterboxLifecycleEventFactory()) {
                val testLeash = mock<SurfaceControl>()
                val testToken = mock<WindowContainerToken>()
                inputChange {
                    leash { testLeash }
                    token { testToken }
                    runningTaskInfo { ti ->
                        ti.taskId = 10
                    }
                }
                validateCanHandle { canHandle ->
                    assert(canHandle == false)
                }
                validateCreateLifecycleEvent { event ->
                    assert(event == null)
                }
            }
        }
    }

    /**
     * Runs a test scenario providing a Robot.
     */
    fun runTestScenario(consumer: Consumer<ActivityLetterboxLifecycleEventFactoryRobotTest>) {
        val robot = ActivityLetterboxLifecycleEventFactoryRobotTest()
        consumer.accept(robot)
    }

    /**
     * Robot contextual to [ActivityLetterboxLifecycleEventFactory].
     */
    class ActivityLetterboxLifecycleEventFactoryRobotTest {

        private val letterboxTaskInfoRepository: LetterboxTaskInfoRepository =
            LetterboxTaskInfoRepository()

        fun getLetterboxLifecycleEventFactory(): () -> LetterboxLifecycleEventFactory = {
            ActivityLetterboxLifecycleEventFactory(letterboxTaskInfoRepository)
        }

        fun addToTaskRepository(key: Int, state: LetterboxTaskInfoState) {
            letterboxTaskInfoRepository.insert(key = key, item = state, overrideIfPresent = true)
        }
    }
}
