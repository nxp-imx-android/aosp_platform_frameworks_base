/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.pm;

import static android.os.Process.myUid;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageInstaller;
import android.os.PermissionEnforcer;
import android.os.UserHandle;
import android.platform.test.annotations.Presubmit;
import android.util.ArrayMap;

import com.android.server.LocalServices;
import com.android.server.SystemServiceManager;
import com.android.server.pm.verify.developer.DeveloperVerifierController;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@Presubmit
@RunWith(JUnit4.class)
public class PackageInstallerServiceTest {
    private @Mock PermissionEnforcer mMockPermissionEnforcer;
    private @Mock SystemServiceManager mMockSystemServiceManager;
    private @Mock DeveloperVerifierController mMockDeveloperVerifierController;
    private String mPackageName;
    private PackageManagerService mPms;
    private @Mock Computer mMockSnapshot;

    @Rule
    public final MockSystemRule rule = new MockSystemRule();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        rule.system().stageNominalSystemState();
        mPackageName = this.getClass().getPackageName();
        PackageManagerServiceTestParams testParams = new PackageManagerServiceTestParams();
        testParams.packages = new ArrayMap<>();
        when(rule.mocks().getContext().getSystemService(Context.PERMISSION_ENFORCER_SERVICE))
                .thenReturn(mMockPermissionEnforcer);
        doReturn(mMockDeveloperVerifierController).when(
                () -> DeveloperVerifierController.getInstance(any(), any(), argThat(
                        componentName -> componentName.getPackageName().equals(mPackageName)
                ))
        );
        doReturn(mMockSystemServiceManager).when(
                () -> LocalServices.getService(SystemServiceManager.class));
        doReturn(mPackageName).when(mMockDeveloperVerifierController).getVerifierPackageName();
        mPms = spy(new PackageManagerService(rule.mocks().getInjector(), testParams));
        doReturn(mMockSnapshot).when(mPms).snapshotComputer();
        doReturn(myUid()).when(mMockSnapshot).getPackageUidInternal(
                eq(mPackageName), anyLong(), anyInt(), anyInt());
    }

    @Test
    public void testVerificationPolicyPerUser() {
        PackageInstallerService service = new PackageInstallerService(
                rule.mocks().getContext(), mPms, null,
                new ComponentName(mPackageName, this.getClass().getName()));
        final int defaultPolicy = service.getDeveloperVerificationPolicy(
                /* userId= */ UserHandle.USER_SYSTEM);
        assertThat(defaultPolicy).isAtLeast(PackageInstaller.DEVELOPER_VERIFICATION_POLICY_NONE);
        assertThat(service.setDeveloperVerificationPolicy(
                /* policy= */ PackageInstaller.DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_CLOSED,
                /* userId= */ UserHandle.USER_SYSTEM)).isTrue();
        // Test with a non-existing user
        final int newUserId = 1;
        assertThrows(IllegalStateException.class, () -> service.getDeveloperVerificationPolicy(
                /* userId= */ newUserId));
        assertThrows(IllegalStateException.class,
                () -> service.setDeveloperVerificationPolicy(
                /* policy= */ PackageInstaller.DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_CLOSED,
                /* userId= */ newUserId));
        // Add a user
        service.onUserAdded(newUserId);
        assertThat(service.getDeveloperVerificationPolicy(newUserId)).isEqualTo(defaultPolicy);
        assertThat(service.setDeveloperVerificationPolicy(
                PackageInstaller.DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_WARN, newUserId)
        ).isTrue();
        assertThat(service.getDeveloperVerificationPolicy(newUserId)).isEqualTo(
                PackageInstaller.DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_WARN);
        // Remove a user
        service.onUserRemoved(newUserId);
        assertThrows(IllegalStateException.class, () -> service.getDeveloperVerificationPolicy(
                /* userId= */ newUserId));
        assertThrows(
                IllegalStateException.class,
                () -> service.setDeveloperVerificationPolicy(
                        /* policy= */
                        PackageInstaller.DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_CLOSED,
                        /* userId= */ newUserId));
    }

    @Test
    public void testVerifierIsNullThrowsException() {
        doReturn(mMockDeveloperVerifierController).when(
                () -> DeveloperVerifierController.getInstance(any(), any(), eq(null))
        );
        when(mMockDeveloperVerifierController.getVerifierPackageName()).thenReturn(null);
        PackageInstallerService service = new PackageInstallerService(
                rule.mocks().getContext(), mPms, null, null);
        // When there is no verifier specified by the system, no one can change the policy.
        assertThrows(SecurityException.class,
                () -> service.setDeveloperVerificationPolicy(
                /* policy= */ PackageInstaller.DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_CLOSED,
                /* userId= */ UserHandle.USER_SYSTEM));
    }
}
