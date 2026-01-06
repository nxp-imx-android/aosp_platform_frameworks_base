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

import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;
import android.util.proto.ProtoInputStream;
import android.util.proto.ProtoOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

@Presubmit
@RunWith(AndroidTestingRunner.class)
public class TaskStackBroadcastMessageTest {

    @Test
    public void testRoundTrip_notEmpty_works() throws IOException {
        verifyRoundTrip(
                new TaskStackBroadcastMessage(
                        Arrays.asList(
                                new RemoteTaskInfo(
                                        1,
                                        "package_name",
                                        true,
                                        50,
                                        new HandoffOptions(true, true)))));
    }

    @Test
    public void testRoundTrip_isEmpty_works() throws IOException {
        verifyRoundTrip(new TaskStackBroadcastMessage(List.of()));
    }

    @Test
    public void testGetFieldNumber_returnsCorrectValue() {
        TaskStackBroadcastMessage TaskStackBroadcastMessage =
                new TaskStackBroadcastMessage(new ArrayList<>());

        assertThat(TaskStackBroadcastMessage.getFieldNumber())
                .isEqualTo(android.companion.TaskContinuityMessage.TASK_STACK_BROADCAST);
    }

    private void verifyRoundTrip(TaskStackBroadcastMessage expected) throws IOException {
        final ProtoOutputStream pos = new ProtoOutputStream();
        expected.write(pos);
        pos.flush();

        assertThat(pos.getBytes()).isNotEmpty();

        final ProtoInputStream pis = new ProtoInputStream(pos.getBytes());
        final TaskStackBroadcastMessage actual = TaskStackBroadcastMessage.readFromProto(pis);

        assertThat(actual).isEqualTo(expected);
    }
}
