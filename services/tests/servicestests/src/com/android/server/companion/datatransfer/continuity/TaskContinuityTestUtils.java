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

package com.android.server.companion.datatransfer.continuity;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.companion.AssociationInfo;
import android.companion.CompanionDeviceManager;
import android.companion.ICompanionDeviceManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;

import androidx.test.platform.app.InstrumentationRegistry;

import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;

public final class TaskContinuityTestUtils {

    public static Context createMockContext() {
        return Mockito.spy(
            new ContextWrapper(
                InstrumentationRegistry
                    .getInstrumentation()
                    .getTargetContext()));
    }

    public static ICompanionDeviceManager createMockCompanionDeviceManager(Context context) {
        ICompanionDeviceManager mockCompanionDeviceManagerService
            = mock(ICompanionDeviceManager.class);

        CompanionDeviceManager companionDeviceManager = new CompanionDeviceManager(
            mockCompanionDeviceManagerService,
            context);

        when(context.getSystemService(Context.COMPANION_DEVICE_SERVICE))
            .thenReturn(companionDeviceManager);

        return mockCompanionDeviceManagerService;
    }

    public static ActivityManager.RunningTaskInfo createRunningTaskInfo(
        int taskId,
        String packageName,
        long lastActiveTime) {

        ActivityManager.RunningTaskInfo taskInfo = new ActivityManager.RunningTaskInfo();
        taskInfo.taskId = taskId;
        taskInfo.baseActivity = new ComponentName(packageName, "className");
        taskInfo.lastActiveTime = lastActiveTime;
        return taskInfo;
    }

    public static AssociationInfo createAssociationInfo(int id, String displayName) {
        return new AssociationInfo.Builder(id, 0, "com.android.test")
            .setDisplayName(displayName)
            .build();
    }
}