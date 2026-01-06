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
import java.io.IOException;
import java.util.Objects;

/** Base class for reading protos. */
public abstract class ProtoReader<T extends Proto> {

    /**
     * Reads a proto from a proto input stream, assuming this is a nested field of the proto.
     *
     * @param pis The proto input stream to read from.
     * @param fieldNumber The field number of the proto.
     * @return The deserialized proto.
     * @throws IOException If there is an error reading the proto.
     */
    public T read(@NonNull ProtoInputStream pis, long fieldNumber) throws IOException {
        Objects.requireNonNull(pis);
        long token = pis.start(fieldNumber);
        T result = read(pis);
        pis.end(token);
        return result;
    }

    /**
     * Reads a proto from a proto input stream, assuming this is the root field of the proto.
     *
     * @param pis The proto input stream to read from.
     * @return The deserialized proto.
     * @throws IOException If there is an error reading the proto.
     */
    @Nullable
    public abstract T read(@NonNull ProtoInputStream pis) throws IOException;
}
