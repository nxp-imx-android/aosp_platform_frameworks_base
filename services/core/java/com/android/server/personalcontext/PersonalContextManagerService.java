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

import android.annotation.PermissionManuallyEnforced;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.UserHandle;
import android.service.personalcontext.IPersonalContextManager;
import android.service.personalcontext.PersonalContextManager;
import android.service.personalcontext.RenderToken;
import android.service.personalcontext.hint.ContextHint;
import android.service.personalcontext.hint.ContextHintWrapper;
import android.service.personalcontext.insight.ContextInsight;
import android.service.personalcontext.insight.ContextInsightWrapper;
import android.service.personalcontext.hint.NotificationEvent;
import android.service.personalcontext.hint.NotificationHint;
import android.util.Log;
import android.util.Slog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.content.PackageMonitor;
import com.android.internal.util.DumpUtils;
import com.android.server.SystemService;
import com.android.server.personalcontext.component.Component;
import com.android.server.personalcontext.component.Refiner;
import com.android.server.personalcontext.component.Renderer;
import com.android.server.personalcontext.component.Transformer;
import com.android.server.personalcontext.component.client.ServiceClientRefiner;
import com.android.server.personalcontext.component.client.ServiceClientRenderer;
import com.android.server.personalcontext.component.client.ServiceClientTransformer;
import com.android.server.personalcontext.component.client.ServiceClientUnderstander;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * @hide
 */
public class PersonalContextManagerService extends SystemService {
    // TODO(b/450089078): Move these actions to Intent.
    public static final String ACTION_REFINER_SERVICE =
            "android.service.personalcontext.RefinerService";

    public static final String ACTION_UNDERSTANDER_SERVICE =
            "android.service.personalcontext.UnderstanderService";

    public static final String ACTION_TRANSFORMER_SERVICE =
            "android.service.personalcontext.TransformerService";

    public static final String ACTION_RENDERER_SERVICE =
            "android.service.personalcontext.RendererService";

    private static final String TAG = "PersonalContext";

    // Set DEBUG_LOGGING to false to disable debug logs and remove from user builds.
    private static final boolean DEBUG_LOGGING = Log.isLoggable(TAG, Log.DEBUG);

    private final Monitor mMonitor = new Monitor(this);

    private boolean mRegisteredMonitor = false;

    private final Map<String, Set<Refiner>> mRefinersByPackage = new HashMap<>();
    private final Map<String, Set<Transformer>> mTransformersByPackage = new HashMap<>();
    private final Map<String, Set<Renderer>> mRenderersByPackage = new HashMap<>();
    private final Set<Refiner> mRefiners = new HashSet<>();
    private final Set<Transformer> mTransformers = new HashSet<>();
    private final Set<Renderer> mRenderers = new HashSet<>();

    private final PersonalContextManagerInternal mInternalService =
            new PersonalContextManagerInternal() {
                @Override
                public void onNotificationEvent(@NonNull NotificationEvent event) {
                    final List<ContextHint> hints =
                            List.of(new NotificationHint.NotificationHintBuilder(event).build());
                    // TODO(b/434644900): Start refiner workflow with the hints.
                }
            };

    public PersonalContextManagerService(Context context) {
        super(context);
    }

    @Override
    public void onStart() {
        publishBinderService(
                PersonalContextManager.PERSONAL_CONTEXT_SERVICE,
                new BinderService(this));
        publishLocalService(PersonalContextManagerInternal.class, mInternalService);

        Slog.i(TAG, "Personal Context Service started");
    }

    @Override
    public void onUserUnlocked(@NonNull TargetUser user) {
        if (mRegisteredMonitor) {
            return;
        }

        mRegisteredMonitor = true;

        if (DEBUG_LOGGING) Slog.d(TAG, "Registering internal components");
        // Register in-process components here.

        if (DEBUG_LOGGING) Slog.d(TAG, "Registering external components");
        registerComponents(null);

        if (DEBUG_LOGGING) Slog.d(TAG, "Starting package monitor");
        mMonitor.register(
                getContext(),
                /* looper= */ null,
                /* user= */ UserHandle.CURRENT,
                /* externalStorage= */ false);
    }

