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

import android.app.TaskInfo;
import android.util.proto.ProtoInputStream;
import android.util.proto.ProtoOutputStream;

import java.io.IOException;

public class RemoteTaskInfo {

    private int mId = 0;
    private String mLabel = "";
    private long mLastUsedTimeMillis = 0;
    private byte[] mTaskIcon = new byte[0];

    public RemoteTaskInfo(TaskInfo taskInfo) {
        mId = taskInfo.taskId;
        mLabel = taskInfo.taskDescription.getLabel().toString();
        mLastUsedTimeMillis = taskInfo.lastActiveTime;
    }

    public RemoteTaskInfo(ProtoInputStream protoInputStream)
        throws IOException {

        while (protoInputStream.nextField() != ProtoInputStream.NO_MORE_FIELDS) {
            switch (protoInputStream.getFieldNumber()) {
              case (int) android.companion.RemoteTaskInfo.ID:
                    mId = protoInputStream.readInt(
                        android.companion.RemoteTaskInfo.ID);

                    break;
                case (int) android.companion.RemoteTaskInfo.LABEL:
                    mLabel = protoInputStream.readString(
                        android.companion.RemoteTaskInfo.LABEL);

                    break;
                case (int) android.companion.RemoteTaskInfo.LAST_USED_TIME_MILLIS:
                    mLastUsedTimeMillis = protoInputStream.readLong(
                        android.companion.RemoteTaskInfo.LAST_USED_TIME_MILLIS);
                    break;
                case (int) android.companion.RemoteTaskInfo.TASK_ICON:
                    mTaskIcon = protoInputStream
                        .readBytes(android.companion.RemoteTaskInfo.TASK_ICON);
                    break;
            }
       }
   }

    public int getId() {
        return mId;
    }

    public String getLabel() {
        return mLabel;
    }

    public long getLastUsedTimeMillis() {
        return mLastUsedTimeMillis;
    }

    public byte[] getTaskIcon() {
        return mTaskIcon;
    }

    public void writeToProto(ProtoOutputStream protoOutputStream) {
        protoOutputStream
            .writeInt32(android.companion.RemoteTaskInfo.ID, mId);
        protoOutputStream
            .writeString(android.companion.RemoteTaskInfo.LABEL, mLabel);
        protoOutputStream
            .writeInt64(
                android.companion.RemoteTaskInfo.LAST_USED_TIME_MILLIS,
                mLastUsedTimeMillis);
        protoOutputStream
            .writeBytes(android.companion.RemoteTaskInfo.TASK_ICON, mTaskIcon);
    }
}