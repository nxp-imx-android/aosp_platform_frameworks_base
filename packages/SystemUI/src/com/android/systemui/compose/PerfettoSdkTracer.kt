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

package com.android.systemui.compose

import android.os.PerfettoTrace
import android.os.PerfettoTrackEventExtra
import androidx.compose.runtime.InternalComposeTracingApi

// rc = recomposition tracing
private val RECOMPOSITION_CATEGORY: PerfettoTrace.Category = PerfettoTrace.Category("rc")

/**
 * Tracer that uses the framework version of the perfetto sdk to output information about
 * recompositions.
 */
@OptIn(InternalComposeTracingApi::class)
object PerfettoSdkTracer : RecompositionStateTracingObserver.Tracer {

    /** Register the [RECOMPOSITION_CATEGORY] with the Perfetto sdk. */
    fun register() {
        RECOMPOSITION_CATEGORY.register()
    }

    override fun beginSection(
        sectionName: String,
        debugInfo: String,
        flowIds: LongArray,
        terminatingFlowIds: LongArray,
    ) {
        PerfettoTrace.begin(RECOMPOSITION_CATEGORY, sectionName)
            .with(flowIds, terminatingFlowIds, debugInfo)
            .emit()
    }

    override fun instantEvent(
        sectionName: String,
        debugInfo: String,
        flowIds: LongArray,
        terminatingFlowIds: LongArray,
    ) {
        PerfettoTrace.instant(RECOMPOSITION_CATEGORY, sectionName)
            .with(flowIds, terminatingFlowIds, debugInfo)
            .emit()
    }

    override fun endSection() {
        PerfettoTrace.end(RECOMPOSITION_CATEGORY).emit()
    }

    private fun PerfettoTrackEventExtra.Builder.with(
        flowIds: LongArray,
        terminatingFlowIds: LongArray,
        debugInfo: String,
    ): PerfettoTrackEventExtra.Builder {
        flowIds.forEach { addFlow(it) }
        terminatingFlowIds.forEach { addTerminatingFlow(it) }
        addArg("debugInfo", debugInfo)
        return this
    }

    override fun isEnabled(): Boolean = RECOMPOSITION_CATEGORY.isEnabled
}
