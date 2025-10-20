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

package com.android.server.stats.binder;

import android.annotation.RequiresNoPermission;
import android.content.Context;
import android.os.Binder;
import android.os.binder.BinderCallsStats;
import android.os.binder.BinderSpamStats;
import android.os.binder.IBinderStatsConsumerService;
import android.util.Slog;

import com.android.internal.util.FrameworkStatsLog;
import com.android.server.SystemService;

/**
 * Service that acts as a proxy to statsd for native binder stats.
 *
 * The binder stats are collected in libbinder and sent to this service.
 * This service then forwards the stats to statsd via socket using {@link FrameworkStatsLog} class.
 *
 * @hide
 */
public class BinderStatsConsumerService extends IBinderStatsConsumerService.Stub {
    private static final String TAG = "BinderStatsConsumerService";
    private static final boolean DEBUG = false;

    public BinderStatsConsumerService() {}

    @Override
    @RequiresNoPermission
    public void reportCallStats(BinderCallsStats[] callStatsArray) {
        if (DEBUG) {
            Slog.d(TAG, "reportCallStats");
        }
        if (callStatsArray.length > 256) {
            Slog.w(TAG,
                    "Too many call stats received, dropping stats. Count: "
                            + callStatsArray.length);
            return;
        }
        int callingUid = Binder.getCallingUid();
        for (BinderCallsStats callStats : callStatsArray) {
            if (callStats.interfaceDescriptor.length() > 256
                    || callStats.aidlMethod.length() > 256) {
                Slog.w(TAG, "Interface descriptor or AIDL method too long, dropping stats.");
                continue;
            }
            FrameworkStatsLog.write(FrameworkStatsLog.BINDER_CALLS_REPORTED, callStats.clientUid,
                    callingUid, callStats.interfaceDescriptor, callStats.aidlMethod,
                    callStats.callCount, callStats.durationSumMicros,
                    callStats.secondsWithAtLeast10Calls, callStats.secondsWithAtLeast50Calls);
        }
    }

    @Override
    @RequiresNoPermission
    public void reportSpamStats(BinderSpamStats[] spamStatsArray) {
        if (DEBUG) {
            Slog.d(TAG, "reportSpamStats");
        }
        if (spamStatsArray.length > 256) {
            Slog.w(TAG,
                    "Too many spam stats received, dropping stats. Count: "
                            + spamStatsArray.length);
            return;
        }
        int callingUid = Binder.getCallingUid();
        for (BinderSpamStats spamStats : spamStatsArray) {
            if (spamStats.interfaceDescriptor.length() > 256
                    || spamStats.aidlMethod.length() > 256) {
                Slog.w(TAG, "Interface descriptor or AIDL method too long, dropping spam stats.");
                continue;
            }
            FrameworkStatsLog.write(FrameworkStatsLog.BINDER_SPAM_REPORTED, spamStats.clientUid,
                    callingUid, spamStats.interfaceDescriptor, spamStats.aidlMethod,
                    spamStats.secondsWithAtLeast125Calls, spamStats.secondsWithAtLeast250Calls);
        }
    }

    /**
     * Lifecycle and related code
     */
    public static final class Lifecycle extends SystemService {
        private BinderStatsConsumerService mService;

        public Lifecycle(Context context) {
            super(context);
        }

        @Override
        public void onStart() {
            mService = new BinderStatsConsumerService();
            try {
                publishBinderService(Context.BINDER_STATS_CONSUMER_SERVICE, mService);
                if (DEBUG) {
                    Slog.d(TAG, "Published binder_stats_consumer service");
                }
            } catch (Exception e) {
                Slog.e(TAG, "Failed to publish binder_stats_consumer service", e);
            }
        }
    }
}
