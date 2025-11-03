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

package com.android.server.companion.virtual.computercontrol;

import static com.android.server.companion.virtual.computercontrol.ComputerControlAllowlistController.COMPUTER_CONTROL_NAMESPACE;
import static com.android.server.companion.virtual.computercontrol.ComputerControlAllowlistController.COMPUTER_CONTROL_AUTOMATABLE_APP_ALLOWLIST_KEY;
import static com.android.server.companion.virtual.computercontrol.ComputerControlAllowlistController.COMPUTER_CONTROL_AUTOMATABLE_APP_DENYLIST_KEY;
import static com.android.server.companion.virtual.computercontrol.ComputerControlAllowlistController.COMPUTER_CONTROL_SESSION_OWNER_ALLOWLIST_KEY;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;

import android.annotation.NonNull;
import android.companion.virtualdevice.flags.Flags;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Process;
import android.os.SystemClock;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.SetFlagsRule;
import android.provider.DeviceConfig;
import android.util.ArrayMap;
import android.util.PackageUtils;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.internal.annotations.GuardedBy;

import com.google.common.util.concurrent.MoreExecutors;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Random;

@Presubmit
@RunWith(JUnitParamsRunner.class)
public class ComputerControlAllowlistControllerTest {

    private static final long TIMEOUT_MILLIS = 1000L;
    private static final Random RANDOM = new Random();

    @Rule
    public SetFlagsRule mSetFlagsRule = new SetFlagsRule();
    @Mock
    private PackageManager mPackageManager;

    private AutoCloseable mMockitoSession;
    private ComputerControlAllowlistController mAllowlistController;
    private File mSessionOwnerAllowlistFile;
    private File mAutomatableAppAllowlistFile;
    private File mAutomatableAppDenylistFile;
    private final DeviceConfigWriter mDeviceConfigWriter = new DeviceConfigWriter();
    private final Context mContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Before
    public void setUp() throws Exception {
        mMockitoSession = MockitoAnnotations.openMocks(this);
        final Context spyContext = spy(new ContextWrapper(mContext));
        when(spyContext.getPackageManager()).thenReturn(mPackageManager);
        // Use a separate folder for each test case, for better isolation.
        final String folderName = String.valueOf(RANDOM.nextInt() & Integer.MAX_VALUE);
        mSessionOwnerAllowlistFile =
                new File(new File(mContext.getFilesDir(), folderName), "session_owners.txt");
        mAutomatableAppAllowlistFile =
                new File(new File(mContext.getFilesDir(), folderName), "automatable_apps.txt");
        mAutomatableAppDenylistFile =
                new File(new File(mContext.getFilesDir(), folderName), "blocked_apps.txt");
        mAllowlistController = new ComputerControlAllowlistController(spyContext,
                MoreExecutors.directExecutor(), mSessionOwnerAllowlistFile,
                mAutomatableAppAllowlistFile, mAutomatableAppDenylistFile);
        mAllowlistController.initialize();
    }

    @After
    public void tearDown() throws Exception {
        DeviceConfig.removeOnPropertiesChangedListener(mAllowlistController);
        mDeviceConfigWriter.reset();
        mSessionOwnerAllowlistFile.delete();
        mAutomatableAppDenylistFile.delete();
        mAutomatableAppAllowlistFile.delete();
        mMockitoSession.close();
    }

    @DisableFlags(Flags.FLAG_COMPUTER_CONTROL_ALLOWLISTS)
    @Test
    public void isPackageAllowedToCreateSession_anyPackageName_returnsTrue() {
        assertTrue(mAllowlistController.isPackageAllowedToCreateSession("hello"));
        assertTrue(mAllowlistController.isPackageAllowedToCreateSession(mContext.getPackageName()));
        assertTrue(mAllowlistController.isPackageAllowedToCreateSession(null));
    }

    @DisableFlags(Flags.FLAG_COMPUTER_CONTROL_ALLOWLISTS)
    @Test
    public void isPackageAutomatable_anyPackageName_returnsTrue() {
        assertTrue(mAllowlistController.isPackageAutomatable("hello"));
        assertTrue(mAllowlistController.isPackageAutomatable(mContext.getPackageName()));
        assertTrue(mAllowlistController.isPackageAutomatable(null));
    }

