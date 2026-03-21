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

import static java.util.Collections.emptySet;

import android.Manifest;
import android.annotation.EnforcePermission;
import android.annotation.PermissionManuallyEnforced;
import android.annotation.RequiresNoPermission;
import android.annotation.SuppressLint;
import android.annotation.UserIdInt;
import android.app.ActivityManagerInternal;
import android.app.BroadcastOptions;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManagerInternal;
import android.database.ContentObserver;
import android.os.Binder;
import android.os.ParcelUuid;
import android.os.PermissionEnforcer;
import android.os.Process;
import android.os.UserHandle;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.service.personalcontext.Flags;
import android.service.personalcontext.IPersonalContextManager;
import android.service.personalcontext.PersonalContextManager;
import android.service.personalcontext.RenderToken;
import android.service.personalcontext.Token;
import android.service.personalcontext.embedded.InsightSurfaceClientInfo;
import android.service.personalcontext.hint.ContextHint;
import android.service.personalcontext.hint.ContextHintWrapper;
import android.service.personalcontext.hint.NotificationEvent;
import android.service.personalcontext.hint.NotificationHint;
import android.service.personalcontext.hint.PublishedContextHint;
import android.service.personalcontext.hint.PublishedContextHintWrapper;
import android.service.personalcontext.hint.TextClassificationHint;
import android.service.personalcontext.insight.ContextInsight;
import android.service.personalcontext.insight.ContextInsightWrapper;
import android.service.personalcontext.insight.PublishedContextInsight;
import android.service.personalcontext.insight.interaction.AttributionDetails;
import android.service.personalcontext.insight.interaction.InsightEvent;
import android.util.Log;
import android.util.Slog;
import android.util.SparseArray;
import android.view.textclassifier.TextClassification;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.internal.util.DumpUtils;
import com.android.server.SystemService;
import com.android.server.contentcapture.ContentCaptureManagerInternal;
import com.android.server.notification.NotificationManagerInternal;
import com.android.server.personalcontext.component.Refiner;
import com.android.server.personalcontext.component.Renderer;
import com.android.server.personalcontext.embedded.EmbeddedInsightRenderer;
import com.android.server.personalcontext.notifications.ContextActionResolver;
import com.android.server.personalcontext.notifications.NotificationActionFactory;
import com.android.server.personalcontext.notifications.NotificationActionRenderer;
import com.android.server.personalcontext.textclassifier.TextClassificationActionRenderer;
import com.android.server.textclassifier.personalcontext.PersonalContextBridge;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
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

    @VisibleForTesting
    static final String ROLE_SYSTEM_UI_INTELLIGENCE = "android.app.role.SYSTEM_UI_INTELLIGENCE";

    /**
     * Default value for the per-app personal context mode setting, if
     * {@link Settings.Secure.PERSONAL_CONTEXT_MODE_ENABLED_DEFAULT} is not present and the app's
     * setting value has not been manually changed.
     *
     * <p>0 means disabled, 1 means enabled.
     *
     * @see PersonalContextManager#isPersonalContextModeEnabled(String)
     */
    // TODO(b/481494584): flip the default to disabled (0).
    public static final int PERSONAL_CONTEXT_MODE_ENABLED_DEFAULT_VALUE = 1;

    static final SecretKeySpec HINT_SIGNING_KEY;

    static {
        // Generate a new random signing key on each system start.
        final byte[] key = new byte[64];
        new SecureRandom().nextBytes(key);
        HINT_SIGNING_KEY = new SecretKeySpec(key, PublishedContextHint.HMAC_ALGORITHM);
    }

    private static class SettingObserver extends ContentObserver {
        private final Context mContext;
        private final Runnable mEnabledChangedAction;

        SettingObserver(
                @NonNull Context context,
                @Nullable Executor executor,
                @NonNull Runnable enabledChangedAction,
                int unused) {
            super(executor, unused);
            mContext = context;
            mEnabledChangedAction = enabledChangedAction;
        }

        @Override
        public void onChange(boolean selfChange) {
            mContext.sendBroadcastAsUser(
                    new Intent(PersonalContextManager.ACTION_PERSONAL_CONTEXT_ENABLED_CHANGED)
                            .addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY),
                    UserHandle.ALL);
            mEnabledChangedAction.run();
        }

        public void register() {
            mContext.getContentResolver().registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.PERSONAL_CONTEXT_ENABLED), false,
                    this, UserHandle.USER_ALL);
        }

        public void unregister() {
            mContext.getContentResolver().unregisterContentObserver(this);
        }
    }

    /** Encapsulates all state associated with a specific user. */
    private record UserState(
            @NonNull ContextComponentManager componentManager,
            @NonNull ContextComponentMonitor monitor,
            @NonNull HintInvalidationUnderstander hintInvalidationUnderstander,
            @NonNull NotificationActionRenderer notificationActionRenderer,
            @NonNull EmbeddedInsightRenderer embeddedInsightRenderer,
            @Nullable TextClassificationActionRenderer textClassificationActionRenderer,
            @NonNull SettingObserver observer) {
        /** Unregisters the monitor and setting observer, cleaning up the user state. */
        void cleanup() {
            monitor.unregister();
            observer.unregister();
        }
    }

    /**
     * Factory interface for creating {@link EmbeddedInsightRenderer} instances.
     */
    public interface EmbeddedInsightRendererFactory {
        /**
         * Create an {@link EmbeddedInsightRenderer} instance.
         * @param userContext the context for the user
         * @param executor the executor to use to execute embedded renderer tasks
         * @return A new {@link EmbeddedInsightRenderer} instance.
         */
        EmbeddedInsightRenderer createEmbeddedInsightRenderer(
                Context userContext, AccessController accessController, Executor executor);
    }

    // TODO(b/454430085): Inject these fields.
    private final ScheduledExecutorService mExecutor = Executors.newSingleThreadScheduledExecutor();
    private final SparseArray<UserState> mUserStates = new SparseArray<>();
    private final ContextLogger mLogger = new ContextLogger();

    private final RoleManager mRoleManager;
    private final ActivityManagerInternal mActivityManager;
    private final PackageManagerInternal mPackageManager;
    private final ContentCaptureManagerInternal mContentCaptureManagerInternal;
    private final EmbeddedInsightRendererFactory mEmbeddedInsightRendererFactory;

    private final AccessController mAccessController;
    private final PersonalContextManagerInternal mInternalService = new LocalService();

    public PersonalContextManagerService(Context context) {
        this(context, new AccessController(context), EmbeddedInsightRenderer::new);
    }

    protected PersonalContextManagerService(
            Context context,
            AccessController controller,
            EmbeddedInsightRendererFactory embeddedInsightRendererFactory) {
        super(context);

        mRoleManager = context.getSystemService(RoleManager.class);
        mActivityManager = getLocalService(ActivityManagerInternal.class);
        mPackageManager = getLocalService(PackageManagerInternal.class);
        mContentCaptureManagerInternal = getLocalService(ContentCaptureManagerInternal.class);
        mAccessController = controller;
        mEmbeddedInsightRendererFactory = embeddedInsightRendererFactory;
    }

    private void checkUidAccess(int uid, @AccessController.Access int access) {
        if (!mAccessController.hasAccess(uid, access)) {
            throw new SecurityException(
                    "component (uid=" + uid + ") not allowed to perform operation requiring access:"
                            + access);
        }
    }

    @Override
    public void onStart() {
        publishBinderService(
                PersonalContextManager.PERSONAL_CONTEXT_SERVICE,
                new BinderService(this, mPackageManager));
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
                    new ContextComponentManager(userContext, user.getUserHandle(),
                            mAccessController);
            final ContextComponentMonitor monitor = new ContextComponentMonitor(componentManager);
            final HintInvalidationUnderstander hintInvalidationUnderstander =
                    new HintInvalidationUnderstander(
                            (insight, componentId) ->
                                    startInsightWorkflow(userId, componentId, Set.of(insight)));
            final SettingObserver observer = new SettingObserver(userContext, mExecutor,
                    () -> handleIsEnabledSettingChanged(userId), 0);
            final NotificationActionRenderer notificationActionRenderer =
                    new NotificationActionRenderer(
                            getLocalService(NotificationManagerInternal.class),
                            new NotificationActionFactory(
                                    userContext,
                                    userContext.getPackageManager(),
                                    new ContextActionResolver(userContext)));
            final EmbeddedInsightRenderer embeddedInsightRenderer =
                    mEmbeddedInsightRendererFactory.createEmbeddedInsightRenderer(
                            userContext, mAccessController, Executors.newSingleThreadExecutor());

            TextClassificationActionRenderer textClassificationActionRenderer;
            PersonalContextBridge tcPersonalContextBridge =
                    getLocalService(PersonalContextBridge.class);
            if (tcPersonalContextBridge != null) {
                textClassificationActionRenderer =
                        new TextClassificationActionRenderer(tcPersonalContextBridge);
            } else {
                Slog.w(
                        TAG,
                        "TextClassificationManagerService not found. Skip creating "
                                + "TextClassificationActionRenderer");
                textClassificationActionRenderer = null;
            }

            mUserStates.put(
                    userId,
                    new UserState(
                            componentManager,
                            monitor,
                            hintInvalidationUnderstander,
                            notificationActionRenderer,
                            embeddedInsightRenderer,
                            textClassificationActionRenderer,
                            observer));
        }
    }

    @Override
    public void onUserUnlocked(@NonNull TargetUser user) {
        final int userId = user.getUserIdentifier();
        Slog.i(TAG, "Unlocking user " + userId);

        UserState userState = getUserStateSynchronized(userId);
        if (userState == null) {
            onUserStarting(user);
            userState = getUserStateSynchronized(userId);
            if (userState == null) {
                Slog.e(TAG, "Failed to create UserState for unlocking user " + userId);
                return;
            }
        }

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Slog.d(TAG, "Registering setting observer for user " + userId);
        }
        userState.observer().register();


        if (!isEnabledForUser(userId)) {
            // Personal context isn't enabled for this user, so don't register any components.
            return;
        }

        registerComponentsForCurrentUser(userId, "user unlocked");
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

    @VisibleForTesting
    void handleIsEnabledSettingChanged(int userId) {
        if (isEnabledForUser(userId)) {
            registerComponentsForCurrentUser(userId, "personal context enabled");
        } else {
            unregisterComponentsForCurrentUser(userId, "personal context disabled");
        }
    }

    private void registerComponentsForCurrentUser(int userId, String reason) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Slog.d(TAG, "Registering components for user " + userId + ", reason: " + reason);
        }

        UserState userState = getUserStateSynchronized(userId);
        if (userState == null) {
            Slog.e(TAG, "Unknown user id when registering components: " + userId);
            return;
        }

        final ContextComponentManager componentManager = userState.componentManager();

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Slog.d(TAG, "Registering internal components for user " + userId);
        }
        componentManager.register(userState.hintInvalidationUnderstander());
        componentManager.register(userState.notificationActionRenderer());
        componentManager.register(userState.embeddedInsightRenderer());
        if (userState.textClassificationActionRenderer != null) {
            componentManager.register(userState.textClassificationActionRenderer());
        }

        userState.embeddedInsightRenderer().onRegistered();

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Slog.d(TAG, "Registering external components for user " + userId);
        }
        componentManager.registerComponentsForAllPackages();

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Slog.d(TAG, "Starting package monitor for user " + userId);
        }
        final UserHandle userHandle = UserHandle.getUserHandleForUid(userId);
        userState
                .monitor()
                .register(
                        getContext().createContextAsUser(userHandle, 0),
                        /* looper= */ null,
                        userHandle,
                        /* externalStorage= */ false);
    }

    private void unregisterComponentsForCurrentUser(int userId, String reason) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Slog.d(TAG, "Unregistering all components for user " + userId);
        }

        UserState userState = getUserStateSynchronized(userId);
        if (userState == null) {
            Slog.e(TAG, "Unknown user id when unregistering components: " + userId);
            return;
        }

        final ContextComponentManager componentManager = userState.componentManager();
        userState.monitor().unregister();
        componentManager.unregisterAllComponents(reason);
        userState.embeddedInsightRenderer().onUnregistered();
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

    @VisibleForTesting
    void startRefinerWorkflow(
            @UserIdInt int userId,
            int callingUid,
            Set<ContextHint> hints,
            Set<RenderToken> renderTokens,
            Set<ContextHint> attributionHints) {
        if (!isEnabledForUser(userId)) {
            Slog.w(TAG, "Can't start refiner workflow because personal context is not enabled.");
            return;
        }

        final ContextComponentManager componentManager = getComponentManagerForUser(userId);
        if (componentManager == null) {
            Slog.w(TAG, "Cannot start refiner workflow, no component manager for user " + userId);
            return;
        }

        try {
            final Set<PublishedContextHint> signedAttributionHints = new HashSet<>();
            if (attributionHints != null) {
                for (ContextHint hint : attributionHints) {
                    signedAttributionHints.add(signHint(hint, callingUid, emptySet(), emptySet()));
                }
            }

            final Set<PublishedContextHint> signedHints = new HashSet<>();
            for (ContextHint hint : hints) {
                signedHints.add(signHint(hint, callingUid, renderTokens, signedAttributionHints));
            }

            RefinerWorkflow.start(
                    componentManager,
                    signedHints,
                    renderTokens,
                    HINT_SIGNING_KEY,
                    mLogger,
                    mExecutor,
                    (componentId, insights)
                            -> startInsightWorkflow(userId, componentId, insights));
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    @VisibleForTesting
    void startInsightWorkflow(@UserIdInt int userId, UUID componentId,
            Set<ContextInsight> insights) {
        if (!isEnabledForUser(userId)) {
            Slog.w(TAG, "Can't start insight workflow because personal context is not enabled.");
            return;
        }

        final HashSet<PublishedContextInsight> publishedInsights = new HashSet<>();
        for (ContextInsight insight : insights) {
            publishedInsights.add(new PublishedContextInsight(insight, componentId));
        }

        startPublishedInsightWorkflow(userId, componentId, publishedInsights);
    }

    private void startPublishedInsightWorkflow(@UserIdInt int userId, UUID componentId,
            Set<PublishedContextInsight> insights) {
        final ContextComponentManager componentManager = getComponentManagerForUser(userId);
        if (componentManager == null) {
            Slog.w(TAG, "Cannot start renderer workflow, no component manager for user " + userId);
            return;
        }

        RendererWorkflow.start(componentManager, insights, HINT_SIGNING_KEY, mLogger,
                mExecutor);
    }

    /** Returns the component manager for the given user, for testing purposes. */
    @VisibleForTesting
    @Nullable
    ContextComponentManager getComponentManagerForUser(@UserIdInt int userId) {
        final UserState userState = getUserStateSynchronized(userId);
        return userState != null ? userState.componentManager() : null;
    }

    /** Returns the embedded renderer for the given user, for testing purposes. */
    @VisibleForTesting
    @Nullable
    EmbeddedInsightRenderer getEmbeddedRendererForUser(@UserIdInt int userId) {
        final UserState userState = getUserStateSynchronized(userId);
        return userState != null ? userState.embeddedInsightRenderer() : null;
    }

    private void registerInsightSurfaceClient(
            int userId,
            InsightSurfaceClientInfo clientInfo) {
        if (!isEnabledForUser(userId)) {
            Slog.w(TAG, "Can't register insight surface client because personal context is not "
                    + "enabled.");
            return;
        }

        final UserState userState = getUserStateSynchronized(userId);
        if (userState == null) {
            return;
        }

        userState.embeddedInsightRenderer.registerInsightSurfaceClient(clientInfo);
    }

    private void unregisterInsightSurfaceClient(int userId, UUID id) {
        final UserState userState = getUserStateSynchronized(userId);
        if (userState != null) {
            userState.embeddedInsightRenderer().unregisterInsightSurfaceClient(id);
        }
    }

    @VisibleForTesting
    void publishInsightSurfaceHints(
            int userId,
            int callingUid,
            Set<ContextHint> hints,
            InsightSurfaceClientInfo clientInfo) {
        if (!isEnabledForUser(userId)) {
            Slog.w(TAG,
                    "Can't publish insight surface hints because personal context is not enabled.");
            return;
        }

        final UserState userState = getUserStateSynchronized(userId);
        if (userState == null) {
            Slog.e(TAG, "No user state when publishing insight surface hints");
            return;
        }

        final RenderToken renderToken =
                userState.embeddedInsightRenderer.getRenderTokenForClient(clientInfo);
        if (renderToken == null) {
            Slog.e(TAG, "No render token for client " + clientInfo.getId());
            return;
        }

        startRefinerWorkflow(userId, callingUid, hints, Set.of(renderToken), emptySet());
    }

    @SuppressLint("MissingPermission")
    private void sendPersonalContextModeChangedBroadcasts(
            String packageName, @UserIdInt int userId) {
        Intent broadcastIntent =
                new Intent(Intent.ACTION_PERSONAL_CONTEXT_MODE_CHANGED)
                        .addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY)
                        .setPackage(packageName);
        broadcastIntent.putExtra(Intent.EXTRA_PACKAGE_NAME, packageName);

        // Send broadcast to the application the setting changed for.
        UserHandle userHandle = UserHandle.getUserHandleForUid(userId);
        final BroadcastOptions options = BroadcastOptions.makeBasic();
        // This allows the broadcasting system to discard any older broadcasts waiting to be
        // delivered. This is okay as the intent doesn't contain any info the application for which
        // the setting changed cares about. They only need to know that the setting changed.
        options.setDeliveryGroupPolicy(BroadcastOptions.DELIVERY_GROUP_POLICY_MOST_RECENT);
        getContext().sendBroadcastAsUser(broadcastIntent, userHandle, null, options.toBundle());

        // TODO(b/482458833): send change notification directly to content capture framework

        // Send broadcast to the user's content capture service, if any.
        String contentCaptureServicePackageName = null;
        if (mContentCaptureManagerInternal != null) {
            contentCaptureServicePackageName =
                    mContentCaptureManagerInternal.getContentCaptureServicePackageNameForUser(
                            userId);
            if (contentCaptureServicePackageName != null) {
                Intent contentCaptureServiceBroadcastIntent =
                        new Intent(broadcastIntent).setPackage(contentCaptureServicePackageName);
                getContext()
                        .sendBroadcastAsUser(
                                contentCaptureServiceBroadcastIntent, userHandle, null, null);
            }
        }

        // Send broadcast to the system UI intelligence app, if any.
        if (mRoleManager != null) {
            List<String> roleHolderPackageNames =
                    mRoleManager.getRoleHoldersAsUser(ROLE_SYSTEM_UI_INTELLIGENCE, userHandle);
            for (String rolePackageName : roleHolderPackageNames) {
                if (rolePackageName == contentCaptureServicePackageName) {
                    // The content capture service may also be a system UI intelligence app.
                    continue;
                }
                Intent roleBroadcastIntent =
                        new Intent(broadcastIntent).setPackage(rolePackageName);
                getContext().sendBroadcastAsUser(roleBroadcastIntent, userHandle, null, null);
            }
        }
    }

    private void reportEvent(
            int userId,
            int callingUid,
            InsightEvent event) {
        final UserState userState = getUserStateSynchronized(userId);
        if (userState == null) {
            Slog.e(TAG, "No user state when reporting insight event");
            return;
        }

        final UUID componentId = event.getInsight().getPublisherComponentId();

        final Refiner refiner = userState.componentManager.getRefinerById(componentId);
        if (refiner == null) {
            Slog.e(
                    TAG,
                    "No component found with ID " + componentId + " when reporting insight event");
            return;
        }

        final String packageName = mPackageManager.getNameForUid(callingUid);
        refiner.handleEvent(packageName, event);
    }

    private void updateEmbeddedClientInfo(
            int userId,
            InsightSurfaceClientInfo oldClientInfo,
            InsightSurfaceClientInfo newClientInfo) {
        final UserState userState = getUserStateSynchronized(userId);
        if (userState == null) {
            Slog.e(TAG, "No user state when updating embedded client info");
            return;
        }

        userState.embeddedInsightRenderer().updateClientInfo(oldClientInfo, newClientInfo);
    }

    // TODO(b/492179930): Remove this synchronized block and replace it with a better thread-safe
    // way to access mUserStates.
    private UserState getUserStateSynchronized(int userId) {
        synchronized (mUserStates) {
            return mUserStates.get(userId);
        }
    }

    private PublishedContextHint signHint(
            ContextHint hint,
            int callingUid,
            Set<RenderToken> renderTokens,
            Set<PublishedContextHint> attributionHints)
            throws GeneralSecurityException {
        return new PublishedContextHint.Builder(hint, HINT_SIGNING_KEY)
                .setOriginatingPackage(mPackageManager.getNameForUid(callingUid))
                .addRenderTokens(renderTokens)
                .addAttributionHints(attributionHints)
                .build();
    }

    private boolean isEnabledForUser(int userId) {
        // TODO(b/477958468): Make the default "disabled".
        return Settings.Secure.getIntForUser(
                getContext().getContentResolver(),
                Settings.Secure.PERSONAL_CONTEXT_ENABLED,
                1, userId) == 1;
    }

    @VisibleForTesting
    static final class BinderService extends IPersonalContextManager.Stub {
        private final WeakReference<PersonalContextManagerService> mService;
        private final PackageManagerInternal mPackageManager;

        @VisibleForTesting
        BinderService(
                PersonalContextManagerService service, PackageManagerInternal packageManager) {
            super(PermissionEnforcer.fromContext(service.getContext()));
            mService = new WeakReference<>(service);
            mPackageManager = packageManager;
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
        public boolean isPersonalContextModeEnabled(String packageName, @UserIdInt int userId) {
            final int callingUid = Binder.getCallingUid();

            // Manifest.permission.QUERY_ALL_PACKAGES permission is enforced inside package manager.
            return Boolean.TRUE.equals(
                    Binder.withCleanCallingIdentity(
                            () -> {
                                int personalContextMode =
                                        mPackageManager.getPersonalContextMode(
                                                packageName, callingUid, userId);
                                if (personalContextMode
                                        == PackageManager.PERSONAL_CONTEXT_MODE_UNSET) {
                                    // Mode is unset for this app, check the default value.
                                    return Settings.Secure.getIntForUser(
                                                    getService().getContext().getContentResolver(),
                                                    Settings.Secure
                                                            .PERSONAL_CONTEXT_MODE_ENABLED_DEFAULT,
                                                    PERSONAL_CONTEXT_MODE_ENABLED_DEFAULT_VALUE,
                                                    userId)
                                            == 1;
                                } else {
                                    return personalContextMode
                                            == PackageManager.PERSONAL_CONTEXT_MODE_USER_ON;
                                }
                            }));
        }

        @EnforcePermission(android.Manifest.permission.CHANGE_PERSONAL_CONTEXT_MODE)
        @Override
        public void setPersonalContextModeEnabled(
                String packageName, @UserIdInt int userId, boolean enabled) {
            setPersonalContextModeEnabled_enforcePermission();
            final int callingUid = Binder.getCallingUid();
            Binder.withCleanCallingIdentity(
                    () -> {
                        int mode =
                                enabled
                                        ? PackageManager.PERSONAL_CONTEXT_MODE_USER_ON
                                        : PackageManager.PERSONAL_CONTEXT_MODE_USER_OFF;
                        if (mPackageManager.setPersonalContextMode(
                                packageName, callingUid, userId, mode)) {
                            getService()
                                    .sendPersonalContextModeChangedBroadcasts(packageName, userId);
                        }
                    });
        }

        // Suppressing warning as enforcement is currently behind a flag
        @SuppressWarnings("MissingEnforcePermissionHelper")
        @EnforcePermission(android.Manifest.permission.PERSONAL_CONTEXT_READ_SETTINGS)
        @Override
        public boolean isEnabled(int userId) {
            if (android.service.personalcontext.Flags.enforcePersonalContextPermissions()) {
                isEnabled_enforcePermission();
            }
            verifyUser(userId);
            return Boolean.TRUE.equals(
                    Binder.withCleanCallingIdentity(
                            () -> getService().isEnabledForUser(userId)));
        }

        // Suppressing warning as enforcement is currently behind a flag
        @SuppressWarnings("MissingEnforcePermissionHelper")
        @EnforcePermission(android.Manifest.permission.PERSONAL_CONTEXT_WRITE_SETTINGS)
        @Override
        public void setEnabled(int userId, boolean enabled) {
            if (android.service.personalcontext.Flags.enforcePersonalContextPermissions()) {
                setEnabled_enforcePermission();
            }
            verifyUser(userId);
            Binder.withCleanCallingIdentity(
                    () -> {
                        final Context context = getService().getContext();
                        Settings.Secure.putIntForUser(
                                context.getContentResolver(),
                                Settings.Secure.PERSONAL_CONTEXT_ENABLED,
                                enabled ? 1 : 0, userId);
                    });
        }

        // Suppressing warning as enforcement is currently behind a flag
        @SuppressWarnings("MissingEnforcePermissionHelper")
        @EnforcePermission(android.Manifest.permission.PERSONAL_CONTEXT_PUBLISH_HINTS)
        @Override
        public void publishTriggeringHint(
                List<ContextHintWrapper> hints,
                List<RenderToken> renderTokens,
                List<ContextHintWrapper> attributionHints,
                int userId) {
            if (android.service.personalcontext.Flags.enforcePersonalContextPermissions()) {
                publishTriggeringHint_enforcePermission();
            }
            verifyUser(userId);

            final int callingUid = Binder.getCallingUid();

            // TODO(b/450547433): Add security checks.
            Binder.withCleanCallingIdentity(
                    () -> {
                        final PersonalContextManagerService service = getService();

                        if (Flags.enforcePersonalContextAllowlistAccessControl()) {
                            service.checkUidAccess(callingUid,
                                    AccessController.ACCESS_PUBLISH_HINTS);
                        }

                        service
                                .startRefinerWorkflow(
                                        userId,
                                        callingUid,
                                        ContextHintWrapper.unwrapInto(hints, new HashSet<>()),
                                        new HashSet<>(
                                                renderTokens == null ? List.of() : renderTokens),
                                        ContextHintWrapper.unwrapInto(attributionHints,
                                                new HashSet<>()));
                    });
        }

        // Suppressing warning as enforcement is currently behind a flag
        @SuppressWarnings("MissingEnforcePermissionHelper")
        @EnforcePermission(android.Manifest.permission.PERSONAL_CONTEXT_PUBLISH_INSIGHTS)
        @Override
        public void publishInsight(List<ContextInsightWrapper> insights, ParcelUuid componentId,
                int userId) {
            if (android.service.personalcontext.Flags.enforcePersonalContextPermissions()) {
                publishInsight_enforcePermission();
            }
            verifyUser(userId);

            int callingUid = Binder.getCallingUid();

            Binder.withCleanCallingIdentity(
                    () -> {

                        final PersonalContextManagerService service = getService();

                        if (Flags.enforcePersonalContextAllowlistAccessControl()) {
                            service.checkUidAccess(callingUid,
                                    AccessController.ACCESS_PUBLISH_INSIGHTS);
                        }
                        service
                                .startInsightWorkflow(
                                        userId,
                                        componentId.getUuid(),
                                        ContextInsightWrapper.unwrapInto(
                                                insights, new HashSet<>()));
                    });
        }

        @RequiresNoPermission
        @Override
        public PublishedContextHintWrapper signHint(
                ContextHintWrapper hint, List<ContextHintWrapper> attributionHints) {
            final int callingUid = Binder.getCallingUid();

            return Binder.withCleanCallingIdentity(
                    () -> {
                        final Set<PublishedContextHint> signedAttributionHints =
                                new HashSet<>();
                        if (attributionHints != null) {
                            for (ContextHintWrapper attributionHint : attributionHints) {
                                signedAttributionHints.add(getService().signHint(
                                        attributionHint.getContextHint(),
                                        callingUid,
                                        emptySet(),
                                        emptySet()));
                            }
                        }

                        return new PublishedContextHintWrapper(getService().signHint(
                                hint.getContextHint(),
                                callingUid,
                                emptySet(),
                                signedAttributionHints));
                    });
        }

        // Suppressing warning as enforcement is currently behind a flag
        @SuppressWarnings("MissingEnforcePermissionHelper")
        @EnforcePermission(android.Manifest.permission.PERSONAL_CONTEXT_HOST_INSIGHT_SURFACE)
        @Override
        public void registerInsightSurfaceClient(
                InsightSurfaceClientInfo clientInfo,
                int userId) {
            if (android.service.personalcontext.Flags.enforcePersonalContextPermissions()) {
                registerInsightSurfaceClient_enforcePermission();
            }
            verifyUser(userId);

            final int callingUid = Binder.getCallingUid();

            // TODO(b/450547433): Add security checks.
            Binder.withCleanCallingIdentity(
                    () -> {
                        final PersonalContextManagerService service = getService();

                        if (Flags.enforcePersonalContextAllowlistAccessControl()) {
                            service.checkUidAccess(callingUid,
                                    AccessController.ACCESS_RECEIVE_INSIGHTS
                                    | AccessController.ACCESS_PUBLISH_HINTS);
                        }
                        service.registerInsightSurfaceClient(
                                userId,
                                clientInfo);
                    });
        }

        @PermissionManuallyEnforced
        @Override
        public Token mintToken() {
            return new Token();
        }

        @PermissionManuallyEnforced
        @Override
        public void unregisterInsightSurfaceClient(ParcelUuid id, int userId) {
            verifyUser(userId);

            // TODO(b/450547433): Add security checks.
            Binder.withCleanCallingIdentity(
                    () -> getService().unregisterInsightSurfaceClient(userId, id.getUuid()));
        }

        // Suppressing warning as enforcement is currently behind a flag
        @SuppressWarnings("MissingEnforcePermissionHelper")
        @EnforcePermission(Manifest.permission.PERSONAL_CONTEXT_PUBLISH_HINTS)
        @Override
        public void publishInsightSurfaceHints(
                List<ContextHintWrapper> hints, InsightSurfaceClientInfo clientInfo, int userId) {
            if (android.service.personalcontext.Flags.enforcePersonalContextPermissions()) {
                publishInsightSurfaceHints_enforcePermission();
            }
            verifyUser(userId);

            final int callingUid = Binder.getCallingUid();

            // TODO(b/450547433): Add security checks.
            Binder.withCleanCallingIdentity(
                    () -> {
                        final PersonalContextManagerService service = getService();

                        if (Flags.enforcePersonalContextAllowlistAccessControl()) {
                            service.checkUidAccess(callingUid,
                                    AccessController.ACCESS_PUBLISH_HINTS);
                        }
                        getService()
                                .publishInsightSurfaceHints(
                                        userId,
                                        callingUid,
                                        ContextHintWrapper.unwrapInto(hints, new HashSet<>()),
                                        clientInfo);
                    }
            );
        }

        @PermissionManuallyEnforced
        @Override
        public void reportEvent(InsightEvent event, int userId) {
            verifyUser(userId);

            final int callingUid = Binder.getCallingUid();

            // TODO(b/450547433): Add security checks.
            Binder.withCleanCallingIdentity(
                    () -> getService().reportEvent(
                            userId,
                            callingUid,
                            event));
        }

        @PermissionManuallyEnforced
        @Override
        public void showAttribution(ContextInsightWrapper insight) {
            final AttributionDetails attributionDetails =
                    insight.getContextInsight().getAttributionDetails();

            // TODO(b/475328786): Handle showing the attribution.
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
                    fout.println("isEnabled=" + service.isEnabledForUser(userId));
                    UserState userState = service.mUserStates.valueAt(i);
                    userState.componentManager().dump(fout);
                    userState.embeddedInsightRenderer().dump(fout);
                }
            }

            service.mLogger.dump(fout);
        }

        @PermissionManuallyEnforced
        @Override
        public void updateEmbeddedClientInfo(
                InsightSurfaceClientInfo oldClientInfo,
                InsightSurfaceClientInfo newClientInfo,
                int userId) {
            verifyUser(userId);
            Binder.withCleanCallingIdentity(
                    () -> getService().updateEmbeddedClientInfo(
                            userId, oldClientInfo, newClientInfo));
        }
    }

    @VisibleForTesting
    class LocalService extends PersonalContextManagerInternal {
        @Override
        public void onNotificationEvent(@NonNull NotificationEvent event) {
            final StatusBarNotification sbn = getSbnFromNotificationEvent(event);
            if (sbn == null) {
                Slog.e(TAG, "Could not get SBN from notification event.");
                return;
            }

            final UserHandle user = sbn.getUser();
            final UserState userState = getUserStateSynchronized(user.getIdentifier());
            if (userState == null) {
                Slog.e(TAG, "No user state for user " + user.getIdentifier());
                return;
            }

            final HashSet<RenderToken> rendererTokens = new HashSet<>();

            for (Renderer renderer : userState.componentManager.getRenderersWithProperties(
                    Renderer.PROPERTY_CAN_RECEIVE_NOTIFICATION_INSIGHTS)) {
                rendererTokens.add(renderer.mintRenderToken());
            }

            startRefinerWorkflow(
                    user.getIdentifier(),
                    Process.myUid(),
                    Set.of(new NotificationHint.Builder(event).build()),
                    rendererTokens,
                    Collections.emptySet());
        }

        @Override
        public void onTextClassifyRequest(
                int userId, String sessionId, @NonNull TextClassification.Request request) {
            final UserState userState = getUserStateSynchronized(userId);
            if (userState == null) {
                Slog.e(TAG, "No user state for user " + userId);
                return;
            }
            if (userState.textClassificationActionRenderer == null) {
                Slog.e(TAG, "No text classification renderer defined");
                return;
            }

            startRefinerWorkflow(
                    userId,
                    Process.myUid(),
                    Set.of(new TextClassificationHint.Builder(request, sessionId).build()),
                    Set.of(userState.textClassificationActionRenderer().mintRenderToken()),
                    Collections.emptySet());
        }

        @Override
        public void publishTriggeringHint(@NonNull Set<ContextHint> hints,
                @Nullable Set<RenderToken> renderTokens, int userId) {
            startRefinerWorkflow(
                    userId, Process.myUid(), hints, renderTokens, Collections.emptySet());
        }
    }
}
