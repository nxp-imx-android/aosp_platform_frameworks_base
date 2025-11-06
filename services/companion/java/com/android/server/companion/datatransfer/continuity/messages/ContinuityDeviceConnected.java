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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Deserialized version of the {@link ContinuityDeviceConnected} proto. */
public record ContinuityDeviceConnected(@NonNull List<RemoteTaskInfo> remoteTasks)
        implements TaskContinuityMessage {

    private static final String TAG = ContinuityDeviceConnected.class.getSimpleName();

    public ContinuityDeviceConnected {
        Objects.requireNonNull(remoteTasks);
    }

    @NonNull
    public static ContinuityDeviceConnected readFromProto(@NonNull ProtoInputStream pis)
            throws IOException {

        Objects.requireNonNull(pis);

        List<RemoteTaskInfo> remoteTasks = new ArrayList<>();
        while (pis.nextField() != ProtoInputStream.NO_MORE_FIELDS) {
            switch (pis.getFieldNumber()) {
                case (int) android.companion.ContinuityDeviceConnected.REMOTE_TASKS:
                    RemoteTaskInfo remoteTaskInfo =
                            RemoteTaskInfo.CREATOR.read(
                                    pis, android.companion.ContinuityDeviceConnected.REMOTE_TASKS);
                    if (remoteTaskInfo != null) {
                        remoteTasks.add(remoteTaskInfo);
                    } else {
                        Slog.w(TAG, "Failed to read RemoteTaskInfo from proto.");
                    }
                    break;
            }
        }

        return new ContinuityDeviceConnected(remoteTasks);
    }

    /** Returns the proto field number for this message type. */
    @Override
    public long getFieldNumber() {
        return android.companion.TaskContinuityMessage.DEVICE_CONNECTED;
    }

    /** Writes this object to a proto output stream. */
    @Override
    public void writeToProto(@NonNull ProtoOutputStream pos) throws IOException {
        Objects.requireNonNull(pos);

        for (RemoteTaskInfo remoteTaskInfo : remoteTasks()) {
            RemoteTaskInfo.CREATOR.write(
                    pos, android.companion.ContinuityDeviceConnected.REMOTE_TASKS, remoteTaskInfo);
        }
    }
}
