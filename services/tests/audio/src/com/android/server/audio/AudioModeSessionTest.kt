/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.server.audio

import android.content.AttributionSource
import android.media.AudioDeviceAttributes.ROLE_OUTPUT
import android.media.AudioDeviceInfo
import android.media.AudioDevicePort
import android.media.AudioSystem
import android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO
import android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
import android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
import android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET
import android.media.AudioManager
import android.media.AudioManager.MODE_IN_COMMUNICATION
import android.media.AudioManager.MODE_NORMAL
import android.media.AudioManager.MODE_RINGTONE
import android.media.AudioModeSession.ROUTING_RESULT_PREEMPTED
import android.media.AudioModeSession.ROUTING_RESULT_SUCCESSFUL
import android.media.audio.AudioModeSessionRequest
import android.media.audio.DeviceIdentity
import android.media.audio.IAudioModeSession
import android.media.audio.IAudioModeSessionCallback
import android.os.IBinder
import android.platform.test.annotations.Presubmit
import android.platform.test.ravenwood.RavenwoodRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@Presubmit
@RunWith(AndroidJUnit4::class)
class AudioModeSessionTest {

    @get:Rule val mRavenwood = RavenwoodRule()

    // Standard mock devices
    private val mSpeaker: AudioDeviceInfo = AudioDeviceInfo(
        AudioDevicePort.createForTesting(AudioSystem.DEVICE_OUT_SPEAKER, "", "")
    )
    private val mEarpiece: AudioDeviceInfo = AudioDeviceInfo(
        AudioDevicePort.createForTesting(AudioSystem.DEVICE_OUT_EARPIECE, "", "")
    )
    private val mWiredHeadset: AudioDeviceInfo = AudioDeviceInfo(
        AudioDevicePort.createForTesting(AudioSystem.DEVICE_OUT_WIRED_HEADSET, "", "headset_address")
    )
    private val mBtSco: AudioDeviceInfo = AudioDeviceInfo(
        AudioDevicePort.createForTesting(AudioSystem.DEVICE_OUT_BLUETOOTH_SCO, "", "sco_address")
    )

    private val mAttributionSource = AttributionSource(1000, 1234, "com.test", null)
    private val mAudioService: AudioService = mock {
        on { requestAudioFocusForModeSession(any(), any(), any(), any(), any()) } doReturn
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }
    private val mCallbackBinder: IBinder = mock()
    private val mCallback: IAudioModeSessionCallback = mock {
        on { asBinder() } doReturn mCallbackBinder
    }
    private val mDeviceBroker: AudioDeviceBroker = mock()

    private fun createDefaultRequest() =
        AudioModeSessionRequest().apply {
            attributionSource = mAttributionSource.asState()
            mode = MODE_IN_COMMUNICATION
            isDisplayActiveUseCase = true
            noFocusModes = intArrayOf()
        }

    private fun createSession(
        request: AudioModeSessionRequest = createDefaultRequest()
    ): AudioModeSession {
        return AudioModeSession(mAudioService, mDeviceBroker, request, mCallback, { it.run() })
    }

    @Test
    fun createSession_initializesState() {
        createSession()
        val inOrderService = inOrder(mAudioService)
        inOrderService.verify(mAudioService)
            .requestAudioFocusForModeSession(
                eq(mAttributionSource),
                eq(mCallbackBinder),
                any(),
                eq(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT),
                any(),
            )
        inOrderService.verify(mAudioService).setMode(eq(MODE_IN_COMMUNICATION), any(), any())
    }

    @Test
    fun createSession_noFocusMode_neverGainsFocus() {
        val request =
            createDefaultRequest().apply { noFocusModes = intArrayOf(MODE_IN_COMMUNICATION) }

        createSession(request)

        verify(mAudioService, never())
            .requestAudioFocusForModeSession(any(), any(), any(), any(), any())
    }


    @Test
    fun close_cleansUp() {
        val session = createSession()
        session.close()
        verify(mDeviceBroker).removeAudioModeSession(session)
        verify(mCallback).onClosed()
        val inOrderService = inOrder(mAudioService)
        inOrderService.verify(mAudioService).setMode(eq(MODE_NORMAL), any(), any())
        inOrderService.verify(mAudioService)
            .abandonAudioFocusForModeSession(
                eq(mAttributionSource),
                eq(mCallbackBinder),
                any(),
                any(),
            )
    }

