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

package com.android.server.companion.virtual.computercontrol;

import static android.app.PendingIntent.FLAG_CANCEL_CURRENT;
import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.app.PendingIntent.FLAG_ONE_SHOT;
import static android.companion.virtual.computercontrol.ComputerControlSession.CLOSE_REASON_USER_INITIATED;

import static com.android.server.companion.virtual.computercontrol.ComputerControlStatsLog.COMPUTER_CONTROL_FAILED_SESSION_REPORTED__REASON__CALLER_NOT_ALLOWED;
import static com.android.server.companion.virtual.computercontrol.ComputerControlStatsLog.COMPUTER_CONTROL_FAILED_SESSION_REPORTED__REASON__INVALID_TARGET_APPLICATION;
import static com.android.server.companion.virtual.computercontrol.ComputerControlStatsLog.COMPUTER_CONTROL_FAILED_SESSION_REPORTED__REASON__MANAGED_POLICY_DISABLED;
import static com.android.server.companion.virtual.computercontrol.ComputerControlStatsLog.COMPUTER_CONTROL_FAILED_SESSION_REPORTED__REASON__NAME_NOT_UNIQUE;
import static com.android.server.companion.virtual.computercontrol.ComputerControlStatsLog.COMPUTER_CONTROL_FAILED_SESSION_REPORTED__REASON__SESSION_PENDING_NOTIFICATION_FAILED;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AppOpsManager;
import android.app.IApplicationThread;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.app.admin.DevicePolicyManagerInternal;
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtual.computercontrol.ComputerControlSession;
import android.companion.virtual.computercontrol.ComputerControlSessionParams;
import android.companion.virtual.computercontrol.IComputerControlSessionCallback;
import android.content.AttributionSource;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.UserHandle;
import android.os.UserManager;
import android.security.authenticationpolicy.AuthenticationPolicyManager;
import android.util.ArraySet;
import android.util.Slog;

import com.android.internal.R;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.LocalManagerRegistry;
import com.android.server.LocalServices;
import com.android.server.ServiceThread;
import com.android.server.Watchdog;
import com.android.server.appop.AppOpsManagerLocal;
import com.android.server.companion.virtual.VirtualDeviceManagerInternal;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Objects;

/**
 * Handles creation and lifecycle of {@link ComputerControlSession}s.
 *
 * <p>This class enforces session creation policies, such as limiting the number of concurrent
 * sessions and preventing creation when the device is locked.
 */
public final class ComputerControlSessionProcessor implements Watchdog.Monitor {

    private static final String TAG = ComputerControlSessionProcessor.class.getSimpleName();

    // TODO(b/419548594): Make this configurable.
    @VisibleForTesting
    static final int MAXIMUM_CONCURRENT_SESSIONS = 1;
    @VisibleForTesting
    static final int MIN_EXTENSION_VERSION_FOR_ANDROID_17 = 5;

    @Nullable
    private final String mReferenceDisplayAddress;

    private final Context mContext;
    private final KeyguardManager mKeyguardManager;
    private final AuthenticationPolicyManager mAuthenticationPolicyManager;
    private final AppOpsManager mAppOpsManager;
    private final AppOpsManagerLocal mAppOpsManagerLocal;
    private final PackageManager mPackageManager;
    private final UserManager mUserManager;
    private final DevicePolicyManager mDevicePolicyManager;
    private final DevicePolicyManagerInternal mDevicePolicyManagerInternal;
    private final VirtualDeviceManagerInternal mVirtualDeviceManagerInternal;
    private final VirtualDeviceFactory mVirtualDeviceFactory;
    private final PendingIntentFactory mPendingIntentFactory;
    private final ComputerControlAllowlistController mAllowlistController;

    /** The binders of all currently active sessions. */
    @GuardedBy("mSessions")
    private final ArraySet<ComputerControlSessionImpl> mSessions = new ArraySet<>();

