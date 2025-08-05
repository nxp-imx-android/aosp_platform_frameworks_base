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
import android.util.Log
import androidx.annotation.GuardedBy
import com.android.settingslib.media.LocalMediaManager.MediaDeviceState.STATE_CONNECTED
import com.android.settingslib.media.LocalMediaManager.MediaDeviceState.STATE_CONNECTING
import com.android.settingslib.media.LocalMediaManager.MediaDeviceState.STATE_CONNECTING_FAILED
import com.android.settingslib.media.LocalMediaManager.MediaDeviceState.STATE_DISCONNECTED
import java.util.concurrent.CopyOnWriteArraySet

private const val TAG = "SuggestedDeviceManager"

/**
 * Provides data to render and handles user interactions for the suggested device chip within the
 * Android Media Controls.
 *
 * This class exposes the [SuggestedDeviceState] which is calculated based on:
 * - Lists of device suggestions and media routes (media devices) provided by the Media Router.
 * - User interactions with the suggested device chip.
 * - The results of user-initiated connection attempts to these devices.
 */
class SuggestedDeviceManager(private val localMediaManager: LocalMediaManager) {
  private val lock: Any = Object()
  private val listeners = CopyOnWriteArraySet<Listener>()
  @GuardedBy("lock") private var mediaDevices: List<MediaDevice> = listOf()
  @GuardedBy("lock") private var suggestions: List<SuggestedDeviceInfo> = listOf()
  @GuardedBy("lock") private var suggestedDeviceState: SuggestedDeviceState? = null

  private val localMediaManagerDeviceCallback =
    object : LocalMediaManager.DeviceCallback {
      override fun onDeviceListUpdate(newDevices: List<MediaDevice>?) {
        val stateChanged = synchronized(lock) {
          mediaDevices = newDevices?.toList() ?: listOf()
          updateSuggestedDeviceStateLocked()
        }
        if (stateChanged) {
          dispatchOnSuggestedDeviceUpdated()
        }
      }

      override fun onDeviceSuggestionsUpdated(newSuggestions: List<SuggestedDeviceInfo>) {
        val stateChanged = synchronized(lock) {
          suggestions = newSuggestions
          updateSuggestedDeviceStateLocked()
        }
        if (stateChanged) {
          dispatchOnSuggestedDeviceUpdated()
        }
      }

      override fun onConnectionAttemptedForSuggestion(
        newSuggestedDeviceState: SuggestedDeviceState
      ) {
        synchronized(lock) {
          if (!isCurrentSuggestion(newSuggestedDeviceState.suggestedDeviceInfo)) {
            Log.w(TAG, "onConnectionAttemptedForSuggestion. Suggestion got changed.")
            return
          }
          if (
            suggestedDeviceState?.connectionState != STATE_DISCONNECTED &&
              suggestedDeviceState?.connectionState != STATE_CONNECTING_FAILED
          ) {
            return
          }
          suggestedDeviceState = suggestedDeviceState?.copy(connectionState = STATE_CONNECTING)
        }
        dispatchOnSuggestedDeviceUpdated()
      }

      override fun onConnectSuggestedDeviceFinished(
        newSuggestedDeviceState: SuggestedDeviceState,
        success: Boolean,
      ) {
        if (!isCurrentSuggestion(newSuggestedDeviceState.suggestedDeviceInfo)) {
          Log.w(TAG, "onConnectSuggestedDeviceFinished. Suggestion got changed.")
          return
        }
        synchronized(lock) {
          val connectionState = if (success) STATE_CONNECTED else STATE_CONNECTING_FAILED
          suggestedDeviceState = suggestedDeviceState?.copy(connectionState = connectionState)
        }
        dispatchOnSuggestedDeviceUpdated()
      }
    }

  fun addListener(listener: Listener) {
    val shouldRegisterCallback = synchronized(lock) {
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
    val shouldUnregisterCallback = synchronized(lock) {
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
    suggestedDeviceState: SuggestedDeviceState,
    routingChangeInfo: RoutingChangeInfo,
  ) {
    if (!isCurrentSuggestion(suggestedDeviceState.suggestedDeviceInfo)) {
      Log.w(TAG, "Suggestion got changed, aborting connection.")
      return
    }
    localMediaManager.connectSuggestedDevice(suggestedDeviceState, routingChangeInfo)
  }

  private fun eagerlyUpdateState() {
    synchronized(lock) {
      mediaDevices = localMediaManager.mediaDevices
      suggestions = localMediaManager.suggestions
      updateSuggestedDeviceStateLocked()
    }
  }

  @GuardedBy("lock")
  private fun updateSuggestedDeviceStateLocked(): Boolean {
    var newSuggestedDeviceState: SuggestedDeviceState? = null
    val previousState = suggestedDeviceState
    val topSuggestion = suggestions.firstOrNull()
    if (topSuggestion != null) {
      val matchedDevice = getDeviceById(mediaDevices, topSuggestion.routeId)
      if (matchedDevice != null) {
        newSuggestedDeviceState = SuggestedDeviceState(topSuggestion, matchedDevice.state)
      }
      if (newSuggestedDeviceState == null) {
        if (previousState != null
          && (topSuggestion.routeId == previousState.suggestedDeviceInfo.routeId)) {
          return false
        }
        newSuggestedDeviceState = SuggestedDeviceState(topSuggestion)
      }
    }

    if (newSuggestedDeviceState != null && isSuggestedDeviceSelected(newSuggestedDeviceState)) {
      newSuggestedDeviceState = null
    }
    if (previousState != newSuggestedDeviceState) {
      synchronized(lock) { suggestedDeviceState = newSuggestedDeviceState }
      return true
    }
    return false
  }

  private fun isSuggestedDeviceSelected(newSuggestedDeviceState: SuggestedDeviceState): Boolean {
    synchronized(lock) {
      return mediaDevices.any { device ->
        device.isSelected() && device.getId() == newSuggestedDeviceState.suggestedDeviceInfo.routeId
      }
    }
  }

  private fun getDeviceById(mediaDevices: List<MediaDevice>, routeId: String): MediaDevice? =
    mediaDevices.find { it.id == routeId }

  private fun isCurrentSuggestion(suggestedDeviceInfo: SuggestedDeviceInfo) =
    synchronized(lock) {
      suggestedDeviceState?.suggestedDeviceInfo?.routeId == suggestedDeviceInfo.routeId
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
