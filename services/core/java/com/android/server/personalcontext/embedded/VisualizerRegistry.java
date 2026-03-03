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

import static android.Manifest.permission.BIND_INSIGHT_SURFACE_VISUALIZER_SERVICE;
import static android.service.personalcontext.embedded.InsightSurfaceSessionException.ERROR_FAILED_TO_CREATE_SESSION;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.UserHandle;
import android.service.personalcontext.RenderToken;
import android.service.personalcontext.embedded.InsightSurfaceClientInfo;
import android.service.personalcontext.embedded.InsightSurfaceVisualizerService;
import android.service.personalcontext.insight.PublishedContextInsight;
import android.util.Slog;

import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.content.PackageMonitor;

import com.google.android.collect.Lists;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * A registry used to track {@link VisualizerConnection}s. Connections are stored and retrieved by
 * their {@link ComponentName}.
 *
 * TODO(b/467086671): Use PersonalContextManagerService's PackageMonitor instead, and then move the
 * rest of the functionality of this class into EmbeddedInsightRenderer. Then this class should no
 * longer be needed.
 *
 * @hide
 */
public class VisualizerRegistry {
    private static final String TAG = "VisualizerRegistry";

    private final Injector mInjector;
    private final Map<ComponentName, VisualizerConnection> mVisualizers = new HashMap<>();

    private final PackageMonitor mMonitor = new PackageMonitor() {
        @Override
        public void onPackageAdded(String packageName, int uid) {
            registerVisualizers(packageName);
        }

        @Override
        public void onPackageRemoved(String packageName, int uid) {
            unregisterVisualizers(packageName);
        }

        @Override
        public boolean onPackageChanged(String packageName, int uid, String[] components) {
            unregisterVisualizers(packageName);
            registerVisualizers(packageName);
            return false;
        }
    };

    /**
     * A helper object to inject dependencies into {@link VisualizerRegistry}.
     * @hide
     */
    @VisibleForTesting
    public interface Injector {
        /**
         * Create a new {@link VisualizerConnection} with the given {@link ComponentName}.
         *
         * @param componentName the {@link ComponentName} of the connection
         * @return the newly created {@link VisualizerConnection}
         */
        VisualizerConnection createVisualizerConnection(ComponentName componentName);

        /**
         * Return a list {@link ServiceInfo} objects for the visualizer services in the given
         * package.
         */
        List<ServiceInfo> fetchVisualizerServiceInfos(@Nullable String packageName);

        /**
         * Register the {@link PackageMonitor} that will be used detect visualizer services as
         * packages come and go.
         */
        void registerPackageMonitor(PackageMonitor monitor);

        /**
         * Queue the given {@link Runnable} action to be performed on a shared thread.
         */
        void queueAction(Runnable action);
    }

    private static final class DefaultInjector implements Injector {
        private final Context mContext;
        private final Executor mExecutor;

        DefaultInjector(Context context, Executor executor) {
            mContext = context;
            mExecutor = executor;
        }

        @Override
        public VisualizerConnection createVisualizerConnection(ComponentName componentName) {
            return new VisualizerConnection(componentName, mContext, mExecutor);
        }

        @Override
        public List<ServiceInfo> fetchVisualizerServiceInfos(@Nullable String packageName) {
            // TODO(b/467129462): Introduce an allow list to further restrict connected services.
            final Intent intent = new Intent(InsightSurfaceVisualizerService.SERVICE_INTERFACE);
            intent.setPackage(packageName);
            final List<ResolveInfo> services =
                    mContext.getPackageManager().queryIntentServices(intent, 0);
            final List<ServiceInfo> result = new ArrayList<>(services.size());
            for (ResolveInfo resolveInfo : services) {
                if (!BIND_INSIGHT_SURFACE_VISUALIZER_SERVICE
                        .equals(resolveInfo.serviceInfo.permission)) {
                    continue;
                }
                result.add(resolveInfo.serviceInfo);
            }

            return result;
        }

        @Override
        public void registerPackageMonitor(PackageMonitor monitor) {
            monitor.register(
                    mContext,
                    /* looper= */ null,
                    /* user= */ UserHandle.CURRENT,
                    /* externalStorage= */ false);
        }

        @Override
        public void queueAction(Runnable action) {
            mExecutor.execute(action);
        }
    }

    VisualizerRegistry(Context context, Executor executor) {
        this(new DefaultInjector(context, executor));
    }

    /** Construct a new {@link VisualizerRegistry}. Provided for testing. */
    @VisibleForTesting
    VisualizerRegistry(Injector injector) {
        mInjector = injector;
    }

    /** Return true if there are no visualizers in this registry. */
    public boolean isEmpty() {
        return mVisualizers.isEmpty();
    }

    /**
     * Tell the registry to start registering visualizers. The registry will also begin monitoring
     * package changes so that visualizers can be automatically added and removed as necessary.
     */
    public void startRegisteringVisualizers() {
        mInjector.queueAction(() -> {
            mVisualizers.clear();

            registerVisualizers(null);
            mInjector.registerPackageMonitor(mMonitor);
        });
    }

    /** Perform the given action on all visualizers. */
    public void performActionOnVisualizers(Consumer<VisualizerConnection> action) {
        mInjector.queueAction(() -> mVisualizers.values().forEach(action));
    }

    /**
     * Attempt to create a visualization for the given {@link InsightSurfaceClientInfo} by finding
     * a visualizer that can accept the given insights.
     */
    public void createVisualizationForClient(
            PublishedContextInsight publishedContextInsight,
            InsightSurfaceClientInfo client,
            RenderToken renderToken) {
        mInjector.queueAction(() ->
                createVisualizationForClient(
                        publishedContextInsight, client, renderToken,
                        mVisualizers.values().iterator()));
    }

    private void registerVisualizers(@Nullable String packageName) {
        mInjector.queueAction(() -> {
            for (ServiceInfo serviceInfo :
                    mInjector.fetchVisualizerServiceInfos(packageName)) {
                final ComponentName visualizerComponentName =
                        new ComponentName(serviceInfo.packageName, serviceInfo.name);
                if (mVisualizers.containsKey(visualizerComponentName)) {
                    Slog.w(TAG, "visualizer already exists");
                    continue;
                }

                final VisualizerConnection visualizer =
                        mInjector.createVisualizerConnection(visualizerComponentName);
                mVisualizers.put(visualizerComponentName, visualizer);

                visualizer.onRegistered();
            }
        });
    }

    private void unregisterVisualizers(String packageName) {
        final List<ComponentName> toRemove = Lists.newArrayList();
        mInjector.queueAction(() -> {
            mVisualizers.entrySet().stream()
                    .filter(entry -> Objects.equals(entry.getKey().getPackageName(), packageName))
                    .forEach(entry -> {
                        entry.getValue().onUnregistered();
                        toRemove.add(entry.getKey());
                    });
            toRemove.forEach(mVisualizers::remove);
        });
    }

    private void createVisualizationForClient(
            PublishedContextInsight publishedInsight,
            InsightSurfaceClientInfo client,
            RenderToken renderToken,
            Iterator<VisualizerConnection> visualizers) {
        if (!visualizers.hasNext()) {
            // Didn't find a visualizer that could produce a visualization.
            client.onVisualizationError(ERROR_FAILED_TO_CREATE_SESSION);
            return;
        }

        final VisualizerConnection nextVisualizer = visualizers.next();
        nextVisualizer.createVisualizationForClient(
                publishedInsight,
                client,
                renderToken,
                (success) -> {
                    if (!success) {
                        createVisualizationForClient(publishedInsight, client, renderToken,
                                visualizers);
                    }
                });
    }
}
