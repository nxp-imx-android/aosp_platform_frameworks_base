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

package com.android.settingslib.spa.restricted

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.isOff
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.settingslib.spa.widget.preference.SwitchPreferenceModel
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.spy

@RunWith(AndroidJUnit4::class)
class RestrictedSwitchPreferenceTest {
    @get:Rule val composeTestRule = createComposeRule()

    private val context: Context = spy(ApplicationProvider.getApplicationContext()) {}

    private val switchPreferenceModel =
        object : SwitchPreferenceModel {
            override val title = TITLE
            private val checkedState = mutableStateOf(true)
            override val checked = { checkedState.value }
            override val onCheckedChange: (Boolean) -> Unit = { checkedState.value = it }
        }

    private var testRestrictedRepository = TestRestrictedRepository()

    @Test
    fun whenRestrictionsIsEmpty_enabled() {
        val restrictions = TestRestrictions(isEmpty = true)

        setContent(restrictions)

        composeTestRule.onNodeWithText(TITLE).assertIsDisplayed().assertIsEnabled()
        composeTestRule.onNode(isOn()).assertIsDisplayed()
    }

    @Test
    fun whenRestrictionsIsEmpty_toggleable() {
        val restrictions = TestRestrictions(isEmpty = true)

        setContent(restrictions)
        composeTestRule.onRoot().performClick()

        composeTestRule.onNode(isOff()).assertIsDisplayed()
    }

    @Test
    fun whenNoRestricted_enabled() {
        val restrictions = TestRestrictions(isEmpty = false)
        testRestrictedRepository.isBlockedWithDetails = false

        setContent(restrictions)

        composeTestRule.onNodeWithText(TITLE).assertIsDisplayed().assertIsEnabled()
        composeTestRule.onNode(isOn()).assertIsDisplayed()
    }

    @Test
    fun whenNoRestricted_toggleable() {
        val restrictions = TestRestrictions(isEmpty = false)
        testRestrictedRepository.isBlockedWithDetails = false

        setContent(restrictions)
        composeTestRule.onRoot().performClick()

        composeTestRule.onNode(isOff()).assertIsDisplayed()
    }

    @Test
    fun whenBlockedWithDetails_notOverrideChecked() {
        val restrictions = TestRestrictions(isEmpty = false)
        testRestrictedRepository.isBlockedWithDetails = true

        setContent(restrictions)

        composeTestRule.onNodeWithText(TITLE).assertIsDisplayed().assertIsEnabled()
        composeTestRule.onNode(isOn()).assertIsDisplayed()
    }

    @Test
    fun whenBlockedWithDetails_click() {
        val restrictions = TestRestrictions(isEmpty = false)
        testRestrictedRepository.isBlockedWithDetails = true

        setContent(restrictions)
        composeTestRule.onRoot().performClick()

        assertThat(testRestrictedRepository.detailsIsShown).isTrue()
    }

    private fun setContent(restrictions: TestRestrictions) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                RestrictedSwitchPreference(
                    model = switchPreferenceModel,
                    restrictions = restrictions,
                    repository = testRestrictedRepository,
                )
            }
        }
    }

    private companion object {
        const val TITLE = "Title"
    }
}
