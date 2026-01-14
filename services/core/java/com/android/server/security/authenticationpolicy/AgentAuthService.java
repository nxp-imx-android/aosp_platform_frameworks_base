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

package com.android.server.security.authenticationpolicy;

import android.annotation.NonNull;
import android.content.Context;
import android.hardware.biometrics.BiometricManager;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Slog;

import com.android.internal.os.BackgroundThread;
import com.android.server.LocalServices;
import com.android.server.SystemService;

import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class AgentAuthService implements AgentAuthServiceInternal {

    private static final String TAG = "AgentAuthService";

    private final Context mContext;
    private final Handler mHandler;
    private final Clock mClock;
    private final long mLastAuthTimeInterval;
    private BiometricManager mBiometricManager;

    AgentAuthService(@NonNull Context context, @NonNull Handler handler,
            @NonNull Clock clock, long lastAuthTimeInterval) {
        mContext = context;
        mHandler = handler;
        mClock = clock;
        mLastAuthTimeInterval = lastAuthTimeInterval;
    }

    void start(@NonNull BiometricManager biometricManager) {
        mBiometricManager = biometricManager;

        // TODO(b/475653265): Update this with the real logic, but print log now as placeholder
        Slog.d(TAG, "Started AgentAuthService - had a recent strong auth: "
                + hasRecentStrongAuth());

        LocalServices.addService(AgentAuthServiceInternal.class, this);
    }

    private boolean hasRecentStrongAuth() {
        try {
            final long now = mClock.millis();
            final long lastAuthTime = mBiometricManager.getLastAuthenticationTime(
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
                            | BiometricManager.Authenticators.BIOMETRIC_STRONG);
            return lastAuthTime > 0 && (now - lastAuthTime) < mLastAuthTimeInterval;
        } catch (Exception e) {
            Slog.e(TAG, "Unable to determine last auth time", e);
        }

        return false;
    }

    /**
     * System service lifecycle for {@link AgentAuthService}.
     */
    public static final class Lifecycle extends SystemService {
        private final AgentAuthService mService;

        public Lifecycle(@NonNull Context context) {
            super(context);

            mService = new AgentAuthService(context,
                    BackgroundThread.getHandler(),
                    SystemClock.elapsedRealtimeClock(),
                    TimeUnit.MINUTES.toMillis(5));
        }

        @Override
        public void onStart() {
            Slog.d(TAG, "Starting AgentAuthService");

            mService.start(Objects.requireNonNull(
                    mService.mContext.getSystemService(BiometricManager.class)));
        }
    }
}
