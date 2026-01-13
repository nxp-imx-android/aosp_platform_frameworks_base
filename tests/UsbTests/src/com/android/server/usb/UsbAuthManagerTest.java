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

package com.android.server.usb;

import static android.hardware.usb.UsbAuthorizationStatus.AUTHORIZED;
import static android.hardware.usb.UsbAuthorizationStatus.DENIED;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.UserInfo;
import android.hardware.usb.DeviceFilter;
import android.hardware.usb.IUsbAuthManager;
import android.hardware.usb.IUsbSerialReader;
import android.hardware.usb.UsbAuthDeviceInfo;
import android.hardware.usb.UsbAuthorizationSystemState;
import android.hardware.usb.UsbConfiguration;
import android.hardware.usb.UsbDevice;
import android.os.IBinder;
import android.os.UserManager;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.util.ArrayMap;

import androidx.test.runner.AndroidJUnit4;

import com.android.server.LocalServices;
import com.android.server.usb.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Base64;

/** Tests for {@link com.android.server.usb.UsbAuthManager} atest UsbTests:UsbAuthManagerTest */
@RunWith(AndroidJUnit4.class)
@EnableFlags({Flags.FLAG_ENABLE_USB_HOST_AUTHORIZATION})
public class UsbAuthManagerTest {
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();
    @Mock private Context mContext;
    @Mock private UserManager mUserManager;
    @Mock private IUsbAuthManager mService;
    @Mock private UsbHostManager mHostManager;
    @Mock private IBinder mBinder;
    private UsbAuthManager.ProvisioningListener mDeviceProvisionedListener;

    private UsbAuthManager mUsbAuthManager;

    private static final int TEST_USER_ID_FULL_ADMIN = 10;
    private static final int TEST_USER_ID_GUEST = 11;

    private static final UserInfo TEST_USER_INFO_FULL_ADMIN =
            new UserInfo(
                    TEST_USER_ID_FULL_ADMIN,
                    "full admin",
                    UserInfo.FLAG_FULL | UserInfo.FLAG_ADMIN);
    private static final UserInfo TEST_USER_INFO_GUEST =
            new UserInfo(TEST_USER_ID_GUEST, "guest", 0);

    private final ArrayMap<String, UsbDeviceFingerprint> mFingerprints = new ArrayMap<>();
    private final ArrayMap<String, UsbDevice> mDevices = new ArrayMap<>();

    static class LocalSerialReader extends IUsbSerialReader.Stub {
        String mSerialNumber;

        LocalSerialReader(String serialNumber) {
            mSerialNumber = serialNumber;
        }

        @Override
        public String getSerial(String packageName) {
            return mSerialNumber;
        }
    }

    // Create a fingerprint with a unique hash. Both device + hashcode must match for a fingerprint
    // to be considered the same so use this to create multiple fingerprints for a single device.
    UsbDeviceFingerprint createFingerprint(UsbDevice device, int hashCode) {
        DeviceFilter df = new DeviceFilter(device);
        byte[] hashBytes =
                new byte[] {
                    (byte) (hashCode >> 24),
                    (byte) (hashCode >> 16),
                    (byte) (hashCode >> 8),
                    (byte) hashCode
                };
        UsbDeviceFingerprint.Hashcode descriptorHash =
                UsbDeviceFingerprint.Hashcode.fromString(
                        Base64.getEncoder().encodeToString(hashBytes));
        UsbDeviceFingerprint.Hashcode emptyHashcode =
                UsbDeviceFingerprint.Hashcode.createEmptyHashcode();
        return new UsbDeviceFingerprint(df, descriptorHash, emptyHashcode, 0, false);
    }

    private UsbDevice getDeviceCopy(String name, String serialNumber) {
        return new UsbDevice.Builder(
                        name, // name
                        0x1234, // vendorId
                        0x5678, // productId
                        0,
                        0,
                        0,
                        "Google",
                        "Fake Product",
                        "version",
                        new UsbConfiguration[0], // configurations
                        "", // empty serial number -- need to spy + return later
                        false,
                        false,
                        false,
                        false,
                        false // unused fields
                        )
                .build(new LocalSerialReader(serialNumber));
    }

