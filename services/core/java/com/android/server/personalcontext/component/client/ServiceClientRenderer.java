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

import static android.Manifest.permission.RECEIVE_SENSITIVE_NOTIFICATIONS;

import android.Manifest;
import android.annotation.PermissionManuallyEnforced;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
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
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Client for renderer services.
 *
 * @hide
 */
public class ServiceClientRenderer
        extends BaseServiceClientComponent<IInsightRenderer> implements Renderer {
    static final String META_DATA_RECEIVE_NOTIFICATION_INSIGHTS =
            "android.service.personalcontext.renderer.receive_notification_insights";
    private InsightFilter mFilter = InsightFilter.REQUIRE_RENDER_TOKEN;

    private final int mProperties;

    public ServiceClientRenderer(Context context, UUID componentId, ServiceInfo serviceInfo,
            UserHandle userHandle) {
        this(
                context,
                componentId,
                serviceInfo,
                userHandle,
                Executors.newSingleThreadExecutor(),
                new Handler(Looper.getMainLooper()));
    }

    protected ServiceClientRenderer(Context context, UUID componentId, ServiceInfo serviceInfo,
            UserHandle userHandle, Executor executor, Handler handler) {
        super(context, componentId, serviceInfo, userHandle, executor, handler);

        int properties = 0;

        final PackageManager packageManager = context.getPackageManager();
        final boolean hasSensitiveNotificationPermission = packageManager
                .checkPermission(RECEIVE_SENSITIVE_NOTIFICATIONS, serviceInfo.packageName)
                == PackageManager.PERMISSION_GRANTED;
        if (hasSensitiveNotificationPermission
                && serviceInfo.metaData != null && serviceInfo.metaData
                .getBoolean(META_DATA_RECEIVE_NOTIFICATION_INSIGHTS, false)) {
            properties |= Renderer.PROPERTY_CAN_RECEIVE_NOTIFICATION_INSIGHTS;
        }

        mProperties = properties;

        runWithScopedBinder((binder, callback) -> {
            try {
                binder.getFilter(getParcelComponentId(), new IGetFilterCallback.Stub() {
                    @PermissionManuallyEnforced
                    @Override
                    public void updateFilter(InsightFilter filter) {
                        mFilter = filter;
                    }
                }, callback);
            } catch (RemoteException e) {
                Slog.e(TAG, "Failed to get renderer filter", e);
            }
        });
    }

    @Override
    public int getProperties() {
        return mProperties;
    }

    @Override
    protected IInsightRenderer getServiceWrapper(IBinder binder) {
        return IInsightRenderer.Stub.asInterface(binder);
    }

    @Override
    protected void initializeClient(IInsightRenderer client) {
    }

    @Override
    public boolean isInterestedInInsight(PublishedContextInsight insight) {
        return mFilter.isInterestedInInsight(insight.getInsight());
    }

    @Override
    public void render(@NonNull PublishedContextInsight publishedContextInsight,
            RenderToken renderToken) {
        if (android.service.personalcontext.Flags.enforcePersonalContextPermissions()
                && !checkPermission(Manifest.permission.PERSONAL_CONTEXT_RECEIVE_INSIGHTS)) {
            Slog.w(
                    TAG,
                    "Service "
                            + getComponentName()
                            + " missing permission "
                            + Manifest.permission.PERSONAL_CONTEXT_RECEIVE_INSIGHTS);
            return;
        }
        runWithScopedBinder((binder, opCallback) -> {
            try {
                binder.render(getParcelComponentId(),
                        new PublishedContextInsightWrapper(publishedContextInsight),
                        renderToken,
                        opCallback);
            } catch (RemoteException e) {
                Slog.e(TAG, "Failed to render insight", e);
            }
        });
    }
}
