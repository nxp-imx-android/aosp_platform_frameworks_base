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
import android.annotation.UserIdInt;
import android.content.Context;
import android.os.Binder;
import android.os.UserHandle;
import android.service.notification.StatusBarNotification;
import android.service.personalcontext.IPersonalContextManager;
import android.service.personalcontext.PersonalContextManager;
import android.service.personalcontext.RenderToken;
import android.service.personalcontext.RenderToken.RenderTokenBuilder;
import android.service.personalcontext.hint.ContextHint;
import android.service.personalcontext.hint.ContextHintWithSignature;
import android.service.personalcontext.hint.ContextHintWrapper;
import android.service.personalcontext.hint.NotificationEvent;
import android.service.personalcontext.hint.NotificationHint;
import android.service.personalcontext.insight.ContextInsight;
import android.service.personalcontext.insight.ContextInsightWrapper;
import android.util.Log;
import android.util.Slog;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.internal.util.DumpUtils;
import com.android.server.SystemService;
import com.android.server.notification.NotificationManagerInternal;
import com.android.server.personalcontext.component.Renderer;
import com.android.server.personalcontext.notifications.NotificationActionRenderer;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import javax.crypto.spec.SecretKeySpec;

/**
 * The system service that manages personal context components and workflows.
 *
 * <p>This service is responsible for discovering and managing the lifecycle of various components
 * (Refiners, Understanders, Renderers) on a per-user basis. It handles incoming contextual data
 * (hints) and routes them through a processing workflow to generate and display actionable insights
 * to the user.
 *
 * @hide
 */
public class PersonalContextManagerService extends SystemService {
    private static final String TAG = "PersonalContext";

    private static final SecretKeySpec HINT_SIGNING_KEY;

    static {
        // Generate a new random signing key on each system start.
        final byte[] key = new byte[64];
        new SecureRandom().nextBytes(key);
        HINT_SIGNING_KEY = new SecretKeySpec(key, ContextHintWithSignature.HMAC_ALGORITHM);
    }

    /** Encapsulates all state associated with a specific user. */
    private record UserState(
            @NonNull ContextComponentManager componentManager,
            @NonNull ContextComponentMonitor monitor,
            @NonNull NotificationActionRenderer notificationActionRenderer) {
        /** Unregisters the monitor, cleaning up the user state. */
        void cleanup() {
            monitor.unregister();
        }
    }

    // TODO(b/454430085): Inject these fields.
    private final ScheduledExecutorService mExecutor = Executors.newSingleThreadScheduledExecutor();
    private final SparseArray<UserState> mUserStates = new SparseArray<>();

    private final PersonalContextManagerInternal mInternalService =
            new PersonalContextManagerInternal() {
                @Override
                public void onNotificationEvent(@NonNull NotificationEvent event) {
                    final Set<ContextHint> hints =
                            Set.of(new NotificationHint.NotificationHintBuilder(event).build());

                    final StatusBarNotification sbn = getSbnFromNotificationEvent(event);
                    if (sbn == null) {
                        Slog.e(TAG, "Could not get SBN from notification event.");
                        return;
                    }

                    final UserHandle user = sbn.getUser();
                    final UserState userState;
                    synchronized (mUserStates) {
                        userState = mUserStates.get(user.getIdentifier());
                    }

                    if (userState == null) {
                        Slog.e(TAG, "No user state for user " + user.getIdentifier());
                        return;
                    }

                    final RenderToken renderToken =
                            new RenderTokenBuilder()
                                    .setRendererComponentId(
                                            userState.notificationActionRenderer().getComponentId())
                                    .build();

                    startRefinerWorkflow(user.getIdentifier(), hints, renderToken);
                }
            };

    public PersonalContextManagerService(Context context) {
        super(context);
    }

    @Override
    public void onStart() {
        publishBinderService(
                PersonalContextManager.PERSONAL_CONTEXT_SERVICE, new BinderService(this));
        publishLocalService(PersonalContextManagerInternal.class, mInternalService);
        Slog.i(TAG, "Personal Context Service started");
    }

    @Override
    public void onUserStarting(@NonNull TargetUser user) {
        final int userId = user.getUserIdentifier();
        synchronized (mUserStates) {
            final UserState oldState = mUserStates.get(userId);
            if (oldState != null) {
                Slog.w(TAG, "Cleaning up old state for starting user: " + userId);
                oldState.cleanup();
            }

            Slog.i(TAG, "Creating new state for user " + userId);
            Context userContext = getContext().createContextAsUser(user.getUserHandle(), 0);
            final ContextComponentManager componentManager =
                    new ContextComponentManager(userContext);
            final ContextComponentMonitor monitor = new ContextComponentMonitor(componentManager);
            final NotificationActionRenderer renderer =
                    new NotificationActionRenderer(
                            userContext,
                            getLocalService(NotificationManagerInternal.class),
                            userContext.getPackageManager());
            mUserStates.put(userId, new UserState(componentManager, monitor, renderer));
        }
    }

