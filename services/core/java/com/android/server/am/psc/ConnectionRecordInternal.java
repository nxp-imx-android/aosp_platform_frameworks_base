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

package com.android.server.am.psc;

import android.ravenwood.annotation.RavenwoodKeepWholeClass;

/**
 * An abstract base class encapsulating common internal properties and state for a single binding
 * to a service.
 */
@RavenwoodKeepWholeClass
public abstract class ConnectionRecordInternal {
    /** The service binding operation. */
    private final long mFlags;
    /** Whether there are currently ongoing transactions over this service connection. */
    private boolean mOngoingCalls;

    public ConnectionRecordInternal(long flags) {
        this.mFlags = flags;
    }

    public long getFlags() {
        return mFlags;
    }

    /**
     * Checks if any of specific flags (int) is set for this connection.
     * Because the bind flags are bitwise flags, we can check for multiple flags by
     * bitwise-ORing them together (e.g., {@code hasFlag(FLAG1 | FLAG2)}). In this
     * case, the method returns true if *any* of the specified flags are present.
     */
    public boolean hasFlag(final int flag) {
        return hasFlag(Integer.toUnsignedLong(flag));
    }

    /**
     * Checks if any of specific flags (long) is set for this connection.
     * Because the bind flags are bitwise flags, we can check for multiple flags by
     * bitwise-ORing them together (e.g., {@code hasFlag(FLAG1 | FLAG2)}). In this
     * case, the method returns true if *any* of the specified flags are present.
     */
    public boolean hasFlag(final long flag) {
        return (mFlags & flag) != 0;
    }

    /** Checks if all of the specific flags (int) are NOT set for this connection. */
    public boolean notHasFlag(final int flag) {
        return !hasFlag(flag);
    }

    /** Checks if all of the specific flag (long) are NOT set for this connection. */
    public boolean notHasFlag(final long flag) {
        return !hasFlag(flag);
    }

    public boolean getOngoingCalls() {
        return mOngoingCalls;
    }

    /** Sets the ongoing calls status for this connection. Returns true if the status is changed. */
    public boolean setOngoingCalls(boolean ongoingCalls) {
        if (mOngoingCalls != ongoingCalls) {
            mOngoingCalls = ongoingCalls;
            return true;
        }
        return false;
    }
}
