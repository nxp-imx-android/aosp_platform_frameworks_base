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

import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;

import android.util.proto.ProtoOutputStream;
import android.util.proto.ProtoInputStream;
import android.util.proto.ProtoParseException;

import org.junit.runner.RunWith;
import org.junit.Test;

import java.io.IOException;

@Presubmit
@RunWith(AndroidTestingRunner.class)
public class ContinuityDeviceConnectedTest {

    @Test
    public void testConstructor_fromCurrentForegroundTaskId() {
        final int currentForegroundTaskId = 1234;
        final ContinuityDeviceConnected continuityDeviceConnected
            = new ContinuityDeviceConnected(currentForegroundTaskId);
        assertThat(continuityDeviceConnected.getCurrentForegroundTaskId())
            .isEqualTo(currentForegroundTaskId);
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
    public void testConstructor_fromProto_missingCurrentForegroundTaskId_throwsException(){
        final ProtoOutputStream pos = new ProtoOutputStream();
        pos.flush();

        final ProtoInputStream pis = new ProtoInputStream(pos.getBytes());
        ProtoParseException e = expectThrows(
            ProtoParseException.class,
            () -> new ContinuityDeviceConnected(pis));

        assertThat(e)
            .hasMessageThat()
            .isEqualTo("Missing required field: current_foreground_task_id");
    }

    @Test
    public void testWriteToProto_writesValidProto() throws IOException {
        int currentForegroundTaskId = 1234;
        ContinuityDeviceConnected continuityDeviceConnected
            = new ContinuityDeviceConnected(currentForegroundTaskId);
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
        int currentForegroundTaskId = 1234;
        ContinuityDeviceConnected expected
            = new ContinuityDeviceConnected(currentForegroundTaskId);
        final ProtoOutputStream pos = new ProtoOutputStream();
        expected.writeToProto(pos);
        pos.flush();

        final ProtoInputStream pis = new ProtoInputStream(pos.getBytes());
        final ContinuityDeviceConnected actual
            = new ContinuityDeviceConnected(pis);

        assertThat(actual.getCurrentForegroundTaskId())
            .isEqualTo(expected.getCurrentForegroundTaskId());
    }
}