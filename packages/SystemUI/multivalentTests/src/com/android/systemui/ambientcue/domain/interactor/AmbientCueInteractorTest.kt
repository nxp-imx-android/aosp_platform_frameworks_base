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

package com.android.systemui.ambientcue.domain.interactor

import android.content.applicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.ambientcue.data.repository.ambientCueRepository
import com.android.systemui.ambientcue.data.repository.fake
import com.android.systemui.ambientcue.shared.model.ActionModel
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.res.R
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class AmbientCueInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos()

    @Test
    fun isDeactivated_setTrue_true() =
        kosmos.runTest {
            val isDeactivated by collectLastValue(ambientCueRepository.isDeactivated)
            ambientCueInteractor.setDeactivated(true)
            assertThat(isDeactivated).isTrue()
        }

    @Test
    fun isDeactivated_setFalse_False() =
        kosmos.runTest {
            val isDeactivated by collectLastValue(ambientCueRepository.isDeactivated)
            ambientCueInteractor.setDeactivated(false)
            assertThat(isDeactivated).isFalse()
        }

    @Test
    fun actions_setActions_actionsUpdated() =
        kosmos.runTest {
            val actions by collectLastValue(ambientCueInteractor.actions)
            val testActions =
                listOf(
                    ActionModel(
                        icon =
                            applicationContext.resources.getDrawable(
                                R.drawable.ic_content_paste_spark,
                                applicationContext.theme,
                            ),
                        label = "Sunday Morning",
                        attribution = null,
                        onPerformAction = {},
                    )
                )
            ambientCueRepository.fake.setActions(testActions)
            assertThat(actions).isEqualTo(testActions)
        }

    @Test
    fun isImeVisible_setTrue_true() =
        kosmos.runTest {
            val isImeVisible by collectLastValue(ambientCueInteractor.isImeVisible)
            ambientCueInteractor.setImeVisible(true)
            assertThat(isImeVisible).isTrue()
        }

    @Test
    fun isImeVisible_setFalse_false() =
        kosmos.runTest {
            val isImeVisible by collectLastValue(ambientCueInteractor.isImeVisible)
            ambientCueInteractor.setImeVisible(false)
            assertThat(isImeVisible).isFalse()
        }
}
