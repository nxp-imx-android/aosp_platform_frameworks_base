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

import android.media.MediaRoute2Info.TYPE_REMOTE_SPEAKER
import android.media.RoutingChangeInfo
import android.media.RoutingChangeInfo.ENTRY_POINT_SYSTEM_MEDIA_CONTROLS
import android.media.SuggestedDeviceInfo
import com.android.settingslib.media.LocalMediaManager.MediaDeviceState.STATE_CONNECTED
import com.android.settingslib.media.LocalMediaManager.MediaDeviceState.STATE_CONNECTING
import com.android.settingslib.media.LocalMediaManager.MediaDeviceState.STATE_CONNECTING_FAILED
import com.android.settingslib.media.LocalMediaManager.MediaDeviceState.STATE_DISCONNECTED
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner

private const val ROUTE_ID_1 = "ROUTE_ID_1"
private const val ROUTE_ID_2 = "ROUTE_ID_2"
private const val ROUTE_1_NAME = "ROUTE_1_NAME"
private const val ROUTE_2_NAME = "ROUTE_2_NAME"

@RunWith(RobolectricTestRunner::class)
class SuggestedDeviceManagerTest {

  private var localMediaManager: LocalMediaManager = mock<LocalMediaManager>()
  private var listener = mock<SuggestedDeviceManager.Listener>()
  private var listener2 = mock<SuggestedDeviceManager.Listener>()
  private lateinit var mSuggestedDeviceManager: SuggestedDeviceManager

  private val mediaDevice1 =
    mock<MediaDevice> {
      on { id } doReturn ROUTE_ID_1
      on { state } doReturn STATE_DISCONNECTED
      on { isSelected } doReturn false
    }

  private val mediaDevice2 =
    mock<MediaDevice> {
      on { id } doReturn ROUTE_ID_2
      on { state } doReturn STATE_DISCONNECTED
      on { isSelected } doReturn false
    }

  private val suggestedDeviceInfo1 =
    SuggestedDeviceInfo.Builder(ROUTE_1_NAME, ROUTE_ID_1, TYPE_REMOTE_SPEAKER).build()

  private val suggestedDeviceInfo2 =
    SuggestedDeviceInfo.Builder(ROUTE_2_NAME, ROUTE_ID_2, TYPE_REMOTE_SPEAKER).build()

  private val routingChangeInfo =
    RoutingChangeInfo(ENTRY_POINT_SYSTEM_MEDIA_CONTROLS, /* isSuggested= */ true)

  @Before
  fun setUp() {
    mSuggestedDeviceManager = SuggestedDeviceManager(localMediaManager)
  }

  @Test
  fun addListener_firstListener_registersCallback() {
    // Initially no listeners
    mSuggestedDeviceManager.addListener(listener)

    // Verify that the callback is registered with LocalMediaManager
    verify(localMediaManager).registerCallback(any())
    verify(localMediaManager).mediaDevices
    verify(localMediaManager).suggestions
    verifyNoMoreInteractions(localMediaManager)
  }

  @Test
  fun addListener_multipleListeners_registersCallbackOnce() {
    mSuggestedDeviceManager.addListener(listener)

    verify(localMediaManager).registerCallback(any())

    mSuggestedDeviceManager.addListener(listener2)

    verify(localMediaManager).registerCallback(any())
  }

  @Test
  fun removeListener_lastListener_unregistersCallback() {
    mSuggestedDeviceManager.addListener(listener)
    mSuggestedDeviceManager.addListener(listener2)
    mSuggestedDeviceManager.removeListener(listener)

    verify(localMediaManager, never()).unregisterCallback(any())

    mSuggestedDeviceManager.removeListener(listener2)

    verify(localMediaManager).unregisterCallback(any())
  }

  @Test
  fun requestDeviceSuggestion_callsLocalMediaManager() {
    mSuggestedDeviceManager.requestDeviceSuggestion()

    verify(localMediaManager).requestDeviceSuggestion()
  }

  @Test
  fun getSuggestedDevice_beforeListenersSet_callsLocalMediaManager() {
    localMediaManager.stub {
      on { mediaDevices } doReturn listOf(mediaDevice1)
      on { suggestions } doReturn listOf(suggestedDeviceInfo1)
    }

    assertThat(mSuggestedDeviceManager.getSuggestedDevice())
      .isEqualTo(SuggestedDeviceState(suggestedDeviceInfo1, mediaDevice1.state))
    verify(localMediaManager).mediaDevices
    verify(localMediaManager).suggestions
  }

  @Test
  fun getSuggestedDevice_addListener_callsLocalMediaManager() {
    localMediaManager.stub {
      on { mediaDevices } doReturn listOf(mediaDevice1)
      on { suggestions } doReturn listOf(suggestedDeviceInfo1)
    }

    mSuggestedDeviceManager.addListener(listener)

    verify(localMediaManager).mediaDevices
    verify(localMediaManager).suggestions

    assertThat(mSuggestedDeviceManager.getSuggestedDevice())
      .isEqualTo(SuggestedDeviceState(suggestedDeviceInfo1, mediaDevice1.state))
    // No additional calls are made after listeners are set
    verify(localMediaManager).mediaDevices
    verify(localMediaManager).suggestions
  }

