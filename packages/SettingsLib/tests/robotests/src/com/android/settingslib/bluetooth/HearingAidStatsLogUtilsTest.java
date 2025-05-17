/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.settingslib.bluetooth;

import static com.android.settingslib.bluetooth.HearingAidStatsLogUtils.CONNECTED_HISTORY_EXPIRED_DAY;
import static com.android.settingslib.bluetooth.HearingAidStatsLogUtils.HistoryType.TYPE_HEARABLE_DEVICES_CONNECTED;
import static com.android.settingslib.bluetooth.HearingAidStatsLogUtils.HistoryType.TYPE_HEARABLE_DEVICES_PAIRED;
import static com.android.settingslib.bluetooth.HearingAidStatsLogUtils.HistoryType.TYPE_HEARING_DEVICES_CONNECTED;
import static com.android.settingslib.bluetooth.HearingAidStatsLogUtils.HistoryType.TYPE_HEARING_DEVICES_PAIRED;
import static com.android.settingslib.bluetooth.HearingAidStatsLogUtils.HistoryType.TYPE_LE_HEARABLE_CONNECTED;
import static com.android.settingslib.bluetooth.HearingAidStatsLogUtils.HistoryType.TYPE_LE_HEARING_CONNECTED;
import static com.android.settingslib.bluetooth.HearingAidStatsLogUtils.PAIRED_HISTORY_EXPIRED_DAY;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothProfile;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.internal.util.FrameworkStatsLog;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
public class HearingAidStatsLogUtilsTest {

    private static final String TEST_DEVICE_ADDRESS = "00:A1:A1:A1:A1:A1";
    private static final int TEST_HISTORY_TYPE = TYPE_HEARING_DEVICES_CONNECTED;

    @Rule
    public final MockitoRule mockito = MockitoJUnit.rule();

    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Mock
    private CachedBluetoothDevice mCachedBluetoothDevice;

    @Test
    public void setBondEntryForDevice_addsEntryToDeviceAddressToBondEntryMap() {
        when(mCachedBluetoothDevice.getAddress()).thenReturn(TEST_DEVICE_ADDRESS);

        HearingAidStatsLogUtils.setBondEntryForDevice(
                FrameworkStatsLog.HEARING_AID_INFO_REPORTED__BOND_ENTRY__BLUETOOTH,
                mCachedBluetoothDevice);

        final HashMap<String, Integer> map =
                HearingAidStatsLogUtils.getDeviceAddressToBondEntryMap();
        assertThat(map.containsKey(TEST_DEVICE_ADDRESS)).isTrue();
        assertThat(map.get(TEST_DEVICE_ADDRESS)).isEqualTo(
                FrameworkStatsLog.HEARING_AID_INFO_REPORTED__BOND_ENTRY__BLUETOOTH);
    }

    @Test
    public void logHearingAidInfo_removesEntryFromDeviceAddressToBondEntryMap() {
        when(mCachedBluetoothDevice.getAddress()).thenReturn(TEST_DEVICE_ADDRESS);

        HearingAidStatsLogUtils.setBondEntryForDevice(
                FrameworkStatsLog.HEARING_AID_INFO_REPORTED__BOND_ENTRY__BLUETOOTH,
                mCachedBluetoothDevice);
        HearingAidStatsLogUtils.logHearingAidInfo(mCachedBluetoothDevice);

        final HashMap<String, Integer> map =
                HearingAidStatsLogUtils.getDeviceAddressToBondEntryMap();
        assertThat(map.containsKey(TEST_DEVICE_ADDRESS)).isFalse();
    }

    @Test
    public void addCurrentTimeToHistory_addNewData() {
        final long todayStartOfDay = convertToStartOfDayTime(System.currentTimeMillis());
        final long lastData = todayStartOfDay - TimeUnit.DAYS.toMillis(6);
        HearingAidStatsLogUtils.addToHistory(mContext, TEST_HISTORY_TYPE, lastData);

        HearingAidStatsLogUtils.addCurrentTimeToHistory(mContext, TEST_HISTORY_TYPE);

        LinkedList<Long> history = HearingAidStatsLogUtils.getHistory(mContext, TEST_HISTORY_TYPE);
        assertThat(history).isNotNull();
        assertThat(history.size()).isEqualTo(2);
    }
    @Test
    public void addCurrentTimeToHistory_skipSameDateData() {
        final long todayStartOfDay = convertToStartOfDayTime(System.currentTimeMillis());
        HearingAidStatsLogUtils.addToHistory(mContext, TEST_HISTORY_TYPE, todayStartOfDay);

        HearingAidStatsLogUtils.addCurrentTimeToHistory(mContext, TEST_HISTORY_TYPE);

        LinkedList<Long> history = HearingAidStatsLogUtils.getHistory(mContext, TEST_HISTORY_TYPE);
        assertThat(history).isNotNull();
        assertThat(history.size()).isEqualTo(1);
        assertThat(history.getFirst()).isEqualTo(todayStartOfDay);
    }

