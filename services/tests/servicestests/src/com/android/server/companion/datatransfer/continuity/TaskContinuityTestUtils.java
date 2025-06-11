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
import android.content.Context;
import android.content.ContextWrapper;

import com.android.server.companion.datatransfer.continuity.messages.TaskContinuityMessage;
import com.android.server.companion.datatransfer.continuity.messages.TaskContinuityMessageData;

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

    public static TaskContinuityMessageData verifyMessageSent(
        ICompanionDeviceManager companionDeviceManagerService,
        int[] associationIds,
        int times) throws Exception {

        ArgumentCaptor<byte[]> messageCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(companionDeviceManagerService, times(times)).sendMessage(
            eq(CompanionDeviceManager.MESSAGE_ONEWAY_TASK_CONTINUITY),
            messageCaptor.capture(),
            eq(associationIds));
        TaskContinuityMessage taskContinuityMessage = new TaskContinuityMessage(
            messageCaptor.getValue());
        return taskContinuityMessage.getData();
    }

    public static ActivityManager.RunningTaskInfo createRunningTaskInfo(
        int taskId,
        String label,
        long lastActiveTime) {

        ActivityManager.RunningTaskInfo taskInfo = new ActivityManager.RunningTaskInfo();
        taskInfo.taskId = taskId;
        taskInfo.taskDescription = new ActivityManager.TaskDescription(label);
        taskInfo.lastActiveTime = lastActiveTime;
        return taskInfo;
    }

    public static AssociationInfo createAssociationInfo(int id, String displayName) {
        return new AssociationInfo.Builder(id, 0, "com.android.test")
            .setDisplayName(displayName)
            .build();
    }
}