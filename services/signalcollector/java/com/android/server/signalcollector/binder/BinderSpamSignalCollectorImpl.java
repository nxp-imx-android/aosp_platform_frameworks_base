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

import android.os.OutcomeReceiver;
import android.os.binder.BinderSpamStats;
import android.util.ArrayMap;

import com.android.os.profiling.anomaly.collector.SubscriptionId;
import com.android.os.profiling.anomaly.collector.binder.BinderSpamConfig;
import com.android.os.profiling.anomaly.collector.binder.BinderSpamData;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class BinderSpamSignalCollectorImpl extends BinderSpamSignalCollector {
    private final ArrayMap<SubscriptionId, ReceiverKey> mSubscriptions = new ArrayMap<>();
    private final ConcurrentHashMap<ReceiverKey, OutcomeReceiver<BinderSpamData, Throwable>>
            mReceivers = new ConcurrentHashMap<>();

    @Override
    public void onBinderSpamDataReported(BinderSpamStats[] statsArray) {
        for (BinderSpamStats stats : statsArray) {
            ReceiverKey receiverKey = new ReceiverKey(stats.interfaceDescriptor, stats.aidlMethod);
            var receiver = mReceivers.get(receiverKey);
            if (receiver != null) {
                receiver.onResult(new BinderSpamData.Builder()
                        .setInterfaceName(stats.interfaceDescriptor)
                        .setMethodName(stats.aidlMethod)
                        .setCallingUid(stats.clientUid)
                        .setCallCount(stats.peakCallCountPerSecond)
                        .build());
            }
        }
    }

    /** This method is not thread-safe. */
    @Override
    public SubscriptionId subscribe(BinderSpamConfig config,
            OutcomeReceiver<BinderSpamData, Throwable> receiver) {
        Objects.requireNonNull(config);
        Objects.requireNonNull(receiver);
        SubscriptionId id = SubscriptionId.generateNew();
        ReceiverKey key = new ReceiverKey(config.getInterfaceName(), config.getMethodName());
        mSubscriptions.put(id, key);
        mReceivers.put(key, receiver);
        return id;
    }

    /** This method is not thread-safe. */
    @Override
    public void unsubscribe(SubscriptionId subscriptionId) {
        Objects.requireNonNull(subscriptionId);
        ReceiverKey removedKey = mSubscriptions.remove(subscriptionId);
        if (removedKey != null) {
            mReceivers.remove(removedKey);
        }
    }

    @Override
    public BinderSpamData getData(SubscriptionId subscriptionId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void requestUpdate(SubscriptionId subscriptionId) {
        throw new UnsupportedOperationException();
    }

    /** A key object used to look up receivers for a binder method. */
    private record ReceiverKey(String interfaceName, String methodName) {}
}
