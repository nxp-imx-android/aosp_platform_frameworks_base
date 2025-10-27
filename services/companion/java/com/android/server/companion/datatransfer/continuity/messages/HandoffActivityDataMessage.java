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
import android.app.HandoffActivityData;
import android.util.proto.ProtoInputStream;
import android.util.proto.ProtoOutputStream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record HandoffActivityDataMessage(HandoffActivityData activity,
                                         byte[][] packageSignatureDigests) {

    public static HandoffActivityDataMessage fromProto(
            @NonNull ProtoInputStream protoInputStream)
            throws IOException {
        Objects.requireNonNull(protoInputStream);

        HandoffActivityData activityData = null;
        List<byte[]> packageSignatureDigests = new ArrayList<>();
        while (protoInputStream.nextField() != ProtoInputStream.NO_MORE_FIELDS) {
            switch (protoInputStream.getFieldNumber()) {
                case (int) android.companion.HandoffActivityDataMessage.ACTIVITY:
                    long token =
                            protoInputStream.start(
                                    android.companion.HandoffActivityDataMessage.ACTIVITY);
                    activityData = HandoffActivityDataSerializer.readFromProto(protoInputStream);
                    protoInputStream.end(token);
                    break;
                case (int) android.companion.HandoffActivityDataMessage.SIGNATURE_DIGESTS:
                    packageSignatureDigests.add(protoInputStream.readBytes(
                            android.companion.HandoffActivityDataMessage.SIGNATURE_DIGESTS));
                    break;
            }
        }

        if (activityData == null) {
            throw new IOException(
                    "HandoffActivityDataMessage is missing HandoffActivityData field");
        }

        return new HandoffActivityDataMessage(activityData,
                packageSignatureDigests.toArray(new byte[0][]));
    }

    public void writeToProto(ProtoOutputStream protoOutputStream) throws IOException {
        long token = protoOutputStream.start(
                android.companion.HandoffActivityDataMessage.ACTIVITY);
        HandoffActivityDataSerializer.writeToProto(activity(), protoOutputStream);
        protoOutputStream.end(token);

        for (byte[] signatureDigest : packageSignatureDigests()) {
            protoOutputStream.write(
                    android.companion.HandoffActivityDataMessage.SIGNATURE_DIGESTS,
                    signatureDigest);
        }
    }
}
