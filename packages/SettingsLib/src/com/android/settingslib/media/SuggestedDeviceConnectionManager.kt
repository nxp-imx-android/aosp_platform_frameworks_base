/*
 * Copyright 2025 The Android Open Source Project
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
package com.android.settingslib.media

import android.media.RoutingChangeInfo
import android.os.Handler
import android.util.Log
import androidx.annotation.MainThread
import androidx.annotation.OpenForTesting
import com.android.internal.annotations.GuardedBy
import com.android.settingslib.media.LocalMediaManager.MediaDeviceState

typealias ConnectionFinishedCallback = (SuggestedDeviceState, Boolean) -> Unit

/**
 * Controls the connection for a suggested device pill in Media Controls. Responsible to start the
 * route scan if the suggested device is not discovered yet.
 */
@OpenForTesting
open class SuggestedDeviceConnectionManager(
    val localMediaManager: LocalMediaManager,
    var connectSuggestedDeviceHandler: Handler,
) {
    /** Callback for notifying that connection to suggested device is finished. */
    private var connectionFinishedCallback: ConnectionFinishedCallback? = null
    private val lock = Any()

    @GuardedBy("lock") var connectingSuggestedDeviceState: ConnectingSuggestedDeviceState? = null

    /**
     * Connects to a suggested device. If the device is not already scanned, a scan will be started
     * to attempt to discover the device.
     *
     * @param suggestion the suggested device to connect to.
     * @param routingChangeInfo the invocation details of the connect device request.
     */
    @OpenForTesting
    open fun connectSuggestedDevice(
        suggestion: SuggestedDeviceState,
        routingChangeInfo: RoutingChangeInfo,
    ) {
        synchronized(lock) {
            if (connectingSuggestedDeviceState != null) {
                Log.w(TAG, "Connection already in progress.")
                return
            }
            for (device in localMediaManager.mediaDevices) {
                if (suggestion.suggestedDeviceInfo.routeId == device.id) {
                    Log.i(TAG, "Device is available, connecting. deviceId = ${device.id}")
                    localMediaManager.connectDevice(device, routingChangeInfo)
                    return
                }
            }
            connectingSuggestedDeviceState =
                ConnectingSuggestedDeviceState(suggestion, routingChangeInfo.entryPoint).apply {
                    tryConnect()
                }
        }
    }

    @OpenForTesting
    open fun setConnectionFinishedCallback(callback: ConnectionFinishedCallback?) {
        connectionFinishedCallback = callback
    }

    inner class ConnectingSuggestedDeviceState(
        val suggestedDeviceState: SuggestedDeviceState,
        @RoutingChangeInfo.EntryPoint entryPoint: Int,
    ) {
        var isConnectionAttemptActive: Boolean = false
        var didAttemptCompleteSuccessfully: Boolean = false

        private val mDeviceCallback =
            object : LocalMediaManager.DeviceCallback {
                override fun onDeviceListUpdate(mediaDevices: List<MediaDevice>) {
                    synchronized(lock) {
                        for (mediaDevice in mediaDevices) {
                            if (isSuggestedDevice(mediaDevice)) {
                                Log.i(
                                    TAG,
                                    "Scan found matched device, connecting. deviceId = ${mediaDevice.id}",
                                )
                                localMediaManager.connectDevice(
                                    mediaDevice,
                                    RoutingChangeInfo(entryPoint, /* isSuggested= */ true),
                                )
                                isConnectionAttemptActive = true
                                break
                            }
                        }
                    }
                }

                override fun onSelectedDeviceStateChanged(
                    device: MediaDevice,
                    @MediaDeviceState state: Int,
                ) {
                    if (isSuggestedDevice(device) && (state == MediaDeviceState.STATE_CONNECTED)) {
                        if (
                            !connectSuggestedDeviceHandler.hasCallbacks(
                                mConnectionAttemptFinishedRunnable
                            )
                        ) {
                            return
                        }
                        didAttemptCompleteSuccessfully = true
                        // Remove the postDelayed runnable previously set and post a new one
                        // to be executed right away.
                        connectSuggestedDeviceHandler.removeCallbacks(
                            mConnectionAttemptFinishedRunnable
                        )
                        connectSuggestedDeviceHandler.post(mConnectionAttemptFinishedRunnable)
                    }
                }

                fun isSuggestedDevice(device: MediaDevice): Boolean {
                    return connectingSuggestedDeviceState != null &&
                        (connectingSuggestedDeviceState!!
                            .suggestedDeviceState
                            .suggestedDeviceInfo
                            .routeId == device.id)
                }
            }

        val mConnectionAttemptFinishedRunnable: Runnable = Runnable {
            synchronized(lock) {
                connectingSuggestedDeviceState = null
                isConnectionAttemptActive = false
            }
            localMediaManager.unregisterCallback(mDeviceCallback)
            localMediaManager.stopScan()
            Log.i(TAG, "Scan stopped. success = $didAttemptCompleteSuccessfully")
            dispatchOnConnectionFinished(suggestedDeviceState, didAttemptCompleteSuccessfully)
        }

        @MainThread
        private fun dispatchOnConnectionFinished(state: SuggestedDeviceState, success: Boolean) {
            connectionFinishedCallback?.invoke(state, success)
        }

        fun tryConnect() {
            // Attempt connection only if there isn't one already in progress.
            if (isConnectionAttemptActive) {
                return
            }
            Log.i(TAG, "Scanning for devices.")
            // Reset mDidAttemptCompleteSuccessfully at the start of each connection attempt.
            didAttemptCompleteSuccessfully = false
            localMediaManager.registerCallback(mDeviceCallback)
            localMediaManager.startScan()
            connectSuggestedDeviceHandler.postDelayed(
                mConnectionAttemptFinishedRunnable,
                SCAN_DURATION_MS,
            )
        }
    }

    companion object {
        private const val TAG = "SuggestedDeviceConnectionManager"
        private const val SCAN_DURATION_MS = 10000L
    }
}
