/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.pm.verify.pkg;

import static android.os.Process.INVALID_UID;
import static android.os.Process.SYSTEM_UID;
import static android.provider.DeviceConfig.NAMESPACE_PACKAGE_MANAGER_SERVICE;

import static com.android.server.pm.PackageInstallerService.isValidPackageName;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.SharedLibraryInfo;
import android.content.pm.SigningInfo;
import android.content.pm.verify.pkg.IVerificationSessionInterface;
import android.content.pm.verify.pkg.IVerifierService;
import android.content.pm.verify.pkg.VerificationSession;
import android.content.pm.verify.pkg.VerificationStatus;
import android.net.Uri;
import android.os.Handler;
import android.os.PersistableBundle;
import android.os.SystemProperties;
import android.provider.DeviceConfig;
import android.util.Slog;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.infra.AndroidFuture;
import com.android.internal.infra.ServiceConnector;
import com.android.server.pm.Computer;
import com.android.server.pm.PackageInstallerSession;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * This class manages the bind to the verifier agent installed on the device that implements
 * {@link android.content.pm.verify.pkg.VerifierService} and handles all its interactions.
 */
public class VerifierController {
    private static final String TAG = "VerifierController";
    private static final boolean DEBUG = false;

    /**
     * Configurable maximum amount of time in milliseconds to wait for a verifier to respond to
     * a verification request.
     * Flag type: {@code long}
     * Namespace: NAMESPACE_PACKAGE_MANAGER_SERVICE
     */
    private static final String PROPERTY_VERIFICATION_REQUEST_TIMEOUT_MILLIS =
            "verification_request_timeout_millis";
    // Default duration to wait for a verifier to respond to a verification request.
    private static final long DEFAULT_VERIFICATION_REQUEST_TIMEOUT_MILLIS =
            TimeUnit.MINUTES.toMillis(1);
    /**
     * Configurable maximum amount of time in milliseconds that the verifier can request to extend
     * the verification request timeout duration to. This is the maximum amount of time the system
     * can wait for a request before it times out.
     * Flag type: {@code long}
     * Namespace: NAMESPACE_PACKAGE_MANAGER_SERVICE
     */
    private static final String PROPERTY_MAX_VERIFICATION_REQUEST_EXTENDED_TIMEOUT_MILLIS =
            "max_verification_request_extended_timeout_millis";
    // Max duration allowed to wait for a verifier to respond to a verification request.
    private static final long DEFAULT_MAX_VERIFICATION_REQUEST_EXTENDED_TIMEOUT_MILLIS =
            TimeUnit.MINUTES.toMillis(10);
    /**
     * Configurable maximum amount of time in milliseconds for the system to wait from the moment
     * when the installation session requires a verification, till when the request is delivered to
     * the verifier, pending the connection to be established. If the request has not been delivered
     * to the verifier within this amount of time, e.g., because the verifier has crashed or ANR'd,
     * the controller then sends a failure status back to the installation session.
     * Flag type: {@code long}
     * Namespace: NAMESPACE_PACKAGE_MANAGER_SERVICE
     */
    private static final String PROPERTY_VERIFIER_CONNECTION_TIMEOUT_MILLIS =
            "verifier_connection_timeout_millis";
    // The maximum amount of time to wait from the moment when the session requires a verification,
    // till when the request is delivered to the verifier, pending the connection to be established.
    private static final long DEFAULT_VERIFIER_CONNECTION_TIMEOUT_MILLIS =
            TimeUnit.SECONDS.toMillis(10);

    // The maximum amount of time to wait before the system unbinds from the verifier.
    private static final long UNBIND_TIMEOUT_MILLIS = TimeUnit.HOURS.toMillis(6);

    private static VerifierController sInstance;

    private final Context mContext;
    private final Handler mHandler;
    // Guards the remote service object, as well as the verifier name and UID, which should all be
    // changed at the same time.
    private final Object mLock = new Object();
    @Nullable
    @GuardedBy("mLock")
    private ServiceConnector<IVerifierService> mRemoteService;
    @GuardedBy("mLock")
    private int mRemoteServiceUid = INVALID_UID;
    @NonNull
    private final Injector mInjector;

    @Nullable
    private final String mVerifierPackageName;

    // Repository of active verification sessions and their status, mapping from id to status.
    @NonNull
    @GuardedBy("mVerificationStatus")
    private final SparseArray<VerificationStatusTracker> mVerificationStatus = new SparseArray<>();

