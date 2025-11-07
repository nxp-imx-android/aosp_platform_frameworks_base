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
import android.platform.test.annotations.EnableFlags
import android.view.Display.DEFAULT_DISPLAY
import android.view.accessibility.Flags
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.accessibility.common.ShortcutChooserDialogConstants
import com.android.internal.accessibility.common.ShortcutConstants.UserShortcutType
import com.android.systemui.SysuiTestCase
import com.android.systemui.accessibility.data.repository.FakeAccessibilityShortcutsRepository
import com.android.systemui.accessibility.data.repository.accessibilityShortcutsRepository
import com.android.systemui.accessibility.shortcutchooser.domain.ShortcutChooserDialogInteractor
import com.android.systemui.accessibility.shortcutchooser.domain.shortcutChooserDialogInteractor
import com.android.systemui.broadcast.broadcastDispatcher
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.backgroundScope
import com.android.systemui.kosmos.runTest
import com.android.systemui.runOnMainThreadAndWaitForIdleSync
import com.android.systemui.statusbar.phone.systemUIDialogFactory
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(Flags.FLAG_ENABLE_A11Y_TOP_ROW_SHORTCUT)
class ShortcutChooserDialogStartableTest : SysuiTestCase() {
    private companion object {
        const val SHORTCUT_TYPE = UserShortcutType.TOP_ROW_KEY

        const val TALKBACK_TARGET_NAME =
            FakeAccessibilityShortcutsRepository.FAKE_TALKBACK_TARGET_NAME
        const val MAGNIFICATION_TARGET_NAME =
            FakeAccessibilityShortcutsRepository.FAKE_MAGNIFICATION_TARGET_NAME
    }

    @get:Rule val composeTestRule = createEmptyComposeRule()

    private val kosmos = testKosmosNew()

    private val Kosmos.underTest by
        Kosmos.Fixture {
            ShortcutChooserDialogStartable(
                shortcutChooserDialogInteractor,
                systemUIDialogFactory,
                backgroundScope,
            )
        }

    @Before
    fun setUp() {
        onTeardown {
            runOnMainThreadAndWaitForIdleSync {
                with(kosmos) { underTest.currentDialog?.dismiss() }
            }
        }
    }

    @Test
    fun start_doesNotShowDialogByDefault() =
        kosmos.runTest {
            underTest.start()

            assertThat(underTest.currentDialog).isNull()
        }

    @Test
    fun createDialog_topRowKey_noSelectedTargets_showInitialScreen_andClickCancelButton() =
        kosmos.runTest {
            underTest.start()

            sendIntentInMainThread()
            composeTestRule.waitForIdle()

            // Verify that when there is no selected targets by default, the dialog type should be
            // tutorial dialog.
            assertThat(underTest.currentDialog!!.isShowing).isTrue()
            assertThat(underTest.currentScreenState!!.value)
                .isEqualTo(ShortcutChooserDialogStartable.DialogScreen.INITIAL)
            composeTestRule.waitForIdle()
            // Click on the composable negative button on the top row key tutorial dialog.
            composeTestRule.onNodeWithTag("cancel_button").performClick()

            // Will dismiss the tutorial dialog.
            composeTestRule.waitForIdle()
            assertThat(underTest.currentDialog).isNull()
        }

    @Test
    fun createDialog_hardware_noSelectedTargets_noDialog() =
        kosmos.runTest {
            underTest.start()

            sendIntentInMainThread(UserShortcutType.HARDWARE)

            assertThat(underTest.currentDialog).isNull()
        }

    @Test
    fun createDialog_topRowKey_noSelectedTargets_andClickAddFeatureButton_showEditTargetsDialog() =
        kosmos.runTest {
            underTest.start()

            sendIntentInMainThread()
            composeTestRule.waitForIdle()

            // Click on the composable positive button on the top row key tutorial dialog.
            composeTestRule.onNodeWithTag("add_features_button").performClick()

            // Will do the recomposition to the Edit targets dialog.
            composeTestRule.waitForIdle()
            assertThat(underTest.currentDialog?.isShowing).isTrue()
            assertThat(underTest.currentScreenState!!.value)
                .isEqualTo(ShortcutChooserDialogStartable.DialogScreen.EDIT_TARGETS)
        }