    @EnableFlags(Flags.FLAG_COMPUTER_CONTROL_ALLOWLISTS)
    @Test
    public void isPackageAllowedToCreateSession_allowlistedSessionOwner_sameUid_returnsTrue()
            throws Exception {
        final String packageName = "com.hello.app2";
        final Signature signature = generateSignature((byte) 1);
        final String certificateDigest = preparePackage(packageName, signature);
        // Make PackageManager infer that the given package is associated with the calling uid.
        when(mPackageManager.getPackageUidAsUser(eq(packageName), anyInt()))
                .thenReturn(Process.myUid());

        mDeviceConfigWriter.allowlistSessionOwner(packageName, certificateDigest);
        SystemClock.sleep(TIMEOUT_MILLIS);

        assertTrue(mAllowlistController.isPackageAllowedToCreateSession(packageName));
    }

    @EnableFlags(Flags.FLAG_COMPUTER_CONTROL_ALLOWLISTS)
    @Test
    public void isPackageAllowedToCreateSession_allowlistedSessionOwners_sameUid_returnsTrue()
            throws Exception {
        final String packageName1 = "com.hello.appp1";
        final Signature signature1 = generateSignature((byte) 1);
        final String certificateDigest1 = preparePackage(packageName1, signature1);
        final String packageName2 = "com.hello.appp2";
        final Signature signature2 = generateSignature((byte) 2);
        final String certificateDigest2 = preparePackage(packageName2, signature2);
        final List<ComputerControlAllowlistController.SignedPackage> sessionOwners = List.of(
                new ComputerControlAllowlistController.SignedPackage(
                        packageName1, certificateDigest1),
                new ComputerControlAllowlistController.SignedPackage(
                        packageName2, certificateDigest2));
        // Make PackageManager infer that any package is associated with the calling uid.
        when(mPackageManager.getPackageUidAsUser(any(), anyInt()))
                .thenReturn(Process.myUid());

        mDeviceConfigWriter.allowlistSessionOwners(sessionOwners);
        SystemClock.sleep(TIMEOUT_MILLIS);

        assertTrue(mAllowlistController.isPackageAllowedToCreateSession(packageName1));
        assertTrue(mAllowlistController.isPackageAllowedToCreateSession(packageName2));
    }

    @EnableFlags(Flags.FLAG_COMPUTER_CONTROL_ALLOWLISTS)
    @Test
    public void isPackageAllowedToCreateSession_allowlistedSessionOwner_differentUid_returnsFalse()
            throws Exception {
        final String packageName = "com.hello.app3";
        final Signature signature = generateSignature((byte) 2);
        final String certificateDigest = preparePackage(packageName, signature);
        // Make PackageManager infer that the given package is not associated with the calling uid.
        when(mPackageManager.getPackageUidAsUser(eq(packageName), anyInt()))
                .thenReturn(Process.myUid() + 1);

        mDeviceConfigWriter.allowlistSessionOwner(packageName, certificateDigest);
        SystemClock.sleep(TIMEOUT_MILLIS);

        assertFalse(mAllowlistController.isPackageAllowedToCreateSession(packageName));
    }

    @EnableFlags(Flags.FLAG_COMPUTER_CONTROL_ALLOWLISTS)
    @Test
    public void isPackageAllowedToCreateSession_notAllowlistedSessionOwner_sameUid_returnsFalse()
            throws Exception {
        final String packageName = "com.hello.app1";
        final Signature signature = generateSignature((byte) 1);
        preparePackage(packageName, signature);
        // Make PackageManager infer that the given package is associated with the calling uid.
        when(mPackageManager.getPackageUidAsUser(eq(packageName), anyInt()))
                .thenReturn(Process.myUid());

        assertFalse(mAllowlistController.isPackageAllowedToCreateSession(packageName));
    }

