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
import android.annotation.Nullable;
import android.util.proto.ProtoInputStream;
import android.util.proto.ProtoOutputStream;
import com.android.internal.util.FrameworkStatsLog;
import java.io.IOException;
import java.util.Objects;

/** Deserialized version of the HandoffRequestMessage proto. */
public record HandoffRequestMessage(int taskId) implements TaskContinuityMessage {

    public static final ProtoCreator<HandoffRequestMessage> CREATOR =
            new ProtoCreator<HandoffRequestMessage>() {
                @Override
                public HandoffRequestMessage read(@NonNull ProtoInputStream pis)
                        throws IOException {
                    Objects.requireNonNull(pis);

                    int taskId = 0;
                    while (pis.nextField() != ProtoInputStream.NO_MORE_FIELDS) {
                        switch (pis.getFieldNumber()) {
                            case (int) android.companion.HandoffRequestMessage.TASK_ID:
                                taskId =
                                        pis.readInt(
                                                android.companion.HandoffRequestMessage.TASK_ID);
                                break;
                        }
                    }

                    return new HandoffRequestMessage(taskId);
                }

                @Override
                public void write(
                        @NonNull ProtoOutputStream pos, @Nullable HandoffRequestMessage value)
                        throws IOException {
                    if (value == null) {
                        return;
                    }

                    Objects.requireNonNull(pos)
                            .write(android.companion.HandoffRequestMessage.TASK_ID, value.taskId());
                }
            };

    @Override
    public long getFieldNumber() {
        return android.companion.TaskContinuityMessage.HANDOFF_REQUEST;
    }

    @Override
    public void writeToProto(@NonNull ProtoOutputStream pos) throws IOException {
        HandoffRequestMessage.CREATOR.write(Objects.requireNonNull(pos), this);
    }

    @Override
    public int getTypeForMetrics() {
        return FrameworkStatsLog.TASK_CONTINUITY_MESSAGE_SENT__MESSAGE_TYPE__HANDOFF_REQUEST;
    }
}
