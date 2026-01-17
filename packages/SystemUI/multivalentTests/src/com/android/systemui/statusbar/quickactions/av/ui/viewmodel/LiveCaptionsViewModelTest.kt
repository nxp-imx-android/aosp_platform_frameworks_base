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

package com.android.systemui.statusbar.quickactions.av.ui.viewmodel

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class LiveCaptionsViewModelTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()
    private val Kosmos.underTest by
        Kosmos.Fixture {
            val res = liveCaptionsViewModelFactory.create()
            res.activateIn(testScope)
            res
        }

    @Test
    fun titleAndImage() =
        kosmos.runTest {
            assertThat(underTest.state.subText)
                .isEqualTo(com.android.systemui.res.R.string.av_live_captions)
            assertThat(underTest.state.image)
                .isEqualTo(com.android.systemui.res.R.drawable.subtitles)
        }

    @Test
    fun initialState_disabled() = kosmos.runTest { assertThat(underTest.state.isEnabled).isFalse() }

    @Test
    fun onClick_doesNothing() =
        kosmos.runTest {
            underTest.onClick()
            // No state change expected as it is not implemented
            // TODO(b/467631817): Add the test after the backend is connected.
            assertThat(underTest.state.isEnabled).isFalse()
        }
}
