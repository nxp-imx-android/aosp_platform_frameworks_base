/*
 * Copyright 2018 The Android Open Source Project
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
package com.android.settingslib.media;

import static com.android.media.flags.Flags.avoidBinderCallsDuringRender;
import static com.android.media.flags.Flags.avoidBinderCallsForMutingExpectedDevice;
import static com.android.settingslib.media.MediaDevice.SelectionBehavior.SELECTION_BEHAVIOR_TRANSFER;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHearingAid;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.MediaRoute2Info;
import android.media.RouteListingPreference;

import com.android.settingslib.R;
import com.android.settingslib.bluetooth.BluetoothUtils;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;

/**
 * BluetoothMediaDevice extends MediaDevice to represents Bluetooth device.
 */
public class BluetoothMediaDevice extends MediaDevice {

    private static final String TAG = "BluetoothMediaDevice";

    private final CachedBluetoothDevice mCachedDevice;
    private final AudioManager mAudioManager;
    private final Boolean mIsMutingExpectedDevice;

    BluetoothMediaDevice(
            @NonNull Context context,
            @NonNull CachedBluetoothDevice device,
            @Nullable MediaRoute2Info info,
            @Nullable DynamicRouteAttributes dynamicRouteAttributes,
            @Nullable RouteListingPreference.Item item) {
        this(context, device, info, dynamicRouteAttributes, item,
                /* isMutingExpectedDevice= */ false);
    }

    BluetoothMediaDevice(
            @NonNull Context context,
            @NonNull CachedBluetoothDevice device,
            @Nullable MediaRoute2Info info,
            @Nullable DynamicRouteAttributes dynamicRouteAttributes,
            @Nullable RouteListingPreference.Item item,
            Boolean isMutingExpectedDevice) {
        super(context, info, dynamicRouteAttributes, item);
        mCachedDevice = device;
        mIsMutingExpectedDevice = isMutingExpectedDevice;
        mAudioManager = context.getSystemService(AudioManager.class);
        initDeviceRecord();
    }

    @Override
    public String getName() {
        if (avoidBinderCallsDuringRender()) {
            if (mRouteInfo != null) {
                // Prefer name from route info since CachedBluetoothDevice#getName results in an
                // IPC call.
                return mRouteInfo.getName().toString();
            } else {
                return mCachedDevice.getName();
            }
        }

        return mCachedDevice.getName();
    }

    @Override
    public String getSummary() {
        return isConnected() || mCachedDevice.isBusy()
                ? mCachedDevice.getConnectionSummary()
                : mContext.getString(R.string.bluetooth_disconnected);
    }

    @Override
    public CharSequence getSummaryForTv(int lowBatteryColorRes) {
        return isConnected() || mCachedDevice.isBusy()
                ? mCachedDevice.getTvConnectionSummary(lowBatteryColorRes)
                : mContext.getString(R.string.bluetooth_saved_device);
    }

    @Override
    public int getSelectionBehavior() {
        // We don't allow apps to override the selection behavior of system routes.
        return SELECTION_BEHAVIOR_TRANSFER;
    }

    @Override
    public Drawable getIcon() {
        return BluetoothUtils.isAdvancedUntetheredDevice(mCachedDevice.getDevice())
                ? mContext.getDrawable(R.drawable.ic_earbuds_advanced)
                : BluetoothUtils.getBtClassDrawableWithDescription(mContext, mCachedDevice).first;
    }

    @Override
    public Drawable getIconWithoutBackground() {
        return BluetoothUtils.isAdvancedUntetheredDevice(mCachedDevice.getDevice())
                ? mContext.getDrawable(R.drawable.ic_earbuds_advanced)
                : BluetoothUtils.getBtClassDrawableWithDescription(mContext, mCachedDevice).first;
    }

    @Override
    public String getId() {
        if (mCachedDevice.isHearingDevice()) {
            if (mCachedDevice.getHiSyncId() != BluetoothHearingAid.HI_SYNC_ID_INVALID) {
                return Long.toString(mCachedDevice.getHiSyncId());
            }
        }
        return mCachedDevice.getAddress();
    }

    /**
     * Get current CachedBluetoothDevice
     */
    public CachedBluetoothDevice getCachedDevice() {
        return mCachedDevice;
    }

    @Override
    protected boolean isCarKitDevice() {
        final BluetoothClass bluetoothClass = mCachedDevice.getDevice().getBluetoothClass();
        if (bluetoothClass != null) {
            switch (bluetoothClass.getDeviceClass()) {
                // Both are common CarKit class
                case BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE:
                case BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO:
                    return true;
            }
        }
        return false;
    }

    @Override
    public boolean isFastPairDevice() {
        return mCachedDevice != null
                && BluetoothUtils.getBooleanMetaData(
                mCachedDevice.getDevice(), BluetoothDevice.METADATA_IS_UNTETHERED_HEADSET);
    }

    @Override
    public boolean isMutingExpectedDevice() {
        if (avoidBinderCallsForMutingExpectedDevice()) {
            return mIsMutingExpectedDevice;
        }
        return mAudioManager.getMutingExpectedDevice() != null && mCachedDevice.getAddress().equals(
                mAudioManager.getMutingExpectedDevice().getAddress());
    }

    @Override
    public boolean isConnected() {
        return mCachedDevice.getBondState() == BluetoothDevice.BOND_BONDED
                && mCachedDevice.isConnected();
    }
}
