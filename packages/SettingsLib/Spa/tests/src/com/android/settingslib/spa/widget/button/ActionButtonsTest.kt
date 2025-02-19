/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.settingslib.spa.widget.button

import android.platform.test.annotations.RequiresFlagsDisabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Launch
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.settingslib.widget.theme.flags.Flags.FLAG_IS_EXPRESSIVE_DESIGN_ENABLED
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActionButtonsTest {
    @get:Rule
    val composeTestRule = createComposeRule()
    @get:Rule
    val mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Test
    fun button_displayed() {
        composeTestRule.setContent {
            ActionButtons(
                listOf(
                    ActionButton(
                        text = "Open",
                        imageVector = Icons.AutoMirrored.Outlined.Launch
                    ) {},
                )
            )
        }

        composeTestRule.onNodeWithText("Open").assertIsDisplayed()
    }

    @RequiresFlagsDisabled(FLAG_IS_EXPRESSIVE_DESIGN_ENABLED)
    @Test
    fun button_clickable() {
        var clicked by mutableStateOf(false)
        composeTestRule.setContent {
            ActionButtons(
                listOf(
                    ActionButton(text = "Open", imageVector = Icons.AutoMirrored.Outlined.Launch) {
                        clicked = true
                    },
                )
            )
        }

        composeTestRule.onNodeWithText("Open").performClick()

        assertThat(clicked).isTrue()
    }

    @Test
    fun twoButtons_positionIsAligned() {
        composeTestRule.setContent {
            ActionButtons(
                listOf(
                    ActionButton(
                        text = "Open",
                        imageVector = Icons.AutoMirrored.Outlined.Launch
                    ) {},
                    ActionButton(text = "Close", imageVector = Icons.Outlined.Close) {},
                )
            )
        }

        assertThat(composeTestRule.onNodeWithText("Open").getBoundsInRoot().top)
            .isEqualTo(composeTestRule.onNodeWithText("Close").getBoundsInRoot().top)
    }
}