    /**
     * Get an instance of VerifierController.
     */
    public static VerifierController getInstance(@NonNull Context context, @NonNull Handler handler,
            @Nullable String verifierPackageName) {
        if (sInstance == null) {
            sInstance = new VerifierController(
                    context, handler, verifierPackageName, new Injector());
        }
        return sInstance;
    }

    @VisibleForTesting
    public VerifierController(@NonNull Context context, @NonNull Handler handler,
            @Nullable String verifierPackageName, @NonNull Injector injector) {
        mContext = context;
        mHandler = handler;
        mVerifierPackageName = verifierPackageName;
        mInjector = injector;
    }

    /**
     * Used by the installation session to get the package name of the installed verifier.
     * It can be overwritten by a system config for testing purpose.
     * TODO(b/360129657): remove debug property and verifier override after moving tests to cts-root
     */
    @Nullable
    public String getVerifierPackageName() {
        final String verifierPackageOverride = SystemProperties.get(
                "debug.pm.verification_service_provider_override", "");
        if (!verifierPackageOverride.isEmpty() && isValidPackageName(verifierPackageOverride)) {
            return verifierPackageOverride;
        }
        return mVerifierPackageName;
    }

    /**
     * Called to start querying and binding to a qualified verifier agent.
     *
     * @return False if a qualified verifier agent doesn't exist on device, so that the system can
     * handle this situation immediately after the call.
     * <p>
     * Notice that since this is an async call, even if this method returns true, it doesn't
     * necessarily mean that the binding connection was successful. However, the system will only
     * try to bind once per installation session, so that it doesn't waste resource by repeatedly
     * trying to bind if the verifier agent isn't available during a short amount of time.
     * <p>
     * If the verifier agent exists but cannot be started for some reason, all the notify* methods
     * in this class will fail asynchronously and quietly. The system will learn about the failure
     * after receiving the failure from
     * {@link PackageInstallerSession.VerifierCallback#onConnectionFailed}.
     */
    public boolean bindToVerifierServiceIfNeeded(Supplier<Computer> snapshotSupplier, int userId) {
        if (DEBUG) {
            Slog.i(TAG, "Requesting to bind to the verifier service.");
        }
        if (mRemoteService != null) {
            // Already connected
            if (DEBUG) {
                Slog.i(TAG, "Verifier service is already connected.");
            }
            return true;
        }
        final String verifierPackageName = getVerifierPackageName();
        if (verifierPackageName == null) {
            // The system has no verifier installed, and if it has not been overwritten by any tests
            return false;
        }
        final int verifierUid = snapshotSupplier.get().getPackageUidInternal(
                verifierPackageName, 0, userId, /* callingUid= */ SYSTEM_UID);
        if (verifierUid == INVALID_UID) {
            if (DEBUG) {
                Slog.i(TAG, "Unable to find the UID of the qualified verifier."
                        + " Is it installed on " + userId + "?");
            }
            return false;
        }
        synchronized (mLock) {
            mRemoteService = mInjector.getRemoteService(
                    verifierPackageName, mContext, userId, mHandler);
            mRemoteServiceUid = verifierUid;
        }

        if (DEBUG) {
            Slog.i(TAG, "Connecting to a qualified verifier: " + verifierPackageName);
        }
        mRemoteService.setServiceLifecycleCallbacks(
                new ServiceConnector.ServiceLifecycleCallbacks<>() {
                    @Override
                    public void onConnected(@NonNull IVerifierService service) {
                        Slog.i(TAG, "Verifier " + verifierPackageName + " is connected");
                    }

                    @Override
                    public void onDisconnected(@NonNull IVerifierService service) {
                        Slog.w(TAG,
                                "Verifier " + verifierPackageName + " is disconnected");
                        destroy();
                    }

                    @Override
                    public void onBinderDied() {
                        Slog.w(TAG, "Verifier " + verifierPackageName + " has died");
                        destroy();
                    }

                    private void destroy() {
                        synchronized (mLock) {
                            if (isVerifierConnectedLocked()) {
                                mRemoteService.unbind();
                                mRemoteService = null;
                                mRemoteServiceUid = INVALID_UID;
                            }
                        }
                    }
                });
        AndroidFuture<IVerifierService> unusedFuture = mRemoteService.connect();
        return true;
    }

    @GuardedBy("mLock")
    private boolean isVerifierConnectedLocked() {
        return mRemoteService != null;
    }

    /**
     * Called to notify the bound verifier agent that a package name is available and will soon be
     * requested for verification.
     */
    public void notifyPackageNameAvailable(@NonNull String packageName) {
        synchronized (mLock) {
            if (!isVerifierConnectedLocked()) {
                if (DEBUG) {
                    Slog.i(TAG, "Verifier is not connected. Not notifying package name available");
                }
                return;
            }
            // Best effort. We don't check for the result.
            mRemoteService.run(service -> {
                if (DEBUG) {
                    Slog.i(TAG, "Notifying package name available for " + packageName);
                }
                service.onPackageNameAvailable(packageName);
            });
        }
    }

