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

import org.junit.runner.RunWith;
import org.junit.Test;
import static org.junit.Assert.assertThrows;

import android.util.proto.ProtoOutputStream;
import android.util.proto.ProtoInputStream;

import java.io.IOException;

@Presubmit
@RunWith(AndroidTestingRunner.class)
public class HandoffActivityDataMessageTest {
    @Test
    public void testHandoffActivityDataMessage_roundtrips_works()
            throws Exception {
        ComponentName componentName =
                new ComponentName("com.example.app", "com.example.app.Activity");
        Uri fallbackUri = Uri.parse("http://example.com/fallback");
        PersistableBundle extras = new PersistableBundle();
        extras.putString("key", "value");

        HandoffActivityData activityData = new HandoffActivityData.Builder(componentName)
                .setFallbackUri(fallbackUri)
                .setExtras(extras)
                .build();
        byte[][] expectedDigests = new byte[][]{new byte[]{1, 2, 3, 4}};
        HandoffActivityDataMessage expected =
                new HandoffActivityDataMessage(activityData,
                        expectedDigests);

        ProtoOutputStream pos = new ProtoOutputStream();
        expected.writeToProto(pos);
        pos.flush();

        ProtoInputStream pis = new ProtoInputStream(pos.getBytes());
        HandoffActivityDataMessage actual =
                HandoffActivityDataMessage.fromProto(pis);

        assertThat(actual.activity().getComponentName()).isEqualTo(
                expected.activity().getComponentName());
        assertThat(actual.activity().getFallbackUri()).isEqualTo(
                expected.activity().getFallbackUri());
        assertThat(actual.activity().getExtras()).isNotNull();
        assertThat(actual.activity().getExtras().size()).isEqualTo(
                expected.activity().getExtras().size());
        for (String key : expected.activity().getExtras().keySet()) {
            assertThat(actual.activity().getExtras().getString(key))
                    .isEqualTo(expected.activity().getExtras().getString(key));
        }
    }

    @Test
    public void testHandoffActivityDataMessage_roundtrips_Exception()
            throws Exception {
        ProtoInputStream pis = new ProtoInputStream(new byte[]{});
        assertThrows(IOException.class, () -> HandoffActivityDataMessage.fromProto(pis));
    }
}
