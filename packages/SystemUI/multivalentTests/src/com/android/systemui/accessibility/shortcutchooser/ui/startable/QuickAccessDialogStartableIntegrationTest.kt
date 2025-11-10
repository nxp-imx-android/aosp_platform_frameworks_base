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

package com.android.systemui.accessibility.shortcutchooser.ui.startable

import android.content.Intent
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.view.Display.DEFAULT_DISPLAY
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.accessibility.data.repository.FakeAccessibilityShortcutsRepository
import com.android.systemui.accessibility.data.repository.accessibilityShortcutsRepository
import com.android.systemui.accessibility.shortcutchooser.domain.interactor.QuickAccessDialogInteractor
import com.android.systemui.accessibility.shortcutchooser.ui.viewmodel.quickAccessDialogViewModelFactory
import com.android.systemui.broadcast.broadcastDispatcher
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.runOnMainThreadAndWaitForIdleSync
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class QuickAccessDialogStartableIntegrationTest : SysuiTestCase() {
    private companion object {
        const val SHORTCUT_TYPE = QuickAccessDialogInteractor.SHORTCUT_TYPE

        const val TALKBACK_TARGET_NAME =
            FakeAccessibilityShortcutsRepository.FAKE_TALKBACK_TARGET_NAME
    }

    @get:Rule val composeTestRule = createEmptyComposeRule()

    private val kosmos = testKosmosNew()
    private val viewModel = kosmos.quickAccessDialogViewModelFactory.create()

    @Before
    fun setUp() {
        with(kosmos) { quickAccessDialogStartable.start() }
    }

    @After
    fun tearDown() {
        runOnMainThreadAndWaitForIdleSync { with(kosmos) { viewModel.dismissDialog() } }
    }

    @Test
    @EnableFlags(Flags.FLAG_LAUNCH_ACCESSIBILITY_QUICK_ACCESS_DIALOG_PERMISSION)
    fun start_someShortcutsEnabled() =
        kosmos.runTest {
            assertThat(
                    accessibilityShortcutsRepository
                        .getSelectedAccessibilityTargetsInfo(SHORTCUT_TYPE)
                        .map { it.targetName }
                        .toSet()
                )
                .isEqualTo(
                    accessibilityShortcutsRepository
                        .getAllAccessibilityTargetsInfo(SHORTCUT_TYPE)
                        .map { it.targetName }
                        .toSet()
                )
        }

    @Test
    @EnableFlags(Flags.FLAG_LAUNCH_ACCESSIBILITY_QUICK_ACCESS_DIALOG_PERMISSION)
    fun start_dialogNotShownByDefault() = kosmos.runTest { assertThat(isDialogVisible()).isFalse() }

    @Test
    @DisableFlags(Flags.FLAG_LAUNCH_ACCESSIBILITY_QUICK_ACCESS_DIALOG_PERMISSION)
    fun createDialog_withFlagDisabled_doesNotShowDialog() =
        kosmos.runTest {
            sendIntentOnMainThread()
            composeTestRule.waitForIdle()

            assertThat(isDialogVisible()).isFalse()
        }

    @Test
    @EnableFlags(Flags.FLAG_LAUNCH_ACCESSIBILITY_QUICK_ACCESS_DIALOG_PERMISSION)
    fun createDialog_showsDialog_thenClickDoneButton_dismissesDialog() =
        kosmos.runTest {
            sendIntentOnMainThread()
            composeTestRule.waitForIdle()

            assertThat(isDialogVisible()).isTrue()

            composeTestRule.onNodeWithTag("done_button").performClick()
            composeTestRule.waitForIdle()

            assertThat(isDialogVisible()).isFalse()
        }

    @Test
    @EnableFlags(Flags.FLAG_LAUNCH_ACCESSIBILITY_QUICK_ACCESS_DIALOG_PERMISSION)
    fun createDialog_clickTarget_performsShortcut() =
        kosmos.runTest {
            sendIntentOnMainThread()
            composeTestRule.waitForIdle()

            assertThat(isDialogVisible()).isTrue()

            composeTestRule.onNodeWithText("Screen Reader").performClick()
            composeTestRule.waitForIdle()

            assertThat(
                    accessibilityShortcutsRepository
                        .getAllAccessibilityTargetsInfo(SHORTCUT_TYPE)
                        .filter { it.isToggleOn }
                        .map { it.targetName }
                        .toSet()
                )
                .isEqualTo(setOf(TALKBACK_TARGET_NAME))
        }

    private fun Kosmos.isDialogVisible() = viewModel.isDialogVisible.value

    private fun Kosmos.sendIntentOnMainThread() {
        runOnMainThreadAndWaitForIdleSync {
            broadcastDispatcher.sendIntentToMatchingReceiversOnly(
                context,
                Intent().apply {
                    action = QuickAccessDialogInteractor.ACTION
                    putExtra(QuickAccessDialogInteractor.DISPLAY_ID, DEFAULT_DISPLAY)
                },
            )
        }
    }
}
