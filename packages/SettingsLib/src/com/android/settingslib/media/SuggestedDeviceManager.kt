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

package com.android.settingslib.media

import android.media.RoutingChangeInfo
import android.media.SuggestedDeviceInfo
import android.os.Handler
import android.util.Log
import androidx.annotation.GuardedBy
import com.android.settingslib.media.LocalMediaManager.MediaDeviceState
import com.android.settingslib.media.LocalMediaManager.MediaDeviceState.STATE_CONNECTED
import com.android.settingslib.media.LocalMediaManager.MediaDeviceState.STATE_CONNECTING
import com.android.settingslib.media.LocalMediaManager.MediaDeviceState.STATE_CONNECTING_FAILED
import com.android.settingslib.media.LocalMediaManager.MediaDeviceState.STATE_DISCONNECTED
import com.android.settingslib.media.LocalMediaManager.MediaDeviceState.STATE_SELECTED
import java.util.concurrent.CopyOnWriteArraySet

private const val TAG = "SuggestedDeviceManager"

private const val CONNECTING_TIMEOUT_MS = 20_000L
private const val CONNECTING_FAILED_TIMEOUT_MS = 10_000L

/**
 * Provides data to render and handles user interactions for the suggested device chip within the
 * Android Media Controls.
 *
 * This class exposes the [SuggestedDeviceState] which is calculated based on:
 * - Lists of device suggestions and media routes (media devices) provided by the Media Router.
 * - User interactions with the suggested device chip.
 * - The results of user-initiated connection attempts to these devices.
 *
 * @param localMediaManager an instance of [LocalMediaManager]
 * @param handler a MainHandler to run timeout events on.
 */
