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

package android.service.personalcontext.renderer;

import android.service.personalcontext.insight.ContextInsightWrapper;
import android.service.personalcontext.renderer.RendererFilter;
import android.service.personalcontext.RenderToken;

/** @hide */
interface IInsightRenderer {
    /**
     * Called with a list of insights to render. isFirst will be true if this is the first renderer
     * to see these insights.
     */
    void render(in List<ContextInsightWrapper> insights, in boolean isFirst);

    /**
     * The personal context engine will call this method when the renderer is being registered. The
     * renderer should return a {@link RendererFilter} to indicate what kinds of insights and hints
     * it is interested in rendering.
     */
    RendererFilter onRegister(in String componentId);

    /**
     * Ask the renderer to mint a new {@link RenderToken}.
     */
    RenderToken mintRenderToken();
}