    /**
     * Called to notify the bound verifier agent that a package previously notified via
     * {@link android.content.pm.verify.pkg.VerifierService#onPackageNameAvailable(String)}
     * will no longer be requested for verification, possibly because the installation is canceled.
     */
    public void notifyVerificationCancelled(@NonNull String packageName) {
        synchronized (mLock) {
            if (!isVerifierConnectedLocked()) {
                if (DEBUG) {
                    Slog.i(TAG, "Verifier is not connected. Not notifying verification cancelled");
                }
                return;
            }
            // Best effort. We don't check for the result.
            mRemoteService.run(service -> {
                if (DEBUG) {
                    Slog.i(TAG, "Notifying verification cancelled for " + packageName);
                }
                service.onVerificationCancelled(packageName);
            });
        }
    }

    /**
     * Called to notify the bound verifier agent that a package that's pending installation needs
     * to be verified right now.
     * <p>The verification request must be sent to the verifier as soon as the verifier is
     * connected. If the connection cannot be made within the specified time limit from
     * when the request is sent out, we consider the verification to be failed and notify the
     * installation session.</p>
     * <p>If a response is not returned from the verifier agent within a timeout duration from the
     * time the request is sent to the verifier, the verification will be considered a failure.</p>
     *
     * @param retry whether this request is for retrying a previously incomplete verification.
     */
    public boolean startVerificationSession(Supplier<Computer> snapshotSupplier, int userId,
            int installationSessionId, String packageName,
            Uri stagedPackageUri, SigningInfo signingInfo,
            List<SharedLibraryInfo> declaredLibraries,
            @PackageInstaller.VerificationPolicy int verificationPolicy,
            @Nullable PersistableBundle extensionParams,
            PackageInstallerSession.VerifierCallback callback,
            boolean retry) {
        // Try connecting to the verifier if not already connected
        if (!bindToVerifierServiceIfNeeded(snapshotSupplier, userId)) {
            return false;
        }
        // For now, the verification id is the same as the installation session id.
        final int verificationId = installationSessionId;
        synchronized (mLock) {
            if (!isVerifierConnectedLocked()) {
                if (DEBUG) {
                    Slog.i(TAG, "Verifier is not connected. Not notifying verification required");
                }
                // Normally this should not happen because we just tried to bind. But if the
                // verifier just crashed or just became unavailable, we should notify the
                // installation session so it can finish with a verification failure.
                return false;
            }
            final VerificationSession session = new VerificationSession(
                    /* id= */ verificationId,
                    /* installSessionId= */ installationSessionId,
                    packageName, stagedPackageUri, signingInfo, declaredLibraries, extensionParams,
                    verificationPolicy, new VerificationSessionInterface(callback));
            AndroidFuture<Void> unusedFuture = mRemoteService.post(service -> {
                if (!retry) {
                    if (DEBUG) {
                        Slog.i(TAG, "Notifying verification required for session "
                                + verificationId);
                    }
                    service.onVerificationRequired(session);
                } else {
                    if (DEBUG) {
                        Slog.i(TAG, "Notifying verification retry for session "
                                + verificationId);
                    }
                    service.onVerificationRetry(session);
                }
            }).orTimeout(mInjector.getVerifierConnectionTimeoutMillis(), TimeUnit.MILLISECONDS)
                    .whenComplete((res, err) -> {
                        if (err != null) {
                            Slog.e(TAG, "Error notifying verification request for session "
                                    + verificationId, err);
                            // Notify the installation session so it can finish with verification
                            // failure.
                            callback.onConnectionFailed();
                        }
                    });
        }
        // Keep track of the session status with the ID. Start counting down the session timeout.
        final long defaultTimeoutMillis = mInjector.getVerificationRequestTimeoutMillis();
        final long maxExtendedTimeoutMillis = mInjector.getMaxVerificationExtendedTimeoutMillis();
        final VerificationStatusTracker tracker = new VerificationStatusTracker(
                defaultTimeoutMillis, maxExtendedTimeoutMillis, mInjector);
        synchronized (mVerificationStatus) {
            mVerificationStatus.put(verificationId, tracker);
        }
        startTimeoutCountdown(verificationId, tracker, callback, defaultTimeoutMillis);
        return true;
    }