class SuggestedDeviceManager(
  private val localMediaManager: LocalMediaManager,
  private val handler: Handler,
) {
  private val lock: Any = Object()
  private val listeners = CopyOnWriteArraySet<Listener>()
  @GuardedBy("lock") private var mediaDevices: List<MediaDevice> = listOf()
  @GuardedBy("lock") private var topSuggestion: SuggestedDeviceInfo? = null
  @GuardedBy("lock") private var suggestedDeviceState: SuggestedDeviceState? = null
  // Overrides the connection state obtained from the [MediaDevice] that matches the
  // [topSuggestion]. This is necessary to prevent UI state jumps during connection attempts or
  // when displaying error messages.
  @GuardedBy("lock") @MediaDeviceState private var connectionStateOverride: Int? = null

  private val onConnectionStateOverrideExpiredRunnable = Runnable {
    synchronized(lock) {
      if (connectionStateOverride == STATE_CONNECTING_FAILED) {
        // After the connection error, hide the suggestion chip until the new suggestion is
        // requested.
        topSuggestion = null
      }
      connectionStateOverride = null
      updateSuggestedDeviceStateLocked(topSuggestion, mediaDevices)
    }
    dispatchOnSuggestedDeviceUpdated()
  }

  private val localMediaManagerDeviceCallback =
    object : LocalMediaManager.DeviceCallback {
      override fun onDeviceListUpdate(newDevices: List<MediaDevice>?) {
        val stateChanged =
          synchronized(lock) {
            mediaDevices = newDevices?.toList() ?: listOf()
            updateSuggestedDeviceStateLocked(topSuggestion, mediaDevices)
          }
        if (stateChanged) {
          dispatchOnSuggestedDeviceUpdated()
        }
      }

      override fun onDeviceSuggestionsUpdated(newSuggestions: List<SuggestedDeviceInfo>) {
        val stateChanged =
          synchronized(lock) {
            topSuggestion = newSuggestions.firstOrNull()
            updateSuggestedDeviceStateLocked(topSuggestion, mediaDevices)
          }
        if (stateChanged) {
          dispatchOnSuggestedDeviceUpdated()
        }
      }

      override fun onConnectSuggestedDeviceFinished(
        newSuggestedDeviceState: SuggestedDeviceState,
        success: Boolean,
      ) {
        if (!isCurrentSuggestion(newSuggestedDeviceState.suggestedDeviceInfo)) {
          Log.w(TAG, "onConnectSuggestedDeviceFinished. Suggestion got changed.")
          return
        }
        if (!success) {
          overrideConnectionStateWithExpiration(
            connectionState = STATE_CONNECTING_FAILED,
            timeoutMs = CONNECTING_FAILED_TIMEOUT_MS,
          )
        } // On success, the state should automatically be updated by the MediaDevice state.
      }
    }

  fun addListener(listener: Listener) {
    val shouldRegisterCallback =
      synchronized(lock) {
        val wasSetEmpty = listeners.isEmpty()
        listeners.add(listener)
        wasSetEmpty
      }

    if (shouldRegisterCallback) {
      eagerlyUpdateState()
      localMediaManager.registerCallback(localMediaManagerDeviceCallback)
    }
  }

  fun removeListener(listener: Listener) {
    val shouldUnregisterCallback =
      synchronized(lock) {
        listeners.remove(listener)
        listeners.isEmpty()
      }

    if (shouldUnregisterCallback) {
      localMediaManager.unregisterCallback(localMediaManagerDeviceCallback)
    }
  }

  fun requestDeviceSuggestion() {
    localMediaManager.requestDeviceSuggestion()
  }

  fun getSuggestedDevice(): SuggestedDeviceState? {
    if (listeners.isEmpty()) {
      // If there were no callbacks set, recalculate the state before returning the result.
      eagerlyUpdateState()
    }
    return suggestedDeviceState
  }

  fun connectSuggestedDevice(
    newSuggestedDeviceState: SuggestedDeviceState,
    routingChangeInfo: RoutingChangeInfo,
  ) {
    if (!isCurrentSuggestion(newSuggestedDeviceState.suggestedDeviceInfo)) {
      Log.w(TAG, "Suggestion got changed, aborting connection.")
      return
    }
    overrideConnectionStateWithExpiration(
      connectionState = STATE_CONNECTING,
      timeoutMs = CONNECTING_TIMEOUT_MS,
    )
    localMediaManager.connectSuggestedDevice(newSuggestedDeviceState, routingChangeInfo)
  }

  private fun eagerlyUpdateState() {
    synchronized(lock) {
      mediaDevices = localMediaManager.mediaDevices
      topSuggestion = localMediaManager.suggestions.firstOrNull()
      updateSuggestedDeviceStateLocked(topSuggestion, mediaDevices)
    }
  }

  @GuardedBy("lock")
  private fun updateSuggestedDeviceStateLocked(
    newTopSuggestion: SuggestedDeviceInfo?,
    newMediaDevices: List<MediaDevice>,
  ): Boolean {
    val newSuggestedDeviceState =
      calculateNewSuggestedDeviceStateLocked(newTopSuggestion, newMediaDevices)
    if (newSuggestedDeviceState != suggestedDeviceState) {
      suggestedDeviceState = newSuggestedDeviceState
      return true
    }
    return false
  }

  @GuardedBy("lock")
  private fun calculateNewSuggestedDeviceStateLocked(
    newTopSuggestion: SuggestedDeviceInfo?,
    newMediaDevices: List<MediaDevice>
  ): SuggestedDeviceState? {
    if (newTopSuggestion == null) {
      clearConnectionStateOverrideLocked()
      return null
    }

    val newConnectionState =
      getConnectionStateFromMatchedDeviceLocked(newTopSuggestion, newMediaDevices)
    if (shouldClearStateOverride(newTopSuggestion, newConnectionState)) {
      clearConnectionStateOverrideLocked()
    }

    return if (isConnectedState(newConnectionState)) {
      // Don't display a suggestion if the MediaDevice that matches the suggestion is connected.
      null
    } else {
      SuggestedDeviceState(newTopSuggestion, connectionStateOverride ?: newConnectionState)
    }
  }

  @GuardedBy("lock")
  @MediaDeviceState
  private fun getConnectionStateFromMatchedDeviceLocked(
    newTopSuggestion: SuggestedDeviceInfo,
    newMediaDevices: List<MediaDevice>,
  ): Int {
    val matchedDevice = getDeviceByRouteId(newMediaDevices, newTopSuggestion.routeId)
    if (matchedDevice?.isSelected == true) {
      return STATE_SELECTED
    }
    return matchedDevice?.state ?: STATE_DISCONNECTED
  }

  private fun shouldClearStateOverride(
    newTopSuggestion: SuggestedDeviceInfo,
    @MediaDeviceState newConnectionState: Int,
  ): Boolean {
    // Don't clear the state override if a matched device is in DISCONNECTED state. Currently, the
    // DISCONNECTED state can be reported during connection that can lead to UI flicker.
    return !isCurrentSuggestion(newTopSuggestion) || newConnectionState != STATE_DISCONNECTED
  }

  private fun isConnectedState(@MediaDeviceState state: Int): Boolean =
    state == STATE_CONNECTED || state == STATE_SELECTED

  private fun getDeviceByRouteId(mediaDevices: List<MediaDevice>, routeId: String): MediaDevice? =
    mediaDevices.find { it.routeInfo?.id == routeId }

  private fun isCurrentSuggestion(suggestedDeviceInfo: SuggestedDeviceInfo) =
    synchronized(lock) {
      suggestedDeviceState?.suggestedDeviceInfo?.routeId == suggestedDeviceInfo.routeId
    }

  private fun overrideConnectionStateWithExpiration(connectionState: Int, timeoutMs: Long) {
    synchronized(lock) {
      connectionStateOverride = connectionState
      suggestedDeviceState = suggestedDeviceState?.copy(connectionState = connectionState)
      handler.removeCallbacks(onConnectionStateOverrideExpiredRunnable)
      handler.postDelayed(onConnectionStateOverrideExpiredRunnable, timeoutMs)
    }
    dispatchOnSuggestedDeviceUpdated()
  }

  @GuardedBy("lock")
  private fun clearConnectionStateOverrideLocked() {
    connectionStateOverride = null
    handler.removeCallbacks(onConnectionStateOverrideExpiredRunnable)
  }

  private fun dispatchOnSuggestedDeviceUpdated() {
    val state = synchronized(lock) { suggestedDeviceState }
    Log.i(TAG, "dispatchOnSuggestedDeviceUpdated(), state: $state")
    listeners.forEach { it.onSuggestedDeviceStateUpdated(state) }
  }

  interface Listener {
    fun onSuggestedDeviceStateUpdated(state: SuggestedDeviceState?)
  }
}
