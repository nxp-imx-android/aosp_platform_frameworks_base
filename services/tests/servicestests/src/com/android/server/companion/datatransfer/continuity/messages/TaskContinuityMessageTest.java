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

import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;

import android.util.proto.ProtoOutputStream;
import android.util.proto.ProtoInputStream;
import android.util.proto.ProtoParseException;

import org.junit.runner.RunWith;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Presubmit
@RunWith(AndroidTestingRunner.class)
public class TaskContinuityMessageTest {

    @Test
    public void testConstructor_dataIsContinuityDeviceConnected_hasContinuityDeviceConnectedData()
        throws IOException, ProtoParseException {

        int currentForegroundTaskId = 1234;
        final ProtoOutputStream pos = new ProtoOutputStream();
        long token = pos.start(
            android.companion.TaskContinuityMessage.DEVICE_CONNECTED);
        pos.writeInt32(
            android.companion.ContinuityDeviceConnected.CURRENT_FOREGROUND_TASK_ID,
            currentForegroundTaskId);
        pos.end(token);
        pos.flush();
        byte[] data = pos.getBytes();

        TaskContinuityMessage taskContinuityMessage
            = new TaskContinuityMessage(data);

        assertThat(taskContinuityMessage.getData())
            .isInstanceOf(ContinuityDeviceConnected.class);
        ContinuityDeviceConnected continuityDeviceConnected
            = (ContinuityDeviceConnected) taskContinuityMessage.getData();
        assertThat(continuityDeviceConnected.getCurrentForegroundTaskId())
            .isEqualTo(currentForegroundTaskId);
    }

    @Test
    public void testBuilder_setData_hasData() {
        ContinuityDeviceConnected continuityDeviceConnected
            = new ContinuityDeviceConnected(1, new ArrayList<>());

        TaskContinuityMessage taskContinuityMessage
            = new TaskContinuityMessage.Builder()
                .setData(continuityDeviceConnected)
                .build();

        assertThat(taskContinuityMessage.getData())
            .isEqualTo(continuityDeviceConnected);
    }

    @Test
    public void testToBytes_writesValidProto() throws IOException {
        int currentForegroundTaskId = 1234;
        List<RemoteTaskInfo> remoteTasks = new ArrayList<>();
        ContinuityDeviceConnected continuityDeviceConnected
            = new ContinuityDeviceConnected(
                currentForegroundTaskId,
                remoteTasks);

        TaskContinuityMessage taskContinuityMessage
            = new TaskContinuityMessage.Builder()
                    .setData(continuityDeviceConnected)
                    .build();

        byte[] data = taskContinuityMessage.toBytes();

        ProtoInputStream pis = new ProtoInputStream(data);
        pis.nextField();
        assertThat(pis.getFieldNumber())
            .isEqualTo((int) android.companion.TaskContinuityMessage.DEVICE_CONNECTED);
        long token = pis.start(
            android.companion.TaskContinuityMessage.DEVICE_CONNECTED);
        pis.nextField();
        long foregroundTaskIdFieldNumber =
            android.companion.ContinuityDeviceConnected.CURRENT_FOREGROUND_TASK_ID;
        assertThat(pis.getFieldNumber())
            .isEqualTo((int) foregroundTaskIdFieldNumber);
        assertThat(pis.readInt(foregroundTaskIdFieldNumber))
            .isEqualTo(currentForegroundTaskId);
        pis.end(token);
        pis.nextField();
        assertThat(pis.nextField()).isEqualTo(ProtoInputStream.NO_MORE_FIELDS);
    }

    @Test
    public void testWriteAndRead_roundTrip_works() throws IOException {
        int currentForegroundTaskId = 1234;
        List<RemoteTaskInfo> remoteTasks = new ArrayList<>();
        ContinuityDeviceConnected expectedData
            = new ContinuityDeviceConnected(
                currentForegroundTaskId,
                remoteTasks);

        TaskContinuityMessage expected
            = new TaskContinuityMessage.Builder().setData(expectedData).build();

        byte[] data = expected.toBytes();
        TaskContinuityMessage actual
            = new TaskContinuityMessage(data);

        assertThat(actual.getData()).isInstanceOf(ContinuityDeviceConnected.class);
        ContinuityDeviceConnected actualData
            = (ContinuityDeviceConnected) actual.getData();
        assertThat(actualData.getCurrentForegroundTaskId())
            .isEqualTo(expectedData.getCurrentForegroundTaskId());
    }
}