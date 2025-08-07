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

package com.android.systemui.screencapture.record.largescreen.ui.compose

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.res.R
import com.android.systemui.screencapture.record.largescreen.ui.viewmodel.PreCaptureViewModel
import com.android.systemui.screencapture.record.largescreen.ui.viewmodel.RadioButtonGroupItemViewModel
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class PreCaptureToolbarTest : SysuiTestCase() {

    @get:Rule val composeTestRule = createComposeRule()

    @Mock private lateinit var viewModel: PreCaptureViewModel

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)

        val fakeRegionItems = listOf(RadioButtonGroupItemViewModel(isSelected = true, onClick = {}))
        whenever(viewModel.captureRegionButtonViewModels).thenReturn(fakeRegionItems)
        val fakeTypeItems = listOf(RadioButtonGroupItemViewModel(isSelected = true, onClick = {}))
        whenever(viewModel.captureTypeButtonViewModels).thenReturn(fakeTypeItems)
    }

    @Test
    fun settingsButton_isDisplayed_andHasCorrectContentDescription() {
        whenever(viewModel.screenRecordingSupported).thenReturn(true)
        composeTestRule.setContent {
            PreCaptureToolbar(viewModel = viewModel, expanded = true, onCloseClick = {})
        }
        val expectedDescription =
            context.getString(R.string.screen_capture_toolbar_settings_button_a11y)

        composeTestRule
            .onNodeWithContentDescription(expectedDescription)
            .assertExists()
            .assertIsDisplayed()
            .assertContentDescriptionEquals(expectedDescription)
    }

    @Test
    fun settingsButton_isNotDisplayed_whenFlagIsDisabled() {
        whenever(viewModel.screenRecordingSupported).thenReturn(false)
        composeTestRule.setContent {
            PreCaptureToolbar(viewModel = viewModel, expanded = true, onCloseClick = {})
        }
        val expectedDescription =
            context.getString(R.string.screen_capture_toolbar_settings_button_a11y)

        composeTestRule.onNodeWithContentDescription(expectedDescription).assertDoesNotExist()
    }
}
