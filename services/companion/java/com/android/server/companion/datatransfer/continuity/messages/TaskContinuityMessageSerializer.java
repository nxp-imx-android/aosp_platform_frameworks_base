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

import android.annotation.NonNull;
import android.util.proto.ProtoInputStream;
import android.util.proto.ProtoOutputStream;
import java.io.IOException;
import java.util.Objects;

/**
 * Serialized version of the {@link TaskContinuityMessage} proto, allowing for serialization and
 * deserialization from bytes.
 */
public final class TaskContinuityMessageSerializer {

    @NonNull
    public static TaskContinuityMessage deserialize(@NonNull byte[] data) throws IOException {
        ProtoInputStream pis = new ProtoInputStream(Objects.requireNonNull(data));
        if (pis.nextField() == ProtoInputStream.NO_MORE_FIELDS) {
            throw new IOException("No fields found in TaskContinuityMessage");
        }

        TaskContinuityMessage message = null;
        int fieldNumber = pis.getFieldNumber();
        final long dataToken = pis.start(fieldNumber);
        switch (fieldNumber) {
            case (int) android.companion.TaskContinuityMessage.TASK_STACK_BROADCAST:
                message = TaskStackBroadcastMessage.readFromProto(pis);
                break;
            case (int) android.companion.TaskContinuityMessage.HANDOFF_REQUEST:
                message =
                        HandoffRequestMessage.CREATOR.read(
                                pis, android.companion.TaskContinuityMessage.HANDOFF_REQUEST);
                break;
            case (int) android.companion.TaskContinuityMessage.HANDOFF_REQUEST_RESULT:
                message = HandoffRequestResultMessage.readFromProto(pis);
                break;
        }

        pis.end(dataToken);
        if (message == null) {
            throw new IOException("Serialization did not find a field for message");
        }

        return message;
    }

    @NonNull
    public static byte[] serialize(@NonNull TaskContinuityMessage message) throws IOException {
        Objects.requireNonNull(message);

        ProtoOutputStream pos = new ProtoOutputStream();
        long dataToken = pos.start(message.getFieldNumber());
        message.writeToProto(pos);
        pos.end(dataToken);
        return pos.getBytes();
    }
}
