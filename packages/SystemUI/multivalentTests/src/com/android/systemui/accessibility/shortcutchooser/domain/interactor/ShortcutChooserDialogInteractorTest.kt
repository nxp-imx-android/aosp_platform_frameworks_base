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

package com.android.systemui.accessibility.shortcutchooser.domain.interactor

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.UserHandle
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.view.Display.DEFAULT_DISPLAY
import android.view.Display.INVALID_DISPLAY
import android.view.accessibility.Flags as AccessibilityFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.accessibility.common.ShortcutChooserDialogConstants
import com.android.internal.accessibility.common.ShortcutConstants.UserShortcutType
import com.android.systemui.Flags as SystemUIFlags
import com.android.systemui.SysuiTestCase
import com.android.systemui.accessibility.data.repository.AccessibilityShortcutsRepository
import com.android.systemui.accessibility.shortcutchooser.shared.model.AccessibilityTargetModel
import com.android.systemui.accessibility.shortcutchooser.shared.model.DialogRequestModel
import com.android.systemui.broadcast.broadcastDispatcher
import com.android.systemui.broadcast.mockBroadcastSender
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.testKosmosNew
import com.android.systemui.user.data.repository.userRepository
import com.android.systemui.user.domain.interactor.fakeHeadlessSystemUserMode
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(AccessibilityFlags.FLAG_ENABLE_A11Y_TOP_ROW_SHORTCUT)
class ShortcutChooserDialogInteractorTest : SysuiTestCase() {
    private companion object {
        const val TALKBACK_TARGET_NAME = "fakeTalkBackTargetName"
        const val MAGNIFICATION_TARGET_NAME = "fakeMagnificationTargetName"
    }

    private val kosmos = testKosmosNew()

    private val mockRepository = mock<AccessibilityShortcutsRepository>()

    private val Kosmos.underTest by
        Kosmos.Fixture {
            ShortcutChooserDialogInteractor(
                mockRepository,
                broadcastDispatcher,
                mockBroadcastSender,
                userRepository,
                fakeHeadlessSystemUserMode,
            )
        }

    @Test
    fun dialogRequest_topRowKeyType_onDefaultDisplay_flowIsValid() =
        kosmos.runTest {
            val requestModel by collectLastValue(underTest.dialogRequest)

            sendIntentBroadcast(UserShortcutType.TOP_ROW_KEY, DEFAULT_DISPLAY)

            assertThat(requestModel).isNotNull()
        }

    @Test
    fun dialogRequest_topRowKeyType_onInvalidDisplay_flowIsNull() =
        kosmos.runTest {
            val requestModel by collectLastValue(underTest.dialogRequest)

            sendIntentBroadcast(UserShortcutType.TOP_ROW_KEY, INVALID_DISPLAY)

            assertThat(requestModel).isNull()
        }

    @Test
    fun dialogRequest_hardwareType_onDefaultDisplay_flowIsValid() =
        kosmos.runTest {
            val requestModel by collectLastValue(underTest.dialogRequest)

            sendIntentBroadcast(UserShortcutType.HARDWARE, DEFAULT_DISPLAY)

            assertThat(requestModel).isNotNull()
        }

    @Test
    fun dialogRequest_defaultType_onDefaultDisplay_flowIsNull() =
        kosmos.runTest {
            val requestModel by collectLastValue(underTest.dialogRequest)

            sendIntentBroadcast(UserShortcutType.DEFAULT, DEFAULT_DISPLAY)

            assertThat(requestModel).isNull()
        }

    @Test
    @EnableFlags(SystemUIFlags.FLAG_LAUNCH_ACCESSIBILITY_QUICK_ACCESS_DIALOG_PERMISSION)
    fun dialogRequest_quickAccess_validDisplay_emitsRequestModel() =
        kosmos.runTest {
            val requestModel by collectLastValue(underTest.dialogRequest)

            sendIntentBroadcast(UserShortcutType.QUICK_ACCESS, DEFAULT_DISPLAY)

            assertThat(requestModel)
                .isEqualTo(DialogRequestModel(UserShortcutType.QUICK_ACCESS, DEFAULT_DISPLAY))
        }

