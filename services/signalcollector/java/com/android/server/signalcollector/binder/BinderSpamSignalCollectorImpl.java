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

package com.android.server.signalcollector.binder;

import android.annotation.NonNull;
import android.os.Binder;
import android.os.OutcomeReceiver;
import android.os.SystemClock;
import android.os.binder.BinderSpamStats;
import android.util.ArrayMap;
import android.util.ArraySet;

import com.android.internal.annotations.VisibleForTesting;
import com.android.os.profiling.anomaly.collector.SubscriptionId;
import com.android.os.profiling.anomaly.collector.binder.BinderSpamConfig;
import com.android.os.profiling.anomaly.collector.binder.BinderSpamConfigList;
import com.android.os.profiling.anomaly.collector.binder.BinderSpamData;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

public class BinderSpamSignalCollectorImpl extends BinderSpamSignalCollector {
    private final ArrayMap<SubscriptionId, Set<ReceiverKey>> mSubscriptions = new ArrayMap<>();
    private final ConcurrentHashMap<ReceiverKey, BinderSpamEvaluator>
            mEvaluators = new ConcurrentHashMap<>();

    @Override
    public void onBinderSpamDataReported(BinderSpamStats[] statsArray) {
        for (BinderSpamStats stats : statsArray) {
            for (SubscriptionId subscriptionId : mSubscriptions.keySet()) {
                ReceiverKey receiverKey = new ReceiverKey(stats.interfaceDescriptor,
                        stats.aidlMethod, subscriptionId);
                var evaluator = mEvaluators.get(receiverKey);
                if (evaluator != null) {
                    evaluator.onResult(stats);
                }
            }
        }
    }

    /** This method is not thread-safe. */
    @Override
    @NonNull
    public SubscriptionId subscribe(@NonNull BinderSpamConfigList configList,
            @NonNull OutcomeReceiver<BinderSpamData, Throwable> receiver) {
        Objects.requireNonNull(configList);
        Objects.requireNonNull(receiver);
        SubscriptionId id = SubscriptionId.generateNew();
        ArraySet<ReceiverKey> receiverKeys = new ArraySet<>();
        for (BinderSpamConfig config : configList.getConfigs()) {
            ReceiverKey key =
                    new ReceiverKey(config.getInterfaceName(), config.getMethodName(), id);
            if (!receiverKeys.add(key)) {
                throw new IllegalArgumentException("More than one config for same target!");
            }
            mEvaluators.put(key, new BinderSpamEvaluator(config, receiver,
                    SystemClock::elapsedRealtime));
        }
        mSubscriptions.put(id, receiverKeys);
        return id;
    }

    /** This method is not thread-safe. */
    @Override
    public void unsubscribe(@NonNull SubscriptionId subscriptionId) {
        Objects.requireNonNull(subscriptionId);
        var removedKeys = mSubscriptions.remove(subscriptionId);
        if (removedKeys == null) {
            throw new IllegalArgumentException("The provided subscription ID can not be found!");
        }
        for (var key : removedKeys) {
            mEvaluators.remove(key);
        }
    }

    @Override
    public BinderSpamData getData(@NonNull SubscriptionId subscriptionId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void requestUpdate(@NonNull SubscriptionId subscriptionId) {
        throw new UnsupportedOperationException();
    }

    /** A key object used to look up receivers for a binder method. */
    private record ReceiverKey(
            String interfaceName,
            String methodName,
            SubscriptionId subscriptionId) {}

    @VisibleForTesting
    static class BinderSpamEvaluator {
        private static final long ONE_SECOND_IN_MILLIS = 1000L;
        private final BinderSpamConfig mConfig;
        private final OutcomeReceiver<BinderSpamData, Throwable> mReceiver;
        private final LongSupplier mElapsedRealtimeClock;
        private long mCurrentWindowStartMillis;
        private long mCurrentWindowEndMillis;
        private int mCallCount;

        BinderSpamEvaluator(BinderSpamConfig config,
                OutcomeReceiver<BinderSpamData, Throwable> receiver,
                LongSupplier elapsedRealtimeClock) {
            mConfig = config;
            mReceiver = receiver;
            mElapsedRealtimeClock = elapsedRealtimeClock;
            resetCurrentWindow(mElapsedRealtimeClock.getAsLong());
        }

        void onResult(BinderSpamStats binderSpamStats) {
            long currentElapsedTimeMillis = mElapsedRealtimeClock.getAsLong();
            if (mCurrentWindowEndMillis < currentElapsedTimeMillis) {
                resetCurrentWindow(currentElapsedTimeMillis);
                mCallCount = 0;
            }
            // TODO(b/448909649): Replace this with total reported count when available.
            mCallCount += binderSpamStats.peakCallCountPerSecond;
            if (mCallCount > mConfig.getCallCountThreshold()) {
                Duration timespan = Duration.ofMillis(currentElapsedTimeMillis
                                - mCurrentWindowStartMillis);
                mReceiver.onResult(
                        // TODO(b/423980577): Add caller importance to data
                        new BinderSpamData.Builder()
                                .setInterfaceName(binderSpamStats.interfaceDescriptor)
                                .setMethodName(binderSpamStats.aidlMethod)
                                .setCallingUid(binderSpamStats.clientUid)
                                .setServerUid(Binder.getCallingUid())
                                .setTimespan(timespan)
                                .setCallCount(mCallCount)
                                .build()
                );
                resetCurrentWindow(currentElapsedTimeMillis);
                mCallCount = 0;
            }
        }

        private void resetCurrentWindow(long currentElapsedTimeMillis) {
            // Assume received data is at least one second long.
            mCurrentWindowStartMillis = currentElapsedTimeMillis - ONE_SECOND_IN_MILLIS;
            mCurrentWindowEndMillis =
                    mCurrentWindowStartMillis + mConfig.getWindowSize().toMillis();
        }
    }
}
