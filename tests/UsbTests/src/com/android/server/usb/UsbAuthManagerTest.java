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

import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.UserInfo;
import android.hardware.usb.IUsbAuthManager;
import android.hardware.usb.UsbAuthorizationSystemState;
import android.os.IBinder;
import android.os.UserManager;
import androidx.test.runner.AndroidJUnit4;
import com.android.server.LocalServices;
import com.android.server.usb.flags.Flags;
import android.platform.test.flag.junit.SetFlagsRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Tests for {@link com.android.server.usb.UsbAuthManager} atest UsbTests:UsbAuthManagerTest */
@RunWith(AndroidJUnit4.class)
public class UsbAuthManagerTest {
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();
    @Mock private Context mContext;
    @Mock private UserManager mUserManager;
    @Mock private IUsbAuthManager mService;
    @Mock private IBinder mBinder;

    private UsbAuthManager mUsbAuthManager;

    private static final int TEST_USER_ID_FULL_ADMIN = 10;
    private static final int TEST_USER_ID_GUEST = 11;
    private static final int TEST_USER_ID_ADMIN_ONLY = 12;

    private static final UserInfo TEST_USER_INFO_FULL_ADMIN =
            new UserInfo(
                    TEST_USER_ID_FULL_ADMIN,
                    "full admin",
                    UserInfo.FLAG_FULL | UserInfo.FLAG_ADMIN);
    private static final UserInfo TEST_USER_INFO_GUEST =
            new UserInfo(TEST_USER_ID_GUEST, "guest", 0);
    private static final UserInfo TEST_USER_INFO_ADMIN_ONLY =
            new UserInfo(TEST_USER_ID_ADMIN_ONLY, "admin", UserInfo.FLAG_ADMIN);

    @Before
    public void setUp() throws Exception {
        mSetFlagsRule.enableFlags(Flags.FLAG_ENABLE_USB_AUTHORIZATION);
        LocalServices.removeAllServicesForTest();
        MockitoAnnotations.initMocks(this);

        when(mService.asBinder()).thenReturn(mBinder);

        when(mUserManager.getUserInfo(TEST_USER_ID_FULL_ADMIN))
                .thenReturn(TEST_USER_INFO_FULL_ADMIN);
        when(mUserManager.getUserInfo(TEST_USER_ID_GUEST)).thenReturn(TEST_USER_INFO_GUEST);
        when(mUserManager.getUserInfo(TEST_USER_ID_ADMIN_ONLY))
                .thenReturn(TEST_USER_INFO_ADMIN_ONLY);

        mUsbAuthManager = new UsbAuthManager(mContext, mUserManager, mService);
    }

    @Test
    public void testInitialState_isBooted() throws Exception {
        verify(mService).setSystemState(UsbAuthorizationSystemState.BOOTED);
    }

    @Test
    public void testLoginFullUser_screenUnlocked_isLoggedIn() throws Exception {
        reset(mService);
        mUsbAuthManager.onUpdateLoggedInState(true, TEST_USER_ID_FULL_ADMIN);
        mUsbAuthManager.onUpdateScreenLockedState(false);
        verify(mService).setSystemState(UsbAuthorizationSystemState.LOGGED_IN);
    }

    @Test
    public void testLoginAdminOnlyUser_screenUnlocked_isLoggedIn() throws Exception {
        reset(mService);
        mUsbAuthManager.onUpdateLoggedInState(true, TEST_USER_ID_ADMIN_ONLY);
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
}