    private List<ServiceInfo> getServiceInfo(String action, @Nullable String packageName) {
        final Intent serviceIntent = new Intent(action);
        serviceIntent.setPackage(packageName);
        final List<ResolveInfo> services = getContext().getPackageManager().queryIntentServices(
                serviceIntent, PackageManager.GET_META_DATA);
        final List<ServiceInfo> result = new ArrayList<>(services.size());
        for (ResolveInfo resolveInfo : services) {
            if (resolveInfo != null && resolveInfo.serviceInfo != null) {
                result.add(resolveInfo.serviceInfo);
            }
        }

        return result;
    }

    private void unregisterComponents(String packageName) {
        unregisterComponents(packageName, mRefinersByPackage, mRefiners);
        unregisterComponents(packageName, mTransformersByPackage, mTransformers);
        unregisterComponents(packageName, mRenderersByPackage, mRenderers);
    }

    private <T> void unregisterComponents(
            String packageName, Map<String, Set<T>> componentsByPackage, Set<T> components) {
        final Set<T> oldComponents = componentsByPackage.get(packageName);
        if (oldComponents != null) {
            if (DEBUG_LOGGING) {
                for (T component : oldComponents) {
                    Slog.d(TAG, "Unregistering component: " + component);
                }
            }
            components.removeAll(oldComponents);
            componentsByPackage.remove(packageName);
        }
    }

    private void registerComponents(@Nullable String packageName) {
        for (ServiceInfo serviceInfo : getServiceInfo(ACTION_REFINER_SERVICE, packageName)) {
            registerComponent(
                    new ServiceClientRefiner(getContext(), UUID.randomUUID(), serviceInfo),
                    mRefiners,
                    serviceInfo.packageName,
                    mRefinersByPackage);
        }

        for (ServiceInfo serviceInfo : getServiceInfo(ACTION_UNDERSTANDER_SERVICE, packageName)) {
            registerComponent(
                    new ServiceClientUnderstander(getContext(), UUID.randomUUID(), serviceInfo),
                    mRefiners,
                    serviceInfo.packageName,
                    mRefinersByPackage);
        }

        for (ServiceInfo serviceInfo : getServiceInfo(ACTION_TRANSFORMER_SERVICE, packageName)) {
            registerComponent(
                    new ServiceClientTransformer(getContext(), UUID.randomUUID(), serviceInfo),
                    mTransformers,
                    serviceInfo.packageName,
                    mTransformersByPackage);
        }

        for (ServiceInfo serviceInfo : getServiceInfo(ACTION_RENDERER_SERVICE, packageName)) {
            registerComponent(
                    new ServiceClientRenderer(getContext(), UUID.randomUUID(), serviceInfo),
                    mRenderers,
                    serviceInfo.packageName,
                    mRenderersByPackage);
        }
    }

    private <T> void registerComponent(T component, Set<T> components) {
        registerComponent(
                component,
                components,
                /* packageName= */ null,
                /* componentsByPackage= */ null);
    }

    private <T> void registerComponent(
            @NonNull T component,
            @NonNull Set<T> components,
            @Nullable String packageName,
            @Nullable Map<String, Set<T>> componentsByPackage) {
        if (DEBUG_LOGGING) Slog.d(TAG, "Registering component: " + component);
        components.add(component);
        if (componentsByPackage != null && packageName != null) {
            componentsByPackage.computeIfAbsent(packageName, _unused -> new HashSet<>())
                    .add(component);
        }
    }

    private void startRefinerWorkflow(Set<ContextHint> hints, RenderToken renderToken) {
        // This is just the worst.
        try {
            if (DEBUG_LOGGING) Slog.d(TAG, "Refiner workflow started");

            for (Refiner refiner : mRefiners) {
                if (DEBUG_LOGGING) Slog.d(TAG, "Sending to refiner: " + refiner);
                refiner.refine(hints, newHints -> {});
            }

            if (DEBUG_LOGGING) Slog.d(TAG, "Refiner workflow complete");
        } catch (Exception e) {
            Slog.e(TAG, "Refiner workflow failed", e);
        }
    }

