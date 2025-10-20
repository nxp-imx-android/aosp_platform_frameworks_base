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

package com.android.extensions.computercontrol;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;

import android.companion.virtual.IVirtualDeviceManager;
import android.companion.virtual.VirtualDeviceManager;
import android.content.pm.PackageManager;
import android.testing.TestableContext;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.concurrent.Executors;

@RunWith(AndroidJUnit4.class)
public class ComputerControlExtensionsTest {
    private static final String SESSION_NAME = "test";
    private static final List<String> TARGET_PACKAGE_NAMES = List.of("com.android.foo");

    @Rule
    public final TestableContext mContext = spy(
            new TestableContext(InstrumentationRegistry.getInstrumentation().getTargetContext()));

    @Mock private PackageManager mPackageManager;
    @Mock private IVirtualDeviceManager mIVirtualDeviceManager;
    @Mock private ComputerControlSession.Callback mSessionCallback;

    private ComputerControlSession.Params mParams;
    private AutoCloseable mMockitoSession;

    @Before
    public void setUp() {
        mMockitoSession = MockitoAnnotations.openMocks(this);

        when(mPackageManager.hasSystemFeature(
                PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS))
                .thenReturn(true);
        mContext.setMockPackageManager(mPackageManager);

        mParams = new ComputerControlSession.Params.Builder(mContext)
                .setName(SESSION_NAME)
                .setTargetPackageNames(TARGET_PACKAGE_NAMES)
                .build();
    }

    @After
    public void tearDown() throws Exception {
        mMockitoSession.close();
    }

    @Test
    public void testGetVersion() {
        assertThat(ComputerControlExtensions.getVersion())
                .isEqualTo(ComputerControlExtensions.EXTENSIONS_VERSION);
    }

    @Test
    public void getInstance_missingVirtualDeviceManager_returnsNull() {
        mContext.addMockSystemService(VirtualDeviceManager.class, null);
        assertThat(ComputerControlExtensions.getInstance(mContext)).isNull();
    }

    @Test
    public void getInstance_missingSystemFeature_returnsNull() {
        when(mPackageManager.hasSystemFeature(
                     PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS))
                .thenReturn(false);

        assertThat(ComputerControlExtensions.getInstance(mContext)).isNull();
    }

    @Test
    public void getInstance_returnsNonNull() {
        assertThat(ComputerControlExtensions.getInstance(mContext)).isNotNull();
    }

    @Test
    public void requestSession_withNullParams_throwsNullPointerException() {
        ComputerControlExtensions extensions = ComputerControlExtensions.getInstance(mContext);
        assertThrows(NullPointerException.class, () ->
                extensions.requestSession(
                        null, Executors.newSingleThreadExecutor(), mSessionCallback));
    }

    @Test
    public void requestSession_withNullExecutor_throwsNullPointerException() {
        ComputerControlExtensions extensions = ComputerControlExtensions.getInstance(mContext);
        assertThrows(NullPointerException.class, () ->
                extensions.requestSession(mParams, null, mSessionCallback));
    }

    @Test
    public void requestSession_withNullCallback_throwsNullPointerException() {
        ComputerControlExtensions extensions = ComputerControlExtensions.getInstance(mContext);
        assertThrows(NullPointerException.class, () ->
                extensions.requestSession(mParams, Executors.newSingleThreadExecutor(), null));
    }

    @Test
    public void requestSession_withoutPermission_throwsException() {
        ComputerControlExtensions extensions = ComputerControlExtensions.getInstance(mContext);

        // By default, the CTS process is not allowlisted for the required knownSigner permission.
        assertThrows(SecurityException.class, () ->
                extensions.requestSession(
                        mParams, Executors.newSingleThreadExecutor(), mSessionCallback));
    }

    @Test
    public void requestSession_requestsSession() throws Exception {
        mContext.addMockSystemService(VirtualDeviceManager.class,
                new VirtualDeviceManager(mIVirtualDeviceManager, mContext));
        ComputerControlExtensions extensions = ComputerControlExtensions.getInstance(mContext);
        extensions.requestSession(mParams, Executors.newSingleThreadExecutor(), mSessionCallback);
        verify(mIVirtualDeviceManager).requestComputerControlSession(any(), any(), any());
    }
}
