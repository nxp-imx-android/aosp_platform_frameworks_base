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

package com.android.systemui.accessibility.shortcutchooser.domain

import android.content.Intent
import android.view.Display.DEFAULT_DISPLAY
import android.view.Display.INVALID_DISPLAY
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.accessibility.common.ShortcutChooserDialogConstants
import com.android.internal.accessibility.common.ShortcutConstants.UserShortcutType
import com.android.systemui.SysuiTestCase
import com.android.systemui.accessibility.data.repository.AccessibilityShortcutsRepository
import com.android.systemui.broadcast.broadcastDispatcher
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
class ShortcutChooserDialogInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()
    private val broadcastDispatcher = kosmos.broadcastDispatcher

    // mocks
    private val mockRepository = mock(AccessibilityShortcutsRepository::class.java)

    private val Kosmos.underTest by
        Kosmos.Fixture { ShortcutChooserDialogInteractor(mockRepository, broadcastDispatcher) }

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
    fun getAllAccessibilityTargetsInfo_getListForAllByType() =
        kosmos.runTest {
            val shortcutType = UserShortcutType.TOP_ROW_KEY

            underTest.getAllAccessibilityTargetsInfo(shortcutType)

            verify(mockRepository).getAllAccessibilityTargetsInfo(eq(shortcutType))
        }

    @Test
    fun getSelectedAccessibilityTargetsInfo_getListForSelectedTargetsByType() =
        kosmos.runTest {
            val shortcutType = UserShortcutType.TOP_ROW_KEY

            underTest.getSelectedAccessibilityTargetsInfo(shortcutType)

            verify(mockRepository).getSelectedAccessibilityTargetsInfo(eq(shortcutType))
        }

    @Test
    fun enableShortcutForTargets_topRowKeyType_enableMagnification() =
        kosmos.runTest {
            val shortcutType = UserShortcutType.TOP_ROW_KEY
            val targetName = "fakeMagnifiactionTargetName"

            underTest.enableShortcutForTargets(enable = true, shortcutType, targetName)

            verify(mockRepository)
                .enableShortcutsForTargets(eq(true), eq(shortcutType), eq(targetName))
        }

    @Test
    fun enableShortcutForTargets_topRowKeyType_disableMagnification() =
        kosmos.runTest {
            val shortcutType = UserShortcutType.TOP_ROW_KEY
            val targetName = "fakeMagnifiactionTargetName"

            underTest.enableShortcutForTargets(enable = false, shortcutType, targetName)

            verify(mockRepository)
                .enableShortcutsForTargets(eq(false), eq(shortcutType), eq(targetName))
        }

    @Test
    fun performAccessibilityShortcut_topRowKeyType_toggleMagnification() =
        kosmos.runTest {
            val displayId = DEFAULT_DISPLAY
            val shortcutType = UserShortcutType.TOP_ROW_KEY
            val targetName = "fakeMagnifiactionTargetName"

            underTest.performAccessibilityShortcut(displayId, shortcutType, targetName)

            verify(mockRepository)
                .performAccessibilityShortcut(eq(displayId), eq(shortcutType), eq(targetName))
        }

    @Test
    fun performAccessibilityShortcut_topRowKeyType_invalidDisplay_doNothing() =
        kosmos.runTest {
            val displayId = INVALID_DISPLAY
            val shortcutType = UserShortcutType.TOP_ROW_KEY
            val targetName = "fakeMagnifiactionTargetName"

            underTest.performAccessibilityShortcut(displayId, shortcutType, targetName)

            verify(mockRepository, never())
                .performAccessibilityShortcut(anyInt(), anyInt(), anyString())
        }

    private fun sendIntentBroadcast(@UserShortcutType shortcutType: Int, displayId: Int) {
        val intent =
            Intent().apply {
                action = ShortcutChooserDialogInteractor.ACTION
                putExtra(ShortcutChooserDialogConstants.SHORTCUT_TYPE, shortcutType)
                putExtra(ShortcutChooserDialogConstants.DISPLAY_ID, displayId)
            }

        broadcastDispatcher.sendIntentToMatchingReceiversOnly(context, intent)
    }
}
