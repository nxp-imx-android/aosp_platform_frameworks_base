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

package com.android.systemui.underlay.domain.interactor

import android.content.Intent
import android.content.applicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.res.R
import com.android.systemui.testKosmos
import com.android.systemui.underlay.data.repository.fake
import com.android.systemui.underlay.data.repository.underlayRepository
import com.android.systemui.underlay.shared.model.ActionModel
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class UnderlayInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos()

    @Test
    fun isUnderlayAttached_whenCreated_true() =
        kosmos.runTest {
            val isUnderlayAttached by collectLastValue(underlayInteractor.isUnderlayAttached)
            underlayRepository.fake.setIsUnderlayAttached(true)
            assertThat(isUnderlayAttached).isTrue()
        }

    @Test
    fun isUnderlayAttached_whenDestroyed_false() =
        kosmos.runTest {
            val isUnderlayAttached by collectLastValue(underlayInteractor.isUnderlayAttached)
            underlayRepository.fake.setIsUnderlayAttached(false)
            assertThat(isUnderlayAttached).isFalse()
        }

    @Test
    fun isOverlayVisible_setTrue_true() =
        kosmos.runTest {
            val isOverlayVisible by collectLastValue(underlayInteractor.isOverlayVisible)
            underlayInteractor.setIsOverlayVisible(true)
            assertThat(isOverlayVisible).isTrue()
        }

    @Test
    fun isOverlayVisible_setFalse_False() =
        kosmos.runTest {
            val isOverlayVisible by collectLastValue(underlayInteractor.isOverlayVisible)
            underlayInteractor.setIsOverlayVisible(false)
            assertThat(isOverlayVisible).isFalse()
        }

    @Test
    fun actions_setActions_actionsUpdated() =
        kosmos.runTest {
            val actions by collectLastValue(underlayInteractor.actions)
            val testActions =
                listOf(
                    ActionModel(
                        icon =
                            applicationContext.resources.getDrawable(
                                R.drawable.clipboard,
                                applicationContext.theme,
                            ),
                        label = "Sunday Morning",
                        attribution = null,
                        intent = Intent(),
                    )
                )
            underlayRepository.fake.setActions(testActions)
            assertThat(actions).isEqualTo(testActions)
        }
}
