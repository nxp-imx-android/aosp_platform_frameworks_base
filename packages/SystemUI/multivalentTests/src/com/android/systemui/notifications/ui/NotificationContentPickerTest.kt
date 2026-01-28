/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.notifications.ui.composable

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.SceneKey
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.Kosmos.Fixture
import com.android.systemui.testKosmos
import com.android.systemui.scene.sceneTransitionsBuilder
import com.android.systemui.scene.shared.model.SceneContainerConfig
import com.android.systemui.scene.shared.model.Scenes
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class NotificationContentPickerTest : SysuiTestCase() {

    private val kosmos = testKosmos()
    private val other = SceneKey("other")

    // Override the SceneContainerConfig fixture to define Z-order for testing
    // Order: Lockscreen (0), other (1), Shade (2)
    private val Kosmos.sceneContainerConfig by Fixture {
        SceneContainerConfig(
            sceneKeys = listOf(Scenes.Lockscreen, other, Scenes.Shade),
            initialSceneKey = Scenes.Lockscreen,
            navigationDistances = mapOf(
                Scenes.Lockscreen to 0,
                other to 1,
                Scenes.Shade to 2
            ),
            overlayKeys = emptyList(),
            transitionsBuilder = sceneTransitionsBuilder,
        )
    }

    private val picker = HeadsUpPlaceholderContentPicker(kosmos.sceneContainerConfig)

    @Test
    fun pickContentFrom_lockscreenAndShade_picksShade() {
        val keys = setOf(Scenes.Lockscreen, Scenes.Shade)

        val result = picker.pickContentFrom(keys)

        assertThat(result).isEqualTo(Scenes.Shade)
    }

    @Test
    fun pickContentFrom_otherAndShade_picksLowestZ() {
        val keys = setOf(other, Scenes.Shade)

        val result = picker.pickContentFrom(keys)

        assertThat(result).isEqualTo(other)
    }

    @Test
    fun pickContentFrom_lockscreenAndOther_picksLowestZ() {
        val keys = setOf(Scenes.Lockscreen, other)

        val result = picker.pickContentFrom(keys)

        assertThat(result).isEqualTo(Scenes.Lockscreen)
    }
}