    @Test
    @DisableFlags(SystemUIFlags.FLAG_LAUNCH_ACCESSIBILITY_QUICK_ACCESS_DIALOG_PERMISSION)
    fun dialogRequest_quickAccess_withFlagDisabled_validDisplay_emitsNull() =
        kosmos.runTest {
            val requestModel by collectLastValue(underTest.dialogRequest)

            sendIntentBroadcast(UserShortcutType.QUICK_ACCESS, DEFAULT_DISPLAY)

            assertThat(requestModel).isNull()
        }

    @Test
    @EnableFlags(SystemUIFlags.FLAG_LAUNCH_ACCESSIBILITY_QUICK_ACCESS_DIALOG_PERMISSION)
    fun dialogRequest_quickAccess_invalidDisplay_emitsNull() =
        kosmos.runTest {
            val requestModel by collectLastValue(underTest.dialogRequest)

            sendIntentBroadcast(UserShortcutType.QUICK_ACCESS, INVALID_DISPLAY)

            assertThat(requestModel).isNull()
        }

    @Test
    fun getAllAccessibilityTargets_getListForAllByType() =
        kosmos.runTest {
            val shortcutType = UserShortcutType.TOP_ROW_KEY

            underTest.getAllAccessibilityTargets(shortcutType)

            verify(mockRepository).getAllAccessibilityTargets(eq(shortcutType))
        }

    @Test
    fun getAssignedAccessibilityTargets_getListForSelectedTargetsByType() =
        kosmos.runTest {
            val shortcutType = UserShortcutType.TOP_ROW_KEY

            underTest.getAssignedAccessibilityTargets(shortcutType)

            verify(mockRepository).getSelectedAccessibilityTargets(eq(shortcutType))
        }

    @Test
    fun getAssignedAccessibilityTargetsCount_getAssignedTargetsCount() =
        kosmos.runTest {
            val shortcutType = UserShortcutType.TOP_ROW_KEY
            val targets =
                listOf(
                    createTargetModel(shortcutType, TALKBACK_TARGET_NAME),
                    createTargetModel(shortcutType, MAGNIFICATION_TARGET_NAME),
                )

            whenever(mockRepository.getSelectedAccessibilityTargetsInfo(shortcutType))
                .thenReturn(targets)

            assertThat(underTest.getAssignedAccessibilityTargetsCount(shortcutType))
                .isEqualTo(targets.size)
        }

    @Test
    fun enableShortcutForTarget_topRowKeyType_enableMagnification() =
        kosmos.runTest {
            val shortcutType = UserShortcutType.TOP_ROW_KEY
            val targetName = MAGNIFICATION_TARGET_NAME

            underTest.enableShortcutForTarget(enable = true, shortcutType, targetName)

            verify(mockRepository)
                .enableShortcutsForTargets(eq(true), eq(shortcutType), eq(setOf(targetName)))
        }

    @Test
    fun enableShortcutForTarget_topRowKeyType_disableMagnification() =
        kosmos.runTest {
            val shortcutType = UserShortcutType.TOP_ROW_KEY
            val targetName = MAGNIFICATION_TARGET_NAME

            underTest.enableShortcutForTarget(enable = false, shortcutType, targetName)

            verify(mockRepository)
                .enableShortcutsForTargets(eq(false), eq(shortcutType), eq(setOf(targetName)))
        }

