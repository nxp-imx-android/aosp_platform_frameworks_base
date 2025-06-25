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

import static android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG;
import static android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK;
import static android.hardware.biometrics.SensorProperties.STRENGTH_STRONG;
import static android.hardware.biometrics.SensorProperties.STRENGTH_WEAK;
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
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.hardware.biometrics.BiometricEnrollmentStatus;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricStateListener;
import android.hardware.biometrics.SensorProperties;
import android.hardware.face.FaceManager;
import android.hardware.fingerprint.FingerprintManager;
import android.os.IBinder;
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
import android.security.authenticationpolicy.ISecureLockDeviceStatusListener;
import android.testing.TestableContext;
import android.util.ArrayMap;

import androidx.annotation.NonNull;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final UserHandle mUser = new UserHandle(TEST_USER_ID);
    private final UserHandle mOtherUser = new UserHandle(OTHER_USER_ID);

    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();
    @Rule public final SecureLockDeviceContext mTestContext = new SecureLockDeviceContext(
            InstrumentationRegistry.getInstrumentation().getTargetContext(), mUser, mOtherUser);
    @Rule public LocalServiceKeeperRule mLocalServiceKeeperRule = new LocalServiceKeeperRule();
    @Rule public MockitoRule mockito = MockitoJUnit.rule();

    @Captor private ArgumentCaptor<BiometricStateListener> mBiometricStateListenerCaptor;
    @Captor private ArgumentCaptor<Integer> mSecureLockDeviceAvailableStatusArgumentCaptor;
    @Captor private ArgumentCaptor<Boolean> mSecureLockDeviceEnabledStatusArgumentCaptor;

    @Mock private ActivityManager mActivityManager;
    @Mock private AuthenticationPolicyService mAuthenticationPolicyService;
    @Mock private BiometricManager mBiometricManager;
    @Mock private BiometricManager mUserBiometricManager;
    @Mock private BiometricManager mOtherUserBiometricManager;
    @Mock private DevicePolicyManager mDevicePolicyManager;
    @Mock private FaceManager mFaceManager;
    @Mock private FingerprintManager mFingerprintManager;
    @Mock private IBinder mSecureLockDeviceStatusListenerBinder;
    @Mock private IBinder mSecureLockDeviceStatusOtherListenerBinder;
    @Mock private IPowerManager mIPowerManager;
    @Mock private ISecureLockDeviceStatusListener mSecureLockDeviceStatusListener;
    // For OTHER_USER_ID
    @Mock private ISecureLockDeviceStatusListener mSecureLockDeviceStatusOtherListener;
    @Mock private IThermalService mThermalService;
    @Mock private SecureLockDeviceServiceInternal mSecureLockDeviceServiceInternal;
    @Mock private WindowManagerInternal mWindowManagerInternal;

    private final EnableSecureLockDeviceParams mEnableParams =
            new EnableSecureLockDeviceParams("test");
    private final DisableSecureLockDeviceParams mDisableParams =
            new DisableSecureLockDeviceParams("test");
    private SecureLockDeviceService mSecureLockDeviceService;
    private SecureLockDeviceService.SecureLockDeviceStore mSecureLockDeviceStore;

    @SuppressLint("VisibleForTests")
    @Before
    public void setUp() throws Exception {
        // Mock user-aware BiometricManager retrieval
        mTestContext.mockBiometricManagerForUser(mUser, mUserBiometricManager);
        mTestContext.mockBiometricManagerForUser(mOtherUser, mOtherUserBiometricManager);

        // Mock system services
        mTestContext.addMockSystemService(ActivityManager.class, mActivityManager);
        mTestContext.addMockSystemService(PowerManager.class,
                new PowerManager(mTestContext, mIPowerManager, mThermalService, null));
        mTestContext.addMockSystemService(BiometricManager.class, mBiometricManager);
        mTestContext.addMockSystemService(DevicePolicyManager.class, mDevicePolicyManager);
        mTestContext.addMockSystemService((FaceManager.class), mFaceManager);
        mTestContext.addMockSystemService((FingerprintManager.class), mFingerprintManager);

        when(mActivityManager.isProfileForeground(eq(mUser))).thenReturn(true);
        when(mSecureLockDeviceStatusListener.asBinder())
                .thenReturn(mSecureLockDeviceStatusListenerBinder);
        when(mSecureLockDeviceStatusOtherListener.asBinder())
                .thenReturn(mSecureLockDeviceStatusOtherListenerBinder);

        mLocalServiceKeeperRule.overrideLocalService(AuthenticationPolicyService.class,
                mAuthenticationPolicyService);
        mLocalServiceKeeperRule.overrideLocalService(SecureLockDeviceServiceInternal.class,
                mSecureLockDeviceServiceInternal);
        mLocalServiceKeeperRule.overrideLocalService(WindowManagerInternal.class,
                mWindowManagerInternal);

        mSecureLockDeviceService = new SecureLockDeviceService(mTestContext);
        mSecureLockDeviceStore = mSecureLockDeviceService.getStore();
        mSecureLockDeviceService.onLockSettingsReady();
        mSecureLockDeviceService.onBootCompleted();
    }

    @SuppressLint("VisibleForTests")
    @After
    public void tearDown() throws Exception {
        disableSecureLockDevice(mUser);
        disableSecureLockDevice(mOtherUser);
    }

    @Test
    public void enableSecureLockDevice_goesToSleep_locksDevice() throws RemoteException {
        setupBiometricState(
                true, /* deviceHasStrongBiometricSensor */
                true, /* primaryUserHasStrongBiometricEnrollment */
                false /* otherUserHasStrongBiometricEnrollment */
        );

        enableSecureLockDevice(mUser);

        verify(mIPowerManager).goToSleep(anyLong(), anyInt(), anyInt());
        verify(mWindowManagerInternal).lockNow();
    }

    @Test
    public void disableSecureLockDevice_asUnauthorizedUser_returnsNotAuthorized() {
        setupBiometricState(
                true, /* deviceHasStrongBiometricSensor */
                true, /* primaryUserHasStrongBiometricEnrollment */
                false /* otherUserHasStrongBiometricEnrollment */
        );
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
        setupBiometricState(
                true, /* deviceHasStrongBiometricSensor */
                true, /* primaryUserHasStrongBiometricEnrollment */
                false /* otherUserHasStrongBiometricEnrollment */
        );
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
        setupBiometricState(
                true, /* deviceHasStrongBiometricSensor */
                true, /* primaryUserHasStrongBiometricEnrollment */
                false /* otherUserHasStrongBiometricEnrollment */
        );
        enableSecureLockDevice(mUser);

        boolean isSecureLockDeviceEnabled = mSecureLockDeviceService.isSecureLockDeviceEnabled();
        int enableSecureLockDeviceRequestStatus = enableSecureLockDevice(mUser);

        assertThat(isSecureLockDeviceEnabled).isTrue();
        assertThat(enableSecureLockDeviceRequestStatus).isEqualTo(ERROR_ALREADY_ENABLED);
    }

    @Test
    public void enableSecureLockDevice_userSwitchFails_returnsErrorUnknown() {
        setupBiometricState(
                true, /* deviceHasStrongBiometricSensor */
                true, /* primaryUserHasStrongBiometricEnrollment */
                false /* otherUserHasStrongBiometricEnrollment */
        );
        when(mActivityManager.isProfileForeground(eq(mUser))).thenReturn(false);
        when(mActivityManager.switchUser(eq(TEST_USER_ID))).thenReturn(false);

        assertThat(enableSecureLockDevice(mUser)).isEqualTo(ERROR_UNKNOWN);
    }

    @Test
    public void enableSecureLockDevice_switchesCallingUserToForeground_restrictsUserSwitching() {
        setupBiometricState(
                true, /* deviceHasStrongBiometricSensor */
                false, /* primaryUserHasStrongBiometricEnrollment */
                true /* otherUserHasStrongBiometricEnrollment */
        );

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
        // Calling user TEST_USER_ID has no biometrics enrolled, but some other user on the
        // device has strong biometrics enrolled
        setupBiometricState(
                true, /* deviceHasStrongBiometricSensor */
                false, /* primaryUserHasStrongBiometricEnrollment */
                true /* otherUserHasStrongBiometricEnrollment */
        );

        int secureLockDeviceAvailability =
                mSecureLockDeviceService.getSecureLockDeviceAvailability(mUser);
        int enableSecureLockDeviceRequestStatus = enableSecureLockDevice(mUser);

        assertThat(secureLockDeviceAvailability).isEqualTo(ERROR_NO_BIOMETRICS_ENROLLED);
        assertThat(enableSecureLockDeviceRequestStatus).isEqualTo(ERROR_NO_BIOMETRICS_ENROLLED);
    }

    @Test
    public void secureLockDeviceUnavailable_whenNoStrongBiometricSensors() {
        setupBiometricState(
                false, /* deviceHasStrongBiometricSensor */
                false, /* primaryUserHasStrongBiometricEnrollment */
                false /* otherUserHasStrongBiometricEnrollment */
        );

        int secureLockDeviceAvailability =
                mSecureLockDeviceService.getSecureLockDeviceAvailability(mUser);
        int enableSecureLockDeviceRequestStatus = enableSecureLockDevice(mUser);

        assertThat(secureLockDeviceAvailability).isEqualTo(ERROR_INSUFFICIENT_BIOMETRICS);
        assertThat(enableSecureLockDeviceRequestStatus).isEqualTo(ERROR_INSUFFICIENT_BIOMETRICS);
    }

    @Test
    public void getSecureLockDeviceAvailability_whenMissingStrongBiometricEnrollments() {
        setupBiometricState(
                true, /* deviceHasStrongBiometricSensor */
                false, /* primaryUserHasStrongBiometricEnrollment */
                false /* otherUserHasStrongBiometricEnrollment */
        );
        int secureLockDeviceAvailability =
                mSecureLockDeviceService.getSecureLockDeviceAvailability(mUser);
        int enableSecureLockDeviceRequestStatus = enableSecureLockDevice(mUser);

        assertThat(secureLockDeviceAvailability).isEqualTo(ERROR_NO_BIOMETRICS_ENROLLED);
        assertThat(enableSecureLockDeviceRequestStatus).isEqualTo(ERROR_NO_BIOMETRICS_ENROLLED);
    }

    @Test
    public void getSecureLockDeviceAvailability_success() {
        setupBiometricState(
                true, /* deviceHasStrongBiometricSensor */
                true, /* primaryUserHasStrongBiometricEnrollment */
                false /* otherUserHasStrongBiometricEnrollment */
        );

        int secureLockDeviceAvailability =
                mSecureLockDeviceService.getSecureLockDeviceAvailability(mUser);
        int enableSecureLockDeviceRequestStatus = enableSecureLockDevice(mUser);

        assertThat(secureLockDeviceAvailability).isEqualTo(SUCCESS);
        assertThat(enableSecureLockDeviceRequestStatus).isEqualTo(SUCCESS);
    }

    @Test
    public void isSecureLockDeviceEnabled_updatesState() {
        setupBiometricState(
                true, /* deviceHasStrongBiometricSensor */
                true, /* primaryUserHasStrongBiometricEnrollment */
                false /* otherUserHasStrongBiometricEnrollment */
        );

        boolean isSecureLockDeviceEnabled = mSecureLockDeviceService.isSecureLockDeviceEnabled();
        assertThat(isSecureLockDeviceEnabled).isFalse();

        enableSecureLockDevice(mUser);
        isSecureLockDeviceEnabled = mSecureLockDeviceService.isSecureLockDeviceEnabled();

        assertThat(isSecureLockDeviceEnabled).isTrue();

        disableSecureLockDevice(mUser);
        isSecureLockDeviceEnabled = mSecureLockDeviceService.isSecureLockDeviceEnabled();

        assertThat(isSecureLockDeviceEnabled).isFalse();
    }

    @Test
    public void testAllListenersNotified_onEnableSecureLockDevice() throws RemoteException {
        setupBiometricState(
                true, /* deviceHasStrongBiometricSensor */
                true, /* primaryUserHasStrongBiometricEnrollment */
                false /* otherUserHasStrongBiometricEnrollment */
        );
        mSecureLockDeviceService.registerSecureLockDeviceStatusListener(
                mUser, mSecureLockDeviceStatusListener);
        mSecureLockDeviceService.registerSecureLockDeviceStatusListener(
                mOtherUser, mSecureLockDeviceStatusOtherListener);
        clearInvocations(mSecureLockDeviceStatusListener);
        clearInvocations(mSecureLockDeviceStatusOtherListener);

        enableSecureLockDevice(mUser);

        // Verify listener registered from TEST_USER_ID is notified
        verify(mSecureLockDeviceStatusListener).onSecureLockDeviceAvailableStatusChanged(
                mSecureLockDeviceAvailableStatusArgumentCaptor.capture());
        verify(mSecureLockDeviceStatusListener).onSecureLockDeviceEnabledStatusChanged(
                mSecureLockDeviceEnabledStatusArgumentCaptor.capture());
        int available = mSecureLockDeviceAvailableStatusArgumentCaptor.getValue();
        boolean enabled = mSecureLockDeviceEnabledStatusArgumentCaptor.getValue();
        assertThat(available).isEqualTo(SUCCESS);
        assertThat(enabled).isTrue();

        // Verify listener registered from OTHER_USER_ID is notified
        verify(mSecureLockDeviceStatusOtherListener).onSecureLockDeviceAvailableStatusChanged(
                mSecureLockDeviceAvailableStatusArgumentCaptor.capture());
        verify(mSecureLockDeviceStatusOtherListener).onSecureLockDeviceEnabledStatusChanged(
                mSecureLockDeviceEnabledStatusArgumentCaptor.capture());
        available = mSecureLockDeviceAvailableStatusArgumentCaptor.getValue();
        enabled = mSecureLockDeviceEnabledStatusArgumentCaptor.getValue();
        assertThat(available).isEqualTo(ERROR_NO_BIOMETRICS_ENROLLED);
        assertThat(enabled).isTrue();

    }

    @Test
    public void testAllListenersNotified_onDisableSecureLockDevice() throws RemoteException {
        setupBiometricState(
                true, /* deviceHasStrongBiometricSensor */
                true, /* primaryUserHasStrongBiometricEnrollment */
                false /* otherUserHasStrongBiometricEnrollment */
        );
        mSecureLockDeviceService.registerSecureLockDeviceStatusListener(
                mUser, mSecureLockDeviceStatusListener);
        mSecureLockDeviceService.registerSecureLockDeviceStatusListener(
                mOtherUser, mSecureLockDeviceStatusOtherListener);
        enableSecureLockDevice(mUser);
        clearInvocations(mSecureLockDeviceStatusListener);
        clearInvocations(mSecureLockDeviceStatusOtherListener);

        disableSecureLockDevice(mUser);

        // Verify listener registered from TEST_USER_ID is notified
        verify(mSecureLockDeviceStatusListener).onSecureLockDeviceAvailableStatusChanged(
                mSecureLockDeviceAvailableStatusArgumentCaptor.capture());
        verify(mSecureLockDeviceStatusListener).onSecureLockDeviceEnabledStatusChanged(
                mSecureLockDeviceEnabledStatusArgumentCaptor.capture());
        int available = mSecureLockDeviceAvailableStatusArgumentCaptor.getValue();
        boolean enabled = mSecureLockDeviceEnabledStatusArgumentCaptor.getValue();
        assertThat(available).isEqualTo(SUCCESS);
        assertThat(enabled).isFalse();

        // Verify listener registered from OTHER_USER_ID is notified
        verify(mSecureLockDeviceStatusOtherListener).onSecureLockDeviceAvailableStatusChanged(
                mSecureLockDeviceAvailableStatusArgumentCaptor.capture());
        verify(mSecureLockDeviceStatusOtherListener).onSecureLockDeviceEnabledStatusChanged(
                mSecureLockDeviceEnabledStatusArgumentCaptor.capture());
        available = mSecureLockDeviceAvailableStatusArgumentCaptor.getValue();
        enabled = mSecureLockDeviceEnabledStatusArgumentCaptor.getValue();
        assertThat(available).isEqualTo(ERROR_NO_BIOMETRICS_ENROLLED);
        assertThat(enabled).isFalse();
    }

    @Test
    public void testRelevantListenerNotified_onBiometricEnrollmentAdded() throws RemoteException {
        setupBiometricState(
                true, /* deviceHasStrongBiometricSensor */
                false, /* primaryUserHasStrongBiometricEnrollment */
                false /* otherUserHasStrongBiometricEnrollment */
        );
        mSecureLockDeviceService.registerSecureLockDeviceStatusListener(
                mUser, mSecureLockDeviceStatusListener);
        mSecureLockDeviceService.registerSecureLockDeviceStatusListener(
                mOtherUser, mSecureLockDeviceStatusOtherListener);
        verify(mFingerprintManager).registerBiometricStateListener(
                mBiometricStateListenerCaptor.capture());
        BiometricStateListener biometricStateListener = mBiometricStateListenerCaptor.getValue();
        clearInvocations(mSecureLockDeviceStatusListener);
        clearInvocations(mSecureLockDeviceStatusOtherListener);

        setupBiometricState(
                true, /* deviceHasStrongBiometricSensor */
                true, /* primaryUserHasStrongBiometricEnrollment */
                false /* otherUserHasStrongBiometricEnrollment */
        );
        biometricStateListener.onEnrollmentsChanged(
                TEST_USER_ID,
                1 /* sensorId */,
                true /* hasEnrollments */
        );

        // Verify user that enrolled is notified
        verify(mSecureLockDeviceStatusListener).onSecureLockDeviceAvailableStatusChanged(
                mSecureLockDeviceAvailableStatusArgumentCaptor.capture());
        int available = mSecureLockDeviceAvailableStatusArgumentCaptor.getValue();
        assertThat(available).isEqualTo(SUCCESS);

        // Verify other user is not notified
        verify(mSecureLockDeviceStatusOtherListener, never())
                .onSecureLockDeviceAvailableStatusChanged(anyInt());
    }

    @Test
    public void testRelevantListenerNotified_onBiometricEnrollmentRemoved() throws RemoteException {
        // Add enrollments
        setupBiometricState(
                true, /* deviceHasStrongBiometricSensor */
                true, /* primaryUserHasStrongBiometricEnrollment */
                false /* otherUserHasStrongBiometricEnrollment */
        );
        mSecureLockDeviceService.registerSecureLockDeviceStatusListener(
                mUser, mSecureLockDeviceStatusListener);
        mSecureLockDeviceService.registerSecureLockDeviceStatusListener(
                mOtherUser, mSecureLockDeviceStatusOtherListener);
        verify(mFingerprintManager).registerBiometricStateListener(
                mBiometricStateListenerCaptor.capture());
        BiometricStateListener biometricStateListener = mBiometricStateListenerCaptor.getValue();

        // Remove enrollments
        setupBiometricState(
                true, /* deviceHasStrongBiometricSensor */
                false, /* primaryUserHasStrongBiometricEnrollment */
                false /* otherUserHasStrongBiometricEnrollment */
        );
        clearInvocations(mSecureLockDeviceStatusListener);
        clearInvocations(mSecureLockDeviceStatusOtherListener);

        setupBiometricState(
                true, /* deviceHasStrongBiometricSensor */
                false, /* primaryUserHasStrongBiometricEnrollment */
                false /* otherUserHasStrongBiometricEnrollment */
        );
        biometricStateListener.onEnrollmentsChanged(
                TEST_USER_ID,
                1 /* sensorId */,
                false /* hasEnrollments */
        );

        // Verify user that enrolled is notified
        verify(mSecureLockDeviceStatusListener).onSecureLockDeviceAvailableStatusChanged(
                mSecureLockDeviceAvailableStatusArgumentCaptor.capture());
        int available = mSecureLockDeviceAvailableStatusArgumentCaptor.getValue();
        assertThat(available).isEqualTo(ERROR_NO_BIOMETRICS_ENROLLED);

        // Verify other user is not notified
        verify(mSecureLockDeviceStatusOtherListener, never())
                .onSecureLockDeviceAvailableStatusChanged(anyInt());
    }

    private void setupBiometricState(
            boolean deviceHasStrongBiometricSensor,
            boolean primaryUserHasStrongBiometricEnrollment,
            boolean otherUserHasStrongBiometricEnrollment
    ) {
        if (deviceHasStrongBiometricSensor) {
            when(mBiometricManager.getSensorProperties()).thenReturn(
                    getSensorPropertiesList(STRENGTH_STRONG));
        } else {
            when(mBiometricManager.getSensorProperties()).thenReturn(
                    getSensorPropertiesList(STRENGTH_WEAK));
        }

        if (primaryUserHasStrongBiometricEnrollment) {
            when(mUserBiometricManager.getEnrollmentStatus()).thenReturn(
                    getEnrollmentStatusMap(BIOMETRIC_STRONG));
        } else {
            when(mUserBiometricManager.getEnrollmentStatus()).thenReturn(
                    getEnrollmentStatusMap(BIOMETRIC_WEAK));
        }

        if (otherUserHasStrongBiometricEnrollment) {
            when(mOtherUserBiometricManager.getEnrollmentStatus()).thenReturn(
                    getEnrollmentStatusMap(BIOMETRIC_STRONG));
        } else {
            when(mOtherUserBiometricManager.getEnrollmentStatus()).thenReturn(
                    getEnrollmentStatusMap(BIOMETRIC_WEAK));
        }
    }

    private List<SensorProperties> getSensorPropertiesList(
            @SensorProperties.Strength int strength
    ) {
        return List.of(new SensorProperties(0, strength, List.of()));
    }

    private Map<Integer, BiometricEnrollmentStatus> getEnrollmentStatusMap(int sensorStrength) {
        Map<Integer, BiometricEnrollmentStatus> enrollmentStatusMap = new HashMap<>();
        enrollmentStatusMap.put(BiometricManager.TYPE_FINGERPRINT, new BiometricEnrollmentStatus(
                sensorStrength, 1
        ));

        return enrollmentStatusMap;
    }

    private int enableSecureLockDevice(UserHandle user) {
        return mSecureLockDeviceService.enableSecureLockDevice(user, mEnableParams);
    }

    private int disableSecureLockDevice(UserHandle user) {
        return mSecureLockDeviceService.disableSecureLockDevice(user, mDisableParams);
    }

    private class SecureLockDeviceContext extends TestableContext {
        @Rule public final TestableContext mUserContext = new TestableContext(
                InstrumentationRegistry.getInstrumentation().getTargetContext(), null);
        @Rule public final TestableContext mOtherUserContext = new TestableContext(
                InstrumentationRegistry.getInstrumentation().getTargetContext(), null);
        private final ArrayMap<UserHandle, TestableContext> mMockUserContexts = new ArrayMap<>();

        SecureLockDeviceContext(Context baseContext, UserHandle primaryUser,
                UserHandle otherUser) {
            super(baseContext);
            mMockUserContexts.put(primaryUser, mUserContext);
            mMockUserContexts.put(otherUser, mOtherUserContext);
        }

        @NonNull
        @Override
        public Context createContextAsUser(UserHandle user, int flags) {
            return mMockUserContexts.get(user);
        }

        public void mockBiometricManagerForUser(UserHandle user,
                BiometricManager biometricManager) {
            mMockUserContexts.get(user).addMockSystemService(BiometricManager.class,
                    biometricManager);
        }
    }
}