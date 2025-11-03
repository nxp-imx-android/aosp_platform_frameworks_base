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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.verify;

import android.os.OutcomeReceiver;
import android.os.binder.BinderSpamStats;
import android.os.profiling.anomaly.flags.Flags;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.os.profiling.anomaly.collector.SubscriptionId;
import com.android.os.profiling.anomaly.collector.binder.BinderSpamConfig;
import com.android.os.profiling.anomaly.collector.binder.BinderSpamData;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_ANOMALY_DETECTOR_CORE)
public class BinderSpamSignalCollectorImplTest {

    private static final String TEST_INTERFACE_1 = "com.test.TestInterface1";
    private static final String TEST_METHOD_1 = "testMethod1";
    private static final String TEST_INTERFACE_2 = "com.test.TestInterface2";
    private static final String TEST_METHOD_2 = "testMethod2";
    private static final BinderSpamConfig TEST_CONFIG_1 = new BinderSpamConfig.Builder()
            .setInterfaceName(TEST_INTERFACE_1)
            .setMethodName(TEST_METHOD_1)
            .build();
    private static final BinderSpamConfig TEST_CONFIG_2 = new BinderSpamConfig.Builder()
            .setInterfaceName(TEST_INTERFACE_2)
            .setMethodName(TEST_METHOD_2)
            .build();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();
    @Rule public final MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Mock private OutcomeReceiver<BinderSpamData, Throwable> mReceiver;

    @Captor private ArgumentCaptor<BinderSpamData> mBinderSpamDataArgumentCaptor;

    private BinderSpamSignalCollectorImpl mCollector;

    @Before
    public void setUp() {
        mCollector = new BinderSpamSignalCollectorImpl();
    }

    @Test
    public void onBinderSpamDataReported_shouldOnlyInvokeSubscribedReceiver() {
        BinderSpamStats[] statsArray = new BinderSpamStats[3];
        statsArray[0] = new BinderSpamStats();
        statsArray[0].interfaceDescriptor = TEST_INTERFACE_1;
        statsArray[0].aidlMethod = TEST_METHOD_1;
        statsArray[0].clientUid = 1000;
        statsArray[0].peakCallCountPerSecond = 200;

        statsArray[1] = new BinderSpamStats();
        statsArray[1].interfaceDescriptor = TEST_INTERFACE_2;
        statsArray[1].aidlMethod = TEST_METHOD_2;
        statsArray[1].clientUid = 1001;
        statsArray[1].peakCallCountPerSecond = 201;

        statsArray[2] = new BinderSpamStats();
        statsArray[2].interfaceDescriptor = "NeverSubscribedInterface";
        statsArray[2].aidlMethod = "NeverSubscribedMethod";
        statsArray[2].clientUid = 1002;
        statsArray[2].peakCallCountPerSecond = 202;

        // Subscribe and then unsubscribe to TEST_CONFIG_1.
        SubscriptionId id = mCollector.subscribe(TEST_CONFIG_1, mReceiver);
        mCollector.subscribe(TEST_CONFIG_1, mReceiver);
        mCollector.unsubscribe(id);
        // Subscribe to TEST_CONFIG_2.
        mCollector.subscribe(TEST_CONFIG_2, mReceiver);

        mCollector.onBinderSpamDataReported(statsArray);

        verify(mReceiver).onResult(mBinderSpamDataArgumentCaptor.capture());
        BinderSpamData data = mBinderSpamDataArgumentCaptor.getValue();
        assertThat(data.getInterfaceName()).isEqualTo(statsArray[1].interfaceDescriptor);
        assertThat(data.getMethodName()).isEqualTo(statsArray[1].aidlMethod);
        assertThat(data.getCallingUid()).isEqualTo(statsArray[1].clientUid);
        assertThat(data.getCallCount()).isEqualTo(statsArray[1].peakCallCountPerSecond);
    }

    @Test
    public void subscribe_shouldReturnDifferentSubscriptionId() {
        SubscriptionId id1 = mCollector.subscribe(TEST_CONFIG_1, result -> {});
        SubscriptionId id2 = mCollector.subscribe(TEST_CONFIG_2, result -> {});

        assertThat(id1).isNotNull();
        assertThat(id2).isNotNull();
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    public void subscribe_nullInput_shouldThrowException() {
        assertThrows(NullPointerException.class, () -> mCollector.subscribe(null, result -> {}));
        assertThrows(NullPointerException.class, () -> mCollector.subscribe(TEST_CONFIG_1, null));
        assertThrows(NullPointerException.class, () -> mCollector.subscribe(null, null));
    }

    @Test
    public void unsubscribe_nullInput_shouldThrowException() {
        assertThrows(NullPointerException.class, () -> mCollector.unsubscribe(null));
    }

    @Test
    public void getData_throwUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class,
                () -> mCollector.getData(SubscriptionId.generateNew()));
    }

    @Test
    public void requestUpdate_throwUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class,
                () -> mCollector.requestUpdate(SubscriptionId.generateNew()));
    }

}
