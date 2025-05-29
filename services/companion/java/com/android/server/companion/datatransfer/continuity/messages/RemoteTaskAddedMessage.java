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

import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskInfo;

import android.util.proto.ProtoInputStream;
import android.util.proto.ProtoOutputStream;

import java.io.IOException;

/**
 * Deserialized version of the {@link RemoteTaskAdded} proto.
 */
public class RemoteTaskAddedMessage implements TaskContinuityMessageData {

    private RemoteTaskInfo mTask;

    public RemoteTaskAddedMessage(RemoteTaskInfo task) {
        mTask = task;
    }

    RemoteTaskAddedMessage(ProtoInputStream pis) throws IOException {
        while (pis.nextField() != ProtoInputStream.NO_MORE_FIELDS) {
            switch (pis.getFieldNumber()) {
                case (int) android.companion.RemoteTaskAddedMessage.TASK:
                    final long taskToken = pis.start(
                        android.companion.RemoteTaskAddedMessage.TASK);
                    mTask = new RemoteTaskInfo(pis);
                    pis.end(taskToken);
                    break;
            }
        }
    }

    @Override
    public void writeToProto(ProtoOutputStream pos) {
        long taskToken = pos.start(
            android.companion.RemoteTaskAddedMessage.TASK);

        mTask.writeToProto(pos);
        pos.end(taskToken);
    }

    public RemoteTaskInfo getTask() {
        return mTask;
    }
}