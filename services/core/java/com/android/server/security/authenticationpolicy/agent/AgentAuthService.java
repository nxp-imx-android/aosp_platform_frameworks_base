/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.server.security.authenticationpolicy.agent;

import android.annotation.IntRange;
import android.annotation.NonNull;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.companion.CompanionDeviceManager;
import android.companion.DeviceId;
import android.content.Context;
import android.hardware.biometrics.BiometricManager;
import android.os.Handler;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.Slog;

import com.android.internal.os.BackgroundThread;
import com.android.server.LocalServices;
import com.android.server.SystemService;
import com.android.server.locksettings.LockSettingsInternal;
import com.android.server.security.authenticationpolicy.StrongAuthListener;

import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * This sub-service contains an internal authentication API for agents running on
 * a connected device. Agents may perform automated actions when the user's primary device
 * is locked. This service is responsible for deciding if an agent can continue to run in cases
 * like that by incorporating various "signals," such as the last time a user authenticated on
 * their device, connected device status, etc.
 *
 * This service is not meant to be exposed directly to an agent, but is an internal service
 * that higher-level Android services will use as a part of their overall orchestration.
 *
 * @hide
 */
@SuppressLint("MissingPermission")
public class AgentAuthService implements AgentAuthServiceInternal {

    private static final String TAG = "AgentAuthService";

    private final Context mContext;
    private final Handler mHandler;
    private final StrongAuthListener mStrongAuthListener;

    private LockSettingsInternal mLockSettings;
    private BiometricManager mBiometricManager;
    private CompanionDeviceManager mCompanionDeviceManager;

    private final Clock mClock;
    private final long mLastAuthTimeIntervalMillis;
    private CDMAgentMonitor mAgentMonitor;
    private int mCurrentUserId = UserHandle.USER_NULL;

    // session list, writes must be done on the Handler
    private AgentSessionMap mAgentSessionList;

    AgentAuthService(@NonNull Context context, @NonNull Handler handler,
            @NonNull Clock clock, @IntRange(from = 0) long lastAuthTimeIntervalMillis) {
        mContext = context;
        mHandler = handler;
        mClock = clock;
        mLastAuthTimeIntervalMillis = lastAuthTimeIntervalMillis;
        mAgentSessionList = new AgentSessionMap(mContext, ActivityManager.getCurrentUser());
        mStrongAuthListener = new StrongAuthListener(mHandler, this::onStrongAuthForUser);
    }

    @Override
    public boolean isAgentAuthorized(int userId, DeviceId deviceId) {
        final var association = mCompanionDeviceManager != null ?
                mCompanionDeviceManager.getAssociationByDeviceId(deviceId) : null;
        if (association == null) {
            Slog.w(TAG, "No matching association found for deviceId: " + deviceId);
            return false;
        }

        return isAgentAuthorizedByAssociationId(userId, association.getId());
    }

    @Override
    public boolean isAgentAuthorizedByAssociationId(int userId, int associationId) {
        final var info = mAgentSessionList.get(associationId);
        return info != null && (info.getUserId() == userId) && info.isAllowed();
    }

    /** Start the service start monitoring for connected agents and init for current user. */
    void start(@NonNull LockSettingsInternal lockSettings,
            @NonNull BiometricManager biometricManager,
            @NonNull CompanionDeviceManager companionDeviceManager) {
        mLockSettings = lockSettings;
        mBiometricManager = biometricManager;
        mCompanionDeviceManager = companionDeviceManager;

        mHandler.post(() -> {
            mLockSettings.registerLockSettingsStateListener(
                    mStrongAuthListener.asLockSettingsStateListener());
            mBiometricManager.registerAuthenticationStateListener(
                    mStrongAuthListener.asAuthenticationStateListener());
        });

        initInBackgroundForUser(ActivityManager.getCurrentUser());
    }

    /** Refresh data for the given user (call when the foreground user changes). */
    void initInBackgroundForUser(int userId) {
        mHandler.post(() -> {
            Slog.i(TAG, "Refreshing data for user: " + userId);

            onUserSwitched(userId);
        });
    }

