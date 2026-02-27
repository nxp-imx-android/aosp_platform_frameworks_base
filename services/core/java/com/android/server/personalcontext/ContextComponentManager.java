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

package com.android.server.personalcontext;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.UserHandle;
import android.service.personalcontext.Flags;
import android.service.personalcontext.renderer.InsightRendererService;
import android.text.TextUtils;
import android.util.Log;
import android.util.Slog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.server.personalcontext.component.Component;
import com.android.server.personalcontext.component.Refiner;
import com.android.server.personalcontext.component.Renderer;
import com.android.server.personalcontext.component.client.ServiceClientRefiner;
import com.android.server.personalcontext.component.client.ServiceClientRenderer;
import com.android.server.personalcontext.component.client.ServiceClientUnderstander;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Manages the collection of personal context components.
 *
 * @hide
 */
class ContextComponentManager
        implements RefinerWorkflow.ComponentProvider, RendererWorkflow.ComponentProvider {
    // TODO(b/450089078): Move these actions to Intent.
    public static final String ACTION_REFINER_SERVICE =
            "android.service.personalcontext.RefinerService";

    public static final String ACTION_UNDERSTANDER_SERVICE =
            "android.service.personalcontext.UnderstanderService";

    private static final String TAG = "ContextComponentManager";

    private final Context mContext;
    private final UserHandle mUserHandle;

    private final Map<String, Set<Refiner>> mRefinersByPackage = new HashMap<>();
    private final Map<String, Set<Renderer>> mRenderersByPackage = new HashMap<>();
    private final Map<UUID, Refiner> mRefiners = new HashMap<>();
    private final Map<UUID, Renderer> mRenderers = new HashMap<>();

    private final AccessController mAccessController;

    ContextComponentManager(Context context, UserHandle userHandle,
            AccessController accessController) {
        mContext = context;
        mUserHandle = userHandle;
        mAccessController = accessController;
    }

    /** Registers an in-process refiner or understander. */
    public void register(Refiner refiner) {
        registerComponent(
                refiner,
                mRefiners,
                /* packageName= */ null,
                /* componentsByPackage= */ null);
    }

    /** Registers an in-process renderer. */
    public void register(Renderer renderer) {
        registerComponent(
                renderer,
                mRenderers,
                /* packageName= */ null,
                /* componentsByPackage= */ null);
    }

    /** Looks for all components in packages installed on the device and registers each. */
    public void registerComponentsForAllPackages() {
        registerComponentsForPackage(null);
    }

    /** Looks for all components in a single package installed on the device and registers each. */
    public void registerComponentsForPackage(String packageName) {
        for (ServiceInfo serviceInfo : getServiceInfo(ACTION_REFINER_SERVICE, packageName)) {
            if (Flags.enforcePersonalContextAllowlistAccessControl()
                    && !mAccessController.hasAccess(serviceInfo.packageName,
                    AccessController.ACCESS_RECEIVE_HINTS
                            | AccessController.ACCESS_PUBLISH_HINTS)) {
                continue;
            }

            registerComponent(
                    new ServiceClientRefiner(mContext, UUID.randomUUID(), serviceInfo,
                            mUserHandle),
                    mRefiners,
                    serviceInfo.packageName,
                    mRefinersByPackage);
        }

        for (ServiceInfo serviceInfo : getServiceInfo(ACTION_UNDERSTANDER_SERVICE, packageName)) {
            if (Flags.enforcePersonalContextAllowlistAccessControl()
                    && !mAccessController.hasAccess(serviceInfo.packageName,
                    AccessController.ACCESS_RECEIVE_HINTS
                            | AccessController.ACCESS_PUBLISH_INSIGHTS)) {
                continue;
            }

            registerComponent(
                    new ServiceClientUnderstander(mContext, UUID.randomUUID(), serviceInfo,
                            mUserHandle),
                    mRefiners,
                    serviceInfo.packageName,
                    mRefinersByPackage);
        }

        for (ServiceInfo serviceInfo :
                getServiceInfo(InsightRendererService.SERVICE_INTERFACE, packageName)) {
            if (Flags.enforcePersonalContextAllowlistAccessControl()
                    && !mAccessController.hasAccess(serviceInfo.packageName,
                    AccessController.ACCESS_RECEIVE_INSIGHTS)) {
                continue;
            }

            registerComponent(
                    new ServiceClientRenderer(mContext, UUID.randomUUID(), serviceInfo,
                            mUserHandle),
                    mRenderers,
                    serviceInfo.packageName,
                    mRenderersByPackage);
        }
    }

    /** Unregisters all components provided by a given package installed on the device. */
    public void unregisterComponentsForPackage(String packageName) {
        unregisterComponentsForPackage(packageName, mRefiners, mRefinersByPackage);
        unregisterComponentsForPackage(packageName, mRenderers, mRenderersByPackage);
    }

    /** Gets currently registered refiners/understanders. */
    @Override
    public Collection<Refiner> getRefiners() {
        return mRefiners.values();
    }

    @Override
    public Collection<Renderer> getRenderersWithProperties(
            @Renderer.RendererProperty int properties) {
        final HashSet<Renderer> renderers = new HashSet<>();

        for (Renderer renderer : mRenderers.values()) {
            if (renderer.hasProperties(properties)) {
                renderers.add(renderer);
            }
        }
        return renderers;
    }

    /** Gets currently registered renderers. */
    @Override
    public Collection<Renderer> getRenderers() {
        return mRenderers.values();
    }

    @Nullable
    Refiner getRefinerById(UUID id) {
        return mRefiners.get(id);
    }

    @Nullable
    @Override
    public Renderer getRendererById(UUID id) {
        return mRenderers.get(id);
    }

    private List<ServiceInfo> getServiceInfo(String action, @Nullable String packageName) {
        final Intent serviceIntent = new Intent(action);
        serviceIntent.setPackage(packageName);

        final List<ResolveInfo> services = mContext.getPackageManager().queryIntentServices(
                serviceIntent, PackageManager.GET_META_DATA);

        final List<ServiceInfo> result = new ArrayList<>();
        for (ResolveInfo resolveInfo : services) {
            if (resolveInfo != null && resolveInfo.serviceInfo != null) {
                result.add(resolveInfo.serviceInfo);
            }
        }

        return result;
    }

    private <T extends Component> void unregisterComponentsForPackage(
            String packageName, Map<UUID, T> components, Map<String, Set<T>> componentsByPackage) {
        final Set<T> oldComponents = componentsByPackage.get(packageName);
        if (oldComponents != null) {
            for (T component : oldComponents) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Slog.d(TAG, "Unregistering component: " + component);
                }
                components.remove(component.getComponentId());
            }

            componentsByPackage.remove(packageName);
        }
    }

    private <T extends Component> void registerComponent(
            @NonNull T component,
            @NonNull Map<UUID, T> components,
            @Nullable String packageName,
            @Nullable Map<String, Set<T>> componentsByPackage) {
        if (Log.isLoggable(TAG, Log.DEBUG)) Slog.d(TAG, "Registering component: " + component);

        components.put(component.getComponentId(), component);
        if (componentsByPackage != null && packageName != null) {
            componentsByPackage.computeIfAbsent(packageName, _unused -> new HashSet<>())
                    .add(component);
        }
    }

    public void dump(@NonNull PrintWriter fout) {
        dumpComponentList(fout, "Refiners", mRefiners.values());
        dumpComponentList(fout, "Renderers", mRenderers.values());
    }

    private void dumpComponentList(
            @NonNull PrintWriter fout,
            @NonNull String name,
            @NonNull Collection<? extends Component> components) {
        fout.write(name + "\n");
        fout.write("=".repeat(name.length()) + "\n");
        for (Component component : components) {
            fout.write("  " + component.toString() + "\n");
        }
        fout.write(TextUtils.formatSimple("  (%s configured components)\n", components.size()));
        fout.write("\n");
    }
}
