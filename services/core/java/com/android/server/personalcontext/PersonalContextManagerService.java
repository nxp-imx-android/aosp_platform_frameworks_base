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
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.UserHandle;
import android.service.personalcontext.IPersonalContextManager;
import android.service.personalcontext.PersonalContextManager;
import android.service.personalcontext.RenderToken;
import android.service.personalcontext.hint.ContextHint;
import android.service.personalcontext.hint.ContextHintWrapper;
import android.service.personalcontext.insight.ContextInsightWrapper;
import android.service.personalcontext.hint.NotificationEvent;
import android.service.personalcontext.hint.NotificationHint;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.content.PackageMonitor;
import com.android.internal.util.DumpUtils;
import com.android.server.SystemService;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

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

    private final Monitor mMonitor = new Monitor(this);

    private boolean mRegisteredMonitor = false;

    @NonNull private List<ComponentName> mRefinerComponents = Collections.emptyList();
    @NonNull private List<ComponentName> mUnderstanderComponents = Collections.emptyList();
    @NonNull private List<ComponentName> mTransformerComponents = Collections.emptyList();
    @NonNull private List<ComponentName> mRendererComponents = Collections.emptyList();

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

        Log.d(TAG, "Service started");
    }

    @Override
    public void onUserUnlocked(@NonNull TargetUser user) {
        if (mRegisteredMonitor) {
            return;
        }

        mRegisteredMonitor = true;
        registerComponents();

        Log.d(TAG, "Starting package monitor");
        mMonitor.register(
                getContext(),
                /* looper= */ null,
                /* user= */ UserHandle.CURRENT,
                /* externalStorage= */ false);
    }

    private List<ComponentName> getServiceComponentNames(String action) {
        final Intent serviceIntent = new Intent(action);
        final List<ResolveInfo> services = getContext().getPackageManager().queryIntentServices(
                serviceIntent, PackageManager.GET_META_DATA);
        final List<ComponentName> result = new ArrayList<>(services.size());
        for (ResolveInfo resolveInfo : services) {
            if (resolveInfo != null && resolveInfo.serviceInfo != null) {
                result.add(new ComponentName(
                        resolveInfo.serviceInfo.packageName,
                        resolveInfo.serviceInfo.name));
            }
        }

        return List.copyOf(result);
    }

    private void registerComponents() {
        Log.d(TAG, "Registering components");

        mRefinerComponents = getServiceComponentNames(ACTION_REFINER_SERVICE);
        mUnderstanderComponents = getServiceComponentNames(ACTION_UNDERSTANDER_SERVICE);
        mTransformerComponents = getServiceComponentNames(ACTION_TRANSFORMER_SERVICE);
        mRendererComponents = getServiceComponentNames(ACTION_RENDERER_SERVICE);
    }

    private static final class BinderService extends IPersonalContextManager.Stub {
        private final WeakReference<PersonalContextManagerService> mService;

        private BinderService(PersonalContextManagerService service) {
            mService = new WeakReference<>(service);
        }

        @PermissionManuallyEnforced
        @Override
        public void publishTriggeringHint(List<ContextHintWrapper> hints, RenderToken renderToken) {
            // TODO(b/450547433): Implement this.
        }

        @PermissionManuallyEnforced
        @Override
        public void publishInsight(List<ContextInsightWrapper> insights) {
            // TODO(b/450547433): Implement this.
        }

        @PermissionManuallyEnforced
        @Override
        protected void dump(
                @NonNull FileDescriptor fd,
                @NonNull PrintWriter fout,
                @Nullable String[] args) {
            final PersonalContextManagerService service = mService.get();
            if (service == null) {
                fout.write("Service not available");
                return;
            } else if (!DumpUtils.checkDumpPermission(service.getContext(), TAG, fout)) {
                return;
            }

            dumpComponentList(fout, "Refiners", service.mRefinerComponents);
            dumpComponentList(fout, "Understanders", service.mUnderstanderComponents);
            dumpComponentList(fout, "Transformers", service.mTransformerComponents);
            dumpComponentList(fout, "Renderers", service.mRendererComponents);
        }

        private void dumpComponentList(
                @NonNull PrintWriter fout,
                @NonNull String name,
                @NonNull Collection<ComponentName> components) {
            fout.write(name + "\n");
            fout.write("=".repeat(name.length()) + "\n");
            for (ComponentName component : components) {
                fout.write("  " + component.flattenToString() + "\n");
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

        private void reregisterComponents() {
            // TODO: Make this smart enough to only re-register when there's a meaningful change.
            final PersonalContextManagerService service = mService.get();
            if (service == null) {
                Log.e(TAG, "Service not available, unregistering package monitor");
                unregister();
            } else {
                service.registerComponents();
            }
        }

        @Override
        public boolean onPackageChanged(String packageName, int uid, String[] components) {
            Log.d(TAG, "Package " + packageName + " changed, re-registering components");
            reregisterComponents();
            return false;
        }

        @Override
        public void onPackageUpdateFinished(String packageName, int uid) {
            Log.d(TAG, "Package " + packageName + " updated, re-registering components");
            reregisterComponents();
        }

        @Override
        public void onPackageAdded(String packageName, int uid) {
            Log.d(TAG, "Package " + packageName + " added, re-registering components");
            reregisterComponents();
        }

        @Override
        public void onPackageRemoved(String packageName, int uid) {
            Log.d(TAG, "Package " + packageName + " removed, re-registering components");
            reregisterComponents();
        }
    }
}
