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

package com.android.server.companion.datatransfer.continuity.tasks;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import android.companion.datatransfer.continuity.RemoteTask;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;
import androidx.test.InstrumentationRegistry;
import com.android.frameworks.servicestests.R;
import com.android.server.companion.datatransfer.continuity.messages.HandoffOptions;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskInfo;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@Presubmit
@RunWith(AndroidTestingRunner.class)
public class RemoteTaskFactoryTest {

    @Mock private PackageManager mockPackageManager;
    @Mock private ActivityInfo mockBrowserActivityInfo;

    private RemoteTaskFactory remoteTaskFactory;

    private final String BROWSER_PACKAGE_NAME = "com.android.browser";
    private final String BROWSER_LABEL = "Browser";

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mockPackageManager.getDefaultBrowserPackageNameAsUser(0))
                .thenReturn(BROWSER_PACKAGE_NAME);
        setupMockApplicationInfo(BROWSER_PACKAGE_NAME, BROWSER_LABEL, R.drawable.black_32x32);

        remoteTaskFactory =
                new RemoteTaskFactory(
                        0,
                        InstrumentationRegistry.getInstrumentation().getTargetContext(),
                        mockPackageManager);
    }

    @Test
    public void testCreate_packageInstalled_returnsRemoteTask() {
        int associationId = 1;
        String associationDisplayName = "test_device";
        String packageName = "com.example.app";
        String label = "label";
        setupMockApplicationInfo(packageName, label, R.drawable.black_32x32);
        HandoffOptions handoffOptions = new HandoffOptions(true, false);
        RemoteTaskInfo remoteTaskInfo =
                new RemoteTaskInfo(1, packageName, true, 100, handoffOptions);

        RemoteTask remoteTask =
                remoteTaskFactory.create(associationId, associationDisplayName, remoteTaskInfo);

        assertThat(remoteTask.getAssociationDisplayName()).isEqualTo(associationDisplayName);
        assertThat(remoteTask.getPackageName()).isEqualTo(packageName);
        assertThat(remoteTask.getLabel()).isEqualTo(label);
        assertThat(remoteTask.isHandoffEnabled()).isTrue();
        assertThat(remoteTask.isTaskInForeground()).isTrue();
    }

    @Test
    public void testCreate_handoffDisabled_returnsNull() {
        int associationId = 1;
        String associationDisplayName = "test_device";
        String packageName = "com.example.app";
        String label = "label";
        setupMockApplicationInfo(packageName, label, R.drawable.black_32x32);
        HandoffOptions handoffOptions = new HandoffOptions(false, false);
        RemoteTaskInfo remoteTaskInfo =
                new RemoteTaskInfo(1, packageName, true, 100, handoffOptions);

        RemoteTask remoteTask =
                remoteTaskFactory.create(associationId, associationDisplayName, remoteTaskInfo);

        assertThat(remoteTask).isNull();
    }

    @Test
    public void testCreate_packageNotInstalledAndWebHandoffDisabled_returnsNull() {
        int associationId = 1;
        String associationDisplayName = "test_device";
        String packageName = "com.example.uninstalled_app";
        HandoffOptions handoffOptions = new HandoffOptions(true, true);
        RemoteTaskInfo remoteTaskInfo =
                new RemoteTaskInfo(1, packageName, true, 100, handoffOptions);

        RemoteTask remoteTask =
                remoteTaskFactory.create(associationId, associationDisplayName, remoteTaskInfo);

        assertThat(remoteTask).isNull();
    }

    @Test
    public void testCreate_packageNotInstalledButHandoffAllowedWithoutPackage_returnsRemoteTask() {
        int associationId = 1;
        String associationDisplayName = "test_device";
        String packageName = "com.example.uninstalled_app";
        HandoffOptions handoffOptions = new HandoffOptions(true, false);
        RemoteTaskInfo remoteTaskInfo =
                new RemoteTaskInfo(1, packageName, true, 100, handoffOptions);

        RemoteTask remoteTask =
                remoteTaskFactory.create(associationId, associationDisplayName, remoteTaskInfo);

        assertThat(remoteTask).isNotNull();
        assertThat(remoteTask.getAssociationDisplayName()).isEqualTo(associationDisplayName);
        assertThat(remoteTask.getPackageName()).isEqualTo(BROWSER_PACKAGE_NAME);
        assertThat(remoteTask.getLabel()).isEqualTo(BROWSER_LABEL);
        assertThat(remoteTask.isHandoffEnabled()).isTrue();
        assertThat(remoteTask.isTaskInForeground()).isTrue();
    }

    private void setupMockApplicationInfo(String packageName, String label, int iconResourceId) {
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = packageName;
        packageInfo.applicationInfo = new ApplicationInfo();
        packageInfo.applicationInfo.name = packageName;
        packageInfo.applicationInfo.icon = iconResourceId;
        try {
            when(mockPackageManager.getPackageInfo(
                            eq(packageName), eq(PackageManager.GET_META_DATA)))
                    .thenReturn(packageInfo);
        } catch (PackageManager.NameNotFoundException e) {
        }
        when(mockPackageManager.getApplicationLabel(eq(packageInfo.applicationInfo)))
                .thenReturn(label);
    }
}