    private final Object mHandlerThreadLock = new Object();
    @GuardedBy("mHandlerThreadLock")
    @Nullable
    private ServiceThread mHandlerThread;
    @SuppressWarnings("NullAway") // Session lifecycle makes this @NonNull, though hard to enforce
    private Handler mHandler;

    public ComputerControlSessionProcessor(
            Context context, VirtualDeviceManagerInternal virtualDeviceManagerInternal,
            VirtualDeviceFactory virtualDeviceFactory) {
        this(context, virtualDeviceManagerInternal, virtualDeviceFactory,
                ComputerControlSessionProcessor::createPendingIntent,
                new ComputerControlAllowlistController(context));
        Watchdog.getInstance().addMonitor(this);
    }

    @VisibleForTesting
    ComputerControlSessionProcessor(
            Context context, VirtualDeviceManagerInternal virtualDeviceManagerInternal,
            VirtualDeviceFactory virtualDeviceFactory,
            PendingIntentFactory pendingIntentFactory,
            ComputerControlAllowlistController allowlistController) {
        mContext = context;
        mVirtualDeviceManagerInternal = virtualDeviceManagerInternal;
        mVirtualDeviceFactory = virtualDeviceFactory;
        mPendingIntentFactory = pendingIntentFactory;
        mKeyguardManager = context.getSystemService(KeyguardManager.class);
        mAuthenticationPolicyManager = context.getSystemService(AuthenticationPolicyManager.class);
        mAppOpsManager = context.getSystemService(AppOpsManager.class);
        mAppOpsManagerLocal = LocalManagerRegistry.getManager(AppOpsManagerLocal.class);
        mPackageManager = context.getPackageManager();
        mUserManager = context.getSystemService(UserManager.class);
        mDevicePolicyManager = context.getSystemService(DevicePolicyManager.class);
        mDevicePolicyManagerInternal = LocalServices.getService(DevicePolicyManagerInternal.class);
        mAllowlistController = allowlistController;
        mReferenceDisplayAddress = context.getString(
                R.string.config_computerControlReferenceDisplayPhysicalAddress);
    }

    /** Perform initialization tasks (if any). */
    public void initialize() {
        mAllowlistController.initialize();
    }

    /**
     * Process a new session creation request.
     *
     * <p>A new session will be created. In case of failure, the
     * {@link IComputerControlSessionCallback#onSessionCreationFailed} method on the provided
     * {@code callback} will be invoked.
     */
    public void processNewSessionRequest(
            @NonNull IApplicationThread appThread,
            @NonNull AttributionSource attributionSource,
            @NonNull ComputerControlSessionParams params,
            @NonNull IComputerControlSessionCallback callback) {
        validateParams(attributionSource, params);
        startHandlerThreadIfNeeded();

        final int opResult = mAppOpsManager.noteOpNoThrow(
                AppOpsManager.OP_COMPUTER_CONTROL, attributionSource, "create session");
        if (opResult == AppOpsManager.MODE_ALLOWED) {
            mHandler.post(() -> createSession(appThread, attributionSource, params, callback));
            return;
        } else if (opResult == AppOpsManager.MODE_IGNORED
                || opResult == AppOpsManager.MODE_ERRORED) {
            Slog.w(TAG, "No permission to request computer control session: " + params.getName());
            dispatchSessionCreationFailed(callback, attributionSource, params,
                    ComputerControlSession.ERROR_PERMISSION_DENIED);
            return;
        }

        synchronized (mSessions) {
            if (!checkSessionCreationPreconditionsLocked(attributionSource, params, callback)) {
                return;
            }
        }

        final ResultReceiver resultReceiver =
                new ConsentResultReceiver(appThread, attributionSource, params, callback)
                        .prepareForIpc();
        final Intent intent = new Intent(ComputerControlSession.ACTION_REQUEST_ACCESS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(Intent.EXTRA_PACKAGE_NAME, attributionSource.getPackageName())
                .putExtra(Intent.EXTRA_RESULT_RECEIVER, resultReceiver);
        final PendingIntent pendingIntent =
                mPendingIntentFactory.create(mContext, Binder.getCallingUid(), intent);
        try {
            callback.onSessionPending(pendingIntent);
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to notify ComputerControlSession " + params.getName()
                    + " about pending session");
            ComputerControlStatsController.writeFailedSessionWithStatsReason(
                    mPackageManager, attributionSource, params,
                    COMPUTER_CONTROL_FAILED_SESSION_REPORTED__REASON__SESSION_PENDING_NOTIFICATION_FAILED
            );
        }
    }

