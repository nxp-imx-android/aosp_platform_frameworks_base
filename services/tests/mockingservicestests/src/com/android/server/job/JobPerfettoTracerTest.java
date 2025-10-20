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

package com.android.server.job;

import static com.android.server.job.Flags.FLAG_USE_PERFETTO_SDK_FOR_TRACING;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;

import org.junit.Rule;
import org.junit.Test;

public final class JobPerfettoTracerTest {
    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Test
    @DisableFlags(FLAG_USE_PERFETTO_SDK_FOR_TRACING)
    public void testCreate_returnsNoOpTracer_whenFlagDisabled() {
        assertFalse("The flag should be disabled for this test",
                Flags.usePerfettoSdkForTracing());

        JobPerfettoTracer tracer = JobPerfettoTracer.create();

        assertNotNull("Tracer should not be null", tracer);
        assertFalse("Trace-enabled should be false", tracer.isTraceEnabled());
    }

    @Test
    @DisableFlags(FLAG_USE_PERFETTO_SDK_FOR_TRACING)
    public void testNoOpTracer_methodsAreSafe() {
        JobPerfettoTracer tracer = JobPerfettoTracer.create();

        try {
            // The fluent API of the no-op tracer should be safe to call.
            tracer.startEvent("event")
                    .addField(1L, 1)
                    .addField(2L, 2L)
                    .emit();
        } catch (Exception e) {
            throw new AssertionError("No-op tracer methods should not throw exceptions", e);
        }
    }

    @Test
    @EnableFlags(FLAG_USE_PERFETTO_SDK_FOR_TRACING)
    public void testCreate_returnsPerfettoTracer() {
        JobPerfettoTracer tracer = JobPerfettoTracer.create();

        // Due to the way the Perfetto class provides a static cache of the
        // runtime flag to check the state of the Perfetto API version, there is
        // no safe way to validate the distinction between JobPerfettoTracerV3
        // and JobPerfettoTracerLegacy versions of the object. Therefore, just
        // verify that the tracer is not a no-op instance.
        assertFalse("Tracer should not be a no-op instance",
                tracer instanceof JobPerfettoTracer.JobPerfettoTracerNoOp);
    }
}
