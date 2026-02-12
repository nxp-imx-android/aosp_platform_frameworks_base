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

import android.annotation.PermissionManuallyEnforced;
import android.content.Context;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.os.RemoteException;
import android.os.UserHandle;
import android.service.personalcontext.RenderToken;
import android.service.personalcontext.insight.InsightFilter;
import android.service.personalcontext.insight.PublishedContextInsight;
import android.service.personalcontext.insight.PublishedContextInsightWrapper;
import android.service.personalcontext.renderer.IGetFilterCallback;
import android.service.personalcontext.renderer.IInsightRenderer;
import android.util.Slog;

import androidx.annotation.NonNull;

import com.android.server.personalcontext.component.Renderer;

import java.util.UUID;

/**
 * Client for renderer services.
 *
 * @hide
 */
public class ServiceClientRenderer
        extends BaseServiceClientComponent<IInsightRenderer> implements Renderer {
    private InsightFilter mFilter = InsightFilter.REQUIRE_RENDER_TOKEN;

    public ServiceClientRenderer(Context context, UUID componentId, ServiceInfo serviceInfo,
            UserHandle userHandle) {
        super(context, componentId, serviceInfo, userHandle);

        runWithBinder(binder -> {
            try {
                binder.getFilter(new IGetFilterCallback.Stub() {
                    @PermissionManuallyEnforced
                    @Override
                    public void updateFilter(InsightFilter filter) {
                        mFilter = filter;
                    }
                });
            } catch (RemoteException e) {
                Slog.e(TAG, "Failed to get renderer filter", e);
            }
        });
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
    public boolean isInterestedInInsight(PublishedContextInsight insight) {
        return mFilter.isInterestedInInsight(insight.getInsight());
    }

    @Override
    public void render(@NonNull PublishedContextInsight publishedContextInsight,
            RenderToken renderToken) {
        runWithBinder(binder -> {
            try {
                binder.render(new PublishedContextInsightWrapper(publishedContextInsight),
                        renderToken);
            } catch (RemoteException e) {
                Slog.e(TAG, "Failed to render insight", e);
            }
        });
    }
}
