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

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertEquals;

import android.app.HandoffActivityData;
import android.content.ComponentName;
import android.net.Uri;
import android.os.PersistableBundle;
import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;
import android.util.proto.ProtoInputStream;
import android.util.proto.ProtoOutputStream;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;

@Presubmit
@RunWith(AndroidTestingRunner.class)
public class HandoffActivityDataMessageTest {
    @Test
    public void testHandoffActivityDataMessage_roundtrips_works() throws Exception {
        ComponentName componentName =
                new ComponentName("com.example.app", "com.example.app.Activity");
        Uri fallbackUri = Uri.parse("http://example.com/fallback");
        PersistableBundle extras = new PersistableBundle();
        extras.putString("key", "value");

        HandoffActivityData activityData =
                new HandoffActivityData.Builder(componentName)
                        .setFallbackUri(fallbackUri)
                        .setExtras(extras)
                        .build();
        byte[][] expectedDigests = new byte[][] {new byte[] {1, 2, 3, 4}};
        HandoffActivityDataMessage expected =
                new HandoffActivityDataMessage(activityData, expectedDigests);

        ProtoOutputStream pos = new ProtoOutputStream();
        expected.writeToProto(pos);
        pos.flush();

        ProtoInputStream pis = new ProtoInputStream(pos.getBytes());
        HandoffActivityDataMessage actual = HandoffActivityDataMessage.fromProto(pis);

        assertEquals(expected.activity(), actual.activity());
    }

    @Test
    public void testHandoffActivityDataMessage_emptyData_throws() throws Exception {
        ProtoInputStream pis = new ProtoInputStream(new byte[] {});
        assertThrows(IOException.class, () -> HandoffActivityDataMessage.fromProto(pis));
    }
}