    @Test
    fun setMode_updatesService() {
        val session = createSession()
        session.setMode(MODE_RINGTONE)
        verify(mAudioService).setMode(eq(MODE_RINGTONE), any(), any())
    }

    @Test
    fun onAvailableDevicesChanged_updatesCallback() {
        val session = createSession()
        val devices = listOf(mSpeaker)
        session.onAvailableDevicesChanged(devices)

        val captor = org.mockito.kotlin.argumentCaptor<List<IAudioModeSession.Route>>()
        verify(mCallback).onAvailableRoutesChanged(captor.capture())
        val routes = captor.firstValue
        assert(routes.isNotEmpty())
        assert(matchesRoute(mSpeaker, routes[0]))
    }

    @Test
    fun setRequestedRoute_success_async() {
        val session = createSession()
        val devices = listOf(mSpeaker)
        session.onAvailableDevicesChanged(devices)

        val route =
            IAudioModeSession.Route().apply {
                output =
                    DeviceIdentity().apply {
                        type = mSpeaker.type
                        role = ROLE_OUTPUT
                        address = mSpeaker.address
                    }
            }
        val requestId = session.setRequestedRoute(route)
        assert(requestId != 0)

        verify(mDeviceBroker)
            .setCommunicationDevice(
                any(),
                any(),
                argThat { device -> matchesRoute(device, route) },
                eq(true),
                any(),
            )

        session.onCommunicationDeviceChanged(mSpeaker)

        verify(mCallback).onRoutingResult(eq(requestId), eq(route), eq(ROUTING_RESULT_SUCCESSFUL))
    }

    @Test
    fun setRequestedRoute_preempts_pending() {
        val session = createSession()
        val devices = listOf(mSpeaker, mWiredHeadset)
        session.onAvailableDevicesChanged(devices)

        val speakerRoute =
            IAudioModeSession.Route().apply {
                output =
                    DeviceIdentity().apply {
                        type = mSpeaker.type
                        role = ROLE_OUTPUT
                        address = mSpeaker.address
                    }
            }
        val speakerRequestId = session.setRequestedRoute(speakerRoute)
        verify(mDeviceBroker)
            .setCommunicationDevice(
                any(),
                any(),
                argThat { device -> matchesRoute(device, speakerRoute) },
                eq(true),
                any(),
            )

        clearInvocations(mDeviceBroker)
        val headsetRoute =
            IAudioModeSession.Route().apply {
                output =
                    DeviceIdentity().apply {
                        type = mWiredHeadset.type
                        role = ROLE_OUTPUT
                        address = mWiredHeadset.address
                    }
            }
        val headsetRequestId = session.setRequestedRoute(headsetRoute)

        verify(mCallback)
            .onRoutingResult(eq(speakerRequestId), eq(speakerRoute), eq(ROUTING_RESULT_PREEMPTED))

        verify(mDeviceBroker)
            .setCommunicationDevice(
                any(),
                any(),
                argThat { device -> matchesRoute(device, headsetRoute) },
                eq(true),
                any(),
            )

        session.onCommunicationDeviceChanged(mWiredHeadset)
        verify(mCallback)
            .onRoutingResult(eq(headsetRequestId), eq(headsetRoute), eq(ROUTING_RESULT_SUCCESSFUL))
    }

    @Test
    fun preemptClientRoute_notifies_and_updates() {
        val session = createSession()
        val devices = listOf(mSpeaker)
        session.onAvailableDevicesChanged(devices)

        val speakerRoute =
            IAudioModeSession.Route().apply {
                output =
                    DeviceIdentity().apply {
                        type = mSpeaker.type
                        role = ROLE_OUTPUT
                        address = mSpeaker.address
                    }
            }
        session.preemptClientRoute(speakerRoute)

        verify(mCallback)
            .onExternalRequestedRouteChanged(
                argThat { route -> route.output.type == TYPE_BUILTIN_SPEAKER },
                any(),
            )

        verify(mDeviceBroker)
            .setCommunicationDevice(
                any(),
                any(),
                argThat { device -> matchesRoute(device, speakerRoute) },
                eq(true),
                any(),
            )
    }

