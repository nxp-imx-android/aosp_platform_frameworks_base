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

package com.android.server.security.authenticationpolicy;

import static android.hardware.biometrics.BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED;
import static android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS;
import static android.os.UserManager.DISALLOW_USER_SWITCH;
import static android.security.Flags.FLAG_SECURE_LOCKDOWN;
import static android.security.Flags.FLAG_SECURE_LOCK_DEVICE;
import static android.security.authenticationpolicy.AuthenticationPolicyManager.ERROR_ALREADY_ENABLED;
import static android.security.authenticationpolicy.AuthenticationPolicyManager.ERROR_INSUFFICIENT_BIOMETRICS;
import static android.security.authenticationpolicy.AuthenticationPolicyManager.ERROR_NOT_AUTHORIZED;
import static android.security.authenticationpolicy.AuthenticationPolicyManager.ERROR_NO_BIOMETRICS_ENROLLED;
import static android.security.authenticationpolicy.AuthenticationPolicyManager.ERROR_UNKNOWN;
import static android.security.authenticationpolicy.AuthenticationPolicyManager.SUCCESS;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.hardware.biometrics.BiometricManager;
import android.os.IPowerManager;
import android.os.IThermalService;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.UserHandle;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.SetFlagsRule;
import android.security.authenticationpolicy.DisableSecureLockDeviceParams;
import android.security.authenticationpolicy.EnableSecureLockDeviceParams;
import android.testing.TestableContext;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.internal.util.test.LocalServiceKeeperRule;
import com.android.server.wm.WindowManagerInternal;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/**
 * atest FrameworksServicesTests:SecureLockDeviceServiceTest
 */
@Presubmit
@SmallTest
@EnableFlags({FLAG_SECURE_LOCKDOWN, FLAG_SECURE_LOCK_DEVICE})
@RunWith(AndroidJUnit4.class)
public class SecureLockDeviceServiceTest {
    private static final int TEST_USER_ID = 0;
    private static final int OTHER_USER_ID = 1;

    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();
    @Rule public final TestableContext mContext = new TestableContext(
            InstrumentationRegistry.getInstrumentation().getTargetContext(), null);
    @Rule public LocalServiceKeeperRule mLocalServiceKeeperRule = new LocalServiceKeeperRule();
    @Rule public MockitoRule mockito = MockitoJUnit.rule();

    @Mock private ActivityManager mActivityManager;
    @Mock private BiometricManager mBiometricManager;
    @Mock private DevicePolicyManager mDevicePolicyManager;
    @Mock private IPowerManager mIPowerManager;
    @Mock private IThermalService mThermalService;
    @Mock private SecureLockDeviceServiceInternal mSecureLockDeviceServiceInternal;
    @Mock private WindowManagerInternal mWindowManagerInternal;

    private final EnableSecureLockDeviceParams mEnableParams =
            new EnableSecureLockDeviceParams("test");
    private final DisableSecureLockDeviceParams mDisableParams =
            new DisableSecureLockDeviceParams("test");
    private final UserHandle mUser = UserHandle.of(TEST_USER_ID);
    private final UserHandle mOtherUser = UserHandle.of(OTHER_USER_ID);

    private SecureLockDeviceService mSecureLockDeviceService;
    private SecureLockDeviceService.SecureLockDeviceStore mSecureLockDeviceStore;

    @SuppressLint("VisibleForTests")
    @Before
    public void setUp() {
        // Unable to mock PowerManager directly because final class
        mContext.addMockSystemService(ActivityManager.class, mActivityManager);
        mContext.addMockSystemService(PowerManager.class,
                new PowerManager(mContext, mIPowerManager, mThermalService, null));
        mContext.addMockSystemService(BiometricManager.class, mBiometricManager);
        mContext.addMockSystemService(DevicePolicyManager.class, mDevicePolicyManager);

        when(mActivityManager.isProfileForeground(eq(mUser))).thenReturn(true);
        mLocalServiceKeeperRule.overrideLocalService(SecureLockDeviceServiceInternal.class,
                mSecureLockDeviceServiceInternal);
        mLocalServiceKeeperRule.overrideLocalService(WindowManagerInternal.class,
                mWindowManagerInternal);

        mSecureLockDeviceService = new SecureLockDeviceService(mContext);
        mSecureLockDeviceStore = mSecureLockDeviceService.getStore();
        mSecureLockDeviceService.onLockSettingsReady();
    }

    @After
    public void tearDown() throws Exception {
        disableSecureLockDevice(mUser);
        disableSecureLockDevice(mOtherUser);
    }

