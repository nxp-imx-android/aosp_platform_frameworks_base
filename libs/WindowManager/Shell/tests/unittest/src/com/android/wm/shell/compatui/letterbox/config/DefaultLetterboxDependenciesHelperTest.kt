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

package com.android.wm.shell.compatui.letterbox.config

import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.desktopmode.multidesks.DesksOrganizer
import com.android.wm.shell.util.testLetterboxDependenciesHelper
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.function.Consumer

/**
 * Tests for [DefaultLetterboxDependenciesHelper].
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:DefaultLetterboxDependenciesHelperTest
 */
@RunWith(AndroidTestingRunner::class)
@SmallTest
class DefaultLetterboxDependenciesHelperTest : ShellTestCase() {

    @Test
    fun `Default implementation returns true if isDeskChange is true`() {
        runTestScenario { r ->
            testLetterboxDependenciesHelper(r.getLetterboxLifecycleEventFactory()) {
                inputChange { }
                r.configureDeskChangeChecker(isDeskChange = true)
                validateIsDesktopWindowingAction { isDeskChange ->
                    assert(isDeskChange)
                }
            }
        }
    }

    @Test
    fun `Default implementation returns false if isDeskChange is false`() {
        runTestScenario { r ->
            testLetterboxDependenciesHelper(r.getLetterboxLifecycleEventFactory()) {
                inputChange { }
                r.configureDeskChangeChecker(isDeskChange = true)
                validateIsDesktopWindowingAction { isDeskChange ->
                    assert(!isDeskChange)
                }
            }
        }
    }

    /**
     * Runs a test scenario providing a Robot.
     */
    fun runTestScenario(consumer: Consumer<LetterboxDependenciesHelperRobotTest>) {
        val robot = LetterboxDependenciesHelperRobotTest()
        consumer.accept(robot)
    }

    /**
     * Robot contextual to [LetterboxDependenciesHelper].
     */
    class LetterboxDependenciesHelperRobotTest {

        private val desksOrganizer = mock<DesksOrganizer>()

        fun configureDeskChangeChecker(isDeskChange: Boolean) {
            doReturn(isDeskChange).`when`(desksOrganizer).isDeskChange(any())
        }

        fun getLetterboxLifecycleEventFactory(): () -> LetterboxDependenciesHelper = {
            DefaultLetterboxDependenciesHelper(desksOrganizer)
        }
    }
}
