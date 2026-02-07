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

package com.android.server.appfunctions.allowlist;

import android.annotation.NonNull;
import android.util.ArrayMap;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.infra.AndroidFuture;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Implementation of {@link AppFunctionAllowlistReader} to read allowlist from system service. */
public class SystemAppFunctionAllowlistReader implements AppFunctionAllowlistReader {
    private static SystemAppFunctionAllowlistReader sInstance = null;

    private final Object mTestAllowlistLock = new Object();

    private final AtomicBoolean mEnable = new AtomicBoolean(false);

    @GuardedBy("mTestAllowlistLock")
    private final ArrayMap<String, List<String>> mTestAllowlist = new ArrayMap<>();

    // TODO(b/457349791): Remove this once the source is stable to avoid disruption
    /** Enable allowlist. */
    public void enableAllowlist() {
        mEnable.set(true);
    }

    // TODO(b/457349791): Remove this once the source is stable to avoid disruption
    /** Disable allowlist. */
    public void disableAllowlist() {
        mEnable.set(false);
    }

    // TODO(b/457349791): Remove once allowlist service is ready
    /** Sets test allowlist. */
    public void setTestAllowlist(@NonNull String agentPackageName, @NonNull List<String> packages) {
        synchronized (mTestAllowlistLock) {
            mTestAllowlist.put(agentPackageName, packages);
        }
    }

    // TODO(b/457349791): Remove once allowlist service is ready
    /** Clear test allowlist. */
    public void clearTestAllowlist() {
        synchronized (mTestAllowlistLock) {
            mTestAllowlist.clear();
        }
    }

    // TODO(b/457349791): Update to read from allowlist service when ready
    @Override
    @NonNull
    public AndroidFuture<Boolean> isAllowlisted(
            @NonNull String agentPackageName, @NonNull String targetPackageName) {
        if (mEnable.get()) {
            if (mTestAllowlist.containsKey(agentPackageName)
                    && mTestAllowlist.get(agentPackageName).contains(targetPackageName)) {
                return AndroidFuture.completedFuture(true);
            } else {
                return AndroidFuture.completedFuture(false);
            }
        } else {
            return AndroidFuture.completedFuture(true);
        }
    }

    /** Gets the singleton instance. */
    public static synchronized SystemAppFunctionAllowlistReader getInstance() {
        if (sInstance == null) {
            sInstance = new SystemAppFunctionAllowlistReader();
        }
        return sInstance;
    }
}
