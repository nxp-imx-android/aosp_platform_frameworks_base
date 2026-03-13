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

package com.android.systemui.personalcontext.visualizer.templates

import android.service.personalcontext.embedded.InsightSurfaceClientInfo
import android.service.personalcontext.insight.ContextInsight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/** Responsible for constructing the remote template UI for a particular template type. */
interface VisualizerTemplate {

    /** Validates the inputs. */
    fun validate(insights: ContextInsight): Boolean

    /**
     * The Composable UI content this template constructs for the given inputs.
     *
     * Implementations can access the [InsightSurfaceClientInfo] via
     * [LocalInsightSurfaceClientInfo].
     */
    @Composable fun Content(insights: ContextInsight)
}

/** Provides a [InsightSurfaceClientInfo] that can be used by the template. */
val LocalInsightSurfaceClientInfo =
    compositionLocalOf<InsightSurfaceClientInfo> { error("No InsightSurfaceClientInfo provided") }