  @Test
  fun onDeviceSuggestionsUpdated_noMatchedDevice_dispatchedDisconnectedState() {
    // No devices initially in localMediaManager
    val deviceCallback = addListenerAndCaptureCallback(listener)
    deviceCallback.onDeviceSuggestionsUpdated(listOf(suggestedDeviceInfo1))

    val expectedState =
      SuggestedDeviceState(
        suggestedDeviceInfo = suggestedDeviceInfo1,
        connectionState = STATE_DISCONNECTED,
      )
    verify(listener).onSuggestedDeviceStateUpdated(expectedState)
  }

  @Test
  fun onDeviceSuggestionsUpdated_matchedDeviceNotSelected_dispatchedMediaDeviceState() {
    mediaDevice1.stub { on { state } doReturn STATE_CONNECTING }

    // Set up initial devices
    val deviceCallback = addListenerAndCaptureCallback(listener)
    deviceCallback.onDeviceListUpdate(listOf(mediaDevice1))
    deviceCallback.onDeviceSuggestionsUpdated(listOf(suggestedDeviceInfo1))

    val expectedState =
      SuggestedDeviceState(
        suggestedDeviceInfo = suggestedDeviceInfo1,
        connectionState = STATE_CONNECTING,
      )
    verify(listener).onSuggestedDeviceStateUpdated(expectedState)
  }

  @Test
  fun onDeviceSuggestionsUpdated_matchedDeviceSelected_dispatchesNull() {
    val deviceCallback = addListenerAndCaptureCallback(listener)
    deviceCallback.onDeviceListUpdate(listOf(mediaDevice1))
    deviceCallback.onDeviceSuggestionsUpdated(listOf(suggestedDeviceInfo1))

    verify(listener)
      .onSuggestedDeviceStateUpdated(
        SuggestedDeviceState(
          suggestedDeviceInfo = suggestedDeviceInfo1,
          connectionState = STATE_DISCONNECTED,
        )
      )

    mediaDevice1.stub {
      on { state } doReturn STATE_CONNECTED
      on { isSelected } doReturn true
    }
    deviceCallback.onDeviceListUpdate(listOf(mediaDevice1))
    verify(listener).onSuggestedDeviceStateUpdated(null)
  }

  @Test
  fun onDeviceSuggestionsUpdated_newSuggestionDifferent_dispatchesUpdatedState() {
    val deviceCallback = addListenerAndCaptureCallback(listener)
    deviceCallback.onDeviceListUpdate(listOf(mediaDevice1, mediaDevice2))

    // First suggestion update
    deviceCallback.onDeviceSuggestionsUpdated(listOf(suggestedDeviceInfo1))
    val expectedState1 = SuggestedDeviceState(suggestedDeviceInfo1, mediaDevice1.state)
    verify(listener).onSuggestedDeviceStateUpdated(expectedState1)

    // Second suggestion update with a different suggestion
    deviceCallback.onDeviceSuggestionsUpdated(listOf(suggestedDeviceInfo2))
    val expectedState2 = SuggestedDeviceState(suggestedDeviceInfo2, mediaDevice2.state)
    verify(listener).onSuggestedDeviceStateUpdated(expectedState2)
  }

  @Test
  fun onDeviceSuggestionsUpdated_suggestionCleared_dispatchesNull() {
    val deviceCallback = addListenerAndCaptureCallback(listener)
    deviceCallback.onDeviceListUpdate(listOf(mediaDevice1, mediaDevice2))
    deviceCallback.onDeviceSuggestionsUpdated(listOf(suggestedDeviceInfo1))

    // First suggestion update
    deviceCallback.onDeviceSuggestionsUpdated(listOf(suggestedDeviceInfo1))
    val expectedState1 = SuggestedDeviceState(suggestedDeviceInfo1, mediaDevice1.state)
    verify(listener).onSuggestedDeviceStateUpdated(expectedState1)

    // Second suggestion update with a different suggestion
    deviceCallback.onDeviceSuggestionsUpdated(listOf())
    verify(listener).onSuggestedDeviceStateUpdated(null)
  }

  @Test
  fun onDeviceListUpdate_fromNoMatchToMatchedDevice_dispatchesUpdatedState() {
    val deviceCallback = addListenerAndCaptureCallback(listener)

    // Initial suggestion
    deviceCallback.onDeviceSuggestionsUpdated(listOf(suggestedDeviceInfo1))
    val expectedState1 = SuggestedDeviceState(suggestedDeviceInfo1, STATE_DISCONNECTED)
    verify(listener).onSuggestedDeviceStateUpdated(expectedState1)

    mediaDevice1.stub { on { state } doReturn STATE_CONNECTING }

    // Device list update that now matches the suggestion
    deviceCallback.onDeviceListUpdate(listOf(mediaDevice1))
    val expectedState2 = SuggestedDeviceState(suggestedDeviceInfo1, STATE_CONNECTING)
    verify(listener).onSuggestedDeviceStateUpdated(expectedState2)
  }

