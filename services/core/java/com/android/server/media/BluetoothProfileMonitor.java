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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHearingAid;
import android.bluetooth.BluetoothLeAudio;
import android.bluetooth.BluetoothLeAudioContentMetadata;
import android.bluetooth.BluetoothLeBroadcast;
import android.bluetooth.BluetoothLeBroadcastAssistant;
import android.bluetooth.BluetoothLeBroadcastMetadata;
import android.bluetooth.BluetoothLeBroadcastReceiveState;
import android.bluetooth.BluetoothLeBroadcastSettings;
import android.bluetooth.BluetoothLeBroadcastSubgroupSettings;
import android.bluetooth.BluetoothProfile;
import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

/* package */ class BluetoothProfileMonitor {

    private static final String TAG = BluetoothProfileMonitor.class.getSimpleName();

    /* package */ static final long GROUP_ID_NO_GROUP = -1L;

    private static final String UNDERLINE = "_";
    private static final int DEFAULT_CODE_MAX = 9999;
    private static final int DEFAULT_CODE_MIN = 1000;
    private static final int MIN_NO_DEVICES_FOR_BROADCAST = 2;
    private static final String VALID_PASSWORD_CHARACTERS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()-_=+[]{}|;:,"
                    + ".<>?/";
    private static final int PASSWORD_LENGTH = 16;
    private static final int BROADCAST_NAME_PREFIX_MAX_LENGTH = 27;
    private static final String DEFAULT_BROADCAST_NAME_PREFIX = "Broadcast";
    private static final String IMPROVE_COMPATIBILITY_HIGH_QUALITY = "1";

    @NonNull
    private final ProfileListener mProfileListener = new ProfileListener();

    @NonNull
    private final Context mContext;
    @NonNull
    private final BluetoothAdapter mBluetoothAdapter;

    @Nullable
    private BluetoothA2dp mA2dpProfile;
    @Nullable
    private BluetoothHearingAid mHearingAidProfile;
    @Nullable
    private BluetoothLeAudio mLeAudioProfile;

    private final Executor mExecutor = Executors.newSingleThreadExecutor();

    @GuardedBy("mBroadcastLock")
    private BluetoothLeBroadcast mLeBroadcast;

    private BluetoothLeBroadcastAssistant mLeBroadcastAssistant;
    private BluetoothLeBroadcastMetadata mCachedLeBroadcastMetadata;
    private final List<BluetoothDevice> mDevicesToAdd = new ArrayList<>();
    private int mBroadcastId = 0;
    // Since broadcast name needed to be accessed multiple times in multiple areas
    private String mCachedBroadcastName = "";
    private final Object mBroadcastLock = new Object();

    private final BluetoothLeBroadcast.Callback mLeBroadcastCallback =
            new BluetoothLeBroadcast.Callback() {
                @Override
                public void onBroadcastStarted(int reason, int broadcastId) {
                    mBroadcastId = broadcastId;
                }

                @Override
                public void onBroadcastStartFailed(int reason) {
                    // To prevent broadcast accidentally start when metadata change
                    mDevicesToAdd.clear();
                }

                @Override
                public void onBroadcastStopped(int reason, int broadcastId) {
                    mBroadcastId = 0;
                    // Reset broadcast name, next time broadcast start will generate a new one
                    mCachedBroadcastName = "";
                }

                @Override
                public void onBroadcastStopFailed(int reason) {}

                @Override
                public void onPlaybackStarted(int reason, int broadcastId) {}

                @Override
                public void onPlaybackStopped(int reason, int broadcastId) {}

                @Override
                public void onBroadcastUpdated(int reason, int broadcastId) {}

                @Override
                public void onBroadcastUpdateFailed(int reason, int broadcastId) {}

                @Override
                public void onBroadcastMetadataChanged(
                        int broadcastId,
                        @androidx.annotation.NonNull BluetoothLeBroadcastMetadata metadata) {
                    mCachedLeBroadcastMetadata = metadata;
                    BluetoothProfileMonitor.this.addDevicesToSource(mDevicesToAdd);
                    mDevicesToAdd.clear();
                }
            };

    private final BluetoothLeBroadcastAssistant.Callback mLeBroadcastAssistantCallback =
            new BluetoothLeBroadcastAssistant.Callback() {
                @Override
                public void onSearchStarted(int reason) {}

                @Override
                public void onSearchStartFailed(int reason) {}

                @Override
                public void onSearchStopped(int reason) {}

                @Override
                public void onSearchStopFailed(int reason) {}

                @Override
                public void onSourceFound(
                        @androidx.annotation.NonNull BluetoothLeBroadcastMetadata source) {}

                @Override
                public void onSourceAdded(
                        @androidx.annotation.NonNull BluetoothDevice sink,
                        int sourceId,
                        int reason) {}

                @Override
                public void onSourceAddFailed(
                        @androidx.annotation.NonNull BluetoothDevice sink,
                        @androidx.annotation.NonNull BluetoothLeBroadcastMetadata source,
                        int reason) {}

                @Override
                public void onSourceModified(
                        @androidx.annotation.NonNull BluetoothDevice sink,
                        int sourceId,
                        int reason) {}

                @Override
                public void onSourceModifyFailed(
                        @androidx.annotation.NonNull BluetoothDevice sink,
                        int sourceId,
                        int reason) {}

                @Override
                public void onSourceRemoved(
                        @androidx.annotation.NonNull BluetoothDevice sink,
                        int sourceId,
                        int reason) {}

                @Override
                public void onSourceRemoveFailed(
                        @androidx.annotation.NonNull BluetoothDevice sink,
                        int sourceId,
                        int reason) {}

                @Override
                public void onReceiveStateChanged(
                        @androidx.annotation.NonNull BluetoothDevice sink,
                        int sourceId,
                        @androidx.annotation.NonNull BluetoothLeBroadcastReceiveState state) {}
            };

    BluetoothProfileMonitor(@NonNull Context context,
            @NonNull BluetoothAdapter bluetoothAdapter) {
        mContext = Objects.requireNonNull(context);
        mBluetoothAdapter = Objects.requireNonNull(bluetoothAdapter);
    }

    /* package */ void start() {
        mBluetoothAdapter.getProfileProxy(mContext, mProfileListener, BluetoothProfile.A2DP);
        mBluetoothAdapter.getProfileProxy(mContext, mProfileListener, BluetoothProfile.HEARING_AID);
        mBluetoothAdapter.getProfileProxy(mContext, mProfileListener, BluetoothProfile.LE_AUDIO);
        mBluetoothAdapter.getProfileProxy(
                mContext, mProfileListener, BluetoothProfile.LE_AUDIO_BROADCAST);
        mBluetoothAdapter.getProfileProxy(
                mContext, mProfileListener, BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT);
    }

    /* package */ boolean isProfileSupported(int profile, @NonNull BluetoothDevice device) {
        BluetoothProfile bluetoothProfile;

        synchronized (this) {
            switch (profile) {
                case BluetoothProfile.A2DP:
                    bluetoothProfile = mA2dpProfile;
                    break;
                case BluetoothProfile.LE_AUDIO:
                    bluetoothProfile = mLeAudioProfile;
                    break;
                case BluetoothProfile.HEARING_AID:
                    bluetoothProfile = mHearingAidProfile;
                    break;
                default:
                    throw new IllegalArgumentException(profile
                            + " is not supported as Bluetooth profile");
            }
        }

        if (bluetoothProfile == null) {
            return false;
        }

        return bluetoothProfile.getConnectedDevices().contains(device);
    }

    /* package */ long getGroupId(int profile, @NonNull BluetoothDevice device) {
        synchronized (this) {
            switch (profile) {
                case BluetoothProfile.A2DP:
                    return GROUP_ID_NO_GROUP;
                case BluetoothProfile.LE_AUDIO:
                    return mLeAudioProfile == null ? GROUP_ID_NO_GROUP : mLeAudioProfile.getGroupId(
                            device);
                case BluetoothProfile.HEARING_AID:
                    return mHearingAidProfile == null
                            ? GROUP_ID_NO_GROUP : mHearingAidProfile.getHiSyncId(device);
                default:
                    throw new IllegalArgumentException(profile
                            + " is not supported as Bluetooth profile");
            }
        }
    }

    /**
     * Starts broadcast and connect to given bluetooth devices.
     *
     * @param devices Bluetooth devices that are going to connect the to broadcast
     */
    protected void startPrivateBroadcast(List<BluetoothDevice> devices) {
        if (devices.size() < MIN_NO_DEVICES_FOR_BROADCAST) {
            // At least 2 devices to start broadcast. Theoretically broadcast can be started even
            // with 1 device. However, in the current design and use case, starting broadcast with 1
            // device will be considered as fail.
            Slog.d(TAG, "Insufficient number of device to start broadcast");
            return;
        }

        if (mLeBroadcast == null) {
            // BluetoothLeBroadcast is not initialzed properly
            Slog.e(TAG, "Fail to start private broadcast, LeBroadcast is null");
            return;
        }

        if (mLeBroadcast.getAllBroadcastMetadata().size()
                >= mLeBroadcast.getMaximumNumberOfBroadcasts()) {
            // Max broadcast reached, skip future broadcast request
            Slog.e(TAG, "Fail to start private broadcast, max number of broadcast reached.");
            return;
        }

        mDevicesToAdd.clear();

        // Current broadcast framework only support one subgroup
        BluetoothLeBroadcastSubgroupSettings subgroupSettings =
                buildBroadcastSubgroupSettings(
                        /* language= */ null, getProgramInfo(), isImproveCompatibility());
        BluetoothLeBroadcastSettings settings =
                buildBroadcastSettings(
                        true, // TODO: set to false after framework fix
                        getBroadcastName(),
                        getBroadcastCode(),
                        List.of(subgroupSettings));
        mDevicesToAdd.addAll(devices);
        synchronized (mBroadcastLock) {
            mLeBroadcast.startBroadcast(settings);
        }
    }

    /** Stops the broadcast. */
    protected void stopPrivateBroadcast() {
        synchronized (mBroadcastLock) {
            if (mLeBroadcast != null) {
                mLeBroadcast.stopBroadcast(mBroadcastId);
            } else {
                Slog.e(TAG, "Fail to stop private broadcast, LeBroadcast is null");
            }
        }
    }

    /**
     * Obtains selected bluetooth devices from broadcast assistant that are broadcasting.
     *
     * @return list of selected {@link BluetoothDevice}
     */
    protected List<BluetoothDevice> getBroadcastingDevices() {
        if (mLeBroadcastAssistant == null) {
            return List.of();
        }

        return mLeBroadcastAssistant.getConnectedDevices().stream()
                .filter(
                        device ->
                                mLeBroadcastAssistant.getAllSources(device).stream()
                                        .anyMatch(
                                                source -> source.getBroadcastId() == mBroadcastId))
                .toList();
    }

    /**
     * Perform add device as broadcast source to {@link BluetoothLeBroadcastAssistant}. Devices will
     * then receive audio broadcast
     *
     * @param deviceList - List of {@link BluetoothDevice} for broadcast
     */
    protected void addDevicesToSource(List<BluetoothDevice> deviceList) {
        if (mLeBroadcastAssistant == null) {
            Slog.d(TAG, "BroadcastAssistant is null");
            return;
        }

        if (mCachedLeBroadcastMetadata == null) {
            Slog.d(TAG, "BroadcastMetadata is null");
            return;
        }

        for (BluetoothDevice device : deviceList) {
            mLeBroadcastAssistant.addSource(
                    device, mCachedLeBroadcastMetadata, /* isGroupOp= */ false);
        }
    }

    @Nullable
    private byte[] getBroadcastCode() {
        ContentResolver contentResolver = mContext.getContentResolver();

        String prefBroadcastCode =
                Settings.Secure.getStringForUser(
                        contentResolver,
                        Settings.Secure.BLUETOOTH_LE_BROADCAST_CODE,
                        contentResolver.getUserId());

        byte[] broadcastCode =
                (prefBroadcastCode == null)
                        ? generateRandomPassword().getBytes(StandardCharsets.UTF_8)
                        : prefBroadcastCode.getBytes(StandardCharsets.UTF_8);

        return (broadcastCode != null && broadcastCode.length > 0) ? broadcastCode : null;
    }

    @NonNull
    private String getBroadcastName() {
        if (!mCachedBroadcastName.isEmpty()) {
            return mCachedBroadcastName;
        }

        ContentResolver contentResolver = mContext.getContentResolver();
        String settingBroadcastName =
                Settings.Secure.getStringForUser(
                        contentResolver,
                        Settings.Secure.BLUETOOTH_LE_BROADCAST_NAME,
                        contentResolver.getUserId());

        if (settingBroadcastName == null || settingBroadcastName.isEmpty()) {
            int postfix = ThreadLocalRandom.current().nextInt(DEFAULT_CODE_MIN, DEFAULT_CODE_MAX);
            String name = BluetoothAdapter.getDefaultAdapter().getName();
            if (name == null || name.isEmpty()) {
                name = DEFAULT_BROADCAST_NAME_PREFIX;
            }
            return (name.length() < BROADCAST_NAME_PREFIX_MAX_LENGTH
                            ? name
                            : name.substring(0, BROADCAST_NAME_PREFIX_MAX_LENGTH))
                    + UNDERLINE
                    + postfix;
        }

        return settingBroadcastName;
    }

    @NonNull
    private String getProgramInfo() {
        ContentResolver contentResolver = mContext.getContentResolver();

        String programInfo =
                Settings.Secure.getStringForUser(
                        contentResolver,
                        Settings.Secure.BLUETOOTH_LE_BROADCAST_PROGRAM_INFO,
                        contentResolver.getUserId());

        if (programInfo == null || programInfo.isEmpty()) {
            return getBroadcastName();
        }

        return programInfo;
    }

    private static String generateRandomPassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder stringBuilder = new StringBuilder(PASSWORD_LENGTH);

        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            int randomIndex = random.nextInt(VALID_PASSWORD_CHARACTERS.length());
            stringBuilder.append(VALID_PASSWORD_CHARACTERS.charAt(randomIndex));
        }

        return stringBuilder.toString();
    }

    private boolean isImproveCompatibility() {
        ContentResolver contentResolver = mContext.getContentResolver();
        // BLUETOOTH_LE_BROADCAST_IMPROVE_COMPATIBILITY takes only "1" and "0" in string only. Check
        // android.provider.settings.validators.SecureSettingsValidators for mode details.
        return IMPROVE_COMPATIBILITY_HIGH_QUALITY.equals(
                Settings.Secure.getStringForUser(
                        contentResolver,
                        Settings.Secure.BLUETOOTH_LE_BROADCAST_IMPROVE_COMPATIBILITY,
                        contentResolver.getUserId()));
    }

    private BluetoothLeBroadcastSettings buildBroadcastSettings(
            boolean isPublic,
            String broadcastName,
            byte[] broadcastCode,
            List<BluetoothLeBroadcastSubgroupSettings> subgroupSettingsList) {
        BluetoothLeBroadcastSettings.Builder builder =
                new BluetoothLeBroadcastSettings.Builder()
                        .setPublicBroadcast(isPublic)
                        .setBroadcastName(broadcastName)
                        .setBroadcastCode(broadcastCode);
        for (BluetoothLeBroadcastSubgroupSettings subgroupSettings : subgroupSettingsList) {
            builder.addSubgroupSettings(subgroupSettings);
        }
        return builder.build();
    }

    private BluetoothLeBroadcastSubgroupSettings buildBroadcastSubgroupSettings(
            String language, String programInfo, boolean improveCompatibility) {
        BluetoothLeAudioContentMetadata metadata =
                new BluetoothLeAudioContentMetadata.Builder()
                        .setLanguage(language)
                        .setProgramInfo(programInfo)
                        .build();
        // Current broadcast framework only support one subgroup, thus we still maintain the latest
        // metadata to keep legacy UI working.
        return new BluetoothLeBroadcastSubgroupSettings.Builder()
                .setPreferredQuality(
                        improveCompatibility
                                ? BluetoothLeBroadcastSubgroupSettings.QUALITY_STANDARD
                                : BluetoothLeBroadcastSubgroupSettings.QUALITY_HIGH)
                .setContentMetadata(metadata)
                .build();
    }

    private final class ProfileListener implements BluetoothProfile.ServiceListener {
        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            synchronized (BluetoothProfileMonitor.this) {
                switch (profile) {
                    case BluetoothProfile.A2DP:
                        mA2dpProfile = (BluetoothA2dp) proxy;
                        break;
                    case BluetoothProfile.HEARING_AID:
                        mHearingAidProfile = (BluetoothHearingAid) proxy;
                        break;
                    case BluetoothProfile.LE_AUDIO:
                        mLeAudioProfile = (BluetoothLeAudio) proxy;
                        break;
                    case BluetoothProfile.LE_AUDIO_BROADCAST:
                        mLeBroadcast = (BluetoothLeBroadcast) proxy;
                        mLeBroadcast.registerCallback(mExecutor, mLeBroadcastCallback);
                        break;
                    case BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT:
                        mLeBroadcastAssistant = (BluetoothLeBroadcastAssistant) proxy;
                        mLeBroadcastAssistant.registerCallback(
                                mExecutor, mLeBroadcastAssistantCallback);
                        break;
                }
            }
        }

        @Override
        public void onServiceDisconnected(int profile) {
            synchronized (BluetoothProfileMonitor.this) {
                switch (profile) {
                    case BluetoothProfile.A2DP:
                        mA2dpProfile = null;
                        break;
                    case BluetoothProfile.HEARING_AID:
                        mHearingAidProfile = null;
                        break;
                    case BluetoothProfile.LE_AUDIO:
                        mLeAudioProfile = null;
                        break;
                    case BluetoothProfile.LE_AUDIO_BROADCAST:
                        mLeBroadcast.unregisterCallback(mLeBroadcastCallback);
                        mLeBroadcast = null;
                        mBroadcastId = 0;
                        break;
                    case BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT:
                        mLeBroadcastAssistant.unregisterCallback(mLeBroadcastAssistantCallback);
                        mLeBroadcastAssistant = null;
                        break;
                }
            }
        }
    }
}
