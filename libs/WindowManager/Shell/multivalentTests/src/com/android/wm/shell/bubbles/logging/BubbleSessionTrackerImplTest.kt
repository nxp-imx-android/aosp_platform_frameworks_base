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

package com.android.wm.shell.bubbles.logging

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.logging.InstanceId
import com.android.internal.logging.InstanceIdSequence
import com.android.internal.protolog.ProtoLog
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Unit tests for [BubbleSessionTrackerImpl]. */
@SmallTest
@RunWith(AndroidJUnit4::class)
class BubbleSessionTrackerImplTest {

    private val instanceIdSequence = FakeInstanceIdSequence()
    private val bubbleSessionTracker = BubbleSessionTrackerImpl(instanceIdSequence)

    @Before
    fun setUp() {
        ProtoLog.REQUIRE_PROTOLOGTOOL = false
        ProtoLog.init()
    }

    @Test
    fun startSession_generatesNewSessionId() {
        bubbleSessionTracker.start()
        val firstSessionId = instanceIdSequence.id
        bubbleSessionTracker.stop()

        bubbleSessionTracker.start()
        val secondSessionId = instanceIdSequence.id

        assertThat(firstSessionId).isNotEqualTo(secondSessionId)
    }

    @Test
    fun endSession_shouldNotGenerateNewId() {
        bubbleSessionTracker.start()
        val currentId = instanceIdSequence.id
        bubbleSessionTracker.stop()

        assertThat(currentId).isEqualTo(instanceIdSequence.id)
    }

    class FakeInstanceIdSequence : InstanceIdSequence(/* instanceIdMax= */ 10) {

        var id = -1
            private set

        override fun newInstanceId(): InstanceId {
            id = if (id == -1 || id == 10) 1 else id + 1
            return InstanceId.fakeInstanceId(id)
        }
    }
}