    @Test
    public void addCurrentTimeToHistory_cleanUpExpiredData() {
        final long todayStartOfDay = convertToStartOfDayTime(System.currentTimeMillis());
        final long expiredData = todayStartOfDay - TimeUnit.DAYS.toMillis(6) - 1;
        HearingAidStatsLogUtils.addToHistory(mContext, TEST_HISTORY_TYPE, expiredData);

        HearingAidStatsLogUtils.addCurrentTimeToHistory(mContext, TEST_HISTORY_TYPE);

        LinkedList<Long> history = HearingAidStatsLogUtils.getHistory(mContext, TEST_HISTORY_TYPE);
        assertThat(history).isNotNull();
        assertThat(history.size()).isEqualTo(1);
        assertThat(history.getFirst()).isNotEqualTo(expiredData);
    }

    @Test
    public void getUserCategory_hearingDevices() {
        prepareConnectedHistory(TYPE_HEARING_DEVICES_CONNECTED);

        assertThat(HearingAidStatsLogUtils.getUserCategory(mContext)).isEqualTo(
                HearingAidStatsLogUtils.CATEGORY_HEARING_DEVICES);

        preparePairedHistory(TYPE_HEARING_DEVICES_PAIRED);

        assertThat(HearingAidStatsLogUtils.getUserCategory(mContext)).isEqualTo(
                HearingAidStatsLogUtils.CATEGORY_NEW_HEARING_DEVICES);
    }

    @Test
    public void getUserCategory_hearableDevices() {
        prepareConnectedHistory(TYPE_HEARABLE_DEVICES_CONNECTED);

        assertThat(HearingAidStatsLogUtils.getUserCategory(mContext)).isEqualTo(
                HearingAidStatsLogUtils.CATEGORY_HEARABLE_DEVICES);

        preparePairedHistory(TYPE_HEARABLE_DEVICES_PAIRED);

        assertThat(HearingAidStatsLogUtils.getUserCategory(mContext)).isEqualTo(
                HearingAidStatsLogUtils.CATEGORY_NEW_HEARABLE_DEVICES);
    }

    @Test
    public void getUserCategory_leHearingDevices() {
        prepareConnectedHistory(TYPE_HEARING_DEVICES_CONNECTED);
        prepareConnectedHistory(TYPE_LE_HEARING_CONNECTED);

        assertThat(HearingAidStatsLogUtils.getUserCategory(mContext)).isEqualTo(
                HearingAidStatsLogUtils.CATEGORY_LE_HEARING_DEVICES);

        preparePairedHistory(TYPE_HEARING_DEVICES_PAIRED);

        assertThat(HearingAidStatsLogUtils.getUserCategory(mContext)).isEqualTo(
                HearingAidStatsLogUtils.CATEGORY_NEW_LE_HEARING_DEVICES);
    }

    @Test
    public void getUserCategory_leHearableDevices() {
        prepareConnectedHistory(TYPE_HEARABLE_DEVICES_CONNECTED);
        prepareConnectedHistory(TYPE_LE_HEARABLE_CONNECTED);

        assertThat(HearingAidStatsLogUtils.getUserCategory(mContext)).isEqualTo(
                HearingAidStatsLogUtils.CATEGORY_LE_HEARABLE_DEVICES);

        preparePairedHistory(TYPE_HEARABLE_DEVICES_PAIRED);

        assertThat(HearingAidStatsLogUtils.getUserCategory(mContext)).isEqualTo(
                HearingAidStatsLogUtils.CATEGORY_NEW_LE_HEARABLE_DEVICES);
    }

    @Test
    public void getUserCategory_bothHearingAndHearableDevices_returnHearingDevicesUser() {
        prepareConnectedHistory(TYPE_HEARING_DEVICES_CONNECTED);
        prepareConnectedHistory(TYPE_HEARABLE_DEVICES_CONNECTED);

        assertThat(HearingAidStatsLogUtils.getUserCategory(mContext)).isEqualTo(
                HearingAidStatsLogUtils.CATEGORY_HEARING_DEVICES);
    }

