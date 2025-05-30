/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.wm.shell.compatui.letterbox

import android.graphics.Point
import android.graphics.Rect
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import android.view.SurfaceControl
import android.view.WindowManager.TRANSIT_CLOSE
import android.window.WindowContainerToken
import androidx.test.filters.SmallTest
import com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn
import com.android.window.flags.Flags
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.common.transition.TransitionStateHolder
import com.android.wm.shell.compatui.letterbox.lifecycle.FakeLetterboxLifecycleEventFactory
import com.android.wm.shell.compatui.letterbox.lifecycle.LetterboxLifecycleController
import com.android.wm.shell.compatui.letterbox.lifecycle.LetterboxLifecycleControllerImpl
import com.android.wm.shell.compatui.letterbox.lifecycle.toLetterboxLifecycleEvent
import com.android.wm.shell.recents.RecentsTransitionHandler
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.util.TransitionObserverInputBuilder
import com.android.wm.shell.util.executeTransitionObserverTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.util.function.Consumer

/**
 * Tests for [LetterboxTransitionObserver] using [LetterboxLifecycleControllerImpl].
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:MigrationLetterboxTransitionObserverTest
 */
@RunWith(AndroidTestingRunner::class)
@SmallTest
class MigrationLetterboxTransitionObserverTest : ShellTestCase() {

