/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.server.files;

import android.os.storage.operations.FileOperationRequest;
import android.os.storage.operations.sources.AppDataFileSource;
import android.os.storage.operations.targets.PccTarget;
import android.util.Pair;
import android.util.Slog;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/** Processor for private app data file operations (via installd). */
public class InstalldProcessor implements FileOperationProcessor {
    private static final String TAG = "InstalldProcessor";

    private static final Set<Pair<Class<?>, Class<?>>> SUPPORTED_PAIRS = new HashSet<>();

    static {
        SUPPORTED_PAIRS.add(new Pair<>(AppDataFileSource.class, PccTarget.class));
    }

    @Override
    public void process(FileService.RequestContext ctx, StatusCallback callback)
            throws IOException {
        Slog.d(TAG, "Processing via InstalldProcessor for uid " + ctx.initiatorUid());
        // TODO(b/467302127): Implement interaction with installd
        throw new IOException("InstalldProcessor not implemented yet");
    }

    @Override
    public boolean canHandle(FileOperationRequest request) {
        return SUPPORTED_PAIRS.contains(
                new Pair<>(request.getSource().getClass(), request.getTarget().getClass()));
    }
}
