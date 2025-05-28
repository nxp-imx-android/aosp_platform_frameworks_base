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
import android.util.proto.ProtoParseException;

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

    public TaskContinuityMessage(byte[] data)
        throws IOException, ProtoParseException {

        ProtoInputStream pis = new ProtoInputStream(data);
        while (pis.nextField() != ProtoInputStream.NO_MORE_FIELDS) {
            switch (pis.getFieldNumber()) {
                case (int) android.companion.TaskContinuityMessage.DEVICE_CONNECTED:
                    final long deviceConnectedToken = pis.start(
                        android.companion.TaskContinuityMessage.DEVICE_CONNECTED
                    );

                    mData = new ContinuityDeviceConnected(pis);
                    pis.end(deviceConnectedToken);
                    break;
                case (int) android.companion.TaskContinuityMessage.REMOTE_TASK_ADDED_MESSAGE:
                    final long remoteTaskAddedMessageToken = pis.start(
                        android.companion.TaskContinuityMessage.REMOTE_TASK_ADDED_MESSAGE
                    );

                    mData = new RemoteTaskAddedMessage(pis);
                    pis.end(remoteTaskAddedMessageToken);
                    break;
            }
        }
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
    public byte[] toBytes() {
        ProtoOutputStream pos = new ProtoOutputStream();
        switch (mData) {
            case ContinuityDeviceConnected continuityDeviceConnected:
                long continutyDeviceConnectedToken = pos.start(
                    android.companion.TaskContinuityMessage.DEVICE_CONNECTED
                );

                continuityDeviceConnected.writeToProto(pos);
                pos.end(continutyDeviceConnectedToken);
                break;
            case RemoteTaskAddedMessage remoteTaskAddedMessage:
                long remoteTaskAddedMessageToken = pos.start(
                    android.companion.TaskContinuityMessage.REMOTE_TASK_ADDED_MESSAGE
                );

                remoteTaskAddedMessage.writeToProto(pos);
                pos.end(remoteTaskAddedMessageToken);
                break;
            default:
                break;
        }

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