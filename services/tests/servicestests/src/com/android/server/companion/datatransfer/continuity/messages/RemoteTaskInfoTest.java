/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server.companion.datatransfer.continuity.messages;

import static com.google.common.truth.Truth.assertThat;

import android.app.ActivityManager;
import android.app.TaskInfo;
import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.Test;

import android.util.proto.ProtoOutputStream;
import android.util.proto.ProtoInputStream;
import android.util.proto.ProtoParseException;

@Presubmit
@RunWith(AndroidTestingRunner.class)
public class RemoteTaskInfoTest {

    @Test
    public void testRemoteTaskInfo_fromTaskInfo_works() {
        int expectedId = 1;
        String expectedLabel = "test";
        long expectedLastActiveTime = 0;

        TaskInfo taskInfo = new ActivityManager.RunningTaskInfo();
        taskInfo.taskId = expectedId;
        taskInfo.taskDescription
            = new ActivityManager.TaskDescription(expectedLabel);
        taskInfo.lastActiveTime = expectedLastActiveTime;

        RemoteTaskInfo remoteTaskInfo = new RemoteTaskInfo(taskInfo);

        assertThat(remoteTaskInfo.getId()).isEqualTo(expectedId);
        assertThat(remoteTaskInfo.getLabel()).isEqualTo(expectedLabel);
        assertThat(remoteTaskInfo.getLastUsedTimeMillis())
            .isEqualTo(expectedLastActiveTime);
    }

    @Test
    public void testRemoteTaskInfo_fromProtoStream_setsToDefaultValues()
        throws Exception {

        ProtoInputStream pis = new ProtoInputStream(new byte[0]);
        RemoteTaskInfo remoteTaskInfo = new RemoteTaskInfo(pis);
        assertThat(remoteTaskInfo.getId()).isEqualTo(0);
        assertThat(remoteTaskInfo.getLabel()).isEmpty();
        assertThat(remoteTaskInfo.getLastUsedTimeMillis()).isEqualTo(0);
        assertThat(remoteTaskInfo.getTaskIcon()).isEmpty();
    }

    @Test
    public void testRemoteTaskInfo_fromProtoStream_works() throws Exception {
        int expectedId = 1;
        String expectedLabel = "test";
        long expectedLastActiveTime = 1;
        byte[] expectedTaskIcon = new byte[] {1, 2, 3};

        // Setup the proto stream
        ProtoOutputStream pos = new ProtoOutputStream();
        pos.writeInt32(android.companion.RemoteTaskInfo.ID, expectedId);
        pos.writeString(android.companion.RemoteTaskInfo.LABEL, expectedLabel);
        pos.writeInt64(
            android.companion.RemoteTaskInfo.LAST_USED_TIME_MILLIS,
            expectedLastActiveTime);
        pos.writeBytes(android.companion.RemoteTaskInfo.TASK_ICON,
            expectedTaskIcon);

        pos.flush();

        // Create the RemoteTaskInfo from the proto stream
        ProtoInputStream pis = new ProtoInputStream(pos.getBytes());
        RemoteTaskInfo remoteTaskInfo = new RemoteTaskInfo(pis);

        // Verify the fields
        assertThat(remoteTaskInfo.getId()).isEqualTo(expectedId);
        assertThat(remoteTaskInfo.getLabel()).isEqualTo(expectedLabel);
        assertThat(remoteTaskInfo.getLastUsedTimeMillis())
            .isEqualTo(expectedLastActiveTime);
        assertThat(remoteTaskInfo.getTaskIcon()).isEqualTo(expectedTaskIcon);
    }

    @Test
    public void testWriteAndRead_roundTrip_works() throws Exception {
        int expectedId = 1;
        String expectedLabel = "test";
        long expectedLastActiveTime = 1;

       TaskInfo taskInfo = new ActivityManager.RunningTaskInfo();
        taskInfo.taskId = expectedId;
        taskInfo.taskDescription
            = new ActivityManager.TaskDescription(expectedLabel);
        taskInfo.lastActiveTime = expectedLastActiveTime;
        RemoteTaskInfo remoteTaskInfo = new RemoteTaskInfo(taskInfo);

        ProtoOutputStream pos = new ProtoOutputStream();
        remoteTaskInfo.writeToProto(pos);
        pos.flush();

        ProtoInputStream pis = new ProtoInputStream(pos.getBytes());
        RemoteTaskInfo result = new RemoteTaskInfo(pis);

        assertThat(result.getId()).isEqualTo(expectedId);
        assertThat(result.getLabel()).isEqualTo(expectedLabel);
        assertThat(result.getLastUsedTimeMillis())
            .isEqualTo(expectedLastActiveTime);
    }
}