    // Helper record type for test data.
    public record TestData(
            String deviceAddress,
            UsbAuthDeviceInfo deviceInfo,
            UsbDevice device,
            UsbDeviceFingerprint fingerprint) {}

    private ArrayMap<Integer, TestData> mTestData = new ArrayMap<>();

    private static final Integer FAKE0 = 0;
    private static final Integer FAKE1 = 1;

    private AutoCloseable mMocks;

    @Before
    public void setUp() throws Exception {
        LocalServices.removeAllServicesForTest();
        mMocks = MockitoAnnotations.openMocks(this);

        when(mService.asBinder()).thenReturn(mBinder);

        when(mUserManager.getUserInfo(TEST_USER_ID_FULL_ADMIN))
                .thenReturn(TEST_USER_INFO_FULL_ADMIN);
        when(mUserManager.getUserInfo(TEST_USER_ID_GUEST)).thenReturn(TEST_USER_INFO_GUEST);

        mFingerprints.clear();
        mDevices.clear();

        when(mHostManager.getConnectedDeviceFingerprintForAddress(anyString()))
                .thenAnswer(
                        (invocation) -> {
                            String deviceAddress = invocation.getArgument(0);
                            return mFingerprints.get(deviceAddress);
                        });
        when(mHostManager.getConnectedDeviceForAddress(anyString()))
                .thenAnswer(
                        (invocation) -> {
                            String deviceAddress = invocation.getArgument(0);
                            return mDevices.get(deviceAddress);
                        });

        createTestData();

        mDeviceProvisionedListener = spy(new UsbAuthManager.ProvisioningListener(mContext, null));
        doReturn(false).when(mDeviceProvisionedListener).isDeviceInSetup();

        mUsbAuthManager =
                new UsbAuthManager(
                        mContext, mUserManager, mHostManager, mService, mDeviceProvisionedListener);
        mDeviceProvisionedListener.setAuthManager(mUsbAuthManager);
    }

    @After
    public void tearDown() throws Exception {
        mMocks.close();
    }

    private void createTestData() {
        String address0 = UsbDevice.getDeviceName(1, 1);
        String address1 = UsbDevice.getDeviceName(1, 2);

        UsbAuthDeviceInfo deviceInfo0 = new UsbAuthDeviceInfo();
        deviceInfo0.busNumber = 1;
        deviceInfo0.deviceNumber = 1;

        UsbAuthDeviceInfo deviceInfo1 = new UsbAuthDeviceInfo();
        deviceInfo1.busNumber = 1;
        deviceInfo1.deviceNumber = 2;

        UsbDevice device0 = getDeviceCopy(address0, "");
        UsbDevice device1 = getDeviceCopy(address1, "");

        UsbDeviceFingerprint fingerprint0 = createFingerprint(device0, 12345);
        UsbDeviceFingerprint fingerprint1 = createFingerprint(device1, 34567);

        mTestData.clear();
        mTestData.put(FAKE0, new TestData(address0, deviceInfo0, device0, fingerprint0));
        mTestData.put(FAKE1, new TestData(address1, deviceInfo1, device1, fingerprint1));
    }

    private void addConnectedDevice(TestData data) {
        mFingerprints.put(data.deviceAddress, data.fingerprint);
        mDevices.put(data.deviceAddress, data.device);
    }

    @Test
    public void testInitialState_isBooted() throws Exception {
        verify(mService).setSystemState(UsbAuthorizationSystemState.BOOTED);
    }

    @Test
    public void testSetup_withAndWithoutChanges() throws Exception {
        reset(mService);
        doReturn(true).when(mDeviceProvisionedListener).isDeviceInSetup();

        mUsbAuthManager.onUpdateScreenLockedState(true);
        verify(mService).setSystemState(UsbAuthorizationSystemState.SET_UP);
        mUsbAuthManager.onUpdateLoggedInState(true, TEST_USER_ID_FULL_ADMIN);
        verify(mService).setSystemState(UsbAuthorizationSystemState.SET_UP);

        doReturn(false).when(mDeviceProvisionedListener).isDeviceInSetup();
        mDeviceProvisionedListener.onChange(false);
        verify(mService).setSystemState(UsbAuthorizationSystemState.SCREEN_LOCKED);
    }