    @Override
    public void onUserUnlocked(@NonNull TargetUser user) {
        final int userId = user.getUserIdentifier();
        Slog.i(TAG, "Unlocking user " + userId);

        UserState userState;
        synchronized (mUserStates) {
            userState = mUserStates.get(userId);
        }

        if (userState == null) {
            onUserStarting(user);
            synchronized (mUserStates) {
                userState = mUserStates.get(userId);
            }
            if (userState == null) {
                Slog.e(TAG, "Failed to create UserState for unlocking user " + userId);
                return;
            }
        }

        final ContextComponentManager componentManager = userState.componentManager();

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Slog.d(TAG, "Registering internal components for user " + userId);
        }
        componentManager.register(userState.notificationActionRenderer());

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Slog.d(TAG, "Registering external components for user " + userId);
        }
        componentManager.registerComponentsForAllPackages();

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Slog.d(TAG, "Starting package monitor for user " + userId);
        }
        userState
                .monitor()
                .register(
                        getContext().createContextAsUser(user.getUserHandle(), 0),
                        /* looper= */ null,
                        user.getUserHandle(),
                        /* externalStorage= */ false);
    }

    @Override
    public void onUserStopping(@NonNull TargetUser user) {
        final int userId = user.getUserIdentifier();
        Slog.i(TAG, "Stopping user " + userId);
        synchronized (mUserStates) {
            final UserState userState = mUserStates.get(userId);
            if (userState != null) {
                userState.cleanup();
            }
            mUserStates.remove(userId);
        }
    }

    @Nullable
    private StatusBarNotification getSbnFromNotificationEvent(@NonNull NotificationEvent event) {
        if (event instanceof NotificationEvent.NotificationEnqueuedEvent) {
            return ((NotificationEvent.NotificationEnqueuedEvent) event).getStatusBarNotification();
        } else if (event instanceof NotificationEvent.NotificationRemovedEvent) {
            return ((NotificationEvent.NotificationRemovedEvent) event).getStatusBarNotification();
        }
        return null;
    }

    private void startRefinerWorkflow(
            @UserIdInt int userId, Set<ContextHint> hints, RenderToken renderToken) {
        final ContextComponentManager componentManager = getComponentManagerForUser(userId);
        if (componentManager == null) {
            Slog.w(TAG, "Cannot start refiner workflow, no component manager for user " + userId);
            return;
        }

        RefinerWorkflow.start(
                componentManager,
                hints,
                renderToken,
                HINT_SIGNING_KEY,
                /* eventListener= */ null,
                mExecutor);
    }

    private void startInsightWorkflow(@UserIdInt int userId, Set<ContextInsight> insights) {
        // TODO(b/452425186): Make this into a workflow like refiners.
        final ContextComponentManager componentManager = getComponentManagerForUser(userId);
        if (componentManager == null) {
            Slog.w(TAG, "Cannot start insight workflow, no component manager for user " + userId);
            return;
        }
        try {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Slog.d(TAG, "Insight workflow started for user " + userId);
            }

            for (ContextInsight insight : insights) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Slog.d(TAG, "Handling insight: " + insight);
                }
                for (Renderer renderer : componentManager.getRenderers()) {
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Slog.d(TAG, "Sending to renderer: " + renderer);
                    }
                    renderer.render(insight, false);
                }
            }

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Slog.d(TAG, "Insight workflow complete for user " + userId);
            }
        } catch (Exception e) {
            Slog.e(TAG, "Insight workflow failed for user " + userId, e);
        }
    }

    /** Returns the component manager for the given user, for testing purposes. */
    @VisibleForTesting
    @Nullable
    ContextComponentManager getComponentManagerForUser(@UserIdInt int userId) {
        synchronized (mUserStates) {
            final UserState userState = mUserStates.get(userId);
            return userState != null ? userState.componentManager() : null;
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

        private void verifyUser(@UserIdInt int userId) {
            final int callingUserId = UserHandle.getCallingUserId();
            if (userId != callingUserId) {
                getService()
                        .getContext()
                        .enforceCallingPermission(
                                android.Manifest.permission.INTERACT_ACROSS_USERS,
                                "Cross-user interaction requires INTERACT_ACROSS_USERS. userId="
                                        + userId
                                        + " callingUserId="
                                        + callingUserId);
            }
        }

        @PermissionManuallyEnforced
        @Override
        public void publishTriggeringHint(
                List<ContextHintWrapper> hints, RenderToken renderToken, int userId) {
            verifyUser(userId);

            // TODO(b/450547433): Add security checks.
            Binder.withCleanCallingIdentity(
                    () -> {
                        getService()
                                .startRefinerWorkflow(
                                        userId,
                                        ContextHintWrapper.unwrapInto(hints, new HashSet<>()),
                                        renderToken);
                    });
        }

        @PermissionManuallyEnforced
        @Override
        public void publishInsight(List<ContextInsightWrapper> insights, int userId) {
            verifyUser(userId);

            // TODO(b/450547433): Add security checks.
            Binder.withCleanCallingIdentity(
                    () -> {
                        getService()
                                .startInsightWorkflow(
                                        userId,
                                        ContextInsightWrapper.unwrapInto(
                                                insights, new HashSet<>()));
                    });
        }

        @PermissionManuallyEnforced
        @Override
        protected void dump(
                @NonNull FileDescriptor fd, @NonNull PrintWriter fout, @Nullable String[] args) {
            final PersonalContextManagerService service = getService();
            if (!DumpUtils.checkDumpPermission(service.getContext(), TAG, fout)) {
                return;
            }

            synchronized (service.mUserStates) {
                for (int i = 0; i < service.mUserStates.size(); i++) {
                    int userId = service.mUserStates.keyAt(i);
                    fout.println("User " + userId + ":");
                    UserState userState = service.mUserStates.valueAt(i);
                    userState.componentManager().dump(fout);
                }
            }
        }
    }
}
