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

import android.os.Binder;
import android.os.OutcomeReceiver;
import android.os.binder.BinderSpamStats;
import android.util.ArrayMap;

import com.android.os.profiling.anomaly.collector.SubscriptionId;
import com.android.os.profiling.anomaly.collector.binder.BinderSpamConfig;
import com.android.os.profiling.anomaly.collector.binder.BinderSpamConfigList;
import com.android.os.profiling.anomaly.collector.binder.BinderSpamData;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class BinderSpamSignalCollectorImpl extends BinderSpamSignalCollector {
    private final ArrayMap<SubscriptionId, List<ReceiverKey>> mSubscriptions = new ArrayMap<>();
    // TODO(b/470427842): Currently, a given (interface, method) pair can only have one active
    // receiver at a time. This is a bug because the same pair can exist in different subscription
    // and therefore should be able to have multiple receiver.
    private final ConcurrentHashMap<ReceiverKey, OutcomeReceiver<BinderSpamData, Throwable>>
            mReceivers = new ConcurrentHashMap<>();

    @Override
    public void onBinderSpamDataReported(BinderSpamStats[] statsArray) {
        // This is the UID of the service process that received the spammy binder calls.
        int serverUid = Binder.getCallingUid();
        for (BinderSpamStats stats : statsArray) {
            ReceiverKey receiverKey = new ReceiverKey(stats.interfaceDescriptor, stats.aidlMethod);
            var receiver = mReceivers.get(receiverKey);
            if (receiver != null) {
                // TODO(b/423980577): Add caller importance to data
                receiver.onResult(new BinderSpamData.Builder()
                        .setInterfaceName(stats.interfaceDescriptor)
                        .setMethodName(stats.aidlMethod)
                        .setCallingUid(stats.clientUid)
                        .setServerUid(serverUid)
                        .setCallCount(stats.peakCallCountPerSecond)
                        .build());
            }
        }
    }

    /** This method is not thread-safe. */
    @Override
    public SubscriptionId subscribe(BinderSpamConfigList configList,
            OutcomeReceiver<BinderSpamData, Throwable> receiver) {
        Objects.requireNonNull(configList);
        Objects.requireNonNull(receiver);
        SubscriptionId id = SubscriptionId.generateNew();
        ArrayList<ReceiverKey> receiverKeys = new ArrayList<>();
        for (BinderSpamConfig config : configList.getConfigs()) {
            ReceiverKey key = new ReceiverKey(config.getInterfaceName(), config.getMethodName());
            receiverKeys.add(key);
            mReceivers.put(key, receiver);
        }
        mSubscriptions.put(id, receiverKeys);
        return id;
    }

    /** This method is not thread-safe. */
    @Override
    public void unsubscribe(SubscriptionId subscriptionId) {
        Objects.requireNonNull(subscriptionId);
        var removedKeys = mSubscriptions.remove(subscriptionId);
        if (removedKeys == null) {
            throw new IllegalArgumentException("The provided subscription ID can not be found!");
        }
        for (var key : removedKeys) {
            mReceivers.remove(key);
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
