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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import com.android.os.profiling.anomaly.collector.binder.BinderSpamConfigList;
import com.android.os.profiling.anomaly.collector.binder.BinderSpamData;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.time.Duration;
import java.util.List;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_ANOMALY_DETECTOR_CORE)
public class BinderSpamSignalCollectorImplTest {

    private static final String TEST_INTERFACE_1 = "com.test.TestInterface1";
    private static final String TEST_METHOD_1 = "testMethod1";
    private static final String TEST_INTERFACE_2 = "com.test.TestInterface2";
    private static final String TEST_METHOD_2 = "testMethod2";
    private static final BinderSpamConfig TEST_CONFIG_1 =
            new BinderSpamConfig.Builder()
                    .setInterfaceName(TEST_INTERFACE_1)
                    .setMethodName(TEST_METHOD_1)
                    .setCallCountThreshold(100)
                    .setWindowSize(Duration.ofMinutes(1))
                    .setUids(new int[0])
                    .setCallerImportanceList(new int[0])
                    .build();
    private static final BinderSpamConfig TEST_CONFIG_2 =
            new BinderSpamConfig.Builder()
                    .setInterfaceName(TEST_INTERFACE_2)
                    .setMethodName(TEST_METHOD_2)
                    .setCallCountThreshold(200)
                    .setWindowSize(Duration.ofMinutes(2))
                    .setUids(new int[0])
                    .setCallerImportanceList(new int[0])
                    .build();
    private static final BinderSpamConfig TEST_CONFIG_3 =
            new BinderSpamConfig.Builder()
                    .setInterfaceName(TEST_INTERFACE_2)
                    .setMethodName(TEST_METHOD_2)
                    .setCallCountThreshold(100)
                    .setWindowSize(Duration.ofMinutes(1))
                    .setUids(new int[0])
                    .setCallerImportanceList(new int[0])
                    .build();
    private static final BinderSpamConfigList TEST_CONFIG_LIST =
            new BinderSpamConfigList(List.of(TEST_CONFIG_1, TEST_CONFIG_2));
    private static final BinderSpamConfigList INVALID_TEST_CONFIG_LIST =
            new BinderSpamConfigList(List.of(TEST_CONFIG_2, TEST_CONFIG_3));

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();
    @Rule public final MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Mock private OutcomeReceiver<BinderSpamData, Throwable> mReceiver;

    private BinderSpamSignalCollectorImpl mCollector;

    @Before
    public void setUp() {
        mCollector = new BinderSpamSignalCollectorImpl();
    }

    @Test
    public void onBinderSpamDataReported_shouldInvokeSubscribedReceiver() {
        BinderSpamStats[] statsArray = new BinderSpamStats[]{
                createBinderSpamStats(TEST_INTERFACE_1, TEST_METHOD_1, 1000, 200),
                createBinderSpamStats(TEST_INTERFACE_2, TEST_METHOD_2, 1001, 201),
                createBinderSpamStats(
                        "NeverSubscribedInterface",
                        "NeverSubscribedMethod",
                        /* clientUid */ 1002,
                        /* peakCallCountPerSecond */ 202)
        };

        mCollector.subscribe(TEST_CONFIG_LIST, mReceiver);

        mCollector.onBinderSpamDataReported(statsArray);

        verify(mReceiver, times(2)).onResult(any());
    }

    @Test
    public void onBinderSpamDataReported_shouldInvokeAllSubscribedReceivers() {
        BinderSpamStats[] statsArray = new BinderSpamStats[]{
                createBinderSpamStats(TEST_INTERFACE_1, TEST_METHOD_1, 1000, 200),
                createBinderSpamStats(TEST_INTERFACE_2, TEST_METHOD_2, 1001, 201),
                createBinderSpamStats(
                        "NeverSubscribedInterface",
                        "NeverSubscribedMethod",
                        /* clientUid */ 1002,
                        /* peakCallCountPerSecond */ 202)
        };

        OutcomeReceiver<BinderSpamData, Throwable> anotherReceiver = mock(OutcomeReceiver.class);

        mCollector.subscribe(TEST_CONFIG_LIST, mReceiver);
        mCollector.subscribe(new BinderSpamConfigList(List.of(TEST_CONFIG_3)), anotherReceiver);

        mCollector.onBinderSpamDataReported(statsArray);

        verify(mReceiver, times(2)).onResult(any());
        verify(anotherReceiver).onResult(any());
    }

    @Test
    public void onBinderSpamDataReported_shouldNotInvokeUnsubscribedReceiver() {
        BinderSpamStats[] statsArray = new BinderSpamStats[]{
                createBinderSpamStats(TEST_INTERFACE_1, TEST_METHOD_1, 1000, 200),
                createBinderSpamStats(TEST_INTERFACE_2, TEST_METHOD_2, 1001, 201),
                createBinderSpamStats(
                        "NeverSubscribedInterface",
                        "NeverSubscribedMethod",
                        /* clientUid */ 1002,
                        /* peakCallCountPerSecond */ 202)
        };

        SubscriptionId id = mCollector.subscribe(TEST_CONFIG_LIST, mReceiver);
        mCollector.unsubscribe(id);
        mCollector.onBinderSpamDataReported(statsArray);

        verify(mReceiver, never()).onResult(any());
    }

    @Test
    public void subscribe_shouldReturnDifferentSubscriptionId() {
        SubscriptionId id1 = mCollector.subscribe(TEST_CONFIG_LIST, result -> {});
        SubscriptionId id2 = mCollector.subscribe(
                new BinderSpamConfigList(List.of(TEST_CONFIG_1)),
                result -> {});

        assertThat(id1).isNotNull();
        assertThat(id2).isNotNull();
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    public void subscribe_sameTargetInOneSubscription_throwException() {
        assertThrows(IllegalArgumentException.class,
                () -> mCollector.subscribe(INVALID_TEST_CONFIG_LIST, result -> {}));
    }

    @Test
    public void subscribe_nullInput_shouldThrowException() {
        assertThrows(NullPointerException.class, () -> mCollector.subscribe(null, result -> {}));
        assertThrows(NullPointerException.class,
                () -> mCollector.subscribe(TEST_CONFIG_LIST, null));
        assertThrows(NullPointerException.class, () -> mCollector.subscribe(null, null));
    }

    @Test
    public void unsubscribe_nullInput_shouldThrowException() {
        assertThrows(NullPointerException.class, () -> mCollector.unsubscribe(null));
    }

    @Test
    public void unsubscribe_notFoundInput_shouldThrowException() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> mCollector.unsubscribe(SubscriptionId.generateNew()));
        assertThat(e).hasMessageThat().contains("The provided subscription ID can not be found!");
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

    private static BinderSpamStats createBinderSpamStats(
            String interfaceDescriptor,
            String aidlMethod,
            int clientUid,
            int peakCallCountPerSecond) {
        BinderSpamStats binderSpamStats = new BinderSpamStats();
        binderSpamStats.interfaceDescriptor = interfaceDescriptor;
        binderSpamStats.aidlMethod = aidlMethod;
        binderSpamStats.clientUid = clientUid;
        binderSpamStats.peakCallCountPerSecond = peakCallCountPerSecond;
        return binderSpamStats;
    }
}
