/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.internal.telephony.tests;

import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.content.res.Resources;
import android.os.Process;
import android.os.SystemClock;
import android.platform.test.annotations.Presubmit;
import android.provider.DeviceConfig;
import android.util.ArrayMap;
import android.util.PackageUtils;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.os.BackgroundThread;
import com.android.internal.telephony.MessagingReadRestrictionAllowlistMonitor;

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class MessagingReadRestrictionAllowlistMonitorTest {
    @Rule public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    private static final Random RANDOM = new Random();
    private static final String TEST_PACKAGE_ONE = "com.example.app.one";
    private static final String TEST_PACKAGE_TWO = "com.example.app.two";
    private static final String KEY_READ_RESTRICTION_ALLOWLIST
        = "messaging_read_restriction_allowlist";
    private static final String ALLOWLIST_FILE_NAME = "messaging_read_restriction_allowlist.txt";

    @Mock
    private PackageManager mPackageManager;
    @Mock
    private AppOpsManager mAppOpsManager;

    private File mReadRestrictionAllowlistFile;
    private final Context mContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();
    private Context mSpyContext;
    private MessagingReadRestrictionAllowlistMonitor mAllowlistMonitor;
    private final Executor mBackgroundExecutor = BackgroundThread.getExecutor();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mSpyContext = spy(new ContextWrapper(mContext));
        when(mSpyContext.getSystemService(AppOpsManager.class)).thenReturn(mAppOpsManager);
        when(mSpyContext.getPackageManager()).thenReturn(mPackageManager);
        doNothing().when(mSpyContext).enforceCallingOrSelfPermission(anyString(), anyString());
        // Use a separate folder for each test case, for better isolation.
        final String folderName = String.valueOf(RANDOM.nextInt() & Integer.MAX_VALUE);
        mReadRestrictionAllowlistFile = new File(mTemporaryFolder.newFolder(folderName),
                ALLOWLIST_FILE_NAME);
        mAllowlistMonitor = new MessagingReadRestrictionAllowlistMonitor(mSpyContext,
                mReadRestrictionAllowlistFile, mBackgroundExecutor);
    }

    @After
    public void tearDown() throws Exception {
        mReadRestrictionAllowlistFile.delete();
        writeDeviceConfigAllowlist(KEY_READ_RESTRICTION_ALLOWLIST, null);
    }

    @Test
    public void initialize_readsAllowlistFromDeviceConfig_setsAppOpsForAllowlistedPackages()
            throws Exception {
        final Signature signature = generateSignature((byte) 1);
        final String certificateDigest = preparePackage(TEST_PACKAGE_ONE, signature, true);
        final String configValue = TEST_PACKAGE_ONE + ":" + certificateDigest;
        // Make PackageManager infer that the given package is associated with the calling uid.
        writeDeviceConfigAllowlist(KEY_READ_RESTRICTION_ALLOWLIST, configValue);

        mAllowlistMonitor.initialize();

        awaitForUpdateInBackground();
        verify(mAppOpsManager).setMode(eq(AppOpsManager.OP_READ_RESTRICTED_MESSAGES),
                eq(Process.myUid()), eq(TEST_PACKAGE_ONE), eq(AppOpsManager.MODE_ALLOWED));
        assertFileContents(mReadRestrictionAllowlistFile, configValue);
    }

    @Test
    public void initialize_thenOnPropertiesChanged_allowlistHasNotChanged_doesNotSetAppOpsAgain()
            throws Exception {
        final Signature signature = generateSignature((byte) 1);
        final String certificateDigest = preparePackage(TEST_PACKAGE_ONE, signature, true);
        final String configValue = TEST_PACKAGE_ONE + ":" + certificateDigest;
        // Write the allowlist only to storage. DeviceConfig remains empty.
        Files.writeString(mReadRestrictionAllowlistFile.toPath(), configValue);

        mAllowlistMonitor.initialize();

        awaitForUpdateInBackground();
        verify(mAppOpsManager).setMode(eq(AppOpsManager.OP_READ_RESTRICTED_MESSAGES),
                eq(Process.myUid()), eq(TEST_PACKAGE_ONE), eq(AppOpsManager.MODE_ALLOWED));
        assertFileContents(mReadRestrictionAllowlistFile, configValue);

        // OnPropertiesChanged is called with the same value as before.
        mAllowlistMonitor.onPropertiesChanged(new DeviceConfig.Properties.Builder(
            DeviceConfig.NAMESPACE_TELEPHONY)
            .setString(KEY_READ_RESTRICTION_ALLOWLIST,configValue)
        .build());

        awaitForUpdateInBackground();
        verifyNoMoreInteractions(mAppOpsManager);
        assertFileContents(mReadRestrictionAllowlistFile, configValue);
    }

    @Test
    public void initialize_deviceConfigEmpty_readsAllowlistFromStorage_setsAppOps()
            throws Exception {
        final Signature signature = generateSignature((byte) 1);
        final String certificateDigest = preparePackage(TEST_PACKAGE_ONE, signature, true);
        final String configValue = TEST_PACKAGE_ONE + ":" + certificateDigest;
        // Write the allowlist only to storage. DeviceConfig remains empty.
        Files.writeString(mReadRestrictionAllowlistFile.toPath(), configValue);

        mAllowlistMonitor.initialize();

        awaitForUpdateInBackground();
        verify(mAppOpsManager).setMode(eq(AppOpsManager.OP_READ_RESTRICTED_MESSAGES),
                eq(Process.myUid()), eq(TEST_PACKAGE_ONE), eq(AppOpsManager.MODE_ALLOWED));
        assertFileContents(mReadRestrictionAllowlistFile, configValue);
    }

    @Test
    public void initialize_readsAllowlistFromDeviceConfig_signatureMismatch_doesNotSetAppOps()
            throws Exception {
        final Signature expectedSignature = generateSignature((byte) 1);
        final String expectedCertificateDigest
                = PackageUtils.computeSha256Digest(expectedSignature.toByteArray());
        final String configValue = TEST_PACKAGE_ONE + ":" + expectedCertificateDigest;
        final Signature actualSignature = generateSignature((byte) 2);
        preparePackage(TEST_PACKAGE_ONE, actualSignature, true);
        writeDeviceConfigAllowlist(KEY_READ_RESTRICTION_ALLOWLIST, configValue);

        mAllowlistMonitor.initialize();

        awaitForUpdateInBackground();
        verifyNoInteractions(mAppOpsManager);
        assertFileContents(mReadRestrictionAllowlistFile, configValue);
    }

    @Test
    public void initialize_readsAllowlistFromDeviceConfig_packageNotInstalled_doesNotSetAppOps()
            throws Exception {
        final Signature expectedSignature = generateSignature((byte) 1);
        final String expectedCertificateDigest
                = PackageUtils.computeSha256Digest(expectedSignature.toByteArray());
        final String configValue = TEST_PACKAGE_ONE + ":" + expectedCertificateDigest;
        final Signature actualSignature = generateSignature((byte) 2);
        writeDeviceConfigAllowlist(KEY_READ_RESTRICTION_ALLOWLIST, configValue);

        mAllowlistMonitor.initialize();

        awaitForUpdateInBackground();
        verifyNoInteractions(mAppOpsManager);
        assertFileContents(mReadRestrictionAllowlistFile, configValue);
    }

    @Test
    public void deviceConfigUpdated_revokeAppOpForOldPackage_grantAppOpForNewPackage()
            throws Exception {
        final Signature signature = generateSignature((byte) 1);
        final String certificateDigestOne = preparePackage(TEST_PACKAGE_ONE, signature, true);
        final String certificateDigestTwo = preparePackage(TEST_PACKAGE_TWO, signature, true);
        when(mPackageManager.getPackageUidAsUser(anyString(), anyInt()))
                .thenReturn(Process.myUid());
        writeDeviceConfigAllowlist(KEY_READ_RESTRICTION_ALLOWLIST,
                TEST_PACKAGE_ONE + ":" + certificateDigestOne);
        mAllowlistMonitor.initialize();
        awaitForUpdateInBackground();

        // Update the allowlist in DeviceConfig.
        final String updatedConfigValue = TEST_PACKAGE_TWO + ":" + certificateDigestTwo;
        writeDeviceConfigAllowlist(KEY_READ_RESTRICTION_ALLOWLIST, updatedConfigValue);
        mAllowlistMonitor.onPropertiesChanged(new DeviceConfig.Properties.Builder(
                    DeviceConfig.NAMESPACE_TELEPHONY)
                    .setString(KEY_READ_RESTRICTION_ALLOWLIST,
                            TEST_PACKAGE_TWO + ":" + certificateDigestTwo)
                .build());

        awaitForUpdateInBackground();
        verify(mAppOpsManager).setMode(eq(AppOpsManager.OP_READ_RESTRICTED_MESSAGES),
                eq(Process.myUid()), eq(TEST_PACKAGE_ONE), eq(AppOpsManager.MODE_DEFAULT));
        verify(mAppOpsManager).setMode(eq(AppOpsManager.OP_READ_RESTRICTED_MESSAGES),
                eq(Process.myUid()), eq(TEST_PACKAGE_TWO), eq(AppOpsManager.MODE_ALLOWED));
        assertFileContents(mReadRestrictionAllowlistFile, updatedConfigValue);
    }


    private void awaitForUpdateInBackground() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        mBackgroundExecutor.execute(() -> {
            // Wait for the initialization to complete on the background thread.
            latch.countDown();
        });
        assertTrue("Background task took too long", latch.await(5, TimeUnit.SECONDS));
    }

    private void writeDeviceConfigAllowlist(@NonNull String key, @Nullable String value)
            throws Exception {
        assertTrue(DeviceConfig.setProperty(DeviceConfig.NAMESPACE_TELEPHONY, key, value,
                    /* makeDefault */ false));
    }

    private String preparePackage(@NonNull String packageName, @NonNull Signature signature,
            boolean preinstalled) throws Exception {
        final String certificateDigest = PackageUtils.computeSha256Digest(signature.toByteArray());
        final PackageInfo packageInfo = generatePackageInfo(signature);
        when(mPackageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES))
                .thenReturn(packageInfo);
        final ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.uid = Process.myUid();
        if (preinstalled) {
            applicationInfo.flags |= ApplicationInfo.FLAG_SYSTEM;
        }
        when(mPackageManager.getApplicationInfo(eq(packageName), any()))
                .thenReturn(applicationInfo);
        return certificateDigest;
    }

    private static PackageInfo generatePackageInfo(@NonNull Signature signature) {
        final SigningInfo signingInfo = new SigningInfo(SigningInfo.VERSION_SIGNING_BLOCK_V4,
                List.of(signature), null, null);
        final PackageInfo packageInfo = new PackageInfo();
        packageInfo.signingInfo = signingInfo;
        return packageInfo;
    }

    private static Signature generateSignature(byte i) {
        byte[] signatureBytes = new byte[256];
        signatureBytes[0] = i;
        return new Signature(signatureBytes);
    }

    private void assertFileContents(@NonNull File file, @NonNull String value) throws Exception {
        final String fileContents = Files.readString(file.toPath());
        assertEquals(value, fileContents);
    }
}