    @Test
    fun enableShortcutForAllTargets_enablesUnassignedShortcuts() =
        kosmos.runTest {
            val shortcutType = UserShortcutType.QUICK_ACCESS
            val targetName1 = "com.android.test/TestService1"
            val targetName2 = "com.android.test/TestService2"
            val targets =
                listOf(
                    createTargetModel(shortcutType, targetName1, isAssigned = true),
                    createTargetModel(shortcutType, targetName2, isAssigned = false),
                )
            whenever(mockRepository.getAllAccessibilityTargetsInfo(shortcutType))
                .thenReturn(targets)

            underTest.enableShortcutForAllTargets(shortcutType)

            verify(mockRepository)
                .enableShortcutsForTargets(eq(true), eq(shortcutType), eq(setOf(targetName2)))
        }

    @Test
    fun performAccessibilityShortcut_topRowKeyType_toggleMagnification() =
        kosmos.runTest {
            val displayId = DEFAULT_DISPLAY
            val shortcutType = UserShortcutType.TOP_ROW_KEY
            val targetName = MAGNIFICATION_TARGET_NAME

            underTest.performAccessibilityShortcut(displayId, shortcutType, targetName)

            verify(mockRepository)
                .performAccessibilityShortcut(eq(displayId), eq(shortcutType), eq(targetName))
        }

    @Test
    fun performAccessibilityShortcut_topRowKeyType_invalidDisplay_doNothing() =
        kosmos.runTest {
            val displayId = INVALID_DISPLAY
            val shortcutType = UserShortcutType.TOP_ROW_KEY
            val targetName = MAGNIFICATION_TARGET_NAME

            underTest.performAccessibilityShortcut(displayId, shortcutType, targetName)

            verify(mockRepository, never())
                .performAccessibilityShortcut(anyInt(), anyInt(), anyString())
        }

    @Test
    fun launchQuickAccessDialog_sendBroadcast() =
        kosmos.runTest {
            val intentArgumentCaptor = argumentCaptor<Intent>()
            val userHandleArgumentCaptor = argumentCaptor<UserHandle>()
            underTest.launchQuickAccessDialog(DEFAULT_DISPLAY)

            verify(mockBroadcastSender, times(1))
                .sendBroadcastAsUser(
                    intentArgumentCaptor.capture(),
                    userHandleArgumentCaptor.capture(),
                )
            assertThat(intentArgumentCaptor.firstValue.`package`)
                .isEqualTo(ShortcutChooserDialogInteractor.SYSTEMUI_PACKAGE)
            assertThat(intentArgumentCaptor.firstValue.action)
                .isEqualTo(ShortcutChooserDialogInteractor.QUICK_ACCESS_ACTION)
            assertThat(
                    intentArgumentCaptor.firstValue.getIntExtra(
                        ShortcutChooserDialogConstants.DISPLAY_ID,
                        INVALID_DISPLAY,
                    )
                )
                .isEqualTo(DEFAULT_DISPLAY)
            assertThat(userHandleArgumentCaptor.firstValue).isEqualTo(UserHandle.SYSTEM)
        }

    private fun Kosmos.sendIntentBroadcast(@UserShortcutType shortcutType: Int, displayId: Int) {
        Intent()
            .apply {
                if (shortcutType == UserShortcutType.QUICK_ACCESS) {
                    action = ShortcutChooserDialogInteractor.QUICK_ACCESS_ACTION
                } else {
                    action = ShortcutChooserDialogInteractor.SHORTCUT_CHOOSER_ACTION
                    putExtra(ShortcutChooserDialogConstants.SHORTCUT_TYPE, shortcutType)
                }
                putExtra(ShortcutChooserDialogConstants.DISPLAY_ID, displayId)
            }
            .let { broadcastDispatcher.sendIntentToMatchingReceiversOnly(context, it) }
    }

    private fun createTargetModel(
        @UserShortcutType shortcutType: Int,
        targetName: String,
        isAssigned: Boolean = true,
    ) =
        AccessibilityTargetModel(
            shortcutType = shortcutType,
            targetName = targetName,
            featureName = targetName,
            icon = ColorDrawable(Color.RED),
            isAssigned = isAssigned,
            isToggleable = true,
            isStateOn = false,
        )
}