    private void startTimeoutCountdown(int verificationId, VerificationStatusTracker tracker,
            PackageInstallerSession.VerifierCallback callback, long delayMillis) {
        mHandler.postDelayed(() -> {
            if (DEBUG) {
                Slog.i(TAG, "Checking request timeout for " + verificationId);
            }
            if (!tracker.isTimeout()) {
                if (DEBUG) {
                    Slog.i(TAG, "Timeout is not met for " + verificationId + "; check later.");
                }
                // If the current session is not timed out yet, check again later.
                startTimeoutCountdown(verificationId, tracker, callback,
                        /* delayMillis= */ tracker.getRemainingTime());
            } else {
                if (DEBUG) {
                    Slog.i(TAG, "Request " + verificationId + " has timed out.");
                }
                // The request has timed out. Notify the installation session.
                callback.onTimeout();
                // Remove status tracking and stop the timeout countdown
                removeStatusTracker(verificationId);
            }
        }, /* token= */ tracker, delayMillis);
    }

    /**
     * Called to notify the bound verifier agent that a verification request has timed out.
     */
    public void notifyVerificationTimeout(int verificationId) {
        synchronized (mLock) {
            if (!isVerifierConnectedLocked()) {
                if (DEBUG) {
                    Slog.i(TAG,
                            "Verifier is not connected. Not notifying timeout for "
                                    + verificationId);
                }
                return;
            }
            AndroidFuture<Void> unusedFuture = mRemoteService.post(service -> {
                if (DEBUG) {
                    Slog.i(TAG, "Notifying timeout for " + verificationId);
                }
                service.onVerificationTimeout(verificationId);
            }).whenComplete((res, err) -> {
                if (err != null) {
                    Slog.e(TAG, "Error notifying VerificationTimeout for session "
                            + verificationId, err);
                }
            });
        }
    }

    /**
     * Remove a status tracker after it's no longer needed.
     */
    private void removeStatusTracker(int verificationId) {
        if (DEBUG) {
            Slog.i(TAG, "Removing status tracking for verification " + verificationId);
        }
        synchronized (mVerificationStatus) {
            VerificationStatusTracker tracker = mVerificationStatus.removeReturnOld(verificationId);
            // Cancel the timeout counters if there's any
            if (tracker != null) {
                mInjector.stopTimeoutCountdown(mHandler, tracker);
            }
        }
    }

    /**
     * Assert that the calling UID is the same as the UID of the currently connected verifier.
     */
    public void assertCallerIsCurrentVerifier(int callingUid) {
        synchronized (mLock) {
            if (!isVerifierConnectedLocked()) {
                throw new IllegalStateException(
                        "Unable to proceed because the verifier has been disconnected.");
            }
            if (callingUid != mRemoteServiceUid) {
                throw new IllegalStateException(
                        "Calling uid " + callingUid + " is not the current verifier.");
            }
        }
    }

    // This class handles requests from the remote verifier
    private class VerificationSessionInterface extends IVerificationSessionInterface.Stub {
        private final PackageInstallerSession.VerifierCallback mCallback;

        VerificationSessionInterface(PackageInstallerSession.VerifierCallback callback) {
            mCallback = callback;
        }

        @Override
        public long getTimeoutTime(int verificationId) {
            assertCallerIsCurrentVerifier(getCallingUid());
            synchronized (mVerificationStatus) {
                final VerificationStatusTracker tracker = mVerificationStatus.get(verificationId);
                if (tracker == null) {
                    throw new IllegalStateException("Verification session " + verificationId
                            + " doesn't exist or has finished");
                }
                return tracker.getTimeoutTime();
            }
        }

        @Override
        public long extendTimeRemaining(int verificationId, long additionalMs) {
            assertCallerIsCurrentVerifier(getCallingUid());
            synchronized (mVerificationStatus) {
                final VerificationStatusTracker tracker = mVerificationStatus.get(verificationId);
                if (tracker == null) {
                    throw new IllegalStateException("Verification session " + verificationId
                            + " doesn't exist or has finished");
                }
                return tracker.extendTimeRemaining(additionalMs);
            }
        }

        @Override
        public boolean setVerificationPolicy(int verificationId,
                @PackageInstaller.VerificationPolicy int policy) {
            assertCallerIsCurrentVerifier(getCallingUid());
            synchronized (mVerificationStatus) {
                final VerificationStatusTracker tracker = mVerificationStatus.get(verificationId);
                if (tracker == null) {
                    throw new IllegalStateException("Verification session " + verificationId
                            + " doesn't exist or has finished");
                }
            }
            return mCallback.setVerificationPolicy(policy);
        }

