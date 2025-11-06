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

import android.companion.datatransfer.continuity.RemoteTask;
import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;
import android.util.proto.ProtoInputStream;
import android.util.proto.ProtoOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;

@Presubmit
@RunWith(AndroidTestingRunner.class)
public class RemoteTaskInfoTest extends ProtoCreatorTest<RemoteTaskInfo> {

    @Test
    public void testReadFromProto_noData_returnsDefault() throws Exception {
        verifyDefaultValue(
                RemoteTaskInfo.CREATOR,
                new RemoteTaskInfo(0, "", 0, new byte[0], new HandoffOptions(false, false)));
    }

    @Test
    public void testWriteAndRead_roundTrip_works() throws Exception {
        RemoteTaskInfo remoteTaskInfo =
                new RemoteTaskInfo(1, "label", 100L, new byte[0], new HandoffOptions(true, true));
        verifyRoundTrip(RemoteTaskInfo.CREATOR, remoteTaskInfo);
    }

    @Test
    public void testToRemoteTask_works() {
        // Setup the RemoteTaskInfo
        int expectedId = 1;
        String expectedLabel = "test";
        long expectedLastActiveTime = 100;
        String expectedAssociationDisplayName = "test_device";
        int expectedAssociationId = 2;
        boolean expectedIsHandoffEnabled = true;
        RemoteTaskInfo remoteTaskInfo =
                new RemoteTaskInfo(
                        expectedId,
                        expectedLabel,
                        expectedLastActiveTime,
                        new byte[0],
                        new HandoffOptions(expectedIsHandoffEnabled, false));

        // Convert to RemoteTask
        RemoteTask remoteTask =
                remoteTaskInfo.toRemoteTask(expectedAssociationId, expectedAssociationDisplayName);

        // Verify the fields
        assertThat(remoteTask.getTaskId()).isEqualTo(expectedId);
        assertThat(remoteTask.getLabel()).isEqualTo(expectedLabel);
        assertThat(remoteTask.getLastUsedTimestampMillis()).isEqualTo(expectedLastActiveTime);
        assertThat(remoteTask.getCompanionDeviceAssociationId()).isEqualTo(expectedAssociationId);
        assertThat(remoteTask.getAssociationDisplayName())
                .isEqualTo(expectedAssociationDisplayName);
        assertThat(remoteTask.isHandoffEnabled()).isEqualTo(expectedIsHandoffEnabled);
    }
}