    private void validateParams(@NonNull AttributionSource attributionSource,
            @NonNull ComputerControlSessionParams params) {
        final UserHandle ownerUser = UserHandle.getUserHandleForUid(attributionSource.getUid());
        // TODO: b/445856399 - Support managed profiles.
        Binder.withCleanCallingIdentity(() -> {
            if (!isComputerControlAvailableForUser(ownerUser.getIdentifier())) {
                ComputerControlStatsController.writeFailedSessionWithStatsReason(
                        mPackageManager, attributionSource, params,
                        COMPUTER_CONTROL_FAILED_SESSION_REPORTED__REASON__MANAGED_POLICY_DISABLED);
                throw new SecurityException(
                        "Managed profiles are not allowed to use Computer Control.");
            }
        });

        final Context ownerContext = Binder.withCleanCallingIdentity(
                () -> mContext.createContextAsUser(ownerUser, /* flags = */ 0));
        final PackageManager ownerPackageManager = ownerContext.getPackageManager();
        final String callerPackageName = attributionSource.getPackageName();
        if (!mAllowlistController.isPackageAllowedToCreateSession(callerPackageName,
                ownerPackageManager)) {
            ComputerControlStatsController.writeFailedSessionWithStatsReason(
                    mPackageManager, attributionSource, params,
                    COMPUTER_CONTROL_FAILED_SESSION_REPORTED__REASON__CALLER_NOT_ALLOWED);
            throw new SecurityException("Caller " + callerPackageName + " is not allowlisted");
        }

        synchronized (mSessions) {
            for (int i = 0; i < mSessions.size(); i++) {
                ComputerControlSessionImpl session = mSessions.valueAt(i);
                if (Objects.equals(attributionSource.getPackageName(),
                        session.getOwnerPackageName())
                        && Objects.equals(params.getName(), session.getName())) {
                    ComputerControlStatsController.writeFailedSessionWithStatsReason(
                            mPackageManager, attributionSource, params,
                            COMPUTER_CONTROL_FAILED_SESSION_REPORTED__REASON__NAME_NOT_UNIQUE);
                    throw new IllegalArgumentException("Session name must be unique");
                }
            }
        }

        for (int i = 0; i < params.getTargetPackageNames().size(); i++) {
            final String packageName = params.getTargetPackageNames().get(i);

            if (!mAllowlistController.isPackageAutomatable(
                    packageName, callerPackageName, ownerPackageManager)) {
                ComputerControlStatsController.writeFailedSessionWithStatsReason(
                        mPackageManager, attributionSource, params,
                        COMPUTER_CONTROL_FAILED_SESSION_REPORTED__REASON__INVALID_TARGET_APPLICATION
                );
                throw new IllegalArgumentException(
                        "Invalid target package for ComputerControl: " + packageName);
            }
        }
    }