    @Test
    @DisableFlags(Flags.FLAG_APP_COMPAT_REFACTORING)
    fun `when initialized and flag disabled the observer is not registered`() {
        runTestScenario { r ->
            executeTransitionObserverTest(observerFactory = r.observerFactory) {
                r.invokeShellInit()
                r.checkObservableIsRegistered(expected = false)
            }
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_APP_COMPAT_REFACTORING)
    fun `when initialized and flag enabled the observer is registered`() {
        runTestScenario { r ->
            executeTransitionObserverTest(observerFactory = r.observerFactory) {
                r.invokeShellInit()
                r.checkObservableIsRegistered(expected = true)
            }
        }
    }

    @Test
    fun `LetterboxController not used without TaskInfos in Change`() {
        runTestScenario { r ->
            executeTransitionObserverTest(observerFactory = r.observerFactory) {
                r.invokeShellInit()

                inputBuilder {
                    buildTransitionInfo()
                    addChange(createChange())
                    addChange(createChange())
                    addChange(createChange())
                }

                validateOutput {
                    r.creationEventDetected(expected = false)
                    r.configureStrategyInvoked(expected = false)
                    r.visibilityEventDetected(expected = false)
                    r.destroyEventDetected(expected = false)
                    r.updateSurfaceBoundsEventDetected(expected = false)
                    r.checkOnLetterboxLifecycleEventFactory { factory ->
                        assert(factory.canHandleInvokeTimes == 3)
                    }
                }
            }
        }
    }

    @Test
    fun `When a topActivity is letterboxed surfaces creation is requested`() {
        runTestScenario { r ->
            executeTransitionObserverTest(observerFactory = r.observerFactory) {
                r.invokeShellInit()

                inputBuilder {
                    buildTransitionInfo()
                    r.createTopActivityChange(
                        inputBuilder = this,
                        isLetterboxed = true,
                        letterboxBounds = Rect(500, 0, 1500, 1000),
                        taskPosition = Point(20, 30),
                        taskWidth = 200,
                        taskHeight = 300
                    )
                }

                validateOutput {
                    r.creationEventDetected(expected = true)
                    r.configureStrategyInvoked(expected = true)
                    r.visibilityEventDetected(expected = false)
                    r.destroyEventDetected(expected = false)
                    r.updateSurfaceBoundsEventDetected(
                        expected = true,
                        taskBounds = Rect(20, 30, 200, 300),
                        activityBounds = Rect(500, 0, 1500, 1000)
                    )
                }
            }
        }
    }

    @Test
    fun `When a topActivity is not letterboxed visibility is updated`() {
        runTestScenario { r ->
            executeTransitionObserverTest(observerFactory = r.observerFactory) {
                r.invokeShellInit()

                inputBuilder {
                    buildTransitionInfo()
                    r.createTopActivityChange(inputBuilder = this, isLetterboxed = false)
                }

                validateOutput {
                    r.creationEventDetected(expected = false)
                    r.visibilityEventDetected(expected = true, visible = false)
                    r.destroyEventDetected(expected = false)
                    r.updateSurfaceBoundsEventDetected(expected = false)
                }
            }
        }
    }

    @Test
    fun `When closing change with no recents running letterbox surfaces are destroyed`() {
        runTestScenario { r ->
            executeTransitionObserverTest(observerFactory = r.observerFactory) {
                r.invokeShellInit()

                inputBuilder {
                    buildTransitionInfo()
                    r.configureRecentsState(running = false)
                    r.createClosingChange(inputBuilder = this)
                }

                validateOutput {
                    r.destroyEventDetected(expected = true)
                }
            }
        }
    }

    @Test
    fun `When closing change and recents are running letterbox surfaces are not destroyed`() {
        runTestScenario { r ->
            executeTransitionObserverTest(observerFactory = r.observerFactory) {
                r.invokeShellInit()

                inputBuilder {
                    buildTransitionInfo()
                    r.createClosingChange(inputBuilder = this)
                    r.configureRecentsState(running = true)
                }

                validateOutput {
                    r.destroyEventDetected(expected = false)
                }
            }
        }
    }

    /**
     * Runs a test scenario providing a Robot.
     */
    fun runTestScenario(consumer: Consumer<LetterboxTransitionObserverRobotTest>) {
        val robot = LetterboxTransitionObserverRobotTest()
        consumer.accept(robot)
    }

    class LetterboxTransitionObserverRobotTest {

        companion object {
            @JvmStatic
            private val DISPLAY_ID = 1

            @JvmStatic
            private val TASK_ID = 20
        }

        private val executor: ShellExecutor
        private val shellInit: ShellInit
        private val transitions: Transitions
        private val letterboxController: LetterboxController
        private val letterboxObserver: DelegateLetterboxTransitionObserver
        private val transitionStateHolder: TransitionStateHolder
        private val letterboxStrategy: LetterboxControllerStrategy
        private val letterboxLifecycleContoller: LetterboxLifecycleController
        private var letterboxLifecycleEventFactory: FakeLetterboxLifecycleEventFactory

        val observerFactory: () -> DelegateLetterboxTransitionObserver

        init {
            executor = mock<ShellExecutor>()
            shellInit = ShellInit(executor)
            transitions = mock<Transitions>()
            letterboxController = mock<LetterboxController>()
            letterboxStrategy = mock<LetterboxControllerStrategy>()
            transitionStateHolder =
                TransitionStateHolder(shellInit, mock<RecentsTransitionHandler>())
            spyOn(transitionStateHolder)
            letterboxLifecycleContoller = LetterboxLifecycleControllerImpl(
                letterboxController,
                transitionStateHolder,
                letterboxStrategy
            )
            letterboxLifecycleEventFactory = FakeLetterboxLifecycleEventFactory(
                eventToReturnFactory = { c -> c.toLetterboxLifecycleEvent() }
            )
            letterboxObserver =
                DelegateLetterboxTransitionObserver(
                    shellInit,
                    transitions,
                    letterboxLifecycleContoller,
                    letterboxLifecycleEventFactory
                )
            observerFactory = { letterboxObserver }
        }

        fun invokeShellInit() = shellInit.init()

        fun observer() = letterboxObserver

        fun checkObservableIsRegistered(expected: Boolean) {
            verify(transitions, expected.asMode()).registerObserver(observer())
        }

        fun checkOnLetterboxLifecycleEventFactory(
            consumer: (FakeLetterboxLifecycleEventFactory) -> Unit
        ) {
            consumer(letterboxLifecycleEventFactory)
        }

        fun configureRecentsState(running: Boolean) {
            doReturn(running).`when`(transitionStateHolder).isRecentsTransitionRunning()
        }

        fun creationEventDetected(
            expected: Boolean,
            displayId: Int = DISPLAY_ID,
            taskId: Int = TASK_ID
        ) = verify(
            letterboxController,
            expected.asMode()
        ).createLetterboxSurface(
            eq(LetterboxKey(displayId, taskId)),
            any<SurfaceControl.Transaction>(),
            any<SurfaceControl>(),
            any<WindowContainerToken>()
        )

        // Visible param is used only if expected is true.
        fun visibilityEventDetected(
            expected: Boolean,
            visible: Boolean = true,
            displayId: Int = DISPLAY_ID,
            taskId: Int = TASK_ID
        ) = verify(letterboxController, expected.asMode()).updateLetterboxSurfaceVisibility(
            eq(LetterboxKey(displayId, taskId)),
            any<SurfaceControl.Transaction>(),
            if (expected) eq(visible) else any()
        )

        fun destroyEventDetected(
            expected: Boolean,
            displayId: Int = DISPLAY_ID,
            taskId: Int = TASK_ID
        ) = verify(
            letterboxController,
            expected.asMode()
        ).destroyLetterboxSurface(
            eq(LetterboxKey(displayId, taskId)),
            any<SurfaceControl.Transaction>()
        )

        fun updateSurfaceBoundsEventDetected(
            expected: Boolean,
            displayId: Int = DISPLAY_ID,
            taskId: Int = TASK_ID,
            taskBounds: Rect = Rect(),
            activityBounds: Rect = Rect()
        ) = verify(
            letterboxController,
            expected.asMode()
        ).updateLetterboxSurfaceBounds(
            eq(LetterboxKey(displayId, taskId)),
            any<SurfaceControl.Transaction>(),
            eq(taskBounds),
            eq(activityBounds)
        )

        fun configureStrategyInvoked(expected: Boolean) =
            verify(letterboxStrategy, expected.asMode()).configureLetterboxMode()

        fun createTopActivityChange(
            inputBuilder: TransitionObserverInputBuilder,
            isLetterboxed: Boolean = true,
            letterboxBounds: Rect? = null,
            displayId: Int = DISPLAY_ID,
            taskId: Int = TASK_ID,
            taskPosition: Point = Point(),
            taskWidth: Int = 0,
            taskHeight: Int = 0
        ) {
            inputBuilder.addChange(
                inputBuilder.createChange(
                    changeTaskInfo = inputBuilder.createTaskInfo().apply {
                        appCompatTaskInfo.isTopActivityLetterboxed = isLetterboxed
                        appCompatTaskInfo.topActivityLetterboxBounds = letterboxBounds
                        this.taskId = taskId
                        this.displayId = displayId
                    }
                ).apply {
                    endRelOffset.x = taskPosition.x
                    endRelOffset.y = taskPosition.y
                    endAbsBounds.set(Rect(0, 0, taskWidth, taskHeight))
                })
        }

        fun createClosingChange(
            inputBuilder: TransitionObserverInputBuilder,
            displayId: Int = DISPLAY_ID,
            taskId: Int = TASK_ID
        ) {
            inputBuilder.addChange(changeTaskInfo = inputBuilder.createTaskInfo().apply {
                this.taskId = taskId
                this.displayId = displayId
            }, changeMode = TRANSIT_CLOSE)
        }
    }
}
