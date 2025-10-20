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
import android.os.UserHandle;
import android.service.personalcontext.IPersonalContextManager;
import android.service.personalcontext.PersonalContextManager;
import android.service.personalcontext.RenderToken;
import android.service.personalcontext.hint.ContextHint;
import android.service.personalcontext.hint.ContextHintWrapper;
import android.service.personalcontext.hint.NotificationEvent;
import android.service.personalcontext.hint.NotificationHint;
import android.service.personalcontext.insight.ContextInsight;
import android.service.personalcontext.insight.ContextInsightWrapper;
import android.util.Log;
import android.util.Slog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.util.DumpUtils;
import com.android.server.SystemService;
import com.android.server.personalcontext.component.Refiner;
import com.android.server.personalcontext.component.Renderer;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @hide
 */
public class PersonalContextManagerService extends SystemService {
    private static final String TAG = "PersonalContext";

    private final ContextComponentManager mComponentManager =
            new ContextComponentManager(getContext());
    private final ContextComponentMonitor mMonitor = new ContextComponentMonitor(mComponentManager);

    private boolean mInitialRegistrationStarted = false;

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
        if (mInitialRegistrationStarted) {
            return;
        }

        mInitialRegistrationStarted = true;

        if (Log.isLoggable(TAG, Log.DEBUG)) Slog.d(TAG, "Registering internal components");
        // Register in-process components with mComponentManager.register() here.

        if (Log.isLoggable(TAG, Log.DEBUG)) Slog.d(TAG, "Registering external components");
        mComponentManager.registerComponentsForAllPackages();

        if (Log.isLoggable(TAG, Log.DEBUG)) Slog.d(TAG, "Starting package monitor");
        mMonitor.register(
                getContext(),
                /* looper= */ null,
                /* user= */ UserHandle.CURRENT,
                /* externalStorage= */ false);
    }

    private void startRefinerWorkflow(Set<ContextHint> hints, RenderToken renderToken) {
        // This is just the worst.
        try {
            if (Log.isLoggable(TAG, Log.DEBUG)) Slog.d(TAG, "Refiner workflow started");

            for (Refiner refiner : mComponentManager.getRefiners()) {
                if (Log.isLoggable(TAG, Log.DEBUG)) Slog.d(TAG, "Sending to refiner: " + refiner);
                refiner.refine(hints, newHints -> {});
            }

            if (Log.isLoggable(TAG, Log.DEBUG)) Slog.d(TAG, "Refiner workflow complete");
        } catch (Exception e) {
            Slog.e(TAG, "Refiner workflow failed", e);
        }
    }

    private void startInsightWorkflow(Set<ContextInsight> insights) {
        try {
            // Please don't look at my shame.
            if (Log.isLoggable(TAG, Log.DEBUG)) Slog.d(TAG, "Insight workflow started");

            for (ContextInsight insight : insights) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Slog.d(TAG, "Handling insight: " + insight);
                }
                for (Renderer renderer : mComponentManager.getRenderers()) {
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Slog.d(TAG, "Sending to renderer: " + renderer);
                    }
                    renderer.render(insight, false);
                }
            }

            if (Log.isLoggable(TAG, Log.DEBUG)) Slog.d(TAG, "Insight workflow complete");
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

            service.mComponentManager.dump(fout);
        }
    }
}