    @Test
    public void enableSecureLockDevice_goesToSleep_locksDevice() throws RemoteException {
        setupBiometrics(TEST_USER_ID, true /* hasBiometricEnrolled */,
                true /* isStrongBiometric */);

        enableSecureLockDevice(mUser);

        verify(mIPowerManager).goToSleep(anyLong(), anyInt(), anyInt());
        verify(mWindowManagerInternal).lockNow();
    }

    @Test
    public void disableSecureLockDevice_asUnauthorizedUser_returnsNotAuthorized() {
        setupBiometrics(TEST_USER_ID, true /* hasBiometricEnrolled */,
                true /* isStrongBiometric */);
        assertThat(enableSecureLockDevice(mUser)).isEqualTo(SUCCESS);

        assertThat(mSecureLockDeviceService.isSecureLockDeviceEnabled()).isTrue();
        assertThat(mSecureLockDeviceStore.retrieveSecureLockDeviceClientId()).isEqualTo(
                TEST_USER_ID);

        assertThat(disableSecureLockDevice(mOtherUser)).isEqualTo(ERROR_NOT_AUTHORIZED);
        assertThat(mSecureLockDeviceService.isSecureLockDeviceEnabled()).isTrue();
        assertThat(mSecureLockDeviceStore.retrieveSecureLockDeviceClientId()).isEqualTo(
                TEST_USER_ID);
    }

    @Test
    public void disableSecureLockDevice_asAuthorizedUser_returnsSuccess() {
        setupBiometrics(TEST_USER_ID, true /* hasBiometricEnrolled */,
                true /* isStrongBiometric */);
        assertThat(enableSecureLockDevice(mUser)).isEqualTo(SUCCESS);

        assertThat(mSecureLockDeviceService.isSecureLockDeviceEnabled()).isTrue();
        assertThat(mSecureLockDeviceStore.retrieveSecureLockDeviceClientId()).isEqualTo(
                TEST_USER_ID);

        int disableResult = disableSecureLockDevice(mUser);
        assertThat(disableResult).isEqualTo(SUCCESS);

        assertThat(mSecureLockDeviceService.isSecureLockDeviceEnabled()).isFalse();
        assertThat(mSecureLockDeviceStore.retrieveSecureLockDeviceClientId())
                .isEqualTo(UserHandle.USER_NULL);
        verify(mDevicePolicyManager).clearUserRestrictionGlobally(
                eq(SecureLockDeviceService.class.getSimpleName()), eq(DISALLOW_USER_SWITCH));
    }

    @Test
    public void enableSecureLockDeviceReturnsError_whenAlreadyEnabled() {
        setupBiometrics(TEST_USER_ID, true /* hasBiometricEnrolled */,
                true /* isStrongBiometric */);
        enableSecureLockDevice(mUser);

        boolean isSecureLockDeviceEnabled = mSecureLockDeviceService.isSecureLockDeviceEnabled();
        int enableSecureLockDeviceRequestStatus = enableSecureLockDevice(mUser);

        assertThat(isSecureLockDeviceEnabled).isTrue();
        assertThat(enableSecureLockDeviceRequestStatus).isEqualTo(ERROR_ALREADY_ENABLED);
    }

    @Test
    public void enableSecureLockDevice_userSwitchFails_returnsErrorUnknown() {
        setupBiometrics(TEST_USER_ID, true, true);
        when(mActivityManager.isProfileForeground(eq(mUser))).thenReturn(false);
        when(mActivityManager.switchUser(eq(TEST_USER_ID))).thenReturn(false);

        assertThat(enableSecureLockDevice(mUser)).isEqualTo(ERROR_UNKNOWN);
    }

    @Test
    public void enableSecureLockDevice_switchesCallingUserToForeground_restrictsUserSwitching() {
        setupBiometrics(OTHER_USER_ID, true, true);

        // Mock mUser as current user, successful switch to mOtherUser
        when(mActivityManager.isProfileForeground(eq(mOtherUser))).thenReturn(false)
                .thenReturn(true);
        when(mActivityManager.switchUser(eq(mOtherUser))).thenReturn(true);

        assertThat(enableSecureLockDevice(mOtherUser)).isEqualTo(SUCCESS);

        assertThat(mSecureLockDeviceService.isSecureLockDeviceEnabled()).isTrue();
        assertThat(mSecureLockDeviceStore.retrieveSecureLockDeviceClientId()).isEqualTo(
                OTHER_USER_ID);

        verify(mDevicePolicyManager).addUserRestrictionGlobally(
                eq(SecureLockDeviceService.class.getSimpleName()), eq(DISALLOW_USER_SWITCH));
    }

