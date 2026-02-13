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
import android.os.binder.BinderCallsStats;
import android.os.binder.SingleSecondBinderStats;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Pair;

import com.android.internal.annotations.GuardedBy;
import com.android.os.profiling.anomaly.collector.SubscriptionId;
import com.android.os.profiling.anomaly.collector.binder.BinderSpamConfigList;
import com.android.os.profiling.anomaly.collector.binder.BinderSpamData;

import java.time.Duration;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

public class BinderSpamSignalCollectorImpl extends BinderSpamSignalCollector {
    @GuardedBy("mSubscriptions")
    private final ArrayMap<SubscriptionId,
            Pair<BinderSpamConfigList, OutcomeReceiver<BinderSpamData, Throwable>>> mSubscriptions =
            new ArrayMap<>();
    private volatile ArrayMap<BinderStatsKey, Set<OutcomeReceiver<BinderSpamData, Throwable>>>
            mReceivers;

    @Override
    public void onBinderStatsReported(BinderCallsStats[] statsArray) {
        final int serverUid = Binder.getCallingUid();
        for (var stats : statsArray) {
            BinderSpamData data = new BinderSpamData.Builder()
                    .setInterfaceName(stats.interfaceDescriptor)
                    .setMethodName(stats.aidlMethod)
                    .setCallCount((int) stats.callCount)
                    .setCallingUid(stats.clientUid)
                    .setServerUid(serverUid)
                    .setTimespan(Duration.ofSeconds(5))
                    .build();
            handleData(data);
        }
    }

    @Override
    public void onBinderStatsReported(SingleSecondBinderStats[] statsArray) {
        final int serverUid = Binder.getCallingUid();
        for (var stats : statsArray) {
            BinderSpamData data = new BinderSpamData.Builder()
                    .setInterfaceName(stats.interfaceDescriptor)
                    .setMethodName(stats.aidlMethod)
                    .setCallCount(stats.callCount)
                    .setCallingUid(stats.clientUid)
                    .setServerUid(serverUid)
                    .setTimespan(Duration.ofSeconds(1))
                    .build();
            handleData(data);
        }
    }

    private void handleData(BinderSpamData data) {
        BinderStatsKey key = new BinderStatsKey(data.getInterfaceName(), data.getMethodName());
        mReceivers.getOrDefault(key, Collections.emptySet()).forEach(r -> r.onResult(data));
    }

    @Override
    @NonNull
    public SubscriptionId subscribe(@NonNull BinderSpamConfigList configList,
            @NonNull OutcomeReceiver<BinderSpamData, Throwable> receiver) {
        Objects.requireNonNull(configList);
        Objects.requireNonNull(receiver);
        SubscriptionId id = SubscriptionId.generateNew();
        synchronized (mSubscriptions) {
            mSubscriptions.put(id, new Pair<>(configList, receiver));
            recalculateReceivers();
        }
        return id;
    }

    @Override
    public void unsubscribe(@NonNull SubscriptionId subscriptionId) {
        Objects.requireNonNull(subscriptionId);
        synchronized (mSubscriptions) {
            if (mSubscriptions.remove(subscriptionId) == null) {
                throw new IllegalArgumentException(
                        "The provided subscription ID can not be found!");
            }
            recalculateReceivers();
        }
    }

    @GuardedBy("mSubscriptions")
    private void recalculateReceivers() {
        ArrayMap<BinderStatsKey, Set<OutcomeReceiver<BinderSpamData, Throwable>>> receivers =
                new ArrayMap<>();
        for (var subscription : mSubscriptions.values()) {
            for (var binderSpamConfig : subscription.first.getConfigs()) {
                BinderStatsKey key =
                        new BinderStatsKey(
                                binderSpamConfig.getInterfaceName(),
                                binderSpamConfig.getMethodName());
                receivers.computeIfAbsent(key, k -> new ArraySet<>()).add(subscription.second);
            }
        }
        mReceivers = receivers;
    }

    @Override
    public BinderSpamData getData(@NonNull SubscriptionId subscriptionId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void requestUpdate(@NonNull SubscriptionId subscriptionId) {
        throw new UnsupportedOperationException();
    }

    /** A key object used to identify binder stats. */
    private record BinderStatsKey(String interfaceName, String methodName) {}
}
