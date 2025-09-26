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
import android.util.proto.ProtoOutputStream;
import java.io.IOException;

import org.junit.Test;

class TaskContinuityMessageSerializerTest {

    @Test
    public void testDeserialize_emptyData_throwsIOException() {
        assertThrows(
                IOException.class, () -> TaskContinuityMessageSerializer.deserialize(new byte[0]));
    }

    @Test
    public void testDeserialize_messageIsNotTaskContinuityMessage_throwsIOException()
            throws IOException {
        final ProtoOutputStream pos = new ProtoOutputStream();
        pos.write(1000, "unknown field");
        pos.flush();

        assertThrows(
                IOException.class,
                () -> TaskContinuityMessageSerializer.deserialize(pos.getBytes()));
    }
}