    /**
     * Returns whether the computer control functionality is available for the caller.
     */
    public boolean isComputerControlAvailable(@NonNull AttributionSource attributionSource) {
        final UserHandle ownerUser = UserHandle.getUserHandleForUid(attributionSource.getUid());
        final Context ownerContext;
        final long token = Binder.clearCallingIdentity();
        try {
            if (!isComputerControlAvailableForUser(ownerUser.getIdentifier())) {
                return false;
            }
            ownerContext = mContext.createContextAsUser(ownerUser, /* flags = */ 0);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
        final PackageManager ownerPackageManager = ownerContext.getPackageManager();
        final String callerPackageName = attributionSource.getPackageName();
        try {
            return mAllowlistController.isPackageAllowedToCreateSession(callerPackageName,
                    ownerPackageManager);
        } catch (SecurityException e) {
            return false;
        }
    }

    private boolean isComputerControlAvailableForUser(@UserIdInt int userId) {
        if (!android.companion.virtualdevice.flags.Flags.computerControlManagedProfiles()) {
            return !mDevicePolicyManagerInternal.isUserOrganizationManaged(userId);
        }

        // On fully managed devices, follow nearbyAppStreamingPolicy.
        if (mDevicePolicyManagerInternal.getDeviceOwnerComponent(/* callingUser= */ false)
                != null) {
            return mDevicePolicyManager.getNearbyAppStreamingPolicy(userId)
                    != DevicePolicyManager.NEARBY_STREAMING_DISABLED;
        }

        // TODO: b/445856399 - Support managed profiles. For now they are blocked.
        if (mUserManager.isManagedProfile(userId)) {
            return false;
        }

        // Organization-owned devices with managed profiles are allowed to use Computer Control
        // if the parent profile allows it.
        if (mDevicePolicyManager.isOrganizationOwnedDeviceWithManagedProfile()) {
            UserInfo userInfo = mUserManager.getUserInfo(userId);
            return mDevicePolicyManager.getParentProfileInstance(userInfo)
                    .getNearbyAppStreamingPolicy() != DevicePolicyManager.NEARBY_STREAMING_DISABLED;
        }
        return true;
    }

    /**
     * Returns whether the virtual device with the given ID represents a computer control session.
     */
    public boolean isComputerControlSession(int deviceId) {
        return findSession(deviceId) != null;
    }

    private void startHandlerThreadIfNeeded() {
        synchronized (mHandlerThreadLock) {
            if (mHandlerThread != null) {
                return;
            }
            mHandlerThread =
                    new ServiceThread(TAG, Process.THREAD_PRIORITY_FOREGROUND, /* allowTo= */false);
            mHandlerThread.start();
            mHandler = new Handler(mHandlerThread.getLooper());
        }
    }

    private void createSession(
            @NonNull IApplicationThread appThread,
            @NonNull AttributionSource attributionSource,
            @NonNull ComputerControlSessionParams params,
            @NonNull IComputerControlSessionCallback callback) {
        if (!callback.asBinder().pingBinder()) {
            Slog.w(TAG, "Binder is dead for ComputerControlSession " + params.getName()
                    + ", aborting session creation");
            // Don't bother creating the session if the requester is not around anymore.
            return;
        }

        synchronized (mSessions) {
            if (!checkSessionCreationPreconditionsLocked(attributionSource, params, callback)) {
                return;
            }
        }

        Slog.d(TAG, "Creating ComputerControlSession " + params.getName());
        final ComputerControlSessionImpl session = Binder.withCleanCallingIdentity(
                () -> new ComputerControlSessionImpl(
                        mContext, mAllowlistController, callback.asBinder(), params,
                        appThread, attributionSource,
                        mVirtualDeviceFactory, (closedSession) -> {
                    synchronized (mSessions) {
                        mSessions.remove(closedSession);
                    }
                }, mReferenceDisplayAddress));
        synchronized (mSessions) {
            mSessions.add(session);
        }

        try {
            callback.onSessionCreated(session.getVirtualDisplayId(), session);
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to notify ComputerControlSession " + params.getName()
                    + " about session creation success");
        }
    }

    /** Closes the session with the given ID. */
    public void closeSessionByUserIntent(int deviceId) {
        final var session = findSession(deviceId);
        if (session == null) {
            Slog.w(TAG, "Failed to close ComputerControlSession for unknown deviceId " + deviceId);
            return;
        }
        session.close(CLOSE_REASON_USER_INITIATED);
    }

    @Nullable
    private ComputerControlSessionImpl findSession(int deviceId) {
        synchronized (mSessions) {
            for (int i = 0; i < mSessions.size(); i++) {
                final var session = mSessions.valueAt(i);
                if (session.getDeviceId() == deviceId) {
                    return session;
                }
            }
        }
        return null;
    }