        @Override
        public void reportVerificationIncomplete(int id, int reason) {
            assertCallerIsCurrentVerifier(getCallingUid());
            final VerificationStatusTracker tracker;
            synchronized (mVerificationStatus) {
                tracker = mVerificationStatus.get(id);
                if (tracker == null) {
                    throw new IllegalStateException("Verification session " + id
                            + " doesn't exist or has finished");
                }
            }
            mCallback.onVerificationIncompleteReceived(reason);
            // Remove status tracking and stop the timeout countdown
            removeStatusTracker(id);
        }

        @Override
        public void reportVerificationComplete(int id, VerificationStatus verificationStatus,
                @Nullable PersistableBundle extensionResponse) {
            assertCallerIsCurrentVerifier(getCallingUid());
            final VerificationStatusTracker tracker;
            synchronized (mVerificationStatus) {
                tracker = mVerificationStatus.get(id);
                if (tracker == null) {
                    throw new IllegalStateException("Verification session " + id
                            + " doesn't exist or has finished");
                }
            }
            mCallback.onVerificationCompleteReceived(verificationStatus, extensionResponse);
            // Remove status tracking and stop the timeout countdown
            removeStatusTracker(id);
        }
    }

    @VisibleForTesting
    public static class Injector {
        /**
         * Mock this method to inject the remote service to enable unit testing.
         */
        @NonNull
        public ServiceConnector<IVerifierService> getRemoteService(
                @NonNull String verifierPackageName, @NonNull Context context, int userId,
                @NonNull Handler handler) {
            final Intent intent = new Intent(PackageManager.ACTION_VERIFY_PACKAGE);
            intent.setPackage(verifierPackageName);
            return new ServiceConnector.Impl<>(
                    context, intent, Context.BIND_AUTO_CREATE, userId,
                    IVerifierService.Stub::asInterface) {
                @Override
                protected Handler getJobHandler() {
                    return handler;
                }

                @Override
                protected long getRequestTimeoutMs() {
                    return getVerificationRequestTimeoutMillis();
                }

                @Override
                protected long getAutoDisconnectTimeoutMs() {
                    return UNBIND_TIMEOUT_MILLIS;
                }
            };
        }

        /**
         * This is added so we can mock timeouts in the unit tests.
         */
        public long getCurrentTimeMillis() {
            return System.currentTimeMillis();
        }

        /**
         * This is added so that we don't need to mock Handler.removeCallbacksAndEqualMessages
         * which is final.
         */
        public void stopTimeoutCountdown(Handler handler, Object token) {
            handler.removeCallbacksAndEqualMessages(token);
        }

        /**
         * This is added so that we can mock the verification request timeout duration without
         * calling into DeviceConfig.
         */
        public long getVerificationRequestTimeoutMillis() {
            return getVerificationRequestTimeoutMillisFromDeviceConfig();
        }

        /**
         * This is added so that we can mock the maximum request timeout duration without
         * calling into DeviceConfig.
         */
        public long getMaxVerificationExtendedTimeoutMillis() {
            return getMaxVerificationExtendedTimeoutMillisFromDeviceConfig();
        }

        /**
         * This is added so that we can mock the maximum connection timeout duration without
         * calling into DeviceConfig.
         */
        public long getVerifierConnectionTimeoutMillis() {
            return getVerifierConnectionTimeoutMillisFromDeviceConfig();
        }

        private static long getVerificationRequestTimeoutMillisFromDeviceConfig() {
            return DeviceConfig.getLong(NAMESPACE_PACKAGE_MANAGER_SERVICE,
                    PROPERTY_VERIFICATION_REQUEST_TIMEOUT_MILLIS,
                    DEFAULT_VERIFICATION_REQUEST_TIMEOUT_MILLIS);
        }

        private static long getMaxVerificationExtendedTimeoutMillisFromDeviceConfig() {
            return DeviceConfig.getLong(NAMESPACE_PACKAGE_MANAGER_SERVICE,
                    PROPERTY_MAX_VERIFICATION_REQUEST_EXTENDED_TIMEOUT_MILLIS,
                    DEFAULT_MAX_VERIFICATION_REQUEST_EXTENDED_TIMEOUT_MILLIS);
        }

        private static long getVerifierConnectionTimeoutMillisFromDeviceConfig() {
            return DeviceConfig.getLong(NAMESPACE_PACKAGE_MANAGER_SERVICE,
                    PROPERTY_VERIFIER_CONNECTION_TIMEOUT_MILLIS,
                    DEFAULT_VERIFIER_CONNECTION_TIMEOUT_MILLIS);
        }
    }
}
