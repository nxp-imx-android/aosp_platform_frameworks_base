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
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.view.Display.DEFAULT_DISPLAY
import android.view.Display.INVALID_DISPLAY
import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.hardware.input.Flags
import com.android.internal.accessibility.common.KeyGestureEventConstants
import com.android.systemui.SysuiTestCase
import com.android.systemui.accessibility.data.repository.accessibilityShortcutsRepository
import com.android.systemui.accessibility.keygesture.domain.KeyGestureDialogInteractor
import com.android.systemui.accessibility.keygesture.domain.keyGestureDialogInteractor
import com.android.systemui.broadcast.broadcastDispatcher
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.backgroundScope
import com.android.systemui.kosmos.runTest
import com.android.systemui.runOnMainThreadAndWaitForIdleSync
import com.android.systemui.statusbar.phone.systemUIDialogFactory
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(Flags.FLAG_ENABLE_TALKBACK_AND_MAGNIFIER_KEY_GESTURES)
class KeyGestureDialogStartableTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()
    private val broadcastDispatcher = kosmos.broadcastDispatcher
    private val interactor = kosmos.keyGestureDialogInteractor

    private val Kosmos.underTest by
        Kosmos.Fixture {
            KeyGestureDialogStartable(
                interactor,
                kosmos.systemUIDialogFactory,
                kosmos.backgroundScope,
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
    @DisableFlags(Flags.FLAG_ENABLE_MAGNIFY_MAGNIFICATION_KEY_GESTURE_DIALOG)
    fun start_onMagnificationInfoFlowCollected_showDialog() =
        kosmos.runTest {
            underTest.start()

            sendIntentBroadcastForMagnificationInMainThread()

            assertThat(underTest.currentDialog!!.isShowing).isTrue()
        }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_MAGNIFY_MAGNIFICATION_KEY_GESTURE_DIALOG)
    fun start_onMagnificationDialogCreatedAndDismiss_dialogDismissed() =
        kosmos.runTest() {
            underTest.start()

            sendIntentBroadcastForMagnificationInMainThread()
            assertThat(underTest.currentDialog!!.isShowing).isTrue()
            runOnMainThreadAndWaitForIdleSync { underTest.currentDialog!!.dismiss() }

            assertThat(underTest.currentDialog).isNull()
        }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_MAGNIFY_MAGNIFICATION_KEY_GESTURE_DIALOG)
    fun start_onMagnificationInfoFlowCollected_dialogShowing_ignoreAdditionalFlows() =
        kosmos.runTest {
            underTest.start()
            // Assume that we already have a magnification dialog showing up.
            sendIntentBroadcastForMagnificationInMainThread()
            assertThat(underTest.currentDialog!!.isShowing).isTrue()

            // Then, we collect a flow for Screen reader.
            sendIntentBroadcastForScreenReaderInMainThread(
                KeyGestureDialogInteractor.LAUNCH_DIALOG_ACTION
            )

            // Still show the Magnification dialog.
            assertThat(underTest.currentDialog!!.isShowing).isTrue()
            assertThat(underTest.dialogType)
                .isEqualTo(KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION)
        }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_MAGNIFY_MAGNIFICATION_KEY_GESTURE_DIALOG)
    fun start_onMagReceivedAndDismiss_thenShowScreenReaderAgain_showSecondDialog() =
        kosmos.runTest {
            underTest.start()
            sendIntentBroadcastForMagnificationInMainThread()
            assertThat(underTest.currentDialog!!.isShowing).isTrue()

            runOnMainThreadAndWaitForIdleSync { underTest.currentDialog!!.dismiss() }
            sendIntentBroadcastForScreenReaderInMainThread(
                KeyGestureDialogInteractor.LAUNCH_DIALOG_ACTION
            )

            assertThat(underTest.currentDialog!!.isShowing).isTrue()
            assertThat(underTest.dialogType)
                .isEqualTo(KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_SCREEN_READER)
        }

    @Test
    fun start_invalidKeyGestureType_onNullFlowCollected_noDialog() =
        kosmos.runTest {
            underTest.start()

            sendIntentBroadcastInMainThread(
                keyGestureType = 0,
                metaState = KeyEvent.META_META_ON or KeyEvent.META_ALT_ON,
                keyCode = KeyEvent.KEYCODE_M,
                targetName = "targetNameForMagnification",
                displayId = DEFAULT_DISPLAY,
            )

            assertThat(underTest.currentDialog).isNull()
        }

    @Test
    fun start_invalidMetaState_onNullFlowCollected_noDialog() =
        kosmos.runTest {
            underTest.start()

            sendIntentBroadcastInMainThread(
                keyGestureType = KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION,
                metaState = 0,
                keyCode = KeyEvent.KEYCODE_M,
                targetName = "targetNameForMagnification",
                displayId = DEFAULT_DISPLAY,
            )

            assertThat(underTest.currentDialog).isNull()
        }

    @Test
    fun start_invalidKeyCode_onNullFlowCollected_noDialog() =
        kosmos.runTest {
            underTest.start()

            sendIntentBroadcastInMainThread(
                keyGestureType = KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION,
                metaState = KeyEvent.META_META_ON or KeyEvent.META_ALT_ON,
                keyCode = 0,
                targetName = "targetNameForMagnification",
                displayId = DEFAULT_DISPLAY,
            )

            assertThat(underTest.currentDialog).isNull()
        }

    @Test
    fun start_invalidTargetName_onNullFlowCollected_noDialog() =
        kosmos.runTest {
            underTest.start()

            sendIntentBroadcastInMainThread(
                keyGestureType = KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION,
                metaState = KeyEvent.META_META_ON or KeyEvent.META_ALT_ON,
                keyCode = KeyEvent.KEYCODE_M,
                targetName = "",
                displayId = DEFAULT_DISPLAY,
            )

            assertThat(underTest.currentDialog).isNull()
        }

    @Test
    fun start_invalidDisplayId_onNullFlowCollected_noDialog() =
        kosmos.runTest {
            underTest.start()

            sendIntentBroadcastInMainThread(
                keyGestureType = KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION,
                metaState = KeyEvent.META_META_ON or KeyEvent.META_ALT_ON,
                keyCode = KeyEvent.KEYCODE_M,
                targetName = "targetNameForMagnification",
                displayId = INVALID_DISPLAY,
            )

            assertThat(underTest.currentDialog).isNull()
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MAGNIFY_MAGNIFICATION_KEY_GESTURE_DIALOG)
    fun start_onMagnificationDialog_enablesShortcutAndZoomsIn() =
        kosmos.runTest {
            val shortcutsRepository = kosmos.accessibilityShortcutsRepository
            underTest.start()
            assertThat(shortcutsRepository.areShortcutsEnabled).isFalse()
            assertThat(shortcutsRepository.isMagnificationAndZoomInEnabled).isFalse()

            sendIntentBroadcastForMagnificationInMainThread()

            assertThat(underTest.currentDialog!!.isShowing).isTrue()
            assertThat(shortcutsRepository.areShortcutsEnabled).isTrue()
            assertThat(shortcutsRepository.isMagnificationAndZoomInEnabled).isTrue()
        }

    @Test
    fun start_screenReaderDialog_performsTtsPrompt() =
        kosmos.runTest {
            val shortcutsRepository = kosmos.accessibilityShortcutsRepository
            underTest.start()

            sendIntentBroadcastForScreenReaderInMainThread(
                KeyGestureDialogInteractor.LAUNCH_DIALOG_ACTION
            )

            assertThat(underTest.currentDialog!!.isShowing).isTrue()
            // Screen Reader dialog will create a `TtsPrompt`, so it shouldn't be null.
            assertThat(shortcutsRepository.ttsPrompt).isNotNull()
            // Verify the text used to create the `TtsPrompt` instance above is about Screen Reader
            // dialog.
            // TODO: b/432568819 - Update the expected string here after we get the new tts text
            // from UXW to create the `TtsPrompt` for Screen Reader dialog in production code.
            assertThat(shortcutsRepository.ttsText)
                .isEqualTo("Press Action + Alt + T again to enable Screen Reader")
            assertThat(shortcutsRepository.areShortcutsEnabled).isTrue()
        }

    @Test
    @Ignore(
        "b/432568819 - Add this unittest after ag/35100568, because we need to wait for the main thread idle to dismiss dialog"
    )
    fun start_screenReaderDialog_dismissDialog() =
        kosmos.runTest {
            underTest.start()
            sendIntentBroadcastForScreenReaderInMainThread(
                KeyGestureDialogInteractor.LAUNCH_DIALOG_ACTION
            )
            assertThat(underTest.currentDialog!!.isShowing).isTrue()
            assertThat(underTest.dialogType)
                .isEqualTo(KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_SCREEN_READER)

            // While the Screen Reader dialog is showing, we received a dismissal request to dismiss
            // it.
            sendIntentBroadcastForScreenReaderInMainThread(
                KeyGestureDialogInteractor.DISMISS_DIALOG_ACTION
            )

            assertThat(underTest.currentDialog).isNull()
        }

    @Test
    fun start_magnificationDialog_receivedDismissScreenReaderDialogRequest_doNothing() =
        kosmos.runTest {
            underTest.start()
            sendIntentBroadcastForMagnificationInMainThread()
            assertThat(underTest.currentDialog!!.isShowing).isTrue()
            assertThat(underTest.dialogType)
                .isEqualTo(KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION)

            // While the Magnification dialog is showing, we received a dismissal request to dismiss
            // it.
            sendIntentBroadcastForScreenReaderInMainThread(
                KeyGestureDialogInteractor.DISMISS_DIALOG_ACTION
            )

            // Because the current existing dialog type isn't Screen reader, so we will not dismiss
            // it.
            assertThat(underTest.currentDialog).isNotNull()
        }

    @Test
    fun start_noExistingDialog_receivedDismissScreenReaderDialogRequest_doNothing() =
        kosmos.runTest {
            underTest.start()

            // While there is no dialog, we received a dismissal request. It will do nothing.
            sendIntentBroadcastForScreenReaderInMainThread(
                KeyGestureDialogInteractor.DISMISS_DIALOG_ACTION
            )

            assertThat(underTest.currentDialog).isNull()
        }

    private fun sendIntentBroadcastInMainThread(
        keyGestureType: Int,
        metaState: Int,
        keyCode: Int,
        targetName: String,
        displayId: Int,
        intentAction: String = KeyGestureDialogInteractor.LAUNCH_DIALOG_ACTION,
    ) {
        val intent =
            Intent().apply {
                action = intentAction
                putExtra(KeyGestureEventConstants.KEY_GESTURE_TYPE, keyGestureType)
                putExtra(KeyGestureEventConstants.META_STATE, metaState)
                putExtra(KeyGestureEventConstants.KEY_CODE, keyCode)
                putExtra(KeyGestureEventConstants.TARGET_NAME, targetName)
                putExtra(KeyGestureEventConstants.DISPLAY_ID, displayId)
            }

        // Sending broadcast to create SysUi dialog should be run in main thread.
        runOnMainThreadAndWaitForIdleSync {
            broadcastDispatcher.sendIntentToMatchingReceiversOnly(context, intent)
        }
    }

    private fun sendIntentBroadcastForMagnificationInMainThread() {
        sendIntentBroadcastInMainThread(
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION,
            KeyEvent.META_META_ON or KeyEvent.META_ALT_ON,
            KeyEvent.KEYCODE_M,
            "targetNameForMagnification",
            DEFAULT_DISPLAY,
        )
    }

    private fun sendIntentBroadcastForScreenReaderInMainThread(intentAction: String) {
        sendIntentBroadcastInMainThread(
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_SCREEN_READER,
            KeyEvent.META_META_ON or KeyEvent.META_ALT_ON,
            KeyEvent.KEYCODE_T,
            "targetNameForScreenReader",
            DEFAULT_DISPLAY,
            intentAction,
        )
    }
}
