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

import com.android.server.personalcontext.component.Transformer;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Client for transformer services.
 *
 * @hide
 */
public class ServiceClientTransformer extends BaseServiceClientComponent implements Transformer {
    private static final String TAG = "TransformerClient";

    public ServiceClientTransformer(Context context, UUID componentId, ServiceInfo serviceInfo) {
        super(context, componentId, serviceInfo);
    }

    @Override
    public Set<ContextInsight> getInterestingInsights() {
        // TODO: Implement this.
        return Collections.emptySet();
    }

    @Override
    public void transform(@NonNull ContextInsight insight,
            @NonNull Consumer<Set<ContextInsight>> callback) {
        // TODO: Implement this.
        callback.accept(Collections.emptySet());
    }
}
