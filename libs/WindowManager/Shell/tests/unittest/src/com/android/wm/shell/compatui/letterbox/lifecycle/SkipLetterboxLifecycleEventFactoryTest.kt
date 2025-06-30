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

import android.testing.AndroidTestingRunner
import android.view.WindowManager.TRANSIT_CLOSE
import android.view.WindowManager.TRANSIT_OPEN
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.util.testLetterboxLifecycleEventFactory
import java.util.function.Consumer
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for [SkipLetterboxLifecycleEventFactory].
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:SkipLetterboxLifecycleEventFactoryTest
 */
@RunWith(AndroidTestingRunner::class)
@SmallTest
class SkipLetterboxLifecycleEventFactoryTest : ShellTestCase() {

    @Test
    fun `Factory is active when Change is a Closing one`() {
        runTestScenario { r ->
            testLetterboxLifecycleEventFactory(r.getLetterboxLifecycleEventFactory()) {
                inputChange {
                    mode = TRANSIT_CLOSE
                }
                validateCanHandle { canHandle ->
                    assert(canHandle)
                }
                validateCreateLifecycleEvent { event ->
                    assert(event == null)
                }
            }
        }
    }

    @Test
    fun `Factory is active when Change is for a Task which is NOT a leaf`() {
        runTestScenario { r ->
            testLetterboxLifecycleEventFactory(r.getLetterboxLifecycleEventFactory()) {
                inputChange {
                    mode = TRANSIT_OPEN
                    runningTaskInfo { ti ->
                        ti.appCompatTaskInfo.setIsLeafTask(false)
                    }
                }
                validateCanHandle { canHandle ->
                    assert(canHandle)
                }
                validateCreateLifecycleEvent { event ->
                    assert(event == null)
                }
            }
        }
    }

    @Test
    fun `Factory is skipped when Change is not closing for a Task which is NOT a leaf`() {
        runTestScenario { r ->
            testLetterboxLifecycleEventFactory(r.getLetterboxLifecycleEventFactory()) {
                inputChange {
                    mode = TRANSIT_OPEN
                    runningTaskInfo { ti ->
                        ti.appCompatTaskInfo.setIsLeafTask(true)
                    }
                }
                validateCanHandle { canHandle ->
                    assert(!canHandle)
                }
                validateCreateLifecycleEvent { event ->
                    assert(event != null)
                    assert(event?.type == LetterboxLifecycleEventType.NONE)
                }
            }
        }
    }

    @Test
    fun `Factory is skipped when Change is not closing for an Activity Transition`() {
        runTestScenario { r ->
            testLetterboxLifecycleEventFactory(r.getLetterboxLifecycleEventFactory()) {
                inputChange {
                    mode = TRANSIT_OPEN
                    activityTransitionInfo {
                        this.taskId = 10
                    }
                }
                validateCanHandle { canHandle ->
                    assert(!canHandle)
                }
                validateCreateLifecycleEvent { event ->
                    assert(event != null)
                    assert(event?.type == LetterboxLifecycleEventType.NONE)
                }
            }
        }
    }

    /**
     * Runs a test scenario providing a Robot.
     */
    fun runTestScenario(consumer: Consumer<DisableLetterboxLifecycleEventFactoryRobotTest>) {
        val robot = DisableLetterboxLifecycleEventFactoryRobotTest()
        consumer.accept(robot)
    }

    /**
     * Robot contextual to [TaskInfoLetterboxLifecycleEventFactory].
     */
    class DisableLetterboxLifecycleEventFactoryRobotTest {

        fun getLetterboxLifecycleEventFactory(): () -> LetterboxLifecycleEventFactory = {
            SkipLetterboxLifecycleEventFactory()
        }
    }
}