    @Test
    fun createDialog_topRowKey_editTargetsDialog_selectOneTarget_dismissDialog() =
        kosmos.runTest {
            underTest.start()

            sendIntentInMainThread()
            composeTestRule.waitForIdle()

            // Click on the composable positive button on the top row key tutorial dialog.
            composeTestRule.onNodeWithTag("add_features_button").performClick()
            composeTestRule.waitForIdle()

            // Select only one target on EditDialog.
            composeTestRule.onNodeWithTag(TALKBACK_TARGET_NAME).performClick()
            composeTestRule.waitForIdle()

            assertThat(getSelectedTargetNames()).isEqualTo(setOf(TALKBACK_TARGET_NAME))

            // Finally click on Done button.
            composeTestRule.onNodeWithTag("done_button").performClick()
            composeTestRule.waitForIdle()

            // Will dismiss the dialog, because there are less than two targets selected.
            assertThat(underTest.currentDialog).isNull()
        }

    @Test
    fun createDialog_topRowKey_editTargetsDialog_selectTwoTarget_showToggleTargetsDialog() =
        kosmos.runTest {
            underTest.start()

            sendIntentInMainThread()
            composeTestRule.waitForIdle()

            // Click on the composable positive button on the top row key tutorial dialog.
            composeTestRule.onNodeWithTag("add_features_button").performClick()
            composeTestRule.waitForIdle()

            // Select two targets on EditDialog, e.g. Talkback and Magnification.
            composeTestRule.onNodeWithTag(TALKBACK_TARGET_NAME).performClick()
            composeTestRule.waitForIdle()

            assertThat(getSelectedTargetNames()).isEqualTo(setOf(TALKBACK_TARGET_NAME))

            composeTestRule.onNodeWithTag(MAGNIFICATION_TARGET_NAME).performClick()
            composeTestRule.waitForIdle()

            assertThat(getSelectedTargetNames())
                .isEqualTo(setOf(TALKBACK_TARGET_NAME, MAGNIFICATION_TARGET_NAME))

            // Finally click on Done button.
            composeTestRule.onNodeWithTag("done_button").performClick()
            composeTestRule.waitForIdle()

            // Will show Toggle targets dialog, because there are at least two targets selected.
            assertThat(underTest.currentDialog!!.isShowing).isTrue()
            assertThat(underTest.currentScreenState!!.value)
                .isEqualTo(ShortcutChooserDialogStartable.DialogScreen.TOGGLE_TARGETS)
        }

    @Test
    fun createDialog_hardware_oneSelectedTarget_noDialog() =
        kosmos.runTest {
            underTest.start()
            // Assume there is only one feature selected before pressing the key.
            setTalkBackSelected()

            sendIntentInMainThread(UserShortcutType.HARDWARE)

            assertThat(underTest.currentDialog).isNull()
        }

    @Test
    fun createDialog_topRowKey_oneSelectedTarget_noDialog() =
        kosmos.runTest {
            underTest.start()
            // Assume there is only one feature selected before pressing the key.
            setTalkBackSelected()

            sendIntentInMainThread(UserShortcutType.TOP_ROW_KEY)

            assertThat(underTest.currentDialog).isNull()
        }

