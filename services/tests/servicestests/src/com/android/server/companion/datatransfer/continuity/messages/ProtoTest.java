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

import static com.google.common.truth.Truth.assertThat;

import android.testing.AndroidTestingRunner;
import android.util.proto.ProtoInputStream;
import android.util.proto.ProtoOutputStream;
import org.junit.runner.RunWith;

@RunWith(AndroidTestingRunner.class)
public abstract class ProtoTest<T extends Proto> {

    protected void verifyDefaultValue(ProtoReader<T> reader, T value) throws Exception {
        ProtoInputStream pis = new ProtoInputStream(new byte[0]);
        T result = reader.read(pis);
        assertThat(result).isEqualTo(value);
    }

    protected void verifyRoundTrip(ProtoReader<T> reader, T value) throws Exception {
        ProtoOutputStream pos = new ProtoOutputStream();
        value.write(pos);
        pos.flush();

        ProtoInputStream pis = new ProtoInputStream(pos.getBytes());
        T result = reader.read(pis);

        assertThat(result).isEqualTo(value);
    }
}
