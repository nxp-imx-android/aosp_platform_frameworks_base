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
import static org.mockito.Mockito.verify;

import org.mockito.verification.VerificationMode;

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

    /** Sets the current OOM adjustment for the given process record. */
    public static void setCurAdj(ProcessRecordInternal proc, int adj) {
        proc.setCurAdj(adj);
    }

    /** Verifies the current OOM adjustment was set for the given process record. */
    public static void verifySetCurAdj(ProcessRecordInternal proc, VerificationMode mode,
            int adj) {
        verify(proc, mode).setCurAdj(adj);
    }

    /** Sets the current raw OOM adjustment for the given process record. */
    public static void setCurRawAdj(ProcessRecordInternal proc, int adj) {
        proc.setCurRawAdj(adj);
    }

    /** Verifies the current raw OOM adjustment was set for the given process record. */
    public static void verifySetCurRawAdj(ProcessRecordInternal proc, VerificationMode mode,
            int adj) {
        verify(proc, mode).setCurRawAdj(adj);
    }

    /** Sets the last set OOM adjustment for the given process record. */
    public static void setSetAdj(ProcessRecordInternal proc, int adj) {
        proc.setSetAdj(adj);
    }

    /** Verifies the last set OOM adjustment was set for the given process record. */
    public static void verifySetSetAdj(ProcessRecordInternal proc, VerificationMode mode,
            int adj) {
        verify(proc, mode).setSetAdj(adj);
    }

    /** Sets the last set raw OOM adjustment for the given process record. */
    public static void setSetRawAdj(ProcessRecordInternal proc, int adj) {
        proc.setSetRawAdj(adj);
    }

    /** Verifies the last set raw OOM adjustment was set for the given process record. */
    public static void verifySetSetRawAdj(ProcessRecordInternal proc, VerificationMode mode,
            int adj) {
        verify(proc, mode).setSetRawAdj(adj);
    }

    /** Sets the current process state for the given process record. */
    public static void setCurProcState(ProcessRecordInternal proc, int state) {
        proc.setCurProcState(state);
    }

    /** Verifies the current process state was set for the given process record. */
    public static void verifySetCurProcState(ProcessRecordInternal proc, VerificationMode mode,
            int state) {
        verify(proc, mode).setCurProcState(state);
    }

    /** Sets the current raw process state for the given process record. */
    public static void setCurRawProcState(ProcessRecordInternal proc, int state) {
        proc.setCurRawProcState(state);
    }

    /** Verifies the current raw process state was set for the given process record. */
    public static void verifySetCurRawProcState(ProcessRecordInternal proc, VerificationMode mode,
            int state) {
        verify(proc, mode).setCurRawProcState(state);
    }

    /** Sets the last reported process state for the given process record. */
    public static void setReportedProcState(ProcessRecordInternal proc, int state) {
        proc.setReportedProcState(state);
    }

    /** Verifies the last reported process state was set for the given process record. */
    public static void verifySetReportedProcState(ProcessRecordInternal proc, VerificationMode mode,
            int state) {
        verify(proc, mode).setReportedProcState(state);
    }

    /** Sets the last set process state for the given process record. */
    public static void setSetProcState(ProcessRecordInternal proc, int state) {
        proc.setSetProcState(state);
    }

    /** Verifies the last set process state was set for the given process record. */
    public static void verifySetSetProcState(ProcessRecordInternal proc, VerificationMode mode,
            int state) {
        verify(proc, mode).setSetProcState(state);
    }

    /** Sets the last set CPU time reasons for the given process record. */
    public static void setSetCpuTimeReasons(ProcessRecordInternal proc,
            @OomAdjuster.CpuTimeReasons int reasons) {
        proc.setSetCpuTimeReasons(reasons);
    }

    /** Verifies the last set CPU time reasons were set for the given process record. */
    public static void verifySetSetCpuTimeReasons(ProcessRecordInternal proc, VerificationMode mode,
            @OomAdjuster.CpuTimeReasons int reasons) {
        verify(proc, mode).setSetCpuTimeReasons(reasons);
    }

    /** Sets the last set implicit CPU time reasons for the given process record. */
    public static void setSetImplicitCpuTimeReasons(ProcessRecordInternal proc,
            @OomAdjuster.ImplicitCpuTimeReasons int reasons) {
        proc.setSetImplicitCpuTimeReasons(reasons);
    }

    /** Verifies the last set implicit CPU time reasons were set for the given process record. */
    public static void verifySetSetImplicitCpuTimeReasons(ProcessRecordInternal proc,
            VerificationMode mode, @OomAdjuster.ImplicitCpuTimeReasons int reasons) {
        verify(proc, mode).setSetImplicitCpuTimeReasons(reasons);
    }

    /** Sets the last reported foreground activities state for the given process record. */
    public static void setRepForegroundActivities(ProcessRecordInternal proc, boolean rep) {
        proc.setRepForegroundActivities(rep);
    }

    /**
     * Verifies the last reported foreground activities state was set for the given process record.
     */
    public static void verifySetRepForegroundActivities(ProcessRecordInternal proc,
            VerificationMode mode, boolean rep) {
        verify(proc, mode).setRepForegroundActivities(rep);
    }

    /** Sets the last time a process was in the TOP state. */
    public static void setLastTopTime(ProcessRecordInternal proc, long lastTopTime) {
        proc.setLastTopTime(lastTopTime);
    }

    /** Verifies the last time a process was in the TOP state was set. */
    public static void verifySetLastTopTime(ProcessRecordInternal proc, VerificationMode mode,
            long lastTopTime) {
        verify(proc, mode).setLastTopTime(lastTopTime);
    }
}