    @Test
    fun onAvailableDevicesChanged_removesUnavailableRequestedRoute() {
        val session = createSession()
        // Start with Speaker and Headset available
        val devices = listOf(mSpeaker, mWiredHeadset)
        session.onAvailableDevicesChanged(devices)

        // Request Headset
        val route =
            IAudioModeSession.Route().apply {
                output =
                    DeviceIdentity().apply {
                        type = mWiredHeadset.type
                        role = ROLE_OUTPUT
                        address = mWiredHeadset.address
                    }
            }
        val requestId = session.setRequestedRoute(route)
        assert(requestId != 0)

        verify(mDeviceBroker)
            .setCommunicationDevice(
                any(),
                any(),
                argThat { device -> matchesRoute(device, route) },
                eq(true),
                any(),
            )

        session.onCommunicationDeviceChanged(mWiredHeadset)
        verify(mCallback).onRoutingResult(eq(requestId), eq(route), eq(ROUTING_RESULT_SUCCESSFUL))

        clearInvocations(mDeviceBroker)

        // Headset becomes unavailable (only Speaker remains)
        val newDevices = listOf(mSpeaker)
        session.onAvailableDevicesChanged(newDevices)

        verify(mCallback).onExternalRequestedRouteChanged(eq(null), any())
        // Verify that the requested route is cleared (set to null)
        verify(mDeviceBroker).setCommunicationDevice(any(), any(), eq(null), eq(true), any())
    }

    @Test
    fun setClientPaused_true_pauses() {
        val session = createSession()
        val inOrderService = inOrder(mAudioService)
        session.setClientPaused(true)
        inOrderService.verify(mAudioService).setMode(eq(MODE_NORMAL), any(), any())
        verify(mDeviceBroker).setCommunicationDevice(any(), any(), eq(null), eq(true), any())
        inOrderService.verify(mAudioService)
            .abandonAudioFocusForModeSession(
                eq(mAttributionSource),
                eq(mCallbackBinder),
                any(),
                any(),
            )
        verify(mCallback).onPaused()
    }

    @Test
    fun setClientPaused_false_resumes() {
        val session = createSession()
        val devices = listOf(mSpeaker)
        session.onAvailableDevicesChanged(devices)

        val route =
            IAudioModeSession.Route().apply {
                output =
                    DeviceIdentity().apply {
                        type = mSpeaker.type
                        role = ROLE_OUTPUT
                        address = mSpeaker.address
                    }
            }
        session.setRequestedRoute(route)
        verify(mDeviceBroker)
            .setCommunicationDevice(
                any(),
                any(),
                argThat { device -> matchesRoute(device, route) },
                eq(true),
                any(),
            )
        clearInvocations(mDeviceBroker)
        val inOrderService = inOrder(mAudioService)

        session.setClientPaused(true)
        verify(mDeviceBroker).setCommunicationDevice(any(), any(), eq(null), eq(true), any())
        clearInvocations(mDeviceBroker)
        clearInvocations(mAudioService)

        session.setClientPaused(false)
        inOrderService.verify(mAudioService)
            .requestAudioFocusForModeSession(
                eq(mAttributionSource),
                eq(mCallbackBinder),
                any(),
                eq(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT),
                any(),
            )
        inOrderService.verify(mAudioService)
            .setMode(eq(MODE_IN_COMMUNICATION), any(), eq(mAttributionSource.packageName))
        verify(mDeviceBroker)
            .setCommunicationDevice(
                any(),
                any(),
                eq(mSpeaker),
                eq(true),
                any(),
            )
        verify(mCallback).onResumed(any())
    }

    @Test
    fun serverPause_releasesControl() {
        val session = createSession()
        val inOrderService = inOrder(mAudioService)
        // Pause from server side (e.g. higher priority session active)
        session.pause()

        inOrderService.verify(mAudioService).setMode(eq(MODE_NORMAL), any(), any())
        verify(mDeviceBroker).setCommunicationDevice(any(), any(), eq(null), eq(true), any())
        inOrderService.verify(mAudioService)
            .abandonAudioFocusForModeSession(
                eq(mAttributionSource),
                eq(mCallbackBinder),
                any(),
                any(),
            )
        verify(mCallback).onPaused()
    }

