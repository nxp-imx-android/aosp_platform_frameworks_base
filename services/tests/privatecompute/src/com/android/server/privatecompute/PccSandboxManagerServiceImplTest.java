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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.privatecompute.DataMigrationToPccService;
import android.app.privatecompute.IDataMigrationToPccService;
import android.app.privatecompute.IMigrationRequestResultReceiver;
import android.app.privatecompute.IMigrationRequestResultSender;
import android.app.privatecompute.MigrationException;
import android.app.privatecompute.MigrationRequestResult;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.PackageManagerInternal;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.UserHandle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.runner.AndroidJUnit4;

import com.android.server.LocalServices;
import com.android.server.privatecompute.PccSandboxManagerServiceImpl.Injector;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.ArrayList;
import java.util.List;

/** Unit tests for {@link PccSandboxManagerServiceImpl}. */
@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
public class PccSandboxManagerServiceImplTest {
    private static final int TEST_UID = 10123;
    private static final String TEST_PACKAGE_NAME = "com.example.foo";
    private static final String ANOTHER_PACKAGE_NAME = "com.example.bar";
    private static final String TEST_SERVICE_CLASS = "com.example.foo.MigrationService";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule public MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Mock private Context mContext;
    @Mock private PackageManager mPackageManager;

    @Mock private PackageManagerInternal mPackageManagerInternal;
    @Mock
    private Handler mHandler;
    @Mock
    private IMigrationRequestResultReceiver mCallback;

    @Mock private Injector mInjector;

    private PccSandboxManagerServiceImpl mService;

    @Before
    public void setUp() throws Exception {
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        LocalServices.removeServiceForTest(PackageManagerInternal.class);
        LocalServices.addService(PackageManagerInternal.class, mPackageManagerInternal);
        when(mInjector.getCallingUid()).thenReturn(TEST_UID);
        when(mInjector.getHandler(any())).thenReturn(mHandler);
        mService = new PccSandboxManagerServiceImpl(mContext, mInjector);
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
        List<PersistableBundle> data = new ArrayList<>(1);
        data.add(new PersistableBundle());

        assertThrows(
                SecurityException.class,
                () ->
                        mService.writeToAuditLogInternal(data, ANOTHER_PACKAGE_NAME));
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
        List<PersistableBundle> data = new ArrayList<>(1);
        data.add(new PersistableBundle());

        mService.writeToAuditLogInternal(data, TEST_PACKAGE_NAME);

        // No exception thrown.
    }

    @Test
    public void testWriteToAuditLogInternal_sysPropDisabled_returnsFalse() {
        when(mInjector.auditModeEnabled()).thenReturn(false);
        int testUid = android.os.Process.myUid();
        when(mPackageManagerInternal.isSameApp(
                        TEST_PACKAGE_NAME, testUid, UserHandle.getUserId(testUid)))
                .thenReturn(true);
        List<PersistableBundle> data = new ArrayList<>(1);
        data.add(new PersistableBundle());

        assertFalse(mService.writeToAuditLogInternal(data, TEST_PACKAGE_NAME));
    }

    @Test
    public void testWriteToAuditLogInternal_sysPropEnabled_returnsTrue() {
        when(mInjector.auditModeEnabled()).thenReturn(true);
        int testUid = android.os.Process.myUid();
        when(mPackageManagerInternal.isSameApp(
                        TEST_PACKAGE_NAME, testUid, UserHandle.getUserId(testUid)))
                .thenReturn(true);
        List<PersistableBundle> data = new ArrayList<>(1);
        data.add(new PersistableBundle());

        assertTrue(mService.writeToAuditLogInternal(data, TEST_PACKAGE_NAME));
    }