    @Test
    fun createDialog_topRowKey_twoSelectedTargets_showToggleScreen_andClickTargetRow() =
        kosmos.runTest {
            setTalkbackAndMagnificationSelected()
            underTest.start()

            sendIntentInMainThread()
            composeTestRule.waitForIdle()

            // Verify when there are two selected targets, the dialog type should be Toggle targets
            // dialog.
            assertThat(underTest.currentDialog!!.isShowing).isTrue()
            assertThat(underTest.currentScreenState!!.value)
                .isEqualTo(ShortcutChooserDialogStartable.DialogScreen.TOGGLE_TARGETS)

            // Click on the one target row, e.g. Talkback.
            composeTestRule.onNodeWithTag(TALKBACK_TARGET_NAME).performClick()
            composeTestRule.waitForIdle()

            // Will toggle Talkback feature on/off and dismiss the dialog.
            assertThat(getToggledOnTargetNames()).isEqualTo(setOf(TALKBACK_TARGET_NAME))
            assertThat(underTest.currentDialog).isNull()
        }

    @Test
    fun createDialog_topRowKey_twoSelectedTargets_clickEditButton() =
        kosmos.runTest {
            setTalkbackAndMagnificationSelected()
            underTest.start()

            sendIntentInMainThread()
            composeTestRule.waitForIdle()

            // Click on the Edit button.
            composeTestRule.onNodeWithTag("edit_button").performClick()
            composeTestRule.waitForIdle()

            // Will do the recomposition to the Edit targets dialog.
            assertThat(underTest.currentDialog!!.isShowing).isTrue()
            assertThat(underTest.currentScreenState!!.value)
                .isEqualTo(ShortcutChooserDialogStartable.DialogScreen.EDIT_TARGETS)
        }

    @Test
    fun createDialog_topRowKey_twoSelectedTargets_clickDoneButton() =
        kosmos.runTest {
            setTalkbackAndMagnificationSelected()
            underTest.start()

            sendIntentInMainThread()
            composeTestRule.waitForIdle()

            // Click on the Done button.
            composeTestRule.onNodeWithTag("done_button").performClick()
            composeTestRule.waitForIdle()

            // Will dismiss the dialog.
            assertThat(underTest.currentDialog).isNull()
        }

    private fun Kosmos.sendIntentInMainThread(shortcutType: Int = SHORTCUT_TYPE) {
        // Sending broadcast to create SysUi dialog should be run in main thread.
        runOnMainThreadAndWaitForIdleSync {
            broadcastDispatcher.sendIntentToMatchingReceiversOnly(
                context,
                Intent().apply {
                    action = ShortcutChooserDialogInteractor.ACTION
                    putExtra(ShortcutChooserDialogConstants.SHORTCUT_TYPE, shortcutType)
                    putExtra(ShortcutChooserDialogConstants.DISPLAY_ID, DEFAULT_DISPLAY)
                },
            )
        }
    }

    /**
     * A helper function called before launching dialog. This function is to assume we have two
     * selected targets, which are Talkback and Magnification.
     */
    private fun Kosmos.setTalkbackAndMagnificationSelected() =
        accessibilityShortcutsRepository.enableShortcutsForTargets(
            enable = true,
            shortcutType = SHORTCUT_TYPE,
            targetNames = setOf(TALKBACK_TARGET_NAME, MAGNIFICATION_TARGET_NAME),
        )

    /**
     * A helper function called before launching dialog. This function is to assume we have only one
     * selected targets, which is Talkback.
     */
    private fun Kosmos.setTalkBackSelected() =
        accessibilityShortcutsRepository.enableShortcutsForTargets(
            enable = true,
            shortcutType = SHORTCUT_TYPE,
            targetNames = setOf(TALKBACK_TARGET_NAME),
        )

    private fun Kosmos.getSelectedTargetNames(): Set<String> =
        accessibilityShortcutsRepository
            .getSelectedAccessibilityTargetsInfo(SHORTCUT_TYPE)
            .map { it.targetName }
            .toSet()

    private fun Kosmos.getToggledOnTargetNames(): Set<String> =
        accessibilityShortcutsRepository
            .getAllAccessibilityTargetsInfo(SHORTCUT_TYPE)
            .filter { it.isToggleOn }
            .map { it.targetName }
            .toSet()
}
