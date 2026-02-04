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

package com.android.server.personalcontext.component.client;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.os.UserHandle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.server.personalcontext.component.Renderer;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.UUID;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ServiceClientRendererTest {
    @Mock
    private Context mContext;

    @Mock
    private PackageManager mPackageManager;

    @Mock
    private UserHandle mUserHandle;


    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
    }

    boolean hasNotificationProperty(boolean metaDataPresent, boolean permissionGranted) {
        final ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.packageName = "com.foo.bar";
        serviceInfo.name = "baz";
        serviceInfo.metaData = new Bundle();

        if (metaDataPresent) {
            serviceInfo.metaData
                    .putBoolean(ServiceClientRenderer.META_DATA_RECEIVE_NOTIFICATION_INSIGHTS,
                            true);
        }

        when(mPackageManager.checkPermission(Manifest.permission.RECEIVE_SENSITIVE_NOTIFICATIONS,
                serviceInfo.packageName)).thenReturn(permissionGranted
                ? PackageManager.PERMISSION_GRANTED : PackageManager.PERMISSION_DENIED);

        final ServiceClientRenderer renderer = new ServiceClientRenderer(mContext,
                UUID.randomUUID(), serviceInfo, mUserHandle);
        return (renderer.getProperties()
                & Renderer.PROPERTY_CAN_RECEIVE_NOTIFICATION_INSIGHTS)
                == Renderer.PROPERTY_CAN_RECEIVE_NOTIFICATION_INSIGHTS;
    }

    @Test
    public void testNotifications_requestNoPermissionDenied() {
        assertThat(hasNotificationProperty(true /* metaDataPresent */,
                false /* permissionGranted*/)).isFalse();
    }

    @Test
    public void testNotifications_requestWithPermissionGranted() {
        assertThat(hasNotificationProperty(true /* metaDataPresent */,
                true /* permissionGranted*/)).isTrue();
    }

    @Test
    public void testNotifications_noRequestWithPermissionDenied() {
        assertThat(hasNotificationProperty(false /* metaDataPresent */,
                true /* permissionGranted*/)).isFalse();
    }

    @Test
    public void testNotifications_noRequestNoPermissionDenied() {
        assertThat(hasNotificationProperty(false /* metaDataPresent */,
                false /* permissionGranted*/)).isFalse();
    }
}
