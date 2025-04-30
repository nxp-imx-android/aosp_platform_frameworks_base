/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.underlay.data.repository

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.broadcast.broadcastDispatcher
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class UnderlayRepositoryTest : SysuiTestCase() {
    private val kosmos = testKosmos()

    @Test
    fun isUnderlayAttached_whenCreated_true() =
        kosmos.runTest {
            val isUnderlayAttached by collectLastValue(underlayRepository.isUnderlayAttached)
            runCurrent()

            broadcastDispatcher.sendIntentToMatchingReceiversOnly(
                context,
                Intent(UnderlayRepository.ACTION_CREATE_UNDERLAY),
            )

            assertThat(isUnderlayAttached).isTrue()
        }

    @Test
    fun isUnderlayAttached_whenDestroyed_false() =
        kosmos.runTest {
            val isUnderlayAttached by collectLastValue(underlayRepository.isUnderlayAttached)
            runCurrent()

            broadcastDispatcher.sendIntentToMatchingReceiversOnly(
                context,
                Intent(UnderlayRepository.ACTION_DESTROY_UNDERLAY),
            )

            assertThat(isUnderlayAttached).isFalse()
        }
}
