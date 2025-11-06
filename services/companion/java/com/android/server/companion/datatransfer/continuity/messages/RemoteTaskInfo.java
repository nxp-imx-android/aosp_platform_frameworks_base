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
import android.companion.datatransfer.continuity.RemoteTask;
import android.graphics.drawable.Icon;
import android.util.proto.ProtoInputStream;
import android.util.proto.ProtoOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

public record RemoteTaskInfo(
        int id,
        @NonNull String label,
        long lastUsedTimeMillis,
        @NonNull byte[] taskIcon,
        @NonNull HandoffOptions handoffOptions) {

    private static final String TAG = RemoteTaskInfo.class.getSimpleName();

    public RemoteTaskInfo {
        Objects.requireNonNull(label);
        Objects.requireNonNull(taskIcon);
        Objects.requireNonNull(handoffOptions);
    }

    @NonNull
    public static final ProtoCreator<RemoteTaskInfo> CREATOR =
            new ProtoCreator<RemoteTaskInfo>() {
                @Override
                @Nullable
                public RemoteTaskInfo read(@NonNull ProtoInputStream pis) throws IOException {
                    Objects.requireNonNull(pis);

                    int id = 0;
                    String label = "";
                    long lastUsedTimeMillis = 0;
                    byte[] taskIcon = new byte[0];
                    HandoffOptions handoffOptions = new HandoffOptions(false, false);
                    while (pis.nextField() != ProtoInputStream.NO_MORE_FIELDS) {
                        switch (pis.getFieldNumber()) {
                            case (int) android.companion.RemoteTaskInfo.ID:
                                id = pis.readInt(android.companion.RemoteTaskInfo.ID);

                                break;
                            case (int) android.companion.RemoteTaskInfo.LABEL:
                                label = pis.readString(android.companion.RemoteTaskInfo.LABEL);

                                break;
                            case (int) android.companion.RemoteTaskInfo.LAST_USED_TIME_MILLIS:
                                lastUsedTimeMillis =
                                        pis.readLong(
                                                android.companion.RemoteTaskInfo
                                                        .LAST_USED_TIME_MILLIS);
                                break;
                            case (int) android.companion.RemoteTaskInfo.TASK_ICON:
                                taskIcon =
                                        pis.readBytes(android.companion.RemoteTaskInfo.TASK_ICON);
                                break;
                            case (int) android.companion.RemoteTaskInfo.HANDOFF_OPTIONS:
                                handoffOptions =
                                        HandoffOptions.CREATOR.read(
                                                pis,
                                                android.companion.RemoteTaskInfo.HANDOFF_OPTIONS);
                                break;
                        }
                    }

                    return new RemoteTaskInfo(
                            id, label, lastUsedTimeMillis, taskIcon, handoffOptions);
                }

                @Override
                public void write(@NonNull ProtoOutputStream pos, RemoteTaskInfo value)
                        throws IOException {
                    Objects.requireNonNull(pos);
                    if (value == null) {
                        return;
                    }

                    pos.writeInt32(android.companion.RemoteTaskInfo.ID, value.id());
                    pos.writeString(android.companion.RemoteTaskInfo.LABEL, value.label());
                    pos.writeInt64(
                            android.companion.RemoteTaskInfo.LAST_USED_TIME_MILLIS,
                            value.lastUsedTimeMillis());
                    pos.writeBytes(android.companion.RemoteTaskInfo.TASK_ICON, value.taskIcon());
                    HandoffOptions.CREATOR.write(
                            pos,
                            android.companion.RemoteTaskInfo.HANDOFF_OPTIONS,
                            value.handoffOptions());
                }
            };

    @Override
    public boolean equals(Object o) {
        if (o instanceof RemoteTaskInfo) {
            RemoteTaskInfo other = (RemoteTaskInfo) o;
            return id() == other.id()
                    && label().equals(other.label())
                    && lastUsedTimeMillis() == other.lastUsedTimeMillis()
                    && handoffOptions().equals(other.handoffOptions())
                    && Arrays.equals(taskIcon(), other.taskIcon());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id(), label(), lastUsedTimeMillis(), Arrays.hashCode(taskIcon()), handoffOptions());
    }

    @NonNull
    public RemoteTask toRemoteTask(int associationId, @Nullable String associationDisplayName) {
        Icon taskIcon = null;
        if (taskIcon().length > 0) {
            taskIcon = Icon.createWithData(taskIcon(), 0, taskIcon().length);
        }

        return new RemoteTask.Builder(associationId, id())
                .setLabel(label())
                .setLastUsedTimestampMillis(lastUsedTimeMillis())
                .setAssociationDisplayName(associationDisplayName)
                .setIcon(taskIcon)
                .setHandoffEnabled(handoffOptions().isHandoffEnabled())
                .build();
    }
}
