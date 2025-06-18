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

package com.android.systemui.shade.ui.composable

import android.testing.TestableLooper
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.TestContentScope
import com.android.compose.theme.PlatformTheme
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.qs.ui.composable.EditModeScene
import com.android.systemui.qs.ui.viewmodel.editModeSceneActionsViewModelFactory
import com.android.systemui.qs.ui.viewmodel.editModeSceneContentViewModelFactory
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.testKosmos
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper
@EnableSceneContainer
class EditModeSceneTest : SysuiTestCase() {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val kosmos = testKosmos()

    @Test
    fun testViewHierarchy() {
        val scene = EditModeScene(
            kosmos.editModeSceneContentViewModelFactory,
            kosmos.editModeSceneActionsViewModelFactory,
        )

        // Set the single shade content.
        composeTestRule.setContent {
            PlatformTheme {
                with(scene) {
                    TestContentScope(currentScene = Scenes.QSEditMode) {
                        Content(
                            Modifier
                        )
                    }
                }
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("edit_mode_scene").assertExists()
    }
}