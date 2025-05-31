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
import static org.testng.Assert.expectThrows;

import android.companion.TaskContinuityMessage;

import android.app.ActivityManager;
import android.app.TaskInfo;
import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;
import android.util.proto.ProtoOutputStream;
import android.util.proto.ProtoInputStream;
import android.util.proto.ProtoParseException;

import org.junit.runner.RunWith;
import org.junit.Test;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.io.IOException;

@Presubmit
@RunWith(AndroidTestingRunner.class)
public class ContinuityDeviceConnectedTest {

    @Test
    public void testConstructor_fromObjects() {
        final int currentForegroundTaskId = 1234;
        final List<RemoteTaskInfo> remoteTasks = new ArrayList<>();

        final ContinuityDeviceConnected continuityDeviceConnected
            = new ContinuityDeviceConnected(
                currentForegroundTaskId,
                remoteTasks);

        assertThat(continuityDeviceConnected.getCurrentForegroundTaskId())
            .isEqualTo(currentForegroundTaskId);
        assertThat(continuityDeviceConnected.getRemoteTasks())
            .isEqualTo(remoteTasks);
    }

    @Test
    public void testConstructor_fromProto_hasCurrentForegroundTaskId()
        throws IOException, ProtoParseException {

        final int currentForegroundTaskId = 1234;
        final ProtoOutputStream pos = new ProtoOutputStream();
        pos.writeInt32(
            android.companion.ContinuityDeviceConnected.CURRENT_FOREGROUND_TASK_ID,
            currentForegroundTaskId);
        pos.flush();

        final ProtoInputStream pis = new ProtoInputStream(pos.getBytes());
        final ContinuityDeviceConnected continuityDeviceConnected
            = new ContinuityDeviceConnected(pis);

        assertThat(continuityDeviceConnected.getCurrentForegroundTaskId())
            .isEqualTo(currentForegroundTaskId);
    }

    @Test
    public void testConstructor_fromProto_setsToDefaultIfNoFieldsSet() throws Exception {
        final ProtoInputStream pis = new ProtoInputStream(new byte[0]);
        final ContinuityDeviceConnected continuityDeviceConnected
            = new ContinuityDeviceConnected(pis);

        assertThat(continuityDeviceConnected.getCurrentForegroundTaskId())
            .isEqualTo(0);
        assertThat(continuityDeviceConnected.getRemoteTasks())
            .isEmpty();
    }

    @Test
    public void testWriteToProto_writesValidProto() throws IOException {
        int currentForegroundTaskId = 1234;
        List<RemoteTaskInfo> remoteTasks = new ArrayList<>();
        ContinuityDeviceConnected continuityDeviceConnected
            = new ContinuityDeviceConnected(
                currentForegroundTaskId,
                remoteTasks);
        final ProtoOutputStream pos = new ProtoOutputStream();
        continuityDeviceConnected.writeToProto(pos);
        pos.flush();

        final ProtoInputStream pis = new ProtoInputStream(pos.getBytes());
        pis.nextField();
        long currentForegroundTaskIdFieldNumber =
            android.companion.ContinuityDeviceConnected.CURRENT_FOREGROUND_TASK_ID;
        assertThat(pis.getFieldNumber())
            .isEqualTo((int) currentForegroundTaskIdFieldNumber);
        assertThat(pis.readInt(currentForegroundTaskIdFieldNumber))
            .isEqualTo(currentForegroundTaskId);
        pis.nextField();
        assertThat(pis.nextField()).isEqualTo(ProtoInputStream.NO_MORE_FIELDS);
    }

    @Test
    public void testWriteAndRead_roundTrip_works() throws IOException {
        int expectedTaskId = 1;
        String expectedLabel = "test";
        long expectedLastActiveTime = 0;

        TaskInfo taskInfo = new ActivityManager.RunningTaskInfo();
        taskInfo.taskId = expectedTaskId;
        taskInfo.taskDescription
            = new ActivityManager.TaskDescription(expectedLabel);
        taskInfo.lastActiveTime = expectedLastActiveTime;

        int currentForegroundTaskId = 1234;
        List<RemoteTaskInfo> remoteTasks = Arrays.asList(
            new RemoteTaskInfo(taskInfo));

        ContinuityDeviceConnected expected
            = new ContinuityDeviceConnected(
                currentForegroundTaskId,
                remoteTasks);

        final ProtoOutputStream pos = new ProtoOutputStream();
        expected.writeToProto(pos);
        pos.flush();

        final ProtoInputStream pis = new ProtoInputStream(pos.getBytes());
        final ContinuityDeviceConnected actual
            = new ContinuityDeviceConnected(pis);

        assertThat(actual.getCurrentForegroundTaskId())
            .isEqualTo(expected.getCurrentForegroundTaskId());
        assertThat(actual.getRemoteTasks())
            .hasSize(1);
        RemoteTaskInfo actualTaskInfo = actual.getRemoteTasks().get(0);
        assertThat(actualTaskInfo.getId())
            .isEqualTo(expectedTaskId);
        assertThat(actualTaskInfo.getLabel())
            .isEqualTo(expectedLabel);
        assertThat(actualTaskInfo.getLastUsedTimeMillis())
            .isEqualTo(expectedLastActiveTime);
    }
}