    @GuardedBy("mSessions")
    private boolean checkSessionCreationPreconditionsLocked(
            @NonNull AttributionSource attributionSource,
            @NonNull ComputerControlSessionParams params,
            @NonNull IComputerControlSessionCallback callback) {
        if (isDeviceLocked(attributionSource)) {
            dispatchSessionCreationFailed(callback, attributionSource, params,
                    ComputerControlSession.ERROR_DEVICE_LOCKED);
            return false;
        }
        if (mSessions.size() >= MAXIMUM_CONCURRENT_SESSIONS) {
            dispatchSessionCreationFailed(callback, attributionSource, params,
                    ComputerControlSession.ERROR_SESSION_LIMIT_REACHED);
            return false;
        }
        if (params.getTargetExtensionVersion() >= MIN_EXTENSION_VERSION_FOR_ANDROID_17
                && !mAppOpsManagerLocal.isUidInForeground(attributionSource.getUid())) {
            dispatchSessionCreationFailed(callback, attributionSource, params,
                    ComputerControlSession.ERROR_PERMISSION_DENIED);
            return false;
        }
        return true;
    }

    private boolean isDeviceLocked(@NonNull AttributionSource attributionSource) {
        // If the caller claims to be running on a virtual device, make sure that this is actually
        // the case and this is not just an explicitly created device context. If the uid is not
        // seen on the device they claim to be running on, fallback to default.
        final int deviceId;
        if (attributionSource.getDeviceId() != Context.DEVICE_ID_DEFAULT
                && isDeviceIdAssociationValid(attributionSource)) {
            deviceId = attributionSource.getDeviceId();
        } else {
            deviceId = Context.DEVICE_ID_DEFAULT;
        }
        final int userId = UserHandle.getUserId(attributionSource.getUid());
        return Binder.withCleanCallingIdentity(() -> {
            if (android.companion.Flags.supportAiAgent() && mAuthenticationPolicyManager != null) {
                // TODO(b/482988620): replace null with CDM DeviceId for xdevice scenarios
                return !mAuthenticationPolicyManager.isAgentAuthorized(
                        userId, deviceId, /* companionDeviceId */ null);
            } else {
                return mKeyguardManager.isDeviceLocked(userId, deviceId);
            }
        });
    }

    /** Returns true of the source's UID is seen on the device given by the source's deviceId. */
    private boolean isDeviceIdAssociationValid(@NonNull AttributionSource attributionSource) {
        return mVirtualDeviceManagerInternal.getDeviceIdsForUid(attributionSource.getUid())
                .contains(attributionSource.getDeviceId());
    }

