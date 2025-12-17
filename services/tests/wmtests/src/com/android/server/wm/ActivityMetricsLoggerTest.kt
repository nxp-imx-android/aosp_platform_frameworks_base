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

package com.android.server.wm

import android.platform.test.annotations.Presubmit
import androidx.test.filters.SmallTest
import com.android.server.wm.WindowTestsBase.ActivityBuilder.DEFAULT_FAKE_UID
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for the [ActivityMetricsLogger] class.
 *
 * Build/Install/Run:
 *  atest WmTests:ActivityMetricsLoggerTest
 */
@SmallTest
@Presubmit
@RunWith(WindowTestRunner::class)
class ActivityMetricsLoggerTest : WindowTestsBase() {

    private val metricsLogger: ActivityMetricsLogger
        get() = mSupervisor.activityMetricsLogger

    @Test
    fun testNotifyActivityLaunching_taskTrampoline_tracksOriginator() {
        val uidA = DEFAULT_FAKE_UID + 1
        val activityA = createActivityRecord(mDisplayContent).apply {
            info.applicationInfo.uid = uidA
        }
        val launchingState = metricsLogger.notifyActivityLaunching(
            activityA.intent,
            activityA,
            activityA.uid,
        )

        assertWithMessage("First launch must track caller as originator")
            .that(launchingState.tracksOriginator(activityA.uid))
            .isEqualTo(uidA)

        val activityB = createActivityRecord(mDisplayContent)
        assertWithMessage("Subsequent launch in same sequence must return original caller")
            .that(launchingState.tracksOriginator(activityB.uid))
            .isEqualTo(uidA)
    }
}