    @Test
    public void secureLockDevice_checksBiometricEnrollmentsOfCallingUser() {
        // Calling user TEST_USER_ID has no biometrics enrolled
        setupBiometrics(TEST_USER_ID, false /* hasBiometricEnrolled */,
                false /* isStrongBiometric */);

        // Some other user on the device has strong biometrics enrolled
        setupBiometrics(OTHER_USER_ID, true /* hasBiometricEnrolled */,
                true /* isStrongBiometric */);

        int isSecureLockDeviceAvailable =
                mSecureLockDeviceService.isSecureLockDeviceAvailable(mUser);
        int enableSecureLockDeviceRequestStatus = enableSecureLockDevice(mUser);

        assertThat(isSecureLockDeviceAvailable).isEqualTo(ERROR_NO_BIOMETRICS_ENROLLED);
        assertThat(enableSecureLockDeviceRequestStatus).isEqualTo(ERROR_NO_BIOMETRICS_ENROLLED);
    }

    @Test
    public void secureLockDeviceUnavailable_whenMissingStrongBiometricEnrollment() {
        setupBiometrics(TEST_USER_ID, true /* hasBiometricEnrolled */,
                false /* isStrongBiometric */);

        int isSecureLockDeviceAvailable =
                mSecureLockDeviceService.isSecureLockDeviceAvailable(mUser);
        int enableSecureLockDeviceRequestStatus = enableSecureLockDevice(mUser);

        assertThat(isSecureLockDeviceAvailable).isEqualTo(ERROR_INSUFFICIENT_BIOMETRICS);
        assertThat(enableSecureLockDeviceRequestStatus).isEqualTo(ERROR_INSUFFICIENT_BIOMETRICS);
    }

    @Test
    public void isSecureLockDeviceAvailable_noBiometricsEnrolled() {
        setupBiometrics(TEST_USER_ID, false /* hasBiometricEnrolled */,
                false /* isStrongBiometric */);

        int isSecureLockDeviceAvailable =
                mSecureLockDeviceService.isSecureLockDeviceAvailable(mUser);
        int enableSecureLockDeviceRequestStatus = enableSecureLockDevice(mUser);

        assertThat(isSecureLockDeviceAvailable).isEqualTo(ERROR_NO_BIOMETRICS_ENROLLED);
        assertThat(enableSecureLockDeviceRequestStatus).isEqualTo(ERROR_NO_BIOMETRICS_ENROLLED);
    }

    @Test
    public void isSecureLockDeviceAvailable_success() {
        setupBiometrics(TEST_USER_ID, true /* hasBiometricEnrolled */,
                true /* isStrongBiometric */);

        int isSecureLockDeviceAvailable =
                mSecureLockDeviceService.isSecureLockDeviceAvailable(mUser);
        int enableSecureLockDeviceRequestStatus = enableSecureLockDevice(mUser);

        assertThat(isSecureLockDeviceAvailable).isEqualTo(SUCCESS);
        assertThat(enableSecureLockDeviceRequestStatus).isEqualTo(SUCCESS);
    }

    @Test
    public void isSecureLockDeviceEnabled_updatesState() {
        setupBiometrics(TEST_USER_ID, true /* hasBiometricEnrolled */,
                true /* isStrongBiometric */);

        boolean isSecureLockDeviceEnabled = mSecureLockDeviceService.isSecureLockDeviceEnabled();
        assertThat(isSecureLockDeviceEnabled).isFalse();

        enableSecureLockDevice(mUser);
        isSecureLockDeviceEnabled = mSecureLockDeviceService.isSecureLockDeviceEnabled();

        assertThat(isSecureLockDeviceEnabled).isTrue();

        disableSecureLockDevice(mUser);
        isSecureLockDeviceEnabled = mSecureLockDeviceService.isSecureLockDeviceEnabled();

        assertThat(isSecureLockDeviceEnabled).isFalse();
    }

    private void setupBiometrics(int userId, boolean hasBiometricEnrolled,
            boolean isStrongBiometric) {
        when(mBiometricManager.hasEnrolledBiometrics(eq(userId))).thenReturn(
                hasBiometricEnrolled);
        when(mBiometricManager.canAuthenticate(eq(userId),
                eq(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        )).thenReturn(hasBiometricEnrolled && isStrongBiometric
                ? BIOMETRIC_SUCCESS : BIOMETRIC_ERROR_NONE_ENROLLED);
    }

    private int enableSecureLockDevice(UserHandle user) {
        return mSecureLockDeviceService.enableSecureLockDevice(user, mEnableParams);
    }

    private int disableSecureLockDevice(UserHandle user) {
        return mSecureLockDeviceService.disableSecureLockDevice(user, mDisableParams);
    }
}
