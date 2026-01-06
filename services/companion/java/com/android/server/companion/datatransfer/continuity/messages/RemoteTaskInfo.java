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
import java.io.IOException;
import java.util.Objects;

public record RemoteTaskInfo(
        int id,
        @NonNull String packageName,
        boolean isInForeground,
        long lastUsedTimeMillis,
        @NonNull HandoffOptions handoffOptions)
        implements Proto {

    public RemoteTaskInfo {
        Objects.requireNonNull(packageName);
        Objects.requireNonNull(handoffOptions);
    }

    @Override
    public void write(@NonNull ProtoOutputStream pos) throws IOException {
        Objects.requireNonNull(pos).writeInt32(android.companion.RemoteTaskInfo.ID, id());
        pos.writeString(android.companion.RemoteTaskInfo.PACKAGE_NAME, packageName());
        pos.writeBool(android.companion.RemoteTaskInfo.IS_IN_FOREGROUND, isInForeground());
        pos.writeInt64(
                android.companion.RemoteTaskInfo.LAST_USED_TIME_MILLIS, lastUsedTimeMillis());
        Proto.writeField(pos, android.companion.RemoteTaskInfo.HANDOFF_OPTIONS, handoffOptions());
    }

    @NonNull
    public static final ProtoReader<RemoteTaskInfo> READER =
            new ProtoReader<RemoteTaskInfo>() {
                @Override
                @Nullable
                public RemoteTaskInfo read(@NonNull ProtoInputStream pis) throws IOException {
                    Objects.requireNonNull(pis);

                    int id = 0;
                    String packageName = "";
                    long lastUsedTimeMillis = 0;
                    boolean isInForeground = false;
                    HandoffOptions handoffOptions = new HandoffOptions(false, false);
                    while (pis.nextField() != ProtoInputStream.NO_MORE_FIELDS) {
                        switch (pis.getFieldNumber()) {
                            case (int) android.companion.RemoteTaskInfo.ID:
                                id = pis.readInt(android.companion.RemoteTaskInfo.ID);

                                break;
                            case (int) android.companion.RemoteTaskInfo.PACKAGE_NAME:
                                packageName =
                                        pis.readString(
                                                android.companion.RemoteTaskInfo.PACKAGE_NAME);

                                break;
                            case (int) android.companion.RemoteTaskInfo.LAST_USED_TIME_MILLIS:
                                lastUsedTimeMillis =
                                        pis.readLong(
                                                android.companion.RemoteTaskInfo
                                                        .LAST_USED_TIME_MILLIS);
                                break;
                            case (int) android.companion.RemoteTaskInfo.HANDOFF_OPTIONS:
                                handoffOptions =
                                        HandoffOptions.READER.read(
                                                pis,
                                                android.companion.RemoteTaskInfo.HANDOFF_OPTIONS);
                                break;
                            case (int) android.companion.RemoteTaskInfo.IS_IN_FOREGROUND:
                                isInForeground =
                                        pis.readBoolean(
                                                android.companion.RemoteTaskInfo.IS_IN_FOREGROUND);
                                break;
                        }
                    }

                    return new RemoteTaskInfo(
                            id, packageName, isInForeground, lastUsedTimeMillis, handoffOptions);
                }
            };
}
