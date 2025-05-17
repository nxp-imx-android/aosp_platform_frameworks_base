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

package com.android.wm.shell.flicker.bubbles

import android.tools.flicker.rules.RemoveAllTasksButHomeRule.Companion.removeAllTasksButHome
import android.tools.io.Reader
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * A [org.junit.ClassRule] to record trace with transition.
 *
 * @sample com.android.wm.shell.flicker.bubbles.samples.RecordTraceWithTransitionRuleSample
 *
 * @property setUpBeforeTransition the function to initialize the environment before transition
 * @property transition the transition to execute
 * @property tearDownAfterTransition the function to clean up after transition
 */
class RecordTraceWithTransitionRule(
    private val setUpBeforeTransition: () -> Unit,
    private val transition: () -> Unit,
    private val tearDownAfterTransition: () -> Unit,
) : TestRule {

    /**
     * The reader to read trace from.
     */
    lateinit var reader: Reader

    override fun apply(base: Statement, description: Description?): Statement {
        return object : Statement() {
            override fun evaluate() {
                recordTraceWithTransition()
                base.evaluate()
            }
        }
    }

    private fun recordTraceWithTransition() {
        try {
            setUpBeforeTransition()
            reader = runTransitionWithTrace { transition() }
            tearDownAfterTransition()
        } finally {
            // In case the crash during transition and test App is not removed.
            removeAllTasksButHome()
        }
    }
}