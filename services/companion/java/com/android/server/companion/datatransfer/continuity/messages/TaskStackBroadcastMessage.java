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
import android.util.Slog;
import android.util.proto.ProtoInputStream;
import android.util.proto.ProtoOutputStream;
import com.android.internal.util.FrameworkStatsLog;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Deserialized version of the {@link TaskStackBroadcastMessage} proto. */
public record TaskStackBroadcastMessage(@NonNull List<RemoteTaskInfo> remoteTasks)
        implements TaskContinuityMessage {

    private static final String TAG = TaskStackBroadcastMessage.class.getSimpleName();

    public TaskStackBroadcastMessage {
        Objects.requireNonNull(remoteTasks);
    }

    @NonNull
    public static TaskStackBroadcastMessage readFromProto(@NonNull ProtoInputStream pis)
            throws IOException {

        Objects.requireNonNull(pis);

        boolean isStackEmpty = false;
        List<RemoteTaskInfo> remoteTasks = new ArrayList<>();
        while (pis.nextField() != ProtoInputStream.NO_MORE_FIELDS) {
            switch (pis.getFieldNumber()) {
                case (int) android.companion.TaskStackBroadcastMessage.IS_STACK_EMPTY:
                    isStackEmpty =
                            pis.readBoolean(
                                    android.companion.TaskStackBroadcastMessage.IS_STACK_EMPTY);
                    break;
                case (int) android.companion.TaskStackBroadcastMessage.REMOTE_TASKS:
                    RemoteTaskInfo remoteTaskInfo =
                            RemoteTaskInfo.READER.read(
                                    pis, android.companion.TaskStackBroadcastMessage.REMOTE_TASKS);
                    if (remoteTaskInfo != null) {
                        remoteTasks.add(remoteTaskInfo);
                    } else {
                        Slog.w(TAG, "Failed to read RemoteTaskInfo from proto.");
                    }
                    break;
            }
        }

        if (isStackEmpty && !remoteTasks.isEmpty()) {
            Slog.w(
                    TAG,
                    "Received TaskStackBroadcastMessage which indiciated the stack was empty but"
                            + " also contains tasks.");
        }

        return new TaskStackBroadcastMessage(remoteTasks);
    }

    /** Returns the proto field number for this message type. */
    @Override
    public long getFieldNumber() {
        return android.companion.TaskContinuityMessage.TASK_STACK_BROADCAST;
    }

    /** Writes this object to a proto output stream. */
    @Override
    public void write(@NonNull ProtoOutputStream pos) throws IOException {
        if (remoteTasks().isEmpty()) {
            Objects.requireNonNull(pos)
                    .writeBool(android.companion.TaskStackBroadcastMessage.IS_STACK_EMPTY, true);
        } else {
            for (RemoteTaskInfo remoteTaskInfo : remoteTasks()) {
                Proto.writeField(
                        pos,
                        android.companion.TaskStackBroadcastMessage.REMOTE_TASKS,
                        remoteTaskInfo);
            }
        }
    }

    @Override
    public int getTypeForMetrics() {
        return FrameworkStatsLog.TASK_CONTINUITY_MESSAGE_SENT__MESSAGE_TYPE__TASK_STACK_BROADCAST;
    }
}
