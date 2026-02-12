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

package com.android.server.am.psc;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doCallRealMethod;

/**
 * Utility class for testing Process State Controller (PSC) components.
 *
 * <p>This class resides in the {@code com.android.server.am.psc} package to allow access to
 * package-private members for testing purposes.
 */
public final class MockUtils {
    private MockUtils() {
        // Do not instantiate.
    }

    /**
     * Pass through the package-private setter methods in {@link ServiceRecordInternal} to invoke
     * the real implementation on a mock object.
     */
    public static void passThroughServiceRecordInternal(ServiceRecordInternal record) {
        doCallRealMethod().when(record).setStartRequested(anyBoolean());
        doCallRealMethod().when(record).setIsForeground(anyBoolean());
        doCallRealMethod().when(record).setLastActivity(anyLong());
        doCallRealMethod().when(record).setForegroundServiceType(anyInt());
        doCallRealMethod().when(record).setLastTopAlmostPerceptibleBindRequestUptimeMs(anyLong());
        doCallRealMethod().when(record).setHostProcess(nullable(ProcessRecordInternal.class));
    }

    /** Sets the last time a process was in the TOP state. */
    public static void setLastTopTime(ProcessRecordInternal proc, long lastTopTime) {
        proc.setLastTopTime(lastTopTime);
    }
}
