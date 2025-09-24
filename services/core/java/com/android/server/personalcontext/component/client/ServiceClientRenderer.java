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

package com.android.server.personalcontext.component.client;

import android.content.Context;
import android.content.pm.ServiceInfo;
import android.service.personalcontext.insight.ContextInsight;

import androidx.annotation.NonNull;

import com.android.server.personalcontext.component.Renderer;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

/**
 * Client for renderer services.
 *
 * @hide
 */
public class ServiceClientRenderer extends BaseServiceClientComponent implements Renderer {
    private static final String TAG = "RendererClient";

    public ServiceClientRenderer(Context context, UUID componentId, ServiceInfo serviceInfo) {
        super(context, componentId, serviceInfo);
    }

    @Override
    public Set<ContextInsight> getInterestingInsights() {
        // TODO: Implement this.
        return Collections.emptySet();
    }

    @Override
    public void render(@NonNull ContextInsight insight, boolean isFirst) {
        // TODO: Implement this.
    }
}
