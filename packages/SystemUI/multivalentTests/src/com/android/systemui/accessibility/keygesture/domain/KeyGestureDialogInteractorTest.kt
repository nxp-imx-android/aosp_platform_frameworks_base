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

package com.android.systemui.accessibility.keygesture.domain

import android.content.Intent
import android.content.applicationContext
import android.hardware.input.KeyGestureEvent
import android.os.fakeExecutorHandler
import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.accessibility.common.KeyGestureEventConstants
import com.android.systemui.SysuiTestCase
import com.android.systemui.accessibility.data.repository.AccessibilityShortcutsRepository
import com.android.systemui.broadcast.broadcastDispatcher
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify

@SmallTest
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class KeyGestureDialogInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val broadcastDispatcher = kosmos.broadcastDispatcher
    private val testDispatcher = kosmos.testDispatcher
    private val testScope = kosmos.testScope

    // mocks
    private val repository = mock(AccessibilityShortcutsRepository::class.java)

    private lateinit var underTest: KeyGestureDialogInteractor

    @Before
    fun setUp() {
        underTest =
            KeyGestureDialogInteractor(
                kosmos.applicationContext,
                repository,
                broadcastDispatcher,
                testDispatcher,
                kosmos.fakeExecutorHandler,
            )
    }

    @Test
    fun onPositiveButtonClick_enabledShortcutsForFakeTarget() {
        val enabledTargetName = "fakeTargetName"

        underTest.onPositiveButtonClick(enabledTargetName)

        verify(repository).enableShortcutsForTargets(eq(enabledTargetName))
    }

    @Test
    fun keyGestureConfirmDialogRequest_invalidRequestReceived() {
        testScope.runTest {
            val keyGestureType = KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION
            val metaState = 0
            val keyCode = 0
            val testTargetName = "fakeTargetName"
            val keyGestureConfirmInfo by collectLastValue(underTest.keyGestureConfirmDialogRequest)
            runCurrent()

            sendIntentBroadcast(keyGestureType, metaState, keyCode, testTargetName)
            runCurrent()

            assertThat(keyGestureConfirmInfo).isNull()
        }
    }

    @Test
    fun keyGestureConfirmDialogRequest_getFlowFromIntentForMagnification() {
        testScope.runTest {
            val keyGestureType = KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION
            val metaState = KeyEvent.META_META_ON or KeyEvent.META_ALT_ON
            val keyCode = KeyEvent.KEYCODE_M
            val testTargetName = "targetNameForMagnification"
            collectLastValue(underTest.keyGestureConfirmDialogRequest)
            runCurrent()

            sendIntentBroadcast(keyGestureType, metaState, keyCode, testTargetName)
            runCurrent()

            verify(repository)
                .getTitleToContentForKeyGestureDialog(
                    eq(keyGestureType),
                    eq(metaState),
                    eq(keyCode),
                    eq(testTargetName),
                )
        }
    }

    private fun sendIntentBroadcast(
        keyGestureType: Int,
        metaState: Int,
        keyCode: Int,
        targetName: String,
    ) {
        val intent =
            Intent().apply {
                action = KeyGestureDialogInteractor.ACTION
                putExtra(KeyGestureEventConstants.KEY_GESTURE_TYPE, keyGestureType)
                putExtra(KeyGestureEventConstants.META_STATE, metaState)
                putExtra(KeyGestureEventConstants.KEY_CODE, keyCode)
                putExtra(KeyGestureEventConstants.TARGET_NAME, targetName)
            }

        broadcastDispatcher.sendIntentToMatchingReceiversOnly(context, intent)
    }
}