    @EnableFlags(Flags.FLAG_COMPUTER_CONTROL_ALLOWLISTS)
    @Test
    public void isPackageAutomatable_allowlistedApp_returnsTrue() throws Exception {
        final String packageName = "com.hello.good";
        final Signature signature = generateSignature((byte) 1);
        final String certificateDigest = preparePackage(packageName, signature);

        mDeviceConfigWriter.allowlistAutomatableApp(packageName, certificateDigest);
        SystemClock.sleep(TIMEOUT_MILLIS);

        assertTrue(mAllowlistController.isPackageAutomatable(packageName));
    }

    @Test
    public void isPackageAutomatable_allowlistedApps_returnsTrue() throws Exception {
        final String packageName1 = "com.hello.appp1";
        final Signature signature1 = generateSignature((byte) 1);
        final String certificateDigest1 = preparePackage(packageName1, signature1);
        final String packageName2 = "com.hello.appp2";
        final Signature signature2 = generateSignature((byte) 2);
        final String certificateDigest2 = preparePackage(packageName2, signature2);
        final List<ComputerControlAllowlistController.SignedPackage> apps = List.of(
                new ComputerControlAllowlistController.SignedPackage(
                        packageName1, certificateDigest1),
                new ComputerControlAllowlistController.SignedPackage(
                        packageName2, certificateDigest2));

        mDeviceConfigWriter.allowlistAutomatableApps(apps);
        SystemClock.sleep(TIMEOUT_MILLIS);

        assertTrue(mAllowlistController.isPackageAutomatable(packageName1));
        assertTrue(mAllowlistController.isPackageAutomatable(packageName2));
    }

    @EnableFlags(Flags.FLAG_COMPUTER_CONTROL_ALLOWLISTS)
    @Test
    public void isPackageAutomatable_allAppsAllowlisted_returnsTrue() throws Exception {
        final String packageName1 = "com.hello.foo";
        final Signature signature1 = generateSignature((byte) 1);
        preparePackage(packageName1, signature1);
        final String packageName2 = "com.hello.bar";
        final Signature signature2 = generateSignature((byte) 5);
        preparePackage(packageName2, signature2);

        mDeviceConfigWriter.writeValue(COMPUTER_CONTROL_AUTOMATABLE_APP_ALLOWLIST_KEY, "*");
        SystemClock.sleep(TIMEOUT_MILLIS);

        assertTrue(mAllowlistController.isPackageAutomatable(packageName1));
        assertTrue(mAllowlistController.isPackageAutomatable(packageName2));
    }

    @EnableFlags(Flags.FLAG_COMPUTER_CONTROL_ALLOWLISTS)
    @Test
    public void isPackageAutomatable_notAllowlistedApp_returnsFalse() throws Exception {
        final String packageName = "com.hello.app1";
        final Signature signature = generateSignature((byte) 1);
        preparePackage(packageName, signature);

        assertFalse(mAllowlistController.isPackageAutomatable(packageName));
    }

    @EnableFlags(Flags.FLAG_COMPUTER_CONTROL_ALLOWLISTS)
    @Test
    public void isPackageAutomatable_multipleAllowlistedApps_multipleDenylistedApps()
            throws Exception {
        final String packageName1 = "com.hello.app1";
        final Signature signature1 = generateSignature((byte) 1);
        final String certificateDigest1 = preparePackage(packageName1, signature1);
        final String packageName2 = "com.hello.app2";
        final Signature signature2 = generateSignature((byte) 2);
        final String certificateDigest2 = preparePackage(packageName2, signature2);
        final String packageName3 = "com.hello.app3";
        final Signature signature3 = generateSignature((byte) 3);
        final String certificateDigest3 = preparePackage(packageName3, signature3);
        final List<ComputerControlAllowlistController.SignedPackage> apps = List.of(
                new ComputerControlAllowlistController.SignedPackage(
                        packageName1, certificateDigest1),
                new ComputerControlAllowlistController.SignedPackage(
                        packageName2, certificateDigest2),
                new ComputerControlAllowlistController.SignedPackage(
                        packageName3, certificateDigest3));

        mDeviceConfigWriter.allowlistAutomatableApps(apps);
        SystemClock.sleep(TIMEOUT_MILLIS);

        assertTrue(mAllowlistController.isPackageAutomatable(packageName1));
        assertTrue(mAllowlistController.isPackageAutomatable(packageName2));
        assertTrue(mAllowlistController.isPackageAutomatable(packageName3));

        final List<ComputerControlAllowlistController.SignedPackage> blocked = List.of(
                new ComputerControlAllowlistController.SignedPackage(
                        packageName1, certificateDigest1),
                new ComputerControlAllowlistController.SignedPackage(
                        packageName2, certificateDigest2));
        mDeviceConfigWriter.denylistAutomatableApps(blocked);
        SystemClock.sleep(TIMEOUT_MILLIS);

        assertFalse(mAllowlistController.isPackageAutomatable(packageName1));
        assertFalse(mAllowlistController.isPackageAutomatable(packageName2));
        assertTrue(mAllowlistController.isPackageAutomatable(packageName3));
    }

