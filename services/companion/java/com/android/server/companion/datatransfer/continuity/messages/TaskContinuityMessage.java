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

import android.util.proto.ProtoInputStream;
import android.util.proto.ProtoOutputStream;

import java.io.IOException;

/**
 * Serialized version of the {@link TaskContinuityMessage} proto, allowing for
 * serialization and deserialization from bytes.
 */
public final class TaskContinuityMessage {

    private TaskContinuityMessageData mData;

    TaskContinuityMessage(Builder builder) {
        mData = builder.mData;
    }

    public TaskContinuityMessage(byte[] data) throws IOException {

        ProtoInputStream pis = new ProtoInputStream(data);
        if (pis.nextField() == ProtoInputStream.NO_MORE_FIELDS) {
            return;
        }

        int fieldNumber = pis.getFieldNumber();
        final long dataToken = pis.start(fieldNumber);
        switch (fieldNumber) {
            case (int) android.companion.TaskContinuityMessage.DEVICE_CONNECTED:
                mData = ContinuityDeviceConnected.readFromProto(pis);
                break;
            case (int) android.companion.TaskContinuityMessage.REMOTE_TASK_ADDED:
                mData = RemoteTaskAddedMessage.readFromProto(pis);
                break;
            case (int) android.companion.TaskContinuityMessage.REMOTE_TASK_REMOVED:
                mData = RemoteTaskRemovedMessage.readFromProto(pis);
                break;
            case (int) android.companion.TaskContinuityMessage.REMOTE_TASK_UPDATED:
                mData = RemoteTaskUpdatedMessage.readFromProto(pis);
                break;
            case (int) android.companion.TaskContinuityMessage.HANDOFF_REQUEST:
                mData = HandoffRequestMessage.readFromProto(pis);
                break;
            case (int) android.companion.TaskContinuityMessage.HANDOFF_REQUEST_RESULT:
                mData = HandoffRequestResultMessage.readFromProto(pis);
                break;
        }

        pis.end(dataToken);
    }

    /**
     * Returns the value of the data field.
     */
    public TaskContinuityMessageData getData() {
        return mData;
    }

    /**
     * Serializes this message to bytes.
     */
    public byte[] toBytes() throws IOException {
        ProtoOutputStream pos = new ProtoOutputStream();
        long dataToken = pos.start(mData.getFieldNumber());
        mData.writeToProto(pos);
        pos.end(dataToken);
        return pos.getBytes();
    }

    /**
     * Builder for {@link TaskContinuityMessage}.
     */
    public static final class Builder {
        private TaskContinuityMessageData mData;

        public Builder() {
        }

        /**
         * Sets the value of the data field.
         */
        public Builder setData(TaskContinuityMessageData data) {
            mData = data;
            return this;
        }

        /**
         * Builds a {@link TaskContinuityMessage}.
         */
        public TaskContinuityMessage build() {
            return new TaskContinuityMessage(this);
        }
    }
}