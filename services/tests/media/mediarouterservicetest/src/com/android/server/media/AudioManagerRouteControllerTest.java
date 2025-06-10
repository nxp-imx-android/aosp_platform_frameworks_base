/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server.media;

import static android.Manifest.permission.MODIFY_AUDIO_ROUTING;

import static com.android.server.media.AudioRoutingUtils.ATTRIBUTES_MEDIA;
import static com.android.server.media.AudioRoutingUtils.getMediaAudioProductStrategy;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Instrumentation;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.res.Resources;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioDevicePort;
import android.media.AudioManager;
import android.media.AudioSystem;
import android.media.MediaRoute2Info;
import android.media.RoutingSessionInfo;
import android.media.audiopolicy.AudioProductStrategy;
import android.os.Looper;
import android.os.UserHandle;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RunWith(JUnit4.class)
public class AudioManagerRouteControllerTest {

    private static final String FAKE_ROUTE_NAME = "fake name";

    /**
     * The number of milliseconds to wait for an asynchronous operation before failing an associated
     * assertion.
     */
    private static final int ASYNC_CALL_TIMEOUTS_MS = 1000;

    private static final AudioDeviceInfo FAKE_AUDIO_DEVICE_INFO_BUILTIN_SPEAKER =
            createAudioDeviceInfo(
                    AudioSystem.DEVICE_OUT_SPEAKER, "name_builtin", /* address= */ "");
    private static final AudioDeviceInfo FAKE_AUDIO_DEVICE_INFO_WIRED_HEADSET =
            createAudioDeviceInfo(
                    AudioSystem.DEVICE_OUT_WIRED_HEADSET, "name_wired_hs", /* address= */ "");
    private static final AudioDeviceInfo FAKE_AUDIO_DEVICE_INFO_WIRED_HEADSET_WITH_ADDRESS =
            createAudioDeviceInfo(
                    AudioSystem.DEVICE_OUT_WIRED_HEADSET,
                    "name_wired_hs_with_address",
                    /* address= */ "card=1;device=0");
    private static final AudioDeviceInfo FAKE_AUDIO_DEVICE_INFO_BLUETOOTH_A2DP =
            createAudioDeviceInfo(
                    AudioSystem.DEVICE_OUT_BLUETOOTH_A2DP, "name_a2dp", /* address= */ "12:34:45");

    private static final AudioDeviceInfo FAKE_AUDIO_DEVICE_BUILTIN_EARPIECE =
            createAudioDeviceInfo(
                    AudioSystem.DEVICE_OUT_EARPIECE, /* name= */ "", /* address= */ "");

    private static final AudioDeviceInfo FAKE_AUDIO_DEVICE_NO_NAME =
            createAudioDeviceInfo(
                    AudioSystem.DEVICE_OUT_DGTL_DOCK_HEADSET,
                    /* name= */ "",
                    /* address= */ "");

    private Instrumentation mInstrumentation;
    private AudioDeviceInfo mSelectedAudioDeviceInfo;
    private Set<AudioDeviceInfo> mAvailableAudioDeviceInfos;
    @Mock private AudioManager mMockAudioManager;
    @Mock private DeviceRouteController.EventListener mEventListener;
    @Mock private BluetoothDeviceRoutesManager mMockBluetoothDeviceRoutesManager;
    @Mock private Context mMockContext;
    private Context mRealContext;
    private AudioManagerRouteController mControllerUnderTest;
    private AudioDeviceCallback mAudioDeviceCallback;
    private AudioProductStrategy mMediaAudioProductStrategy;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(MODIFY_AUDIO_ROUTING);
        Resources mockResources = Mockito.mock(Resources.class);
        when(mockResources.getText(anyInt())).thenReturn(FAKE_ROUTE_NAME);
        mRealContext = mInstrumentation.getContext();
        when(mMockContext.getResources()).thenReturn(mockResources);
        // The bluetooth stack needs the application info, but we cannot use a spy because the
        // concrete class is package private, so we just return the application info through the
        // mock.
        when(mMockContext.getApplicationInfo()).thenReturn(mRealContext.getApplicationInfo());
        // Needed to check if it is a TV device.
        when(mMockContext.getPackageManager()).thenReturn(mRealContext.getPackageManager());