    @EnableFlags(Flags.FLAG_COMPUTER_CONTROL_ALLOWLISTS)
    @Test
    public void isPackageAutomatable_allAppsAllowlisted_denylistedApp_returnsFalse()
            throws Exception {
        final String packageName = "com.hello.fun";
        final Signature signature = generateSignature((byte) 7);
        final String certificateDigest = preparePackage(packageName, signature);

        // Allowlist all packages via DeviceConfig.
        mDeviceConfigWriter.writeValue(COMPUTER_CONTROL_AUTOMATABLE_APP_ALLOWLIST_KEY, "*");
        // Denylist the given package via DeviceConfig.
        mDeviceConfigWriter.denylistAutomatableApp(packageName, certificateDigest);
        SystemClock.sleep(TIMEOUT_MILLIS);

        assertFalse(mAllowlistController.isPackageAutomatable(packageName));
    }

    @EnableFlags(Flags.FLAG_COMPUTER_CONTROL_ALLOWLISTS)
    @Test
    @Parameters(method = "getMalformedValues")
    public void deviceConfigMalformedValue_sessionOwnerAllowlist_usesLastPersistedValue(
            String malformedValue) throws Exception {
        final String packageName = "com.hello.app4";
        final Signature signature = generateSignature((byte) 9);
        final String certificateDigest = preparePackage(packageName, signature);
        // Make PackageManager infer that the given package is associated with the calling uid.
        when(mPackageManager.getPackageUidAsUser(eq(packageName), anyInt()))
                .thenReturn(Process.myUid());

        // Allowlist the package via DeviceConfig.
        mDeviceConfigWriter.allowlistSessionOwner(packageName, certificateDigest);
        SystemClock.sleep(TIMEOUT_MILLIS);

        // Verify that the package is actually allowlisted and the allowlist is persisted to disk.
        final Path filePath = Paths.get(mSessionOwnerAllowlistFile.getAbsolutePath());
        final String expectedFileContent = packageName + ":" + certificateDigest;
        assertEquals(expectedFileContent, Files.readString(filePath));
        assertTrue(mAllowlistController.isPackageAllowedToCreateSession(packageName));

        // Write malformed value via DeviceConfig.
        mDeviceConfigWriter.writeValue(COMPUTER_CONTROL_SESSION_OWNER_ALLOWLIST_KEY,
                malformedValue);
        SystemClock.sleep(TIMEOUT_MILLIS);

        // Verify that the package is still allowlisted, based on the last persisted allowlist.
        assertEquals(expectedFileContent, Files.readString(filePath));
        assertTrue(mAllowlistController.isPackageAllowedToCreateSession(packageName));
    }

