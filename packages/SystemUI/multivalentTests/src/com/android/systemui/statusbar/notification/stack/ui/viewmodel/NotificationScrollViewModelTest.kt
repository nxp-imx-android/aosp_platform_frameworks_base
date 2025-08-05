/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.statusbar.notification.stack.ui.viewmodel

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.scene.domain.startable.sceneContainerStartable
import com.android.systemui.shade.domain.interactor.enableDualShade
import com.android.systemui.statusbar.notification.stack.domain.interactor.notificationStackAppearanceInteractor
import com.android.systemui.statusbar.notification.stack.shared.model.ShadeScrimBounds
import com.android.systemui.statusbar.notification.stack.shared.model.ShadeScrimShape
import com.android.systemui.testKosmos
import com.android.systemui.util.state.SynchronouslyObservableState
import com.android.systemui.util.state.observableStateOf
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableSceneContainer
class NotificationScrollViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmos()
    private val Kosmos.underTest by Kosmos.Fixture { notificationScrollViewModel }

    @Before
    fun setUp() =
        with(kosmos) {
            sceneContainerStartable.start()
            enableDualShade()
            runCurrent()
            underTest.activateIn(testScope)
        }

    @Test
    fun getQsPanelScrim_clears() =
        kosmos.runTest {
            var actual: ShadeScrimShape? = null
            val viewLeft = observableStateOf(10)
            val disposable = underTest.getQsScrimShape(viewLeft).observe { shape -> actual = shape }

            assertThat(actual).isNull()
            notificationStackAppearanceInteractor.setQsPanelShapeInWindow(
                ShadeScrimShape(
                    bounds = ShadeScrimBounds(left = 10f, top = 0f, right = 710f, bottom = 600f),
                    topRadius = 0,
                    bottomRadius = 100,
                )
            )
            assertThat(actual).isNotNull()
            notificationStackAppearanceInteractor.setQsPanelShapeInWindow(null)
            assertThat(actual).isNull()

            disposable.dispose()
        }

    @Test
    fun getQsPanelScrim_includesLeftOffset() =
        kosmos.runTest {
            var actual: ShadeScrimShape? = null
            val viewLeft = observableStateOf(10)
            val disposable = underTest.getQsScrimShape(viewLeft).observe { shape -> actual = shape }

            val shapeInWindow =
                ShadeScrimShape(
                    bounds = ShadeScrimBounds(left = 10f, top = 0f, right = 710f, bottom = 600f),
                    topRadius = 0,
                    bottomRadius = 100,
                )

            val expected =
                ShadeScrimShape(
                    bounds = ShadeScrimBounds(left = 0f, top = 0f, right = 700f, bottom = 600f),
                    topRadius = 0,
                    bottomRadius = 100,
                )

            notificationStackAppearanceInteractor.setQsPanelShapeInWindow(shapeInWindow)

            assertThat(actual).isEqualTo(expected)

            disposable.dispose()
        }

    @Test
    fun getQsPanelScrim_updatesWhenLeftOffsetUpdates() =
        kosmos.runTest {
            var actual: ShadeScrimShape? = null
            val viewLeft = SynchronouslyObservableState(0)
            val disposable = underTest.getQsScrimShape(viewLeft).observe { shape -> actual = shape }

            val shapeInWindow =
                ShadeScrimShape(
                    bounds = ShadeScrimBounds(left = 10f, top = 0f, right = 710f, bottom = 600f),
                    topRadius = 0,
                    bottomRadius = 100,
                )

            notificationStackAppearanceInteractor.setQsPanelShapeInWindow(shapeInWindow)

            assertThat(actual).isEqualTo(shapeInWindow)

            viewLeft.value = 10

            val expected =
                ShadeScrimShape(
                    bounds = ShadeScrimBounds(left = 0f, top = 0f, right = 700f, bottom = 600f),
                    topRadius = 0,
                    bottomRadius = 100,
                )

            assertThat(actual).isEqualTo(expected)

            disposable.dispose()
        }
}
