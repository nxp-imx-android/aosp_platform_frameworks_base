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

package com.android.systemui.accessibility.keygesture.ui

import android.content.Intent
import android.hardware.input.KeyGestureEvent
import android.platform.test.annotations.EnableFlags
import android.view.KeyEvent
import androidx.test.annotation.UiThreadTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.hardware.input.Flags
import com.android.internal.accessibility.common.KeyGestureEventConstants
import com.android.systemui.SysuiTestCase
import com.android.systemui.accessibility.keygesture.domain.KeyGestureDialogInteractor
import com.android.systemui.accessibility.keygesture.domain.keyGestureDialogInteractor
import com.android.systemui.broadcast.broadcastDispatcher
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.kosmos.testScope
import com.android.systemui.statusbar.phone.systemUIDialogFactory
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@UiThreadTest
@EnableFlags(Flags.FLAG_ENABLE_TALKBACK_AND_MAGNIFIER_KEY_GESTURES)
class KeyGestureDialogStartableTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val broadcastDispatcher = kosmos.broadcastDispatcher
    private val interactor = kosmos.keyGestureDialogInteractor
    private val testScope = kosmos.testScope

    private lateinit var underTest: KeyGestureDialogStartable

    @Before
    fun setUp() {
        underTest =
            KeyGestureDialogStartable(
                interactor,
                kosmos.systemUIDialogFactory,
                kosmos.applicationCoroutineScope,
            )
    }

    @After
    fun tearDown() {
        // If we show the dialog, we must dismiss the dialog at the end of the test on the main
        // thread.
        underTest.currentDialog?.dismiss()
    }

    @Test
    fun start_doesNotShowDialogByDefault() =
        testScope.runTest {
            underTest.start()
            runCurrent()

            assertThat(underTest.currentDialog).isNull()
        }

    @Test
    fun start_onValidRequestReceived_showDialog() =
        testScope.runTest {
            underTest.start()
            runCurrent()

            // Trigger to send a broadcast event
            sendIntentBroadcast(
                KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION,
                KeyEvent.META_META_ON or KeyEvent.META_ALT_ON,
                KeyEvent.KEYCODE_M,
                "targetNameForMagnification",
            )
            runCurrent()

            assertThat(underTest.currentDialog?.isShowing).isTrue()
        }

    @Test
    fun start_onValidRequestReceived_dialogShowing_ignoreAdditionalRequests() =
        testScope.runTest {
            underTest.start()
            runCurrent()

            // Trigger to send a broadcast event at the first-time for Magnification
            sendIntentBroadcast(
                KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION,
                KeyEvent.META_META_ON or KeyEvent.META_ALT_ON,
                KeyEvent.KEYCODE_M,
                "targetNameForMagnification",
            )
            runCurrent()
            // Trigger to send a broadcast event at the second-time for TalkBack
            sendIntentBroadcast(
                KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_SCREEN_READER,
                KeyEvent.META_META_ON or KeyEvent.META_ALT_ON,
                KeyEvent.KEYCODE_T,
                "targetNameForScreenReader",
            )
            runCurrent()

            // Only show the Magnification dialog.
            assertThat(underTest.currentDialog?.isShowing).isTrue()
            assertThat(underTest.dialogType)
                .isEqualTo(KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION)
        }

    @Test
    fun start_onInvalidRequestReceived_noDialog() =
        testScope.runTest {
            underTest.start()
            runCurrent()

            // Trigger to send a broadcast event
            sendIntentBroadcast(0, 0, KeyEvent.KEYCODE_M, "targetName")
            runCurrent()

            assertThat(underTest.currentDialog).isNull()
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
