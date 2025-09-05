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

import android.content.Context
import android.media.MediaRoute2Info
import android.media.MediaRoute2Info.TYPE_REMOTE_SPEAKER
import android.media.RoutingChangeInfo
import android.media.SuggestedDeviceInfo
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
class SuggestedDeviceConnectionManagerTest {
    private val callback = mock<ConnectionFinishedCallback>()
    private var localMediaManager: LocalMediaManager = mock<LocalMediaManager>()
    private val routeInfo1 =
        mock<MediaRoute2Info> {
            on { name } doReturn TEST_DEVICE_NAME_1
            on { id } doReturn TEST_DEVICE_ID_1
        }
    private val routeInfo2 =
        mock<MediaRoute2Info> {
            on { name } doReturn TEST_DEVICE_NAME_2
            on { id } doReturn TEST_DEVICE_ID_2
        }
    private val suggestedDeviceInfo1 =
        SuggestedDeviceInfo.Builder(TEST_DEVICE_NAME_1, TEST_DEVICE_ID_1, TYPE_REMOTE_SPEAKER)
            .build()
    private val suggestedDeviceInfo2 =
        SuggestedDeviceInfo.Builder(TEST_DEVICE_NAME_2, TEST_DEVICE_ID_2, TYPE_REMOTE_SPEAKER)
            .build()
    private lateinit var mediaDevice1: MediaDevice
    private lateinit var mediaDevice2: MediaDevice
    private lateinit var suggestedDeviceConnectionManager: SuggestedDeviceConnectionManager

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()

        mediaDevice1 =
            InfoMediaDevice(
                context,
                routeInfo1,
                /* dynamicRouteAttributes= */ null,
                /* rlpItem= */ null,
            )

        mediaDevice2 =
            InfoMediaDevice(
                context,
                routeInfo2,
                /* dynamicRouteAttributes= */ null,
                /* rlpItem= */ null,
            )

        suggestedDeviceConnectionManager =
            SuggestedDeviceConnectionManager(localMediaManager, context.mainThreadHandler)
        suggestedDeviceConnectionManager.setConnectionFinishedCallback(callback)
    }

    @Test
    fun connectSuggestedDevice_deviceIsDiscovered_immediatelyConnects() {
        val suggestedDeviceState = SuggestedDeviceState(suggestedDeviceInfo1)
        val mediaDevices = listOf(mediaDevice1)
        localMediaManager.stub { on { getMediaDevices() } doReturn mediaDevices }
        suggestedDeviceConnectionManager.connectSuggestedDevice(
            suggestedDeviceState,
            ROUTING_CHANGE_INFO,
        )

        verify(localMediaManager).connectDevice(mediaDevice1, ROUTING_CHANGE_INFO)
        verify(localMediaManager, never()).startScan()
    }

    @Test
    fun connectSuggestedDevice_deviceIsNotDiscovered_scanStarted() {
        val suggestedDeviceState = SuggestedDeviceState(suggestedDeviceInfo2)
        val mediaDevices = listOf(mediaDevice1)
        localMediaManager.stub { on { getMediaDevices() } doReturn mediaDevices }
        suggestedDeviceConnectionManager.connectSuggestedDevice(
            suggestedDeviceState,
            ROUTING_CHANGE_INFO,
        )

        verify(localMediaManager).startScan()
        verify(localMediaManager, never()).connectDevice(mediaDevice1, ROUTING_CHANGE_INFO)
    }

    @Test
    fun connectSuggestedDevice_deviceDiscoveredAfter_connects() {
        val suggestedDeviceState = SuggestedDeviceState(suggestedDeviceInfo1)
        val mediaDevices = mutableListOf(mediaDevice2)
        localMediaManager.stub { on { getMediaDevices() } doReturn mediaDevices }
        suggestedDeviceConnectionManager.connectSuggestedDevice(
            suggestedDeviceState,
            ROUTING_CHANGE_INFO,
        )
        mediaDevices.add(mediaDevice1)
        captureDeviceCallback().onDeviceListUpdate(mediaDevices)

        verify(localMediaManager).startScan()
        verify(localMediaManager).connectDevice(mediaDevice1, ROUTING_CHANGE_INFO)
    }

    @Test
    fun connectSuggestedDevice_handlerTimesOut_completesConnectionAttempt() {
        val suggestedDeviceState = SuggestedDeviceState(suggestedDeviceInfo1)
        val mediaDevices = mutableListOf(mediaDevice2)
        localMediaManager.stub { on { getMediaDevices() } doReturn mediaDevices }
        suggestedDeviceConnectionManager.connectSuggestedDevice(
            suggestedDeviceState,
            ROUTING_CHANGE_INFO,
        )
        mediaDevices.add(mediaDevice1)
        captureDeviceCallback().onDeviceListUpdate(mediaDevices)

        verify(localMediaManager).connectDevice(mediaDevice1, ROUTING_CHANGE_INFO)

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        verify(callback).invoke(suggestedDeviceState, false)
    }

    @Test
    fun connectSuggestedDevice_connectionSuccess_completesConnectionAttempt() {
        val suggestedDeviceState = SuggestedDeviceState(suggestedDeviceInfo1)
        val mediaDevices = mutableListOf(mediaDevice2)
        localMediaManager.stub { on { getMediaDevices() } doReturn mediaDevices }
        suggestedDeviceConnectionManager.connectSuggestedDevice(
            suggestedDeviceState,
            ROUTING_CHANGE_INFO,
        )
        mediaDevices.add(mediaDevice1)
        val deviceCallback = captureDeviceCallback()
        deviceCallback.onDeviceListUpdate(mediaDevices)

        verify(localMediaManager).connectDevice(mediaDevice1, ROUTING_CHANGE_INFO)

        deviceCallback.onSelectedDeviceStateChanged(
            mediaDevice1,
            LocalMediaManager.MediaDeviceState.STATE_CONNECTED,
        )
        verify(callback).invoke(suggestedDeviceState, true)
    }

    private fun captureDeviceCallback(): LocalMediaManager.DeviceCallback {
        val callbackCaptor = argumentCaptor<LocalMediaManager.DeviceCallback>()
        verify(localMediaManager).registerCallback(callbackCaptor.capture())
        return callbackCaptor.firstValue
    }

    companion object {
        private const val TEST_DEVICE_NAME_1 = "device_name_1"
        private const val TEST_DEVICE_NAME_2 = "device_name_2"
        private const val TEST_DEVICE_ID_1 = "device_id_1"
        private const val TEST_DEVICE_ID_2 = "device_id_2"
        private val ROUTING_CHANGE_INFO =
            RoutingChangeInfo(
                RoutingChangeInfo.ENTRY_POINT_SYSTEM_MEDIA_CONTROLS,
                /* isSuggested= */ true,
            )
    }
}