    private void startInsightWorkflow(Set<ContextInsight> insights) {
        try {
            // Please don't look at my shame.
            if (DEBUG_LOGGING) Slog.d(TAG, "Insight workflow started");

            for (ContextInsight insight : insights) {
                if (DEBUG_LOGGING) Slog.d(TAG, "Handling insight: " + insight);
                for (Renderer renderer : mRenderers) {
                    if (DEBUG_LOGGING) Slog.d(TAG, "Sending to renderer: " + renderer);
                    renderer.render(insight, false);
                }
            }

            if (DEBUG_LOGGING) Slog.d(TAG, "Insight workflow complete");
        } catch (Exception e) {
            Slog.e(TAG, "Insight workflow failed", e);
        }
    }


    private static final class BinderService extends IPersonalContextManager.Stub {
        private final WeakReference<PersonalContextManagerService> mService;

        private BinderService(PersonalContextManagerService service) {
            mService = new WeakReference<>(service);
        }

        private PersonalContextManagerService getService() {
            final PersonalContextManagerService service = mService.get();
            if (service == null) {
                Slog.e(TAG, "Service not available");
                throw new RuntimeException("Service not available");
            }
            return service;
        }

        @PermissionManuallyEnforced
        @Override
        public void publishTriggeringHint(List<ContextHintWrapper> hints, RenderToken renderToken) {
            // TODO(b/450547433): Add security checks.
            getService().startRefinerWorkflow(
                    ContextHintWrapper.unwrapInto(hints, new HashSet<>()), renderToken);
        }

        @PermissionManuallyEnforced
        @Override
        public void publishInsight(List<ContextInsightWrapper> insights) {
            // TODO(b/450547433): Add security checks.
            getService().startInsightWorkflow(
                    ContextInsightWrapper.unwrapInto(insights, new HashSet<>()));
        }

        @PermissionManuallyEnforced
        @Override
        protected void dump(
                @NonNull FileDescriptor fd,
                @NonNull PrintWriter fout,
                @Nullable String[] args) {
            final PersonalContextManagerService service = getService();
            if (!DumpUtils.checkDumpPermission(service.getContext(), TAG, fout)) {
                return;
            }

            dumpComponentList(fout, "Refiners", service.mRefiners);
            dumpComponentList(fout, "Transformers", service.mTransformers);
            dumpComponentList(fout, "Renderers", service.mRenderers);
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
            fout.write(String.format("  (%s configured components)\n", components.size()));
            fout.write("\n");
        }
    }

    private static final class Monitor extends PackageMonitor {
        private final WeakReference<PersonalContextManagerService> mService;

        private Monitor(PersonalContextManagerService service) {
            mService = new WeakReference<>(service);
        }

        private void unregisterComponents(String packageName) {
            final PersonalContextManagerService service = mService.get();
            if (service == null) {
                Slog.e(TAG, "Service not available, unregistering package monitor");
                unregister();
            } else {
                service.unregisterComponents(packageName);
            }
        }

        private void registerComponents(String packageName) {
            final PersonalContextManagerService service = mService.get();
            if (service == null) {
                Slog.e(TAG, "Service not available, unregistering package monitor");
                unregister();
            } else {
                service.registerComponents(packageName);
            }
        }

        @Override
        public boolean onPackageChanged(String packageName, int uid, String[] components) {
            if (DEBUG_LOGGING) {
                Slog.d(TAG, "Package " + packageName + " changed, reregistering components");
            }
            unregisterComponents(packageName);
            registerComponents(packageName);
            return false;
        }

        @Override
        public void onPackageUpdateFinished(String packageName, int uid) {
            if (DEBUG_LOGGING) {
                Slog.d(TAG, "Package " + packageName + " updated, reregistering components");
            }
            unregisterComponents(packageName);
            registerComponents(packageName);
        }

        @Override
        public void onPackageAdded(String packageName, int uid) {
            if (DEBUG_LOGGING) {
                Slog.d(TAG, "Package " + packageName + " added, registering components");
            }
            registerComponents(packageName);
        }

        @Override
        public void onPackageRemoved(String packageName, int uid) {
            if (DEBUG_LOGGING) {
                Slog.d(TAG, "Package " + packageName + " removed, unregistering components");
            }
            unregisterComponents(packageName);
        }
    }
}