  @Test
  fun onConnectSuggestedDeviceFinished_success_dispatchesConnectedState() {
    val deviceCallback = addListenerAndCaptureCallback(listener)

    deviceCallback.onDeviceSuggestionsUpdated(listOf(suggestedDeviceInfo1))
    val initialSuggestedDeviceState = SuggestedDeviceState(suggestedDeviceInfo1, STATE_DISCONNECTED)
    verify(listener).onSuggestedDeviceStateUpdated(initialSuggestedDeviceState)

    deviceCallback.onConnectSuggestedDeviceFinished(initialSuggestedDeviceState, true)
    val connectedState = initialSuggestedDeviceState.copy(connectionState = STATE_CONNECTED)
    verify(listener).onSuggestedDeviceStateUpdated(connectedState)
  }

  @Test
  fun onConnectSuggestedDeviceFinished_failure_dispatchesConnectingFailedState() {
    val deviceCallback = addListenerAndCaptureCallback(listener)

    deviceCallback.onDeviceSuggestionsUpdated(listOf(suggestedDeviceInfo1))
    val initialSuggestedDeviceState = SuggestedDeviceState(suggestedDeviceInfo1, STATE_DISCONNECTED)
    verify(listener).onSuggestedDeviceStateUpdated(initialSuggestedDeviceState)

    deviceCallback.onConnectSuggestedDeviceFinished(initialSuggestedDeviceState, false)
    val failedState = initialSuggestedDeviceState.copy(connectionState = STATE_CONNECTING_FAILED)
    verify(listener).onSuggestedDeviceStateUpdated(failedState)
  }

  @Test
  fun onConnectionAttemptedForSuggestion_fromDisconnected_changesStateToConnecting() {
    val deviceCallback = addListenerAndCaptureCallback(listener)

    // Simulate a failed connection first
    deviceCallback.onDeviceSuggestionsUpdated(listOf(suggestedDeviceInfo1))
    val initialSuggestedDeviceState = SuggestedDeviceState(suggestedDeviceInfo1, STATE_DISCONNECTED)
    deviceCallback.onConnectionAttemptedForSuggestion(initialSuggestedDeviceState)
    val failedState = initialSuggestedDeviceState.copy(connectionState = STATE_CONNECTING)
    verify(listener).onSuggestedDeviceStateUpdated(failedState)
  }

  @Test
  fun connectSuggestedDevice_stateMatches_callsLocalMediaManagerConnect() {
    val deviceCallback = addListenerAndCaptureCallback(listener)
    deviceCallback.onDeviceSuggestionsUpdated(listOf(suggestedDeviceInfo1))

    val currentSuggestedState = SuggestedDeviceState(suggestedDeviceInfo1, STATE_DISCONNECTED)
    mSuggestedDeviceManager.connectSuggestedDevice(currentSuggestedState, routingChangeInfo)

    verify(localMediaManager).connectSuggestedDevice(currentSuggestedState, routingChangeInfo)
  }

  @Test
  fun connectSuggestedDevice_stateDoesNotMatch_doesNotCallLocalMediaManagerConnect() {
    val deviceCallback = addListenerAndCaptureCallback(listener)
    deviceCallback.onDeviceSuggestionsUpdated(listOf(suggestedDeviceInfo1))
    verify(listener)
      .onSuggestedDeviceStateUpdated(
        SuggestedDeviceState(
          suggestedDeviceInfo = suggestedDeviceInfo1,
          connectionState = STATE_DISCONNECTED,
        )
      )

    // Create a different suggested state than what's currently held by the repository
    val differentSuggestedState = SuggestedDeviceState(suggestedDeviceInfo2, STATE_DISCONNECTED)
    mSuggestedDeviceManager.connectSuggestedDevice(differentSuggestedState, routingChangeInfo)

    verify(localMediaManager, never()).connectSuggestedDevice(any(), any())
    verifyNoMoreInteractions(listener)
  }

  /**
   * Helper to get the internal LocalMediaManager.DeviceCallback instance. This relies on the fact
   * that the callback is registered when the first listener is added.
   */
  private fun addListenerAndCaptureCallback(
    listener: SuggestedDeviceManager.Listener
  ): LocalMediaManager.DeviceCallback {
    val callbackCaptor = argumentCaptor<LocalMediaManager.DeviceCallback>()
    mSuggestedDeviceManager.addListener(listener)
    verify(localMediaManager).registerCallback(callbackCaptor.capture())
    return callbackCaptor.firstValue
  }
}