    @Test
    public void updateHistoryIfNeeded_ashaHearingDevice_ashaConnected_historyCorrect() {
        prepareAshaHearingDevice();

        HearingAidProfile ashaProfile = mock(HearingAidProfile.class);
        HearingAidStatsLogUtils.updateHistoryIfNeeded(mContext, mCachedBluetoothDevice,
                ashaProfile, BluetoothProfile.STATE_CONNECTED);

        assertHistorySize(TYPE_HEARING_DEVICES_CONNECTED, 1);
    }

    @Test
    public void updateHistoryIfNeeded_leAudioHearingDevice_hapClientConnected_historyCorrect() {
        prepareLeAudioHearingDevice();

        HapClientProfile hapClientProfile = mock(HapClientProfile.class);
        HearingAidStatsLogUtils.updateHistoryIfNeeded(mContext, mCachedBluetoothDevice,
                hapClientProfile, BluetoothProfile.STATE_CONNECTED);

        assertHistorySize(TYPE_HEARING_DEVICES_CONNECTED, 1);
    }

    @Test
    public void updateHistoryIfNeeded_leAudioHearingDevice_leAudioConnected_historyCorrect() {
        prepareLeAudioHearingDevice();

        LeAudioProfile leAudioProfile = mock(LeAudioProfile.class);
        HearingAidStatsLogUtils.updateHistoryIfNeeded(mContext, mCachedBluetoothDevice,
                leAudioProfile, BluetoothProfile.STATE_CONNECTED);

        assertHistorySize(TYPE_LE_HEARING_CONNECTED, 1);
    }

    @Test
    public void updateHistoryIfNeeded_hearableDevice_a2dpConnected_historyCorrect() {
        prepareHearableDevice();

        A2dpProfile a2dpProfile = mock(A2dpProfile.class);
        HearingAidStatsLogUtils.updateHistoryIfNeeded(mContext, mCachedBluetoothDevice,
                a2dpProfile, BluetoothProfile.STATE_CONNECTED);

        assertHistorySize(TYPE_HEARABLE_DEVICES_CONNECTED, 1);
    }

    @Test
    public void updateHistoryIfNeeded_hearableDevice_leAudioConnected_historyCorrect() {
        prepareHearableDevice();

        LeAudioProfile leAudioProfile = mock(LeAudioProfile.class);
        HearingAidStatsLogUtils.updateHistoryIfNeeded(mContext, mCachedBluetoothDevice,
                leAudioProfile, BluetoothProfile.STATE_CONNECTED);

        assertHistorySize(TYPE_LE_HEARABLE_CONNECTED, 1);
    }

    private long convertToStartOfDayTime(long timestamp) {
        ZoneId zoneId = ZoneId.systemDefault();
        LocalDate date = Instant.ofEpochMilli(timestamp).atZone(zoneId).toLocalDate();
        return date.atStartOfDay(zoneId).toInstant().toEpochMilli();
    }

    private void prepareConnectedHistory(int historyType) {
        final long todayStartOfDay = convertToStartOfDayTime(System.currentTimeMillis());
        for (int i = CONNECTED_HISTORY_EXPIRED_DAY - 1; i >= 0; i--) {
            final long data = todayStartOfDay - TimeUnit.DAYS.toMillis(i);
            HearingAidStatsLogUtils.addToHistory(mContext, historyType, data);
        }
    }

    private void preparePairedHistory(int historyType) {
        final long todayStartOfDay = convertToStartOfDayTime(System.currentTimeMillis());
        final long data = todayStartOfDay - TimeUnit.DAYS.toMillis(PAIRED_HISTORY_EXPIRED_DAY - 1);
        HearingAidStatsLogUtils.addToHistory(mContext, historyType, data);
    }

    private void prepareAshaHearingDevice() {
        doReturn(List.of(mock(HearingAidProfile.class))).when(mCachedBluetoothDevice).getProfiles();
    }

    private void prepareLeAudioHearingDevice() {
        doReturn(List.of(mock(HapClientProfile.class), mock(LeAudioProfile.class))).when(
                mCachedBluetoothDevice).getProfiles();
    }

    private void prepareHearableDevice() {
        doReturn(List.of(mock(A2dpProfile.class), mock(HeadsetProfile.class),
                mock(LeAudioProfile.class))).when(mCachedBluetoothDevice).getProfiles();
    }

    private void assertHistorySize(int type, int size) {
        LinkedList<Long> history = HearingAidStatsLogUtils.getHistory(mContext, type);
        assertThat(history).isNotNull();
        assertThat(history.size()).isEqualTo(size);
    }
}
