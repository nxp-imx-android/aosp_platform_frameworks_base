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

package com.android.server.personalcontext.embedded;

import android.content.Context;
import android.service.personalcontext.RenderToken;
import android.service.personalcontext.embedded.InsightSurfaceClientInfo;
import android.service.personalcontext.insight.ContextInsight;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.server.personalcontext.component.Renderer;

import java.util.UUID;

/** @hide */
public class EmbeddedInsightRenderer implements Renderer {
    private final UUID mComponentId = UUID.randomUUID();

    public EmbeddedInsightRenderer(Context context) {
    }

    /**
     * Register an insight surface client and return a UUID that can be used to unregister the
     * client.
     */
    public UUID registerInsightSurfaceClient(InsightSurfaceClientInfo clientInfo) {
        return UUID.randomUUID();
    }

    /** Unregister the insight surface client with the given id. */
    public void unregisterInsightSurfaceClient(UUID id) {
    }

    /**
     * Get the render token for the given client.
     *
     * @param clientInfo the client to get the render token for
     * @return the render token for the given client, or {@code null} if the client does not exist
     */
    @Nullable
    public RenderToken getRenderTokenForClient(InsightSurfaceClientInfo clientInfo) {
        return null;
    }

    @Override
    public boolean isInterestedInInsight(ContextInsight insight) {
        // Embedded insights should be rendered due to a RenderToken, which bypasses this filter.
        // We don't want any other random insights.
        return false;
    }

    @Override
    public void render(@NonNull ContextInsight insight, boolean isFirst) {}

    @Override
    public UUID getComponentId() {
        return mComponentId;
    }
}