    @Test
    public void testLoginFullUser_screenUnlocked_isLoggedIn() throws Exception {
        reset(mService);
        mUsbAuthManager.onUpdateLoggedInState(true, TEST_USER_ID_FULL_ADMIN);
        mUsbAuthManager.onUpdateScreenLockedState(false);
        verify(mService).setSystemState(UsbAuthorizationSystemState.LOGGED_IN);
    }


    @Test
    public void testLoginFullUser_screenLocked_isScreenLocked() throws Exception {
        mUsbAuthManager.onUpdateLoggedInState(true, TEST_USER_ID_FULL_ADMIN);
        mUsbAuthManager.onUpdateScreenLockedState(true);
        verify(mService).setSystemState(UsbAuthorizationSystemState.SCREEN_LOCKED);
    }

    @Test
    public void testLoginGuestUser_screenUnlocked_isScreenLocked() throws Exception {
        mUsbAuthManager.onUpdateLoggedInState(true, TEST_USER_ID_FULL_ADMIN);
        reset(mService);

        mUsbAuthManager.onUpdateLoggedInState(true, TEST_USER_ID_GUEST);
        mUsbAuthManager.onUpdateScreenLockedState(false);

        verify(mService).setSystemState(UsbAuthorizationSystemState.SCREEN_LOCKED);
    }

    @Test
    public void testLogoutFullUser_noOtherFullUsers_isBooted() throws Exception {
        mUsbAuthManager.onUpdateLoggedInState(true, TEST_USER_ID_FULL_ADMIN);
        reset(mService);

        mUsbAuthManager.onUpdateLoggedInState(false, TEST_USER_ID_FULL_ADMIN);
        verify(mService).setSystemState(UsbAuthorizationSystemState.BOOTED);
    }

    @Test
    public void testScreenLockAndUnlock_changesState() throws Exception {
        mUsbAuthManager.onUpdateLoggedInState(true, TEST_USER_ID_FULL_ADMIN);
        mUsbAuthManager.onUpdateScreenLockedState(false);
        verify(mService).setSystemState(UsbAuthorizationSystemState.LOGGED_IN);
        reset(mService);

        mUsbAuthManager.onUpdateScreenLockedState(true);
        verify(mService).setSystemState(UsbAuthorizationSystemState.SCREEN_LOCKED);
        reset(mService);

        mUsbAuthManager.onUpdateScreenLockedState(false);
        verify(mService).setSystemState(UsbAuthorizationSystemState.LOGGED_IN);
    }

    @Test
    public void testCallbacks_onDeviceAuthorizationStatusChanged() throws Exception {
        TestData fake0 = mTestData.get(FAKE0);
        TestData fake1 = mTestData.get(FAKE1);

        // Add the fake address. Should block here because that device is new and not authorized.
        addConnectedDevice(fake0);
        mUsbAuthManager.usbDeviceAdded(fake0.deviceAddress);
        verify(mHostManager, never()).usbDeviceAuthorized(fake0.deviceAddress);

        // Denial should result in no calls to host manager.
        mUsbAuthManager
                .getEventsListenerForTest()
                .onDeviceAuthorizationStatusChanged(
                        fake0.deviceInfo, DENIED, UsbAuthorizationSystemState.BOOTED);
        verify(mHostManager, never()).usbDeviceAuthorized(fake0.deviceAddress);
        verify(mHostManager, never()).usbDeviceDeauthorized(fake0.deviceAddress);

        // First authorization should result in authorized being called.
        mUsbAuthManager
                .getEventsListenerForTest()
                .onDeviceAuthorizationStatusChanged(
                        fake0.deviceInfo, AUTHORIZED, UsbAuthorizationSystemState.BOOTED);
        verify(mHostManager).usbDeviceAuthorized(fake0.deviceAddress);
        verify(mHostManager, never()).usbDeviceDeauthorized(fake0.deviceAddress);

        // Deauthorizing should result in dauthorize being called.
        mUsbAuthManager
                .getEventsListenerForTest()
                .onDeviceAuthorizationStatusChanged(
                        fake0.deviceInfo, DENIED, UsbAuthorizationSystemState.BOOTED);
        verify(mHostManager).usbDeviceDeauthorized(fake0.deviceAddress);

        // Next, check that we block until the device is seen from the host as well.
        mUsbAuthManager
                .getEventsListenerForTest()
                .onDeviceAuthorizationStatusChanged(
                        fake1.deviceInfo, AUTHORIZED, UsbAuthorizationSystemState.BOOTED);
        verify(mHostManager, never()).usbDeviceAuthorized(fake1.deviceAddress);

        addConnectedDevice(fake1);
        mUsbAuthManager.usbDeviceAdded(fake1.deviceAddress);
        verify(mHostManager).usbDeviceAuthorized(fake1.deviceAddress);
    }

