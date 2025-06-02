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
import static com.android.server.companion.datatransfer.continuity.TaskContinuityTestUtils.createRunningTaskInfo;

import android.app.ActivityManager;
import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;
import android.util.proto.ProtoInputStream;
import android.util.proto.ProtoOutputStream;
import android.util.proto.ProtoParseException;

import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskInfo;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

@Presubmit
@RunWith(AndroidTestingRunner.class)
public class RemoteTaskUpdatedMessageTest {

    @Test
    public void testConstructor_fromObjects() {
        RemoteTaskInfo expected = createNewRemoteTaskInfo("label", 0);

        RemoteTaskUpdatedMessage remoteTaskAddedMessage
            = new RemoteTaskUpdatedMessage(expected);

        assertRemoteTaskInfoEqual(expected, remoteTaskAddedMessage.getTask());
    }

    @Test
    public void testConstructor_fromProto_hasTask() throws IOException {
        final RemoteTaskInfo expected = createNewRemoteTaskInfo("label", 0);
        final ProtoOutputStream pos = new ProtoOutputStream();
        final long taskToken = pos.start(android.companion.RemoteTaskUpdatedMessage.TASK);
        expected.writeToProto(pos);
        pos.end(taskToken);
        pos.flush();

        ProtoInputStream pis = new ProtoInputStream(pos.getBytes());
        RemoteTaskUpdatedMessage remoteTaskAddedMessage
            = new RemoteTaskUpdatedMessage(pis);

        assertRemoteTaskInfoEqual(expected, remoteTaskAddedMessage.getTask());
    }

    @Test
    public void testWriteAndRead_roundTrip_works() throws IOException {
        RemoteTaskUpdatedMessage expected
            = new RemoteTaskUpdatedMessage(createNewRemoteTaskInfo("label", 0));

        final ProtoOutputStream pos = new ProtoOutputStream();
        expected.writeToProto(pos);
        pos.flush();

        final ProtoInputStream pis = new ProtoInputStream(pos.getBytes());
        final RemoteTaskUpdatedMessage actual = new RemoteTaskUpdatedMessage(pis);

        assertRemoteTaskInfoEqual(expected.getTask(), actual.getTask());
    }

    private RemoteTaskInfo createNewRemoteTaskInfo(String label, long lastUsedTimeMillis) {
        ActivityManager.RunningTaskInfo runningTaskInfo
            = createRunningTaskInfo(1, label, lastUsedTimeMillis);
        return new RemoteTaskInfo(runningTaskInfo);
    }

    private void assertRemoteTaskInfoEqual(RemoteTaskInfo expected, RemoteTaskInfo actual) {
        assertThat(actual.getId()).isEqualTo(expected.getId());
        assertThat(actual.getLabel()).isEqualTo(expected.getLabel());
        assertThat(actual.getLastUsedTimeMillis())
            .isEqualTo(expected.getLastUsedTimeMillis());
    }
}