    @Test
    public void testStartNonPccProcessForDataMigration_success() throws Exception {
        setupMigrationService(true);

        // Mock the binder interface
        IDataMigrationToPccService.Stub mockBinder = mock(IDataMigrationToPccService.Stub.class);
        IDataMigrationToPccService mockInterface = mock(IDataMigrationToPccService.class);
        when(mockBinder.queryLocalInterface(any())).thenReturn(mockInterface);

        // Capture the ServiceConnection
        ArgumentCaptor<ServiceConnection> connectionCaptor =
                ArgumentCaptor.forClass(ServiceConnection.class);
        when(mContext.bindServiceAsUser(any(), connectionCaptor.capture(), anyInt(), any()))
                .thenReturn(true);

        mService.startNonPccProcessForDataMigration(mCallback);

        // Verify bind
        verify(mContext).bindServiceAsUser(any(), any(), eq(Context.BIND_AUTO_CREATE),
                eq(UserHandle.getUserHandleForUid(TEST_UID)));

        // Simulate connection
        connectionCaptor.getValue().onServiceConnected(new ComponentName(TEST_PACKAGE_NAME,
                TEST_SERVICE_CLASS), mockBinder);

        // Capture the completion callback passed to the service
        ArgumentCaptor<IMigrationRequestResultSender> completionCallbackCaptor =
                ArgumentCaptor.forClass(IMigrationRequestResultSender.class);
        verify(mockInterface).onMigrationRequested(completionCallbackCaptor.capture());

        // Simulate completion
        MigrationRequestResult result = new MigrationRequestResult(
                MigrationRequestResult.MIGRATION_REQUEST_ACCEPTED, PersistableBundle.EMPTY);
        completionCallbackCaptor.getValue().sendResult(result);

        // Verify callback called
        verify(mCallback).onResult(result);

        // Verify unbind
        verify(mContext).unbindService(connectionCaptor.getValue());
    }

    @Test
    public void testStartNonPccProcessForDataMigration_noPackageForUid() throws Exception {
        when(mPackageManager.getPackagesForUid(TEST_UID)).thenReturn(null);

        mService.startNonPccProcessForDataMigration(mCallback);

        verify(mCallback).onError(eq(MigrationException.ERROR_INVOCATION_FAILED),
                anyString());
    }

    @Test
    public void testStartNonPccProcessForDataMigration_noService() throws Exception {
        when(mPackageManager.getPackagesForUid(TEST_UID)).thenReturn(
                new String[]{TEST_PACKAGE_NAME});
        when(mPackageManager.resolveService(any(), anyInt())).thenReturn(null);

        mService.startNonPccProcessForDataMigration(mCallback);

        verify(mCallback).onError(eq(MigrationException.ERROR_INVOCATION_FAILED),
                anyString());
        verify(mContext, never()).bindServiceAsUser(any(), any(), anyInt(), any());
    }

    @Test
    public void testStartNonPccProcessForDataMigration_missingPermission() throws Exception {
        setupMigrationService(false);

        mService.startNonPccProcessForDataMigration(mCallback);

        verify(mCallback).onError(eq(MigrationException.ERROR_INVOCATION_FAILED),
                anyString());
    }

    @Test
    public void testStartNonPccProcessForDataMigration_timeout() throws Exception {
        setupMigrationService(true);

        // Mock the binder interface
        IDataMigrationToPccService.Stub mockBinder = mock(IDataMigrationToPccService.Stub.class);
        IDataMigrationToPccService mockInterface = mock(IDataMigrationToPccService.class);
        when(mockBinder.queryLocalInterface(any())).thenReturn(mockInterface);

        // Capture the ServiceConnection
        ArgumentCaptor<ServiceConnection> connectionCaptor =
                ArgumentCaptor.forClass(ServiceConnection.class);
        when(mContext.bindServiceAsUser(any(), connectionCaptor.capture(), anyInt(), any()))
                .thenReturn(true);

        // Capture the runnable passed to postDelayed
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        when(mHandler.postDelayed(runnableCaptor.capture(),
                eq(DataMigrationToPccService.MIGRATION_TIMEOUT_MS))).thenReturn(true);

        mService.startNonPccProcessForDataMigration(mCallback);

        // Simulate connection
        connectionCaptor.getValue().onServiceConnected(new ComponentName(TEST_PACKAGE_NAME,
                TEST_SERVICE_CLASS), mockBinder);

        // Run the timeout runnable
        runnableCaptor.getValue().run();

        // Verify onError called
        verify(mCallback).onError(eq(MigrationException.ERROR_TIMEOUT), anyString());

        // Verify unbind
        verify(mContext).unbindService(connectionCaptor.getValue());
    }

    private void setupMigrationService(boolean hasPermission) {
        when(mPackageManager.getPackagesForUid(TEST_UID)).thenReturn(
                new String[]{TEST_PACKAGE_NAME});

        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.serviceInfo = new ServiceInfo();
        resolveInfo.serviceInfo.packageName = TEST_PACKAGE_NAME;
        resolveInfo.serviceInfo.name = TEST_SERVICE_CLASS;
        if (hasPermission) {
            resolveInfo.serviceInfo.permission =
                    "android.permission.BIND_DATA_MIGRATION_FOR_PRIVATECOMPUTE";
        }

        when(mPackageManager.resolveServiceAsUser(any(), anyInt(), anyInt())).thenReturn(
                resolveInfo);
    }
}
