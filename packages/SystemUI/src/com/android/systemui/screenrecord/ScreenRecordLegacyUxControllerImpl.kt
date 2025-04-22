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

package com.android.systemui.screenrecord

import android.app.Dialog
import android.app.PendingIntent
import android.media.projection.StopReason
import com.android.systemui.broadcast.BroadcastDispatcher
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.mediaprojection.MediaProjectionMetricsLogger
import com.android.systemui.mediaprojection.devicepolicy.ScreenCaptureDevicePolicyResolver
import com.android.systemui.mediaprojection.devicepolicy.ScreenCaptureDisabledDialogDelegate
import com.android.systemui.settings.UserTracker
import dagger.Lazy
import java.util.concurrent.Executor

/**
 * [ScreenRecordUxController] implementation of the existing screen recording workflow.
 *
 * This class forwards all calls to the underlying [RecordingController] instance. It manages its
 * own set of listeners and bridges them to the original controller's callback mechanism.
 */
class ScreenRecordLegacyUxControllerImpl(
    @Main private val mainExecutor: Executor,
    broadcastDispatcher: BroadcastDispatcher,
    devicePolicyResolver: Lazy<ScreenCaptureDevicePolicyResolver>,
    userTracker: UserTracker,
    recordingControllerLogger: RecordingControllerLogger,
    mediaProjectionMetricsLogger: MediaProjectionMetricsLogger,
    screenCaptureDisabledDialogDelegate: ScreenCaptureDisabledDialogDelegate,
    screenRecordPermissionDialogDelegateFactory: ScreenRecordPermissionDialogDelegate.Factory,
    screenRecordPermissionContentManagerFactory: ScreenRecordPermissionContentManager.Factory,
) : ScreenRecordUxController {

    // Instantiate RecordingController internally using the injected dependencies
    val recordingController: RecordingController =
        RecordingController(
            this,
            mainExecutor,
            broadcastDispatcher,
            devicePolicyResolver,
            userTracker,
            recordingControllerLogger,
            mediaProjectionMetricsLogger,
            screenCaptureDisabledDialogDelegate,
            screenRecordPermissionDialogDelegateFactory,
            screenRecordPermissionContentManagerFactory,
        )

    private val listeners = mutableListOf<ScreenRecordUxController.StateChangeCallback>()

    // Internal bridge callback that forwards events from the Java controller to Kotlin listeners.
    private val internalCallbackBridge =
        object : RecordingController.RecordingStateChangeCallback {
            override fun onCountdown(millisUntilFinished: Long) {
                listeners.forEach { it.onCountdown(millisUntilFinished) }
            }

            override fun onCountdownEnd() {
                listeners.forEach { it.onCountdownEnd() }
            }

            override fun onRecordingStart() {
                listeners.forEach { it.onRecordingStart() }
            }

            override fun onRecordingEnd() {
                listeners.forEach { it.onRecordingEnd() }
            }
        }

    /** @see RecordingController.isScreenCaptureDisabled */
    override val isScreenCaptureDisabled: Boolean
        get() = recordingController.isScreenCaptureDisabled

    /** @see RecordingController.isStarting */
    override val isStarting: Boolean
        get() = recordingController.isStarting

    /** @see RecordingController.isRecording */
    override val isRecording: Boolean
        get() = recordingController.isRecording

    /** @see RecordingController.getStopReason */
    @get:StopReason
    override val stopReason: Int
        get() = recordingController.stopReason

    // --- Consolidated functions that handles UX events ---

    override fun onScreenRecordQsTileClick() {
        // TODO(b/409330121): move the UX code from ScreenRecordTile to here.
    }

    // --- 1:1 wrapped RecordingController Public API ---

    /** @see RecordingController.createScreenRecordDialog */
    override fun createScreenRecordDialog(onStartRecordingClicked: Runnable?): Dialog {
        return recordingController.createScreenRecordDialog(onStartRecordingClicked)
    }

    /** @see RecordingController.createScreenRecordPermissionContentManager */
    override fun createScreenRecordPermissionContentManager(
        onStartRecordingClicked: Runnable?
    ): ScreenRecordPermissionContentManager {
        return recordingController.createScreenRecordPermissionContentManager(
            onStartRecordingClicked
        )
    }

    /** @see RecordingController.startCountdown */
    override fun startCountdown(
        ms: Long,
        interval: Long,
        startIntent: PendingIntent,
        stopIntent: PendingIntent,
    ) {
        recordingController.startCountdown(ms, interval, startIntent, stopIntent)
    }

    /** @see RecordingController.cancelCountdown */
    override fun cancelCountdown() {
        recordingController.cancelCountdown()
    }

    /** @see RecordingController.stopRecording */
    override fun stopRecording(@StopReason stopReason: Int) {
        recordingController.stopRecording(stopReason)
    }

    /**
     * @see RecordingController.updateState Note: This is typically called internally in response to
     *   broadcasts, but wrapped here for 1:1 completeness.
     */
    override fun updateState(isRecording: Boolean) {
        recordingController.updateState(isRecording)
    }

    /**
     * Adds a listener to receive screen recording state changes. Registers the internal bridge
     * callback with the underlying [RecordingController] if this is the first listener added.
     *
     * @param listener The [StateChangeCallback] to add.
     */
    override fun addCallback(listener: ScreenRecordUxController.StateChangeCallback) {
        // Add the listener before checking size and potentially registering the bridge
        if (listeners.add(listener)) {
            // If this was the first listener added, register the bridge callback
            if (listeners.size == 1) {
                recordingController.addCallback(internalCallbackBridge)
            }
        }
    }

    /**
     * Removes a listener that was previously added. Unregisters the internal bridge callback from
     * the underlying [RecordingController] if this was the last listener removed.
     *
     * @param listener The [StateChangeCallback] to remove.
     */
    override fun removeCallback(listener: ScreenRecordUxController.StateChangeCallback) {
        // Remove the listener before checking size and potentially unregistering the bridge
        if (listeners.remove(listener)) {
            // If this was the last listener, unregister the bridge callback
            if (listeners.isEmpty()) {
                recordingController.removeCallback(internalCallbackBridge)
            }
        }
    }

    companion object {
        const val INTENT_UPDATE_STATE = "com.android.systemui.screenrecord.UPDATE_STATE"
        const val EXTRA_STATE = "extra_state"
    }
}