    private void onUserSwitched(int userId) {
        if (mCurrentUserId == userId) {
            Slog.i(TAG, "user did not change - ignore");
            return;
        }

        // shutdown any existing subscriptions
        if (mAgentMonitor != null) {
            Slog.d(TAG, "Stop listening for associated agents with user: " + mCurrentUserId);

            mAgentMonitor.stop();
            mAgentMonitor = null;
        }

        // reset list to an initial state
        mCurrentUserId = userId;
        mAgentSessionList.clear();
        mAgentSessionList = new AgentSessionMap(mContext, mCurrentUserId);
        mHandler.removeCallbacksAndMessages(null);
        Slog.d(TAG, "Reset sessions / dropped all pending updates");

        // subscribe to events for this new user
        if (userId >= 0) {
            Slog.d(TAG, "Start listening for associated agents with user: " + mCurrentUserId);

            mAgentMonitor = new CDMAgentMonitor(mHandler, userId,
                    mCompanionDeviceManager, new CDMAgentMonitor.Listener() {
                @Override
                public void onAgentConnectionStarted(int associationId) {
                    // no custom interval yet, but this may need to become dynamic
                    // instead of relying only on the overlay config
                    final boolean authorized = hasRecentStrongAuth(0);
                    if (authorized) {
                        Slog.d(TAG, "Start session (authorized): " + associationId);

                        mAgentSessionList.put(associationId, AgentSession.authorized(userId));
                    } else {
                        Slog.d(TAG, "Start session (not authorized): " + associationId);

                        mAgentSessionList.put(associationId, AgentSession.notAuthorized(userId));
                    }
                }

                @Override
                public void onAgentConnectionStopped(int associationId) {
                    Slog.d(TAG, "End session: " + associationId);
                    mAgentSessionList.remove(associationId);
                }
            });
            mAgentMonitor.start();
        }
    }

    private void onStrongAuthForUser(int userId) {
        if (mCurrentUserId != userId) {
            return;
        }

        mAgentSessionList.authorizeAll();
    }

    private boolean hasRecentStrongAuth(@IntRange(from = 0) int intervalMillis) {
        try {
            final long now = mClock.millis();
            final long interval = intervalMillis > 0 ? Math.min(intervalMillis,
                    mLastAuthTimeIntervalMillis) : mLastAuthTimeIntervalMillis;
            final long lastAuthTime = mBiometricManager.getLastAuthenticationTime(
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
                            | BiometricManager.Authenticators.BIOMETRIC_STRONG);
            return lastAuthTime > 0 && (now - lastAuthTime) < interval;
        } catch (Exception e) {
            Slog.e(TAG, "Unable to determine last auth time", e);
        }

        return false;
    }

    /** System service lifecycle for {@link AgentAuthService}. */
    public static final class Lifecycle extends SystemService {
        private final AgentAuthService mService;

        public Lifecycle(@NonNull Context context) {
            super(context);

            mService = new AgentAuthService(context,
                    new Handler(BackgroundThread.getHandler().getLooper()),
                    SystemClock.elapsedRealtimeClock(),
                    TimeUnit.MINUTES.toMillis(30));
        }

        @Override
        public void onStart() {
            LocalServices.addService(AgentAuthServiceInternal.class, mService);

            mService.start(
                    Objects.requireNonNull(
                            LocalServices.getService(LockSettingsInternal.class)),
                    Objects.requireNonNull(
                            mService.mContext.getSystemService(BiometricManager.class)),
                    (CompanionDeviceManager) mService.mContext.getSystemService(
                            Context.COMPANION_DEVICE_SERVICE));
        }

        @Override
        public void onUserSwitching(TargetUser from, TargetUser to) {
            final int fromId = (from == null) ? -1 : from.getUserIdentifier();
            final int toId = to.getUserIdentifier();

            Slog.i(TAG, "user switch: " + fromId + " -> " + toId);
            mService.initInBackgroundForUser(toId);
        }
    }
}
