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
import android.os.IBinder;
import android.os.ParcelUuid;
import android.os.RemoteException;
import android.service.personalcontext.insight.ContextInsight;
import android.service.personalcontext.insight.ContextInsightWrapper;
import android.service.personalcontext.renderer.IInsightRenderer;
import android.util.Slog;

import androidx.annotation.NonNull;

import com.android.server.personalcontext.component.Renderer;

import java.util.List;
import java.util.UUID;

/**
 * Client for renderer services.
 *
 * @hide
 */
public class ServiceClientRenderer
        extends BaseServiceClientComponent<IInsightRenderer> implements Renderer {
    public ServiceClientRenderer(Context context, UUID componentId, ServiceInfo serviceInfo) {
        super(context, componentId, serviceInfo);
    }

    @Override
    protected IInsightRenderer getServiceWrapper(IBinder binder) {
        return IInsightRenderer.Stub.asInterface(binder);
    }

    @Override
    protected void initializeClient(IInsightRenderer client) throws RemoteException {
        client.configure(new ParcelUuid(getComponentId()));
    }

    @Override
    public boolean isInsightInteresting(ContextInsight insight) {
        // TODO(b/452425147): Implement this to use a filter in the package's manifest.
        // For now this runs each insight through every renderer.
        return true;
    }

    @Override
    public void render(@NonNull ContextInsight insight, boolean isFirst) {
        runWithBinder(binder -> {
            try {
                binder.render(List.of(new ContextInsightWrapper(insight)), isFirst);
            } catch (RemoteException e) {
                Slog.e(TAG, "Failed to render insight", e);
            }
        });
    }
}
