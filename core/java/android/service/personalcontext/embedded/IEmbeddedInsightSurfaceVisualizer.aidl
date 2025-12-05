/**
 * Copyright (c) 2025, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.service.personalcontext.embedded;

import android.service.personalcontext.embedded.IEmbeddedInsightSurfaceVisualizerCallback;
import android.service.personalcontext.embedded.InsightSurfaceClientInfo;
import android.service.personalcontext.insight.ContextInsightWrapper;
import android.view.SurfaceControlViewHost;

/** @hide */
interface IEmbeddedInsightSurfaceVisualizer {
    oneway void createVisualizationForClient(
        in List<ContextInsightWrapper> insights,
        in InsightSurfaceClientInfo clientInfo,
        in IEmbeddedInsightSurfaceVisualizerCallback callback);
    oneway void onClientConnected(in InsightSurfaceClientInfo clientInfo);
    oneway void onClientDisconnected(in InsightSurfaceClientInfo clientInfo);
}
