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

package com.android.server.companion.datatransfer.continuity.messages;

import static com.google.common.truth.Truth.assertThat;
import static org.testng.Assert.expectThrows;

import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;

import android.util.proto.ProtoOutputStream;
import android.util.proto.ProtoInputStream;

import org.junit.runner.RunWith;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Presubmit
@RunWith(AndroidTestingRunner.class)
public class TaskContinuityMessageTest {

    @Test
    public void testBuilder_setData_hasData() {
        ContinuityDeviceConnected continuityDeviceConnected
            = new ContinuityDeviceConnected(new ArrayList<>());

        TaskContinuityMessage taskContinuityMessage
            = new TaskContinuityMessage.Builder()
                .setData(continuityDeviceConnected)
                .build();

        assertThat(taskContinuityMessage.getData()).isEqualTo(continuityDeviceConnected);
    }

    @Test
    public void testWriteAndRead_roundTrip_works() throws IOException {
        RemoteTaskInfo remoteTaskInfo = new RemoteTaskInfo(1, "label", 100, new byte[0]);
        ContinuityDeviceConnected expectedData
            = new ContinuityDeviceConnected(List.of(remoteTaskInfo));

        TaskContinuityMessage expected
            = new TaskContinuityMessage.Builder().setData(expectedData).build();

        byte[] data = expected.toBytes();
        TaskContinuityMessage actual = new TaskContinuityMessage(data);

        assertThat(actual.getData()).isInstanceOf(ContinuityDeviceConnected.class);
        ContinuityDeviceConnected actualData = (ContinuityDeviceConnected) actual.getData();
        assertThat(actualData).isEqualTo(expectedData);
    }
}