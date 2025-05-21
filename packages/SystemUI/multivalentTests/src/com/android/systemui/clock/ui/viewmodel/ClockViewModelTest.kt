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

package com.android.systemui.clock.ui.viewmodel

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.broadcast.broadcastDispatcher
import com.android.systemui.kosmos.advanceTimeBy
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.testKosmos
import com.android.systemui.util.time.systemClock
import com.google.common.truth.Truth.assertThat
import java.util.Date
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class ClockViewModelTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val underTest by lazy { kosmos.clockViewModel }

    @Before
    fun setUp() {
        underTest.activateIn(kosmos.testScope)
    }

    @Test
    fun clockText_equalsCurrentTime() =
        kosmos.runTest {
            assertThat(underTest.clockText)
                .isEqualTo(Date(systemClock.currentTimeMillis()).toString())
        }

    @Test
    fun clockText_updatesWhenTimeTick() =
        kosmos.runTest {
            val earlierTime = underTest.clockText
            assertThat(earlierTime).isEqualTo(Date(systemClock.currentTimeMillis()).toString())

            advanceTimeBy(7.seconds)
            broadcastDispatcher.sendIntentToMatchingReceiversOnly(
                context,
                Intent(Intent.ACTION_TIME_TICK),
            )
            runCurrent()

            assertThat(underTest.clockText)
                .isEqualTo(Date(systemClock.currentTimeMillis()).toString())
            assertThat(underTest.clockText).isNotEqualTo(earlierTime)
        }

    @Test
    fun clockText_updatesWhenTimeChanged() =
        kosmos.runTest {
            val earlierTime = underTest.clockText
            assertThat(earlierTime).isEqualTo(Date(systemClock.currentTimeMillis()).toString())

            advanceTimeBy(10.seconds)
            broadcastDispatcher.sendIntentToMatchingReceiversOnly(
                context,
                Intent(Intent.ACTION_TIME_CHANGED),
            )
            runCurrent()

            assertThat(underTest.clockText)
                .isEqualTo(Date(systemClock.currentTimeMillis()).toString())
            assertThat(underTest.clockText).isNotEqualTo(earlierTime)
        }
}
