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

package com.android.server.ondeviceintelligence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.Resources;
import android.os.UserHandle;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(AndroidJUnit4.class)
public class OnDeviceIntelligenceManagerServiceTest {

    @Mock Context mMockContext;
    @Mock ActivityManager mMockActivityManager;
    @Mock Resources mMockResources;
    @Mock RemoteOnDeviceSandboxedInferenceService mMockRemoteInferenceService;

    private OnDeviceIntelligenceManagerService mService;
    private ActivityManager.OnUidImportanceListener mUidImportanceListener;

    private static final int TEST_UID_1 = 12345;
    private static final int TEST_UID_2 = 54321;
    private static final String TEST_SERVICE_NAME = "com.test.package/.TestService";
    private static final ComponentName TEST_COMPONENT_NAME =
            ComponentName.unflattenFromString(TEST_SERVICE_NAME);

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mMockContext.getSystemService(ActivityManager.class)).thenReturn(mMockActivityManager);
        when(mMockContext.getResources()).thenReturn(mMockResources);
        when(mMockContext.bindServiceAsUser(any(), any(), anyInt(), any(UserHandle.class)))
                .thenReturn(true);
        when(mMockContext.createContextAsUser(any(UserHandle.class), anyInt()))
                .thenReturn(mMockContext);
        when(mMockResources.getString(
                        com.android.internal.R.string
                                .config_defaultOnDeviceSandboxedInferenceService))
                .thenReturn(TEST_SERVICE_NAME);
        when(mMockResources.getString(
                        com.android.internal.R.string.config_defaultOnDeviceIntelligenceService))
                .thenReturn(TEST_SERVICE_NAME);

        mService = new OnDeviceIntelligenceManagerService(mMockContext);

        setField(mService, "mIsServiceEnabled", true);
        setField(mService, "mActivityManager", mMockActivityManager);
        setField(mService, "mRemoteInferenceService", mMockRemoteInferenceService);

        mService.onBootPhase(OnDeviceIntelligenceManagerService.PHASE_SYSTEM_SERVICES_READY);

        ArgumentCaptor<ActivityManager.OnUidImportanceListener> listenerCaptor =
                ArgumentCaptor.forClass(ActivityManager.OnUidImportanceListener.class);
        verify(mMockActivityManager).addOnUidImportanceListener(listenerCaptor.capture(), anyInt());
        mUidImportanceListener = listenerCaptor.getValue();
        assertNotNull(mUidImportanceListener);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = OnDeviceIntelligenceManagerService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private Map<Integer, Integer> getUidJobCounts() throws Exception {
        Field field = OnDeviceIntelligenceManagerService.class.getDeclaredField("mUidJobCounts");
        field.setAccessible(true);
        return (Map<Integer, Integer>) field.get(mService);
    }

    private RemoteOnDeviceSandboxedInferenceService getHighPriorityConnection() throws Exception {
        Field field =
                OnDeviceIntelligenceManagerService.class.getDeclaredField(
                        "mHighPriorityConnection");
        field.setAccessible(true);
        return (RemoteOnDeviceSandboxedInferenceService) field.get(mService);
    }

    private Set<Integer> getHighPriorityUids() throws Exception {
        Field field =
                OnDeviceIntelligenceManagerService.class.getDeclaredField("mHighPriorityUids");
        field.setAccessible(true);
        return (Set<Integer>) field.get(mService);
    }

    @Test
    public void testTrackJob_foregroundUid_createsHighPriorityBinding() throws Exception {
        when(mMockActivityManager.getUidImportance(TEST_UID_1))
                .thenReturn(ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND);

        mService.trackJobElevated(TEST_UID_1);

        assertNotNull(getHighPriorityConnection());
        assertTrue(getHighPriorityUids().contains(TEST_UID_1));
        assertEquals(1, (int) getUidJobCounts().get(TEST_UID_1));
    }

    @Test
    public void testTrackJob_backgroundUid_doesNotCreateHighPriorityBinding() throws Exception {
        when(mMockActivityManager.getUidImportance(TEST_UID_1))
                .thenReturn(ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED);

        mService.trackJobElevated(TEST_UID_1);

        assertNull(getHighPriorityConnection());
        assertTrue(getHighPriorityUids().isEmpty());
    }

    @Test
    public void testUntrackJob_lastJob_releasesHighPriorityBinding() throws Exception {
        when(mMockActivityManager.getUidImportance(TEST_UID_1))
                .thenReturn(ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND);
        mService.trackJobElevated(TEST_UID_1);
        assertNotNull(getHighPriorityConnection());

        mService.untrackJobElevated(TEST_UID_1);

        assertNull(getHighPriorityConnection());
        assertTrue(getHighPriorityUids().isEmpty());
        assertNull(getUidJobCounts().get(TEST_UID_1));
    }

    @Test
    public void testMultipleUids_sharedHighPriorityBinding() throws Exception {
        when(mMockActivityManager.getUidImportance(TEST_UID_1))
                .thenReturn(ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND);
        when(mMockActivityManager.getUidImportance(TEST_UID_2))
                .thenReturn(ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND);

        mService.trackJobElevated(TEST_UID_1);
        RemoteOnDeviceSandboxedInferenceService conn1 = getHighPriorityConnection();
        assertNotNull(conn1);

        mService.trackJobElevated(TEST_UID_2);
        RemoteOnDeviceSandboxedInferenceService conn2 = getHighPriorityConnection();
        assertNotNull(conn2);
        assertEquals(conn1, conn2); // Check it's the same shared connection

        assertTrue(getHighPriorityUids().contains(TEST_UID_1));
        assertTrue(getHighPriorityUids().contains(TEST_UID_2));

        mService.untrackJobElevated(TEST_UID_1);
        assertNotNull(getHighPriorityConnection()); // Still should be alive

        mService.untrackJobElevated(TEST_UID_2);
        assertNull(getHighPriorityConnection()); // Now should be gone
    }

    @Test
    public void testUidImportanceListener_toForeground_createsBinding() throws Exception {
        when(mMockActivityManager.getUidImportance(TEST_UID_1))
                .thenReturn(ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED);
        mService.trackJobElevated(TEST_UID_1);
        assertNull(getHighPriorityConnection());

        mUidImportanceListener.onUidImportance(
                TEST_UID_1, ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND);

        assertNotNull(getHighPriorityConnection());
        assertTrue(getHighPriorityUids().contains(TEST_UID_1));
    }

    @Test
    public void testUidImportanceListener_toForeground_noJobs_doesNotCreateBinding()
            throws Exception {
        // Ensure no jobs are tracked for this UID
        assertTrue(getUidJobCounts().isEmpty());

        // Notify listener that UID is in foreground
        mUidImportanceListener.onUidImportance(
                TEST_UID_1, ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND);

        // Verify that no high-priority connection is created
        assertNull(getHighPriorityConnection());
        assertTrue(getHighPriorityUids().isEmpty());
    }

    @Test
    public void testUidImportanceListener_toBackground_releasesBinding() throws Exception {
        when(mMockActivityManager.getUidImportance(TEST_UID_1))
                .thenReturn(ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND);
        mService.trackJobElevated(TEST_UID_1);
        assertNotNull(getHighPriorityConnection());

        mUidImportanceListener.onUidImportance(
                TEST_UID_1, ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED);

        assertNull(getHighPriorityConnection());
        assertTrue(getHighPriorityUids().isEmpty());
    }
}
