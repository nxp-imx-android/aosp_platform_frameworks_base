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

import android.app.HandoffActivityData;
import android.content.ComponentName;
import android.net.Uri;
import android.os.PersistableBundle;
import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;
import android.util.proto.ProtoInputStream;
import android.util.proto.ProtoOutputStream;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

@Presubmit
@RunWith(AndroidTestingRunner.class)
public class HandoffRequestResultMessageTest {

    @Test
    public void testRoundTripSerialization_works() throws IOException {
        int expectedTaskId = 1;
        int expectedStatusCode = 1;
        PersistableBundle extras = new PersistableBundle();
        extras.putString("key", "value");
        HandoffRequestResultMessage expected =
                new HandoffRequestResultMessage(
                        expectedTaskId,
                        expectedStatusCode,
                        List.of(
                                new HandoffActivityDataMessage(
                                        new HandoffActivityData.Builder(
                                                        new ComponentName(
                                                                "com.example.app",
                                                                "com.example.app.Activity"))
                                                .setFallbackUri(
                                                        Uri.parse("http://example.com/fallback"))
                                                .setExtras(extras)
                                                .build(),
                                        List.of(new byte[] {1, 2, 3, 4}))));

        final ProtoOutputStream pos = new ProtoOutputStream();
        expected.write(pos);
        pos.flush();
        final ProtoInputStream pis = new ProtoInputStream(pos.getBytes());
        final HandoffRequestResultMessage actual = HandoffRequestResultMessage.readFromProto(pis);

        assertThat(actual.taskId()).isEqualTo(expected.taskId());
        assertThat(actual.statusCode()).isEqualTo(expected.statusCode());
        assertThat(actual.activities()).hasSize(expected.activities().size());
        for (int i = 0; i < expected.activities().size(); i++) {
            assertThat(actual.activities().get(i)).isEqualTo(expected.activities().get(i));
        }
    }
}
