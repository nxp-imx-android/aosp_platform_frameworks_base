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
import android.view.Display.DEFAULT_DISPLAY
import android.view.Display.INVALID_DISPLAY
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.accessibility.data.repository.AccessibilityShortcutsRepository
import com.android.systemui.accessibility.shortcutchooser.shared.model.AccessibilityTargetModel
import com.android.systemui.accessibility.shortcutchooser.shared.model.QuickAccessDialogRequestModel
import com.android.systemui.broadcast.broadcastDispatcher
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class QuickAccessDialogInteractorTest : SysuiTestCase() {
    private companion object {
        const val SHORTCUT_TYPE = QuickAccessDialogInteractor.SHORTCUT_TYPE
        const val FAKE_TARGET = "com.android.test/TestService"
    }

    private val kosmos = testKosmosNew()

    private val mockRepository = mock<AccessibilityShortcutsRepository>()

    private val Kosmos.underTest by
        Kosmos.Fixture { QuickAccessDialogInteractor(mockRepository, kosmos.broadcastDispatcher) }

    @Test
    fun dialogRequest_validDisplay_emitsRequestModel() =
        kosmos.runTest {
            val requestModel by collectLastValue(underTest.dialogRequest)

            sendIntentBroadcast(DEFAULT_DISPLAY)

            assertThat(requestModel).isEqualTo(QuickAccessDialogRequestModel(DEFAULT_DISPLAY))
        }

    @Test
    fun dialogRequest_invalidDisplay_emitsNull() =
        kosmos.runTest {
            val requestModel by collectLastValue(underTest.dialogRequest)

            sendIntentBroadcast(INVALID_DISPLAY)

            assertThat(requestModel).isNull()
        }

    @Test
    fun getAllAccessibilityTargets_returnsAllTargetModels() =
        kosmos.runTest {
            val targets =
                listOf(
                    createTargetModel("Test Service 1", isAssigned = true),
                    createTargetModel("Test Service 2", isAssigned = false),
                )
            whenever(mockRepository.getAllAccessibilityTargets(SHORTCUT_TYPE))
                .thenReturn(flowOf(targets))

            assertThat(underTest.getAllAccessibilityTargets().first()).isEqualTo(targets)
        }

    @Test
    fun enableShortcutForAllTargets_enablesUnassignedShortcuts() =
        kosmos.runTest {
            val targetName1 = "com.android.test/TestService1"
            val targetName2 = "com.android.test/TestService2"
            val targets =
                listOf(
                    createTargetModel(
                        "Test Service 1",
                        targetName = targetName1,
                        isAssigned = true,
                    ),
                    createTargetModel(
                        "Test Service 2",
                        targetName = targetName2,
                        isAssigned = false,
                    ),
                )
            whenever(mockRepository.getAllAccessibilityTargetsInfo(SHORTCUT_TYPE))
                .thenReturn(targets)

            underTest.enableShortcutForAllTargets()

            verify(mockRepository, never())
                .enableShortcutsForTargets(eq(true), eq(SHORTCUT_TYPE), eq(targetName1))
            verify(mockRepository)
                .enableShortcutsForTargets(eq(true), eq(SHORTCUT_TYPE), eq(targetName2))
        }

    @Test
    fun performAccessibilityShortcut_validDisplay_performsShortcut() =
        kosmos.runTest {
            underTest.performAccessibilityShortcut(DEFAULT_DISPLAY, FAKE_TARGET)

            verify(mockRepository)
                .performAccessibilityShortcut(
                    eq(DEFAULT_DISPLAY),
                    eq(SHORTCUT_TYPE),
                    eq(FAKE_TARGET),
                )
        }

    @Test
    fun performAccessibilityShortcut_invalidDisplay_doesNothing() =
        kosmos.runTest {
            underTest.performAccessibilityShortcut(INVALID_DISPLAY, FAKE_TARGET)

            verify(mockRepository, never())
                .performAccessibilityShortcut(anyInt(), anyInt(), anyString())
        }

    fun createTargetModel(
        featureName: String,
        targetName: String? = null,
        isAssigned: Boolean,
    ): AccessibilityTargetModel {
        val className = featureName.replace(" ", "")
        return AccessibilityTargetModel(
            shortcutType = SHORTCUT_TYPE,
            targetName = targetName ?: "com.android.test/$className",
            featureName = featureName,
            icon = ColorDrawable(Color.RED),
            isAssigned = isAssigned,
            isToggleable = true,
            isToggleOn = false,
        )
    }

    private fun Kosmos.sendIntentBroadcast(displayId: Int) {
        Intent()
            .apply {
                action = QuickAccessDialogInteractor.ACTION
                putExtra(QuickAccessDialogInteractor.DISPLAY_ID, displayId)
            }
            .let { broadcastDispatcher.sendIntentToMatchingReceiversOnly(context, it) }
    }
}
