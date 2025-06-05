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

package com.android.server.biometrics;

import static android.Manifest.permission.USE_BIOMETRIC_INTERNAL;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.RequiresPermission;
import android.os.Handler;
import android.proximity.IProximityResultCallback;
import android.proximity.ProximityResultCode;
import android.security.authenticationpolicy.AuthenticationPolicyManager;
import android.util.Slog;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * This class is a helper class to start and cancel watch ranging. It also provides the state of
 * the watch ranging session.
 */
public class WatchRangingHelper {
    private static final String TAG = "WatchRangingHelper";

    public static final int WATCH_RANGING_IDLE = 0;
    public static final int WATCH_RANGING_SUCCESSFUL = 1;
    public static final int WATCH_RANGING_STARTED = 2;
    public static final int WATCH_RANGING_STOPPED = 3;

    private final long mAuthenticationRequestId;
    private final AuthenticationPolicyManager mAuthenticationPolicyManager;
    private final Handler mHandler;
    private @WatchRangingState int mWatchRangingState = WATCH_RANGING_IDLE;

    @IntDef({
            WATCH_RANGING_IDLE,
            WATCH_RANGING_SUCCESSFUL,
            WATCH_RANGING_STARTED,
            WATCH_RANGING_STOPPED})
    @Retention(RetentionPolicy.SOURCE)
    @interface WatchRangingState {
    }

    WatchRangingHelper(long authenticationRequestId,
            @NonNull AuthenticationPolicyManager authenticationPolicyManager,
            @NonNull Handler handler) {
        mAuthenticationRequestId = authenticationRequestId;
        mAuthenticationPolicyManager = authenticationPolicyManager;
        mHandler = handler;
    }

    /**
     * Start watch ranging and set watch ranging state as per the callback.
     */
    @RequiresPermission(USE_BIOMETRIC_INTERNAL)
    public void startWatchRanging() {
        setWatchRangingState(WatchRangingHelper.WATCH_RANGING_STARTED);

        mAuthenticationPolicyManager.startWatchRangingForIdentityCheck(mAuthenticationRequestId,
                new IProximityResultCallback.Stub() {
                    @Override
                    public void onError(int error) {
                        Slog.v(TAG,
                                "Error received for watch ranging, error code: " + error);
                        mAuthenticationPolicyManager.cancelWatchRangingForRequestId(
                                mAuthenticationRequestId);
                        setWatchRangingState(WatchRangingHelper.WATCH_RANGING_STOPPED);
                    }

                    @Override
                    public void onSuccess(int result) {
                        Slog.v(TAG, "Watch ranging was successful with result " + result);
                        mAuthenticationPolicyManager.cancelWatchRangingForRequestId(
                                mAuthenticationRequestId);
                        setWatchRangingState(result == ProximityResultCode.SUCCESS
                                ? WatchRangingHelper.WATCH_RANGING_SUCCESSFUL
                                : WatchRangingHelper.WATCH_RANGING_STOPPED);
                    }
                }, mHandler);
    }

    /**
     * Cancels watch ranging request.
     */
    @RequiresPermission(USE_BIOMETRIC_INTERNAL)
    public void cancelWatchRanging() {
        mAuthenticationPolicyManager.cancelWatchRangingForRequestId(mAuthenticationRequestId);
    }

    /**
     * Sets the current state of watch ranging.
     */
    public void setWatchRangingState(@WatchRangingState int watchRangingState) {
        mWatchRangingState = watchRangingState;
    }

    /**
     * Returns current state of watch ranging.
     */
    @WatchRangingState
    public int getWatchRangingState() {
        return mWatchRangingState;
    }
}
