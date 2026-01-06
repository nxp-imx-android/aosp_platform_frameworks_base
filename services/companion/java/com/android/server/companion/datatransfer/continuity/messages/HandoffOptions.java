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
import android.util.proto.ProtoInputStream;
import android.util.proto.ProtoOutputStream;
import java.io.IOException;
import java.util.Objects;

/**
 * Options for handoff.
 *
 * <p>This class is used to serialize and deserialize the handoff options from the proto.
 */
public record HandoffOptions(boolean isHandoffEnabled, boolean requirePackageInstalled)
        implements Proto {

    @Override
    public void write(@NonNull ProtoOutputStream pos) throws IOException {
        Objects.requireNonNull(pos)
                .writeBool(android.companion.HandoffOptions.IS_HANDOFF_ENABLED, isHandoffEnabled());
        pos.writeBool(
                android.companion.HandoffOptions.REQUIRE_PACKAGE_INSTALLED,
                requirePackageInstalled());
    }

    public static final ProtoReader<HandoffOptions> READER =
            new ProtoReader<HandoffOptions>() {
                @Override
                public HandoffOptions read(@NonNull ProtoInputStream pis) throws IOException {
                    Objects.requireNonNull(pis);
                    boolean isHandoffEnabled = false;
                    boolean requirePackageInstalled = false;
                    while (pis.nextField() != ProtoInputStream.NO_MORE_FIELDS) {
                        switch (pis.getFieldNumber()) {
                            case (int) android.companion.HandoffOptions.IS_HANDOFF_ENABLED:
                                isHandoffEnabled =
                                        pis.readBoolean(
                                                android.companion.HandoffOptions
                                                        .IS_HANDOFF_ENABLED);
                                break;
                            case (int) android.companion.HandoffOptions.REQUIRE_PACKAGE_INSTALLED:
                                requirePackageInstalled =
                                        pis.readBoolean(
                                                android.companion.HandoffOptions
                                                        .REQUIRE_PACKAGE_INSTALLED);
                                break;
                        }
                    }
                    return new HandoffOptions(isHandoffEnabled, requirePackageInstalled);
                }
            };
}
