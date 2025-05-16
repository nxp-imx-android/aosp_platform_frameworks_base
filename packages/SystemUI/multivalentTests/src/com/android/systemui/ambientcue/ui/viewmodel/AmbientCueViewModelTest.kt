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

package com.android.systemui.ambientcue.ui.viewmodel

import android.content.Context
import android.content.applicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.ambientcue.data.repository.ambientCueRepository
import com.android.systemui.ambientcue.data.repository.fake
import com.android.systemui.ambientcue.domain.interactor.ambientCueInteractor
import com.android.systemui.ambientcue.shared.model.ActionModel
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.advanceTimeBy
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.res.R
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.launch
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class AmbientCueViewModelTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val viewModel = kosmos.ambientCueViewModelFactory.create()

    @Before
    fun setUp() {
        viewModel.activateIn(kosmos.testScope)
    }

    @Test
    fun isVisible_timesOut() =
        kosmos.runTest {
            initializeIsVisible()
            assertThat(viewModel.isVisible).isTrue()

            // Times out when there's no interaction
            advanceTimeBy(AmbientCueViewModel.AMBIENT_CUE_TIMEOUT_SEC)
            runCurrent()
            ambientCueRepository.fake.updateRootViewAttached()
            runCurrent()

            assertThat(viewModel.isVisible).isFalse()
        }

    @Test
    fun isVisible_imeNotVisible_true() =
        kosmos.runTest {
            ambientCueRepository.fake.setActions(testActions(applicationContext))
            ambientCueInteractor.setDeactivated(false)

            ambientCueInteractor.setImeVisible(false)
            ambientCueRepository.fake.updateRootViewAttached()
            runCurrent()

            assertThat(viewModel.isVisible).isTrue()
        }

    @Test
    fun isVisible_imeVisible_false() =
        kosmos.runTest {
            initializeIsVisible()
            assertThat(viewModel.isVisible).isTrue()

            ambientCueInteractor.setImeVisible(true)
            ambientCueRepository.fake.updateRootViewAttached()
            runCurrent()

            assertThat(viewModel.isVisible).isFalse()
        }

    @Test
    fun onClick_collapses() =
        kosmos.runTest {
            ambientCueRepository.fake.setActions(testActions(applicationContext))
            ambientCueInteractor.setDeactivated(false)
            viewModel.expand()
            runCurrent()

            assertThat(viewModel.isExpanded).isTrue()
            val action: ActionViewModel = viewModel.actions.first()

            // UI Collapses upon clicking on an action
            action.onClick()
            assertThat(viewModel.isExpanded).isFalse()
        }

    @Test
    fun delayAndDeactivateCueBar_refreshTimeout() =
        kosmos.runTest {
            ambientCueInteractor.setDeactivated(false)
            testScope.backgroundScope.launch { viewModel.delayAndDeactivateCueBar() }
            advanceTimeBy(10.seconds)
            runCurrent()
            assertThat(ambientCueRepository.isDeactivated.value).isFalse()

            testScope.backgroundScope.launch { viewModel.delayAndDeactivateCueBar() }
            advanceTimeBy(AmbientCueViewModel.AMBIENT_CUE_TIMEOUT_SEC - 10.seconds)
            runCurrent()
            // 5 seconds after calling delayAndDeactivateCueBar() again (totally 15 seconds after
            // test begins), isDeactivated should still be false.
            assertThat(ambientCueRepository.isDeactivated.value).isFalse()
            advanceTimeBy(10.seconds)
            runCurrent()

            // 15 seconds after calling delayAndDeactivateCueBar() again, isDeactivated should be
            // true.
            assertThat(ambientCueRepository.isDeactivated.value).isTrue()
        }

    private fun testActions(applicationContext: Context) =
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

    private fun Kosmos.initializeIsVisible() {
        ambientCueRepository.fake.setActions(testActions(applicationContext))
        ambientCueInteractor.setDeactivated(false)
        ambientCueInteractor.setImeVisible(false)
        ambientCueRepository.fake.updateRootViewAttached()
        runCurrent()
    }
}