    @EnableFlags(Flags.FLAG_COMPUTER_CONTROL_ALLOWLISTS)
    @Test
    public void deviceConfigEmptyString_clearsSessionOwnerAllowlist() throws Exception {
        final String packageName = "com.hello.app4";
        final Signature signature = generateSignature((byte) 9);
        final String certificateDigest = preparePackage(packageName, signature);
        // Make PackageManager infer that the given package is associated with the calling uid.
        when(mPackageManager.getPackageUidAsUser(eq(packageName), anyInt()))
                .thenReturn(Process.myUid());

        // Allowlist the package via DeviceConfig.
        mDeviceConfigWriter.allowlistSessionOwner(packageName, certificateDigest);
        SystemClock.sleep(TIMEOUT_MILLIS);

        // Verify that the package is actually allowlisted and the allowlist is persisted to disk.
        final Path filePath = Paths.get(mSessionOwnerAllowlistFile.getAbsolutePath());
        final String expectedFileContent = packageName + ":" + certificateDigest;
        assertEquals(expectedFileContent, Files.readString(filePath));
        assertTrue(mAllowlistController.isPackageAllowedToCreateSession(packageName));

        // Write empty value via DeviceConfig.
        mDeviceConfigWriter.writeValue(COMPUTER_CONTROL_SESSION_OWNER_ALLOWLIST_KEY, "");
        SystemClock.sleep(TIMEOUT_MILLIS);

        // Verify that the allowlist is cleared.
        assertEquals("", Files.readString(filePath));
        assertFalse(mAllowlistController.isPackageAllowedToCreateSession(packageName));
    }

    @EnableFlags(Flags.FLAG_COMPUTER_CONTROL_ALLOWLISTS)
    @Parameters(method = "getMalformedValues")
    @Test
    public void deviceConfigMalformedValue_automatableAppAllowlist_usesLastPersistedValue(
            String malformedValue) throws Exception {
        final String packageName = "com.hello.app4";
        final Signature signature = generateSignature((byte) 5);
        final String certificateDigest = preparePackage(packageName, signature);

        // Allowlist the package via DeviceConfig.
        mDeviceConfigWriter.allowlistAutomatableApp(packageName, certificateDigest);
        SystemClock.sleep(TIMEOUT_MILLIS);

        // Verify that the package is actually allowlisted and the allowlist is persisted to disk.
        final Path filePath = Paths.get(mAutomatableAppAllowlistFile.getAbsolutePath());
        final String expectedFileContent = packageName + ":" + certificateDigest;
        assertEquals(expectedFileContent, Files.readString(filePath));
        assertTrue(mAllowlistController.isPackageAutomatable(packageName));

        // Write malformed value via DeviceConfig.
        mDeviceConfigWriter.writeValue(COMPUTER_CONTROL_AUTOMATABLE_APP_ALLOWLIST_KEY,
                malformedValue);
        SystemClock.sleep(TIMEOUT_MILLIS);

        // Verify that the package is still allowlisted, based on the last persisted allowlist.
        assertEquals(expectedFileContent, Files.readString(filePath));
        assertTrue(mAllowlistController.isPackageAutomatable(packageName));
    }

    @EnableFlags(Flags.FLAG_COMPUTER_CONTROL_ALLOWLISTS)
    @Test
    public void deviceConfigEmptyString_clearsAutomatableAppAllowlist() throws Exception {
        final String packageName = "com.hello.app4";
        final Signature signature = generateSignature((byte) 9);
        final String certificateDigest = preparePackage(packageName, signature);

        // Allowlist the package via DeviceConfig.
        mDeviceConfigWriter.allowlistAutomatableApp(packageName, certificateDigest);
        SystemClock.sleep(TIMEOUT_MILLIS);

        // Verify that the package is actually allowlisted and the allowlist is persisted to disk.
        final Path filePath = Paths.get(mAutomatableAppAllowlistFile.getAbsolutePath());
        final String expectedFileContent = packageName + ":" + certificateDigest;
        assertEquals(expectedFileContent, Files.readString(filePath));
        assertTrue(mAllowlistController.isPackageAutomatable(packageName));

        // Write empty value via DeviceConfig.
        mDeviceConfigWriter.writeValue(COMPUTER_CONTROL_AUTOMATABLE_APP_ALLOWLIST_KEY, "");
        SystemClock.sleep(TIMEOUT_MILLIS);

        // Verify that the allowlist is cleared.
        assertEquals("", Files.readString(filePath));
        assertFalse(mAllowlistController.isPackageAutomatable(packageName));
    }

