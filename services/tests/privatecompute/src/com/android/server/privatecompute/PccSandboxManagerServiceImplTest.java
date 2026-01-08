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

package com.android.server.privatecompute;

import static android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManagerInternal;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.UserHandle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import androidx.test.runner.AndroidJUnit4;
import com.android.server.LocalServices;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link PccSandboxManagerServiceImpl}. */
@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
public class PccSandboxManagerServiceImplTest {
    private static final int TEST_UID = 10123;
    private static final String TEST_PACKAGE_NAME = "com.example.foo";
    private static final String ANOTHER_PACKAGE_NAME = "com.example.bar";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule public MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Mock private Context mContext;
    @Mock private PackageManager mPackageManager;

    @Mock private PackageManagerInternal mPackageManagerInternal;

    private PccSandboxManagerServiceImpl mService;

    @Before
    public void setUp() throws Exception {
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        LocalServices.removeServiceForTest(PackageManagerInternal.class);
        LocalServices.addService(PackageManagerInternal.class, mPackageManagerInternal);
        mService = new PccSandboxManagerServiceImpl(mContext);
    }

    @Test
    public void testIsPrivateComputeServicesUid_uidInPccRange_returnsFalse() throws Exception {
        assertFalse(mService.isPrivateComputeServicesUid(Process.FIRST_PCC_UID));
    }

    @Test
    public void testIsPrivateComputeServicesUid_uidOutsideApplicationUidRange_returnsFalse()
            throws Exception {
        assertFalse(mService.isPrivateComputeServicesUid(Process.FIRST_APPLICATION_UID - 1));
        assertFalse(mService.isPrivateComputeServicesUid(Process.LAST_APPLICATION_UID + 1));
    }

    @Test
    public void testIsPrivateComputeServicesUid_packageDoesNotHavePermission_returnsFalse() {
        when(mPackageManager.getPackagesForUid(TEST_UID))
                .thenReturn(new String[] {TEST_PACKAGE_NAME});
        when(mPackageManager.checkPermission(
                        android.Manifest.permission.PROVIDE_PRIVATE_COMPUTE_SERVICES,
                        TEST_PACKAGE_NAME))
                .thenReturn(PackageManager.PERMISSION_DENIED);

        assertFalse(mService.isPrivateComputeServicesUid(TEST_UID));
    }

    @Test
    public void testIsPrivateComputeServicesUid_noPackagesForUid_returnsFalse() throws Exception {
        when(mPackageManager.getPackagesForUid(TEST_UID)).thenReturn(null);

        assertFalse(mService.isPrivateComputeServicesUid(TEST_UID));
    }

    @Test
    public void testIsPrivateComputeServicesUid_packageHasPermission_returnsTrue() {
        when(mPackageManager.getPackagesForUid(TEST_UID))
                .thenReturn(new String[] {TEST_PACKAGE_NAME});
        when(mPackageManager.checkPermission(
                        android.Manifest.permission.PROVIDE_PRIVATE_COMPUTE_SERVICES,
                        TEST_PACKAGE_NAME))
                .thenReturn(PackageManager.PERMISSION_GRANTED);

        assertTrue(mService.isPrivateComputeServicesUid(TEST_UID));
    }

    @Test
    public void testIsPrivateComputeServicesUid_multiplePackages_oneHasPermission_returnsTrue() {
        when(mPackageManager.getPackagesForUid(TEST_UID))
                .thenReturn(new String[] {TEST_PACKAGE_NAME, ANOTHER_PACKAGE_NAME});
        when(mPackageManager.checkPermission(
                        android.Manifest.permission.PROVIDE_PRIVATE_COMPUTE_SERVICES,
                        TEST_PACKAGE_NAME))
                .thenReturn(PackageManager.PERMISSION_DENIED);
        when(mPackageManager.checkPermission(
                        android.Manifest.permission.PROVIDE_PRIVATE_COMPUTE_SERVICES,
                        ANOTHER_PACKAGE_NAME))
                .thenReturn(PackageManager.PERMISSION_GRANTED);

        assertTrue(mService.isPrivateComputeServicesUid(TEST_UID));
    }

    @Test
    public void testIsPrivateComputeServicesUid_multiplePackages_noneHavePermission_returnsFalse() {
        when(mPackageManager.getPackagesForUid(TEST_UID))
                .thenReturn(new String[] {TEST_PACKAGE_NAME, ANOTHER_PACKAGE_NAME});
        when(mPackageManager.checkPermission(
                        android.Manifest.permission.PROVIDE_PRIVATE_COMPUTE_SERVICES,
                        TEST_PACKAGE_NAME))
                .thenReturn(PackageManager.PERMISSION_DENIED);
        when(mPackageManager.checkPermission(
                        android.Manifest.permission.PROVIDE_PRIVATE_COMPUTE_SERVICES,
                        ANOTHER_PACKAGE_NAME))
                .thenReturn(PackageManager.PERMISSION_DENIED);

        assertFalse(mService.isPrivateComputeServicesUid(TEST_UID));
    }

    @Test
    public void testWriteToAuditLogInternal_packageNameDoesNotMatchUid_throwsSecurityException() {
        int testUid = android.os.Process.myUid();
        when(mPackageManagerInternal.isSameApp(
                        TEST_PACKAGE_NAME, testUid, UserHandle.getUserId(testUid)))
                .thenReturn(true);
        when(mPackageManagerInternal.isSameApp(
                        ANOTHER_PACKAGE_NAME, testUid, UserHandle.getUserId(testUid)))
                .thenReturn(false);

        assertThrows(
                SecurityException.class,
                () ->
                        mService.writeToAuditLogInternal(
                                new PersistableBundle(), ANOTHER_PACKAGE_NAME));
    }

    @Test
    public void testWriteToAuditLogInternal_packageNameMatchesUid_doesNotThrowSecurityException() {
        int testUid = android.os.Process.myUid();
        when(mPackageManagerInternal.isSameApp(
                        TEST_PACKAGE_NAME, testUid, UserHandle.getUserId(testUid)))
                .thenReturn(true);
        when(mPackageManagerInternal.isSameApp(
                        ANOTHER_PACKAGE_NAME, testUid, UserHandle.getUserId(testUid)))
                .thenReturn(false);

        mService.writeToAuditLogInternal(new PersistableBundle(), TEST_PACKAGE_NAME);

        // No exception thrown.
    }
}
