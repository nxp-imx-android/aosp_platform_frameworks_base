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

package com.android.internal.protolog;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.ServiceManager;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.protolog.common.IProtoLog;
import com.android.internal.protolog.common.IProtoLogGroup;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Controller for managing ProtoLog state and core logic.
 * This class is not thread-safe for concurrent modifications from multiple controllers
 * if shared, but instances are intended to be managed by the ProtoLog class or tests.
 * Internal state is synchronized via mInitLock.
 */
public class ProtoLogController {

    @Nullable
    IProtoLog mProtoLogInstance;

    @NonNull
    private final Object mInitLock = new Object();

    @GuardedBy("mInitLock")
    private final Set<IProtoLogGroup> mGroups = new HashSet<>();

    void registerLogGroupInProcess(@NonNull IProtoLogGroup... groups) {
        synchronized (mInitLock) {
            var newGroups = Arrays.stream(groups)
                    .filter(group -> !mGroups.contains(group))
                    .toArray(IProtoLogGroup[]::new);
            if (newGroups.length == 0) {
                return;
            }

            mGroups.addAll(Arrays.asList(newGroups));

            if (mProtoLogInstance != null) {
                mProtoLogInstance.registerGroups(newGroups);
            }
        }
    }

    void init(@NonNull IProtoLogGroup... groups) {
        registerLogGroupInProcess(groups);

        synchronized (mInitLock) {
            if (mProtoLogInstance != null) {
                return;
            }

            // These tracing instances are only used when we cannot or do not preprocess the source
            // files to extract out the log strings. Otherwise, the trace calls are replaced with
            // calls directly to the generated tracing implementations.
            if (ProtoLog.logOnlyToLogcat()) {
                mProtoLogInstance = new LogcatOnlyProtoLogImpl();
            } else {
                var datasource = ProtoLog.getSharedSingleInstanceDataSource();

                mProtoLogInstance = createAndEnableNewPerfettoProtoLogImpl(
                        datasource, mGroups.toArray(new IProtoLogGroup[0]));
            }
        }
    }

    /**
     * Tear down the ProtoLog instance. This should probably only be called from testing.
     * Otherwise there is no reason to teardown a ProtoLogController as it should exist for the
     * entire life of a process and be the same for the entire duration.
     */
    @VisibleForTesting
    public void tearDown() {
        synchronized (mInitLock) {
            if (mProtoLogInstance == null) {
                return;
            }

            if (mProtoLogInstance instanceof PerfettoProtoLogImpl) {
                ((PerfettoProtoLogImpl) mProtoLogInstance).disable();
            }
        }
    }

    @NonNull
    private PerfettoProtoLogImpl createAndEnableNewPerfettoProtoLogImpl(
            @NonNull ProtoLogDataSource datasource, @NonNull IProtoLogGroup[] groups) {
        try {
            var unprocessedPerfettoProtoLogImpl =
                    new UnprocessedPerfettoProtoLogImpl(datasource, groups);
            unprocessedPerfettoProtoLogImpl.enable();

            return unprocessedPerfettoProtoLogImpl;
        } catch (ServiceManager.ServiceNotFoundException e) {
            throw new RuntimeException("Failed to create PerfettoProtoLogImpl", e);
        }
    }
}