    @EnableFlags(Flags.FLAG_COMPUTER_CONTROL_ALLOWLISTS)
    @Test
    @Parameters(method = "getMalformedValues")
    public void deviceConfigMalformedValue_automatableAppDenylist_usesLastPersistedValue(
            String malformedValue) throws Exception {
        final String packageName = "com.hello.app4";
        final Signature signature = generateSignature((byte) 8);
        final String certificateDigest = preparePackage(packageName, signature);

        // Allowlist all packages via DeviceConfig.
        mDeviceConfigWriter.writeValue(COMPUTER_CONTROL_AUTOMATABLE_APP_ALLOWLIST_KEY, "*");
        // Denylist the package via DeviceConfig.
        mDeviceConfigWriter.denylistAutomatableApp(packageName, certificateDigest);
        SystemClock.sleep(TIMEOUT_MILLIS);

        // Verify that the package is actually denylisted and the denylist is persisted to disk.
        final Path filePath = Paths.get(mAutomatableAppDenylistFile.getAbsolutePath());
        final String expectedFileContent = packageName + ":" + certificateDigest;
        assertEquals(expectedFileContent, Files.readString(filePath));
        assertFalse(mAllowlistController.isPackageAutomatable(packageName));

        // Write malformed value via DeviceConfig.
        mDeviceConfigWriter.writeValue(COMPUTER_CONTROL_AUTOMATABLE_APP_DENYLIST_KEY,
                malformedValue);
        SystemClock.sleep(TIMEOUT_MILLIS);

        // Verify that the package is still denylisted, based on the last persisted allowlist.
        assertEquals(expectedFileContent, Files.readString(filePath));
        assertFalse(mAllowlistController.isPackageAutomatable(packageName));
    }

    @EnableFlags(Flags.FLAG_COMPUTER_CONTROL_ALLOWLISTS)
    @Test
    public void deviceConfigEmptyString_clearsAutomatableAppDenylist() throws Exception {
        final String packageName = "com.hello.app4";
        final Signature signature = generateSignature((byte) 9);
        final String certificateDigest = preparePackage(packageName, signature);

        // Denylist the package via DeviceConfig.
        mDeviceConfigWriter.denylistAutomatableApp(packageName, certificateDigest);
        SystemClock.sleep(TIMEOUT_MILLIS);

        // Verify that the denylist is persisted to disk.
        final Path filePath = Paths.get(mAutomatableAppDenylistFile.getAbsolutePath());
        final String expectedFileContent = packageName + ":" + certificateDigest;
        assertEquals(expectedFileContent, Files.readString(filePath));

        // Write empty value via DeviceConfig.
        mDeviceConfigWriter.writeValue(COMPUTER_CONTROL_AUTOMATABLE_APP_DENYLIST_KEY, "");
        SystemClock.sleep(TIMEOUT_MILLIS);

        // Verify that the denylist is cleared.
        assertEquals("", Files.readString(filePath));
    }

    private String preparePackage(@NonNull String packageName, @NonNull Signature signature)
            throws Exception {
        final String certificateDigest = PackageUtils.computeSha256Digest(signature.toByteArray());
        final PackageInfo packageInfo = generatePackageInfo(signature);
        when(mPackageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES))
                .thenReturn(packageInfo);
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

    @SuppressWarnings("unused") // Parameter for parametrized tests
    private static String[] getMalformedValues() {
        return new String[] {
                null,
                "garbage",
                "1234",
                "com.android.app",
                "@#$%^&*",
        };
    }

    private static final class DeviceConfigWriter {

        private final Object mLock = new Object();
        @GuardedBy("mLock")
        private final ArrayMap<String, String> mOriginalValues = new ArrayMap<>();

        void allowlistSessionOwner(@NonNull String packageName, @NonNull String certificateDigest)
                throws Exception {
            final String value = packageName + ":" + certificateDigest;
            writeValue(COMPUTER_CONTROL_SESSION_OWNER_ALLOWLIST_KEY, value);
        }

