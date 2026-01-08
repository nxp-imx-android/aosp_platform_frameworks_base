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

package com.android.server.signalcollector.binder;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.os.OutcomeReceiver;
import android.os.Process;
import android.os.binder.BinderSpamStats;
import android.os.profiling.anomaly.flags.Flags;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.os.profiling.anomaly.collector.binder.BinderSpamConfig;
import com.android.os.profiling.anomaly.collector.binder.BinderSpamData;
import com.android.server.signalcollector.binder.BinderSpamSignalCollectorImpl.BinderSpamEvaluator;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.time.Duration;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_ANOMALY_DETECTOR_CORE)
public class BinderSpamEvaluatorTest {

    private static final String TEST_INTERFACE = "com.test.TestInterface";
    private static final String TEST_METHOD = "testMethod";
    private static final int CALL_COUNT_THRESHOLD = 100;
    private static final Duration WINDOW_SIZE = Duration.ofMinutes(1);
    private static final int CLIENT_UID = 1001;

    private static final BinderSpamConfig TEST_CONFIG =
            new BinderSpamConfig.Builder()
                    .setInterfaceName(TEST_INTERFACE)
                    .setMethodName(TEST_METHOD)
                    .setCallCountThreshold(CALL_COUNT_THRESHOLD)
                    .setWindowSize(WINDOW_SIZE)
                    .setUids(new int[0])
                    .setCallerImportanceList(new int[0])
                    .build();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();
    @Rule public final MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Mock private OutcomeReceiver<BinderSpamData, Throwable> mReceiver;

    @Captor private ArgumentCaptor<BinderSpamData> mBinderSpamDataArgumentCaptor;

    private BinderSpamEvaluator mEvaluator;
    private long mCurrentTimeMillis;

    @Before
    public void setUp() {
        mCurrentTimeMillis = 1000L;
        mEvaluator = new BinderSpamEvaluator(TEST_CONFIG, mReceiver, () -> mCurrentTimeMillis);
    }

    @Test
    public void onResult_belowThreshold_shouldNotReport() {
        BinderSpamStats stats = createBinderSpamStats(CALL_COUNT_THRESHOLD - 1);

        mEvaluator.onResult(stats);

        verify(mReceiver, never()).onResult(any());
    }

    @Test
    public void onResult_equalThreshold_shouldNotReport() {
        BinderSpamStats stats = createBinderSpamStats(CALL_COUNT_THRESHOLD);

        mEvaluator.onResult(stats);

        verify(mReceiver, never()).onResult(any());
    }

    @Test
    public void onResult_aboveThreshold_shouldReport() {
        BinderSpamStats stats = createBinderSpamStats(CALL_COUNT_THRESHOLD + 1);

        mEvaluator.onResult(stats);

        verify(mReceiver).onResult(mBinderSpamDataArgumentCaptor.capture());
        BinderSpamData data = mBinderSpamDataArgumentCaptor.getValue();
        assertThat(data.getCallCount()).isEqualTo(CALL_COUNT_THRESHOLD + 1);
        assertThat(data.getInterfaceName()).isEqualTo(TEST_INTERFACE);
        assertThat(data.getMethodName()).isEqualTo(TEST_METHOD);
        assertThat(data.getCallingUid()).isEqualTo(CLIENT_UID);
        assertThat(data.getServerUid()).isEqualTo(Process.myUid());
        assertThat(data.getTimespan()).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    public void onResult_accumulateWithinWindow_shouldReportWhenThresholdExceeded() {
        // First call, half the threshold
        mEvaluator.onResult(createBinderSpamStats(CALL_COUNT_THRESHOLD / 2));
        verify(mReceiver, never()).onResult(any());

        // Second call, still within window, total exceeds threshold
        mEvaluator.onResult(createBinderSpamStats(CALL_COUNT_THRESHOLD / 2 + 1));

        verify(mReceiver).onResult(mBinderSpamDataArgumentCaptor.capture());
        BinderSpamData data = mBinderSpamDataArgumentCaptor.getValue();
        assertThat(data.getCallCount()).isEqualTo(CALL_COUNT_THRESHOLD + 1);
    }

    @Test
    public void onResult_aboveThreshold_shouldResetCount() {
        // First call, exceeds threshold, should report
        mEvaluator.onResult(createBinderSpamStats(CALL_COUNT_THRESHOLD + 1));

        verify(mReceiver).onResult(mBinderSpamDataArgumentCaptor.capture());
        BinderSpamData data = mBinderSpamDataArgumentCaptor.getValue();
        assertThat(data.getCallCount()).isEqualTo(CALL_COUNT_THRESHOLD + 1);

        // Advance time within the window
        mCurrentTimeMillis += WINDOW_SIZE.toMillis() - 1;

        // Second call, below threshold but above if added to previous count), should not report
        // because window reset
        mEvaluator.onResult(createBinderSpamStats(CALL_COUNT_THRESHOLD / 2));
        verify(mReceiver, times(1)).onResult(any()); // Still only 1 report from before
    }

    @Test
    public void onResult_windowReset_shouldStartNewWindow() {
        // First call
        mEvaluator.onResult(createBinderSpamStats(CALL_COUNT_THRESHOLD / 2));

        // Advance time past the window
        mCurrentTimeMillis += WINDOW_SIZE.toMillis() + 1;

        // Second call. If window didn't reset, this + previous would exceed threshold.
        // But since window reset, this starts a new count.
        mEvaluator.onResult(createBinderSpamStats(CALL_COUNT_THRESHOLD / 2 + 2));

        verify(mReceiver, never()).onResult(any());
    }

    private static BinderSpamStats createBinderSpamStats(int peakCallCountPerSecond) {
        BinderSpamStats binderSpamStats = new BinderSpamStats();
        binderSpamStats.interfaceDescriptor = TEST_INTERFACE;
        binderSpamStats.aidlMethod = TEST_METHOD;
        binderSpamStats.clientUid = CLIENT_UID;
        binderSpamStats.peakCallCountPerSecond = peakCallCountPerSecond;
        return binderSpamStats;
    }
}