    /** Notifies the client that session creation failed. */
    private void dispatchSessionCreationFailed(@NonNull IComputerControlSessionCallback callback,
            @NonNull AttributionSource attributionSource,
            @NonNull ComputerControlSessionParams params, int reason) {
        try {
            callback.onSessionCreationFailed(reason);
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to notify ComputerControlSession " + params.getName()
                    + " about session creation failure");
        }
        ComputerControlStatsController.writeFailedSessionWithSessionCreationError(
                mPackageManager, attributionSource, params, reason);
    }

    private static PendingIntent createPendingIntent(Context context, int uid, Intent intent) {
        return Binder.withCleanCallingIdentity(() ->
                PendingIntent.getActivityAsUser(
                        context, /*requestCode= */ uid, intent,
                        FLAG_ONE_SHOT | FLAG_CANCEL_CURRENT | FLAG_IMMUTABLE,
                        ActivityOptions.makeBasic()
                                .setPendingIntentCreatorBackgroundActivityStartMode(
                                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                                .toBundle(),
                        UserHandle.CURRENT));
    }

    /**
     * Dump debug information about the state of ComputerControl sessions.
     */
    public void dump(@NonNull FileDescriptor fd, @NonNull PrintWriter fout,
            @Nullable String[] args) {
        String indent = "    ";
        fout.println(indent + "Computer Control Version: " +
                VirtualDeviceManager.COMPUTER_CONTROL_VERSION);
        fout.println(indent + "Maximum Concurrent Sessions: " + MAXIMUM_CONCURRENT_SESSIONS);
        fout.println(indent + "Active computer control sessions: ");
        synchronized (mSessions) {
            for (int i = 0; i < mSessions.size(); i++) {
                mSessions.valueAt(i).dump(fd, fout, args);
            }
        }
    }

    @Override
    public void monitor() {
        synchronized (mSessions) {
            for (int i = 0; i < mSessions.size(); i++) {
                mSessions.valueAt(i).monitor();
            }
        }
        synchronized (mHandlerThreadLock) { /* no-op */ }
        mAllowlistController.monitor();
    }

    private final class ConsentResultReceiver extends ResultReceiver {

        private final IApplicationThread mAppThread;
        private final AttributionSource mAttributionSource;
        private final ComputerControlSessionParams mParams;
        private final IComputerControlSessionCallback mCallback;

        ConsentResultReceiver(
                @NonNull IApplicationThread appThread,
                @NonNull AttributionSource attributionSource,
                @NonNull ComputerControlSessionParams params,
                @NonNull IComputerControlSessionCallback callback) {
            super(mHandler);
            mAppThread = appThread;
            mAttributionSource = attributionSource;
            mParams = params;
            mCallback = callback;
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle data) {
            if (resultCode == Activity.RESULT_OK) {
                mHandler.post(() -> createSession(
                        mAppThread, mAttributionSource, mParams, mCallback));
            } else {
                dispatchSessionCreationFailed(mCallback, mAttributionSource, mParams,
                        ComputerControlSession.ERROR_PERMISSION_DENIED);
            }
        }

        /**
         * Convert this instance of a "locally-defined" ResultReceiver to an instance of
         * {@link android.os.ResultReceiver} itself, which the receiving process will be able to
         * unmarshall.
         */
        private ResultReceiver prepareForIpc() {
            final Parcel parcel = Parcel.obtain();
            writeToParcel(parcel, 0);
            parcel.setDataPosition(0);

            final ResultReceiver ipcFriendly = ResultReceiver.CREATOR.createFromParcel(parcel);
            parcel.recycle();

            return ipcFriendly;
        }
    }

    /**
     * Returns {@code true}, if any of the ongoing computer control sessions are running on the
     * provided virtual display id, {@code false} otherwise.
     */
    public boolean isComputerControlDisplay(int displayId) {
        synchronized (mSessions) {
            for (int i = 0; i < mSessions.size(); i++) {
                if (mSessions.valueAt(i).getVirtualDisplayId() == displayId) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Returns if the provided notification id and tag are used for a computer control session by
     * the given package.
     */
    public boolean isComputerControlNotification(int notificationId,
            @Nullable String notificationTag, @NonNull String packageName) {
        final ComputerControlSessionImpl.NotificationInfo notificationInfo =
                new ComputerControlSessionImpl.NotificationInfo(notificationId, notificationTag);
        synchronized (mSessions) {
            for (int i = 0; i < mSessions.size(); i++) {
                ComputerControlSessionImpl session = mSessions.valueAt(i);
                if (Objects.equals(session.getOwnerPackageName(), packageName)
                        && Objects.equals(session.getNotificationInfo(), notificationInfo)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Interface for creating a virtual device for a computer control session.
     */
    public interface VirtualDeviceFactory {
        /**
         * Creates a new virtual device.
         */
        VirtualDeviceManager.VirtualDevice createVirtualDevice(
                IBinder token,
                AttributionSource attributionSource,
                VirtualDeviceParams params);
    }

    /**
     * Interface for creating a pending intent for a computer control session.
     */
    @VisibleForTesting
    public interface PendingIntentFactory {
        /**
         * Creates a new pending intent.
         */
        PendingIntent create(Context context, int uid, Intent intent);
    }
}