        // Setup the initial state so that the route controller is created in a sensible state.
        mSelectedAudioDeviceInfo = FAKE_AUDIO_DEVICE_INFO_BUILTIN_SPEAKER;
        mAvailableAudioDeviceInfos = Set.of(FAKE_AUDIO_DEVICE_INFO_BUILTIN_SPEAKER);
        updateMockAudioManagerState();
        mMediaAudioProductStrategy = getMediaAudioProductStrategy();

        // Need call setUpControllerUnderTest before each test case.
    }

    @After
    public void tearDown() {
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
    }

    @Test
    public void getSelectedRoute_afterDevicesConnect_returnsRightSelectedRoute() {
        setUpControllerUnderTest(/* useMockBluetoothDeviceRoutesManager= */ false);
        assertThat(mControllerUnderTest.getSelectedRoutes().getFirst().getType())
                .isEqualTo(MediaRoute2Info.TYPE_BUILTIN_SPEAKER);

        addAvailableAudioDeviceInfo(
                /* newSelectedDevice= */ FAKE_AUDIO_DEVICE_INFO_BLUETOOTH_A2DP,
                /* newAvailableDevices...= */ FAKE_AUDIO_DEVICE_INFO_BLUETOOTH_A2DP);
        verify(mEventListener).onDeviceRouteChanged();
        assertThat(mControllerUnderTest.getSelectedRoutes().getFirst().getType())
                .isEqualTo(MediaRoute2Info.TYPE_BLUETOOTH_A2DP);

        addAvailableAudioDeviceInfo(
                /* newSelectedDevice= */ null, // Selected device doesn't change.
                /* newAvailableDevices...= */ FAKE_AUDIO_DEVICE_INFO_WIRED_HEADSET);
        assertThat(mControllerUnderTest.getSelectedRoutes().getFirst().getType())
                .isEqualTo(MediaRoute2Info.TYPE_BLUETOOTH_A2DP);
    }

    @Test
    public void getSelectedRoute_afterDeviceRemovals_returnsExpectedRoutes() {
        setUpControllerUnderTest(/* useMockBluetoothDeviceRoutesManager= */ false);
        addAvailableAudioDeviceInfo(
                /* newSelectedDevice= */ FAKE_AUDIO_DEVICE_INFO_WIRED_HEADSET,
                /* newAvailableDevices...= */ FAKE_AUDIO_DEVICE_INFO_BLUETOOTH_A2DP,
                FAKE_AUDIO_DEVICE_INFO_WIRED_HEADSET);
        verify(mEventListener).onDeviceRouteChanged();

        addAvailableAudioDeviceInfo(
                /* newSelectedDevice= */ FAKE_AUDIO_DEVICE_INFO_BLUETOOTH_A2DP,
                /* newAvailableDevices...= */ FAKE_AUDIO_DEVICE_INFO_BLUETOOTH_A2DP);
        verify(mEventListener, times(2)).onDeviceRouteChanged();
        assertThat(mControllerUnderTest.getSelectedRoutes().getFirst().getType())
                .isEqualTo(MediaRoute2Info.TYPE_BLUETOOTH_A2DP);

        removeAvailableAudioDeviceInfos(
                /* newSelectedDevice= */ null,
                /* devicesToRemove...= */ FAKE_AUDIO_DEVICE_INFO_WIRED_HEADSET);
        assertThat(mControllerUnderTest.getSelectedRoutes().getFirst().getType())
                .isEqualTo(MediaRoute2Info.TYPE_BLUETOOTH_A2DP);

        removeAvailableAudioDeviceInfos(
                /* newSelectedDevice= */ FAKE_AUDIO_DEVICE_INFO_BUILTIN_SPEAKER,
                /* devicesToRemove...= */ FAKE_AUDIO_DEVICE_INFO_WIRED_HEADSET);
        assertThat(mControllerUnderTest.getSelectedRoutes().getFirst().getType())
                .isEqualTo(MediaRoute2Info.TYPE_BUILTIN_SPEAKER);
    }

    @Test
    public void onAudioDevicesAdded_clearsAudioRoutingPoliciesCorrectly() {
        setUpControllerUnderTest(/* useMockBluetoothDeviceRoutesManager= */ false);
        clearInvocations(mMockAudioManager);
        addAvailableAudioDeviceInfo(
                /* newSelectedDevice= */ null, // Selected device doesn't change.
                /* newAvailableDevices...= */ FAKE_AUDIO_DEVICE_BUILTIN_EARPIECE);
        verifyNoMoreInteractions(mMockAudioManager);

        addAvailableAudioDeviceInfo(
                /* newSelectedDevice= */ FAKE_AUDIO_DEVICE_INFO_WIRED_HEADSET,
                /* newAvailableDevices...= */ FAKE_AUDIO_DEVICE_INFO_BLUETOOTH_A2DP);
        verify(mMockAudioManager).removePreferredDeviceForStrategy(mMediaAudioProductStrategy);
    }

    @Test
    public void getAvailableDevices_ignoresInvalidMediaOutputs() {
        setUpControllerUnderTest(/* useMockBluetoothDeviceRoutesManager= */ false);
        addAvailableAudioDeviceInfo(
                /* newSelectedDevice= */ null, // Selected device doesn't change.
                /* newAvailableDevices...= */ FAKE_AUDIO_DEVICE_BUILTIN_EARPIECE);
        verifyNoMoreInteractions(mEventListener);
        assertThat(
                        mControllerUnderTest.getAvailableRoutes().stream()
                                .map(MediaRoute2Info::getType)
                                .toList())
                .containsExactly(MediaRoute2Info.TYPE_BUILTIN_SPEAKER);
        assertThat(mControllerUnderTest.getSelectedRoutes().getFirst().getType())
                .isEqualTo(MediaRoute2Info.TYPE_BUILTIN_SPEAKER);
    }

    @Test
    public void transferTo_setsTheExpectedRoutingPolicy() {
        setUpControllerUnderTest(/* useMockBluetoothDeviceRoutesManager= */ false);
        addAvailableAudioDeviceInfo(
                /* newSelectedDevice= */ FAKE_AUDIO_DEVICE_INFO_WIRED_HEADSET,
                /* newAvailableDevices...= */ FAKE_AUDIO_DEVICE_INFO_BLUETOOTH_A2DP,
                FAKE_AUDIO_DEVICE_INFO_WIRED_HEADSET);
        MediaRoute2Info builtInSpeakerRoute =
                getAvailableRouteWithType(MediaRoute2Info.TYPE_BUILTIN_SPEAKER);
        mControllerUnderTest.transferTo(/* requestId= */ 0L, builtInSpeakerRoute.getId());
        verify(mMockAudioManager, Mockito.timeout(ASYNC_CALL_TIMEOUTS_MS))
                .setPreferredDeviceForStrategy(
                        mMediaAudioProductStrategy,
                        createAudioDeviceAttribute(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                                                   /* address= */ ""));

        MediaRoute2Info wiredHeadsetRoute =
                getAvailableRouteWithType(MediaRoute2Info.TYPE_WIRED_HEADSET);
        mControllerUnderTest.transferTo(/* requestId= */ 0L, wiredHeadsetRoute.getId());
        verify(mMockAudioManager, Mockito.timeout(ASYNC_CALL_TIMEOUTS_MS))
                .setPreferredDeviceForStrategy(
                        mMediaAudioProductStrategy,
                        createAudioDeviceAttribute(AudioDeviceInfo.TYPE_WIRED_HEADSET,
                                                   /* address= */ ""));
    }

    @Test
    public void updateVolume_propagatesCorrectlyToRouteInfo() {
        setUpControllerUnderTest(/* useMockBluetoothDeviceRoutesManager= */ false);
        when(mMockAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC)).thenReturn(2);
        when(mMockAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).thenReturn(3);
        when(mMockAudioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)).thenReturn(1);
        when(mMockAudioManager.isVolumeFixed()).thenReturn(false);
        addAvailableAudioDeviceInfo(
                /* newSelectedDevice= */ FAKE_AUDIO_DEVICE_INFO_WIRED_HEADSET,
                /* newAvailableDevices...= */ FAKE_AUDIO_DEVICE_INFO_WIRED_HEADSET);

        MediaRoute2Info selectedRoute = mControllerUnderTest.getSelectedRoutes().getFirst();
        assertThat(selectedRoute.getType()).isEqualTo(MediaRoute2Info.TYPE_WIRED_HEADSET);
        assertThat(selectedRoute.getVolume()).isEqualTo(2);
        assertThat(selectedRoute.getVolumeMax()).isEqualTo(3);
        assertThat(selectedRoute.getVolumeHandling())
                .isEqualTo(MediaRoute2Info.PLAYBACK_VOLUME_VARIABLE);

        MediaRoute2Info onlyTransferrableRoute =
                mControllerUnderTest.getAvailableRoutes().stream()
                        .filter(it -> !it.equals(selectedRoute))
                        .findAny()
                        .orElseThrow();
        assertThat(onlyTransferrableRoute.getType())
                .isEqualTo(MediaRoute2Info.TYPE_BUILTIN_SPEAKER);
        assertThat(onlyTransferrableRoute.getVolume()).isEqualTo(0);
        assertThat(onlyTransferrableRoute.getVolumeMax()).isEqualTo(0);
        assertThat(onlyTransferrableRoute.getVolume()).isEqualTo(0);
        assertThat(onlyTransferrableRoute.getVolumeHandling())
                .isEqualTo(MediaRoute2Info.PLAYBACK_VOLUME_FIXED);

        when(mMockAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC)).thenReturn(0);
        when(mMockAudioManager.isVolumeFixed()).thenReturn(true);
        mControllerUnderTest.updateVolume(0);
        MediaRoute2Info newSelectedRoute = mControllerUnderTest.getSelectedRoutes().getFirst();
        assertThat(newSelectedRoute.getVolume()).isEqualTo(0);
        assertThat(newSelectedRoute.getVolumeHandling())
                .isEqualTo(MediaRoute2Info.PLAYBACK_VOLUME_FIXED);
    }

    @Test
    public void getAvailableRoutes_whenNoProductNameIsProvided_usesTypeToPopulateName() {
        setUpControllerUnderTest(/* useMockBluetoothDeviceRoutesManager= */ false);
        assertThat(mControllerUnderTest.getSelectedRoutes().getFirst().getName().toString())
                .isEqualTo(FAKE_AUDIO_DEVICE_INFO_BUILTIN_SPEAKER.getProductName().toString());

        addAvailableAudioDeviceInfo(
                /* newSelectedDevice= */ FAKE_AUDIO_DEVICE_NO_NAME,
                /* newAvailableDevices...= */ FAKE_AUDIO_DEVICE_NO_NAME);

        MediaRoute2Info selectedRoute = mControllerUnderTest.getSelectedRoutes().getFirst();
        assertThat(selectedRoute.getName().toString()).isEqualTo(FAKE_ROUTE_NAME);
    }

    @Test
    public void getAvailableRoutes_whenAddressIsPopulatedForNonBluetoothDevice_usesCorrectName() {
        setUpControllerUnderTest(/* useMockBluetoothDeviceRoutesManager= */ false);
        addAvailableAudioDeviceInfo(
                /* newSelectedDevice= */ FAKE_AUDIO_DEVICE_INFO_WIRED_HEADSET_WITH_ADDRESS,
                /* newAvailableDevices...= */ FAKE_AUDIO_DEVICE_INFO_WIRED_HEADSET_WITH_ADDRESS,
                FAKE_AUDIO_DEVICE_INFO_BLUETOOTH_A2DP);

        List<MediaRoute2Info> availableRoutes = mControllerUnderTest.getAvailableRoutes();
        assertThat(availableRoutes.size()).isEqualTo(3);

        assertThat(
                        getAvailableRouteWithType(MediaRoute2Info.TYPE_WIRED_HEADSET)
                                .getName()
                                .toString())
                .isEqualTo(
                        FAKE_AUDIO_DEVICE_INFO_WIRED_HEADSET_WITH_ADDRESS
                                .getProductName()
                                .toString());

        assertThat(
                        getAvailableRouteWithType(MediaRoute2Info.TYPE_BLUETOOTH_A2DP)
                                .getName()
                                .toString())
                .isEqualTo(FAKE_AUDIO_DEVICE_INFO_BLUETOOTH_A2DP.getProductName().toString());
    }

    @Test
    public void
            getAvailableRoutes_whenAddressIsNotPopulatedForNonBluetoothDevice_usesCorrectName() {
        setUpControllerUnderTest(/* useMockBluetoothDeviceRoutesManager= */ false);
        addAvailableAudioDeviceInfo(
                /* newSelectedDevice= */ FAKE_AUDIO_DEVICE_INFO_WIRED_HEADSET,
                /* newAvailableDevices...= */ FAKE_AUDIO_DEVICE_INFO_WIRED_HEADSET);

        List<MediaRoute2Info> availableRoutes = mControllerUnderTest.getAvailableRoutes();
        assertThat(availableRoutes.size()).isEqualTo(2);

        assertThat(
                        getAvailableRouteWithType(MediaRoute2Info.TYPE_BUILTIN_SPEAKER)
                                .getName()
                                .toString())
                .isEqualTo(FAKE_AUDIO_DEVICE_INFO_BUILTIN_SPEAKER.getProductName().toString());

        assertThat(
                        getAvailableRouteWithType(MediaRoute2Info.TYPE_WIRED_HEADSET)
                                .getName()
                                .toString())
                .isEqualTo(FAKE_AUDIO_DEVICE_INFO_WIRED_HEADSET.getProductName().toString());
    }

    @Test
    public void getSessionReleaseType_returnTypeSharing() {
        setUpControllerUnderTest(/* useMockBluetoothDeviceRoutesManager= */ false);
        when(mMockAudioManager.getDevicesForAttributes(ATTRIBUTES_MEDIA))
                .thenReturn(
                        List.of(
                                createAudioDeviceAttribute(
                                        AudioDeviceInfo.TYPE_BLE_BROADCAST, /* address= */ "")));
        assertThat(mControllerUnderTest.getSessionReleaseType())
                .isEqualTo(RoutingSessionInfo.RELEASE_TYPE_SHARING);
    }

    @Test
    public void getSessionReleaseType_returnTypeUnsupported() {
        setUpControllerUnderTest(/* useMockBluetoothDeviceRoutesManager= */ false);
        when(mMockAudioManager.getDevicesForAttributes(ATTRIBUTES_MEDIA))
                .thenReturn(
                        List.of(
                                createAudioDeviceAttribute(
                                        AudioDeviceInfo.TYPE_WIRED_HEADSET, /* address= */ "")));
        assertThat(mControllerUnderTest.getSessionReleaseType())
                .isEqualTo(RoutingSessionInfo.RELEASE_UNSUPPORTED);
    }

    // Internal methods.

    private void setUpControllerUnderTest(boolean useMockBluetoothDeviceRoutesManager) {
        if (useMockBluetoothDeviceRoutesManager) {
            mControllerUnderTest =
                    new AudioManagerRouteController(
                            mMockContext,
                            mMockAudioManager,
                            Looper.getMainLooper(),
                            mMediaAudioProductStrategy,
                            mMockBluetoothDeviceRoutesManager);
        } else {
            BluetoothAdapter btAdapter =
                    mRealContext.getSystemService(BluetoothManager.class).getAdapter();
            mControllerUnderTest =
                    new AudioManagerRouteController(
                            mMockContext,
                            mMockAudioManager,
                            Looper.getMainLooper(),
                            mMediaAudioProductStrategy,
                            btAdapter);
        }
        mControllerUnderTest.registerRouteChangeListener(mEventListener);
        mControllerUnderTest.start(UserHandle.CURRENT_OR_SELF);

        ArgumentCaptor<AudioDeviceCallback> deviceCallbackCaptor =
                ArgumentCaptor.forClass(AudioDeviceCallback.class);
        verify(mMockAudioManager)
                .registerAudioDeviceCallback(deviceCallbackCaptor.capture(), any());
        mAudioDeviceCallback = deviceCallbackCaptor.getValue();

        // We clear any invocations during setup.
        clearInvocations(mEventListener);
    }

    @NonNull
    private MediaRoute2Info getAvailableRouteWithType(int type) {
        return mControllerUnderTest.getAvailableRoutes().stream()
                .filter(it -> it.getType() == type)
                .findFirst()
                .orElseThrow();
    }

    private void addAvailableAudioDeviceInfo(
            @Nullable AudioDeviceInfo newSelectedDevice, AudioDeviceInfo... newAvailableDevices) {
        Set<AudioDeviceInfo> newAvailableDeviceInfos = new HashSet<>(mAvailableAudioDeviceInfos);
        newAvailableDeviceInfos.addAll(List.of(newAvailableDevices));
        mAvailableAudioDeviceInfos = newAvailableDeviceInfos;
        if (newSelectedDevice != null) {
            mSelectedAudioDeviceInfo = newSelectedDevice;
        }
        updateMockAudioManagerState();
        mAudioDeviceCallback.onAudioDevicesAdded(newAvailableDevices);
    }

    private void removeAvailableAudioDeviceInfos(
            @Nullable AudioDeviceInfo newSelectedDevice, AudioDeviceInfo... devicesToRemove) {
        Set<AudioDeviceInfo> newAvailableDeviceInfos = new HashSet<>(mAvailableAudioDeviceInfos);
        List.of(devicesToRemove).forEach(newAvailableDeviceInfos::remove);
        mAvailableAudioDeviceInfos = newAvailableDeviceInfos;
        if (newSelectedDevice != null) {
            mSelectedAudioDeviceInfo = newSelectedDevice;
        }
        updateMockAudioManagerState();
        mAudioDeviceCallback.onAudioDevicesRemoved(devicesToRemove);
    }

    private void updateMockAudioManagerState() {
        int selectedDeviceAttributesType = mSelectedAudioDeviceInfo.getType();
        String selectedDeviceAttributesAddr = mSelectedAudioDeviceInfo.getAddress();
        when(mMockAudioManager.getDevicesForAttributes(ATTRIBUTES_MEDIA))
                .thenReturn(
                        List.of(createAudioDeviceAttribute(selectedDeviceAttributesType,
                                                           selectedDeviceAttributesAddr)));
        when(mMockAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS))
                .thenReturn(mAvailableAudioDeviceInfos.toArray(new AudioDeviceInfo[0]));
    }

    private static AudioDeviceAttributes createAudioDeviceAttribute(
            @AudioDeviceInfo.AudioDeviceType int type,
            @NonNull String address) {
        // Address is unused.
        return new AudioDeviceAttributes(
                AudioDeviceAttributes.ROLE_OUTPUT, type, address);
    }

    private static AudioDeviceInfo createAudioDeviceInfo(
            @AudioDeviceInfo.AudioDeviceType int type, @NonNull String name,
            @NonNull String address) {
        return new AudioDeviceInfo(AudioDevicePort.createForTesting(type, name, address));
    }
}