        void allowlistSessionOwners(
                @NonNull List<ComputerControlAllowlistController.SignedPackage> sessionOwners)
                throws Exception {
            String value = "";
            for (ComputerControlAllowlistController.SignedPackage sessionOwner : sessionOwners) {
                value += sessionOwner.getPackageName() + ":" + sessionOwner.getCertificateDigest()
                        + ",";
            }
            writeValue(COMPUTER_CONTROL_SESSION_OWNER_ALLOWLIST_KEY, value);
        }

        void allowlistAutomatableApp(@NonNull String packageName,
                @NonNull String certificateDigest) throws Exception {
            final String value = packageName + ":" + certificateDigest;
            writeValue(COMPUTER_CONTROL_AUTOMATABLE_APP_ALLOWLIST_KEY, value);
        }

        void allowlistAutomatableApps(
                @NonNull List<ComputerControlAllowlistController.SignedPackage> apps)
                throws Exception {
            String value = "";
            for (ComputerControlAllowlistController.SignedPackage app : apps) {
                value += app.getPackageName() + ":" + app.getCertificateDigest() + ",";
            }
            writeValue(COMPUTER_CONTROL_AUTOMATABLE_APP_ALLOWLIST_KEY, value);
        }

        void denylistAutomatableApp(@NonNull String packageName,
                @NonNull String certificateDigest) throws Exception {
            final String value = packageName + ":" + certificateDigest;
            writeValue(COMPUTER_CONTROL_AUTOMATABLE_APP_DENYLIST_KEY, value);
        }

        void denylistAutomatableApps(
                @NonNull List<ComputerControlAllowlistController.SignedPackage> apps)
                throws Exception {
            String value = "";
            for (ComputerControlAllowlistController.SignedPackage app : apps) {
                value += app.getPackageName() + ":" + app.getCertificateDigest() + ",";
            }
            writeValue(COMPUTER_CONTROL_AUTOMATABLE_APP_DENYLIST_KEY, value);
        }

        void writeValue(@NonNull String key, @NonNull String value) throws Exception {
            synchronized (mLock) {
                if (!mOriginalValues.containsKey(key)) {
                    final String originalValue = DeviceConfig.getProperty(
                            COMPUTER_CONTROL_NAMESPACE, key);
                    if (originalValue != null) {
                        mOriginalValues.put(key, originalValue);
                    }
                }
            }
            assertTrue(DeviceConfig.setProperty(COMPUTER_CONTROL_NAMESPACE, key, value,
                    /* makeDefault */ false));
        }

        void reset() throws Exception {
            synchronized (mLock) {
                if (mOriginalValues.isEmpty()) {
                    // Write empty strings as ComputerControlAllowlistController ignores null
                    // values.
                    assertTrue(DeviceConfig.setProperty(COMPUTER_CONTROL_NAMESPACE,
                            COMPUTER_CONTROL_SESSION_OWNER_ALLOWLIST_KEY, /* value*/ "",
                            /* makeDefault */ false));
                    assertTrue(DeviceConfig.setProperty(COMPUTER_CONTROL_NAMESPACE,
                            COMPUTER_CONTROL_AUTOMATABLE_APP_ALLOWLIST_KEY, /* value*/ "",
                            /* makeDefault */ false));
                    assertTrue(DeviceConfig.setProperty(COMPUTER_CONTROL_NAMESPACE,
                            COMPUTER_CONTROL_AUTOMATABLE_APP_DENYLIST_KEY, /* value*/ "",
                            /* makeDefault */ false));
                } else {
                    for (int i = 0; i < mOriginalValues.size(); ++i) {
                        assertTrue(DeviceConfig.setProperty(COMPUTER_CONTROL_NAMESPACE,
                                mOriginalValues.keyAt(i), mOriginalValues.valueAt(i),
                                /* makeDefault */ false));
                    }
                    mOriginalValues.clear();
                }
                // Wait for changes to propagate.
                SystemClock.sleep(TIMEOUT_MILLIS);
            }
        }
    }
}