    @Test
    fun serverResume_restoresControl() {
        val session = createSession()
        // Setup initial state
        val devices = listOf(mSpeaker)
        session.onAvailableDevicesChanged(devices)

        val route =
            IAudioModeSession.Route().apply {
                output =
                    DeviceIdentity().apply {
                        type = mSpeaker.type
                        role = ROLE_OUTPUT
                        address = mSpeaker.address
                    }
            }
        session.setRequestedRoute(route)
        val inOrderService = inOrder(mAudioService)

        // Server pause
        session.pause()
        verify(mDeviceBroker).setCommunicationDevice(any(), any(), eq(null), eq(true), any())
        clearInvocations(mDeviceBroker, mAudioService)

        // Server resume
        session.resume()

        inOrderService.verify(mAudioService)
            .requestAudioFocusForModeSession(
                eq(mAttributionSource),
                eq(mCallbackBinder),
                any(),
                eq(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT),
                any(),
            )
        inOrderService.verify(mAudioService)
            .setMode(eq(MODE_IN_COMMUNICATION), any(), eq(mAttributionSource.packageName))
        verify(mDeviceBroker)
            .setCommunicationDevice(
                any(),
                any(),
                eq(mSpeaker),
                eq(true),
                any(),
            )
        verify(mCallback).onResumed(any())
    }

    @Test
    fun onCommunicationDeviceChanged_ignores_mismatch() {
        val session = createSession()
        val devices = listOf(mSpeaker, mWiredHeadset)
        session.onAvailableDevicesChanged(devices)

        val speakerRoute =
            IAudioModeSession.Route().apply {
                output =
                    DeviceIdentity().apply {
                        type = mSpeaker.type
                        role = ROLE_OUTPUT
                        address = mSpeaker.address
                    }
            }
        val speakerRequestId = session.setRequestedRoute(speakerRoute)

        // Report a different device change (e.g. system switched to headset unexpectedly or for
        // another reason)
        session.onCommunicationDeviceChanged(mWiredHeadset)

        verify(mCallback, never()).onRoutingResult(any(), any(), any())

        // Now report the correct device
        session.onCommunicationDeviceChanged(mSpeaker)
        verify(mCallback)
            .onRoutingResult(eq(speakerRequestId), eq(speakerRoute), eq(ROUTING_RESULT_SUCCESSFUL))
    }

    @Test
    fun setRequestedRoute_unavailable_doesNotSetDevice() {
        val session = createSession()
        // Available devices: Speaker
        val devices = listOf(mSpeaker)
        session.onAvailableDevicesChanged(devices)

        // Request: Wired Headset (Not available)
        val route =
            IAudioModeSession.Route().apply {
                output =
                    DeviceIdentity().apply {
                        type = mWiredHeadset.type
                        role = ROLE_OUTPUT
                        address = mWiredHeadset.address
                    }
            }
        val requestId = session.setRequestedRoute(route)
        assert(requestId != 0)

        // TODO
    }

    @Test
    fun setMode_focusMode_requestFocus() {
        // Use a session that starts in a NO_FOCUS mode
        val request =
            createDefaultRequest().apply {
                mode = MODE_NORMAL
                noFocusModes = intArrayOf(MODE_NORMAL)
            }
        val session = createSession(request)

        // Should NOT have requested focus initially
        verify(mAudioService, never())
            .requestAudioFocusForModeSession(any(), any(), any(), any(), any())

        // Switch to IN_COMMUNICATION (should acquire focus)
        session.setMode(MODE_IN_COMMUNICATION)
        verify(mAudioService)
            .requestAudioFocusForModeSession(
                eq(mAttributionSource),
                eq(mCallbackBinder),
                any(),
                eq(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT),
                any(),
            )
    }

    @Test
    fun setMode_noFocusMode_abandonsFocus() {
        // Start in IN_COMMUNICATION (default, gains focus)
        val request = createDefaultRequest().apply { noFocusModes = intArrayOf(MODE_NORMAL) }
        val session = createSession(request)
        verify(mAudioService)
            .requestAudioFocusForModeSession(
                eq(mAttributionSource),
                eq(mCallbackBinder),
                any(),
                eq(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT),
                any(),
            )

        clearInvocations(mAudioService)

        // Switch to NORMAL (configured as no-focus)
        session.setMode(MODE_NORMAL)
        verify(mAudioService)
            .abandonAudioFocusForModeSession(
                eq(mAttributionSource),
                eq(mCallbackBinder),
                any(),
                any(),
            )
    }

    private fun matchesRoute(device: AudioDeviceInfo?, route: IAudioModeSession.Route) =
        device != null && device.type == route.output.type && device.address == route.output.address
}