    @Test
    public void testCallbacks_askDevice() throws Exception {
        TestData fake0 = mTestData.get(FAKE0);
        TestData fake1 = mTestData.get(FAKE1);

        // First add the device and then send an Ask request.
        // This should result in the ask being handled right away on the event.
        addConnectedDevice(fake0);
        mUsbAuthManager.usbDeviceAdded(fake0.deviceAddress);

        verify(mService, never()).setAuthorizationStatus(fake0.deviceInfo, AUTHORIZED);
        mUsbAuthManager.getEventsListenerForTest().onDeviceAskForAuthorization(fake0.deviceInfo);
        verify(mService).setAuthorizationStatus(fake0.deviceInfo, AUTHORIZED);

        // Now try the other order. The Ask result won't occur until the device is added.
        mUsbAuthManager.getEventsListenerForTest().onDeviceAskForAuthorization(fake1.deviceInfo);
        verify(mService, never()).setAuthorizationStatus(fake1.deviceInfo, AUTHORIZED);

        addConnectedDevice(fake1);
        mUsbAuthManager.usbDeviceAdded(fake1.deviceAddress);
        verify(mService).setAuthorizationStatus(fake1.deviceInfo, AUTHORIZED);
    }

    @Test
    public void testCallbacks_allowPersistDevice() throws Exception {
        TestData fake0 = mTestData.get(FAKE0);
        TestData fake1 = mTestData.get(FAKE1);

        // First add the device and then send a check persisted request.
        // This should result in the ask being handled right away on the event.
        addConnectedDevice(fake0);
        mUsbAuthManager.usbDeviceAdded(fake0.deviceAddress);

        verify(mService, never()).setAuthorizationStatus(fake0.deviceInfo, DENIED);
        mUsbAuthManager
                .getEventsListenerForTest()
                .onDeviceCheckPersistedAuthorization(fake0.deviceInfo);
        verify(mService).setAuthorizationStatus(fake0.deviceInfo, DENIED);

        // Push the fingerprint to persisted list. The same check should now pass.
        mUsbAuthManager.addFingerprintToPersistedForTest(fake0.fingerprint);
        mUsbAuthManager
                .getEventsListenerForTest()
                .onDeviceCheckPersistedAuthorization(fake0.deviceInfo);
        verify(mService).setAuthorizationStatus(fake0.deviceInfo, AUTHORIZED);

        // Now try the other order. The check persisted result won't occur until the device is
        // added.
        mUsbAuthManager
                .getEventsListenerForTest()
                .onDeviceCheckPersistedAuthorization(fake1.deviceInfo);
        verify(mService, never()).setAuthorizationStatus(fake1.deviceInfo, DENIED);

        addConnectedDevice(fake1);
        mUsbAuthManager.usbDeviceAdded(fake1.deviceAddress);
        verify(mService).setAuthorizationStatus(fake1.deviceInfo, DENIED);
    }
}
