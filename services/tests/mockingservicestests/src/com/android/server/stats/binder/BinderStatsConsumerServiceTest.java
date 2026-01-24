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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import android.os.Process;
import android.os.RemoteException;
import android.os.binder.BinderCallsStats;
import android.os.binder.BinderSpamStats;
import android.os.binder.SingleSecondBinderStats;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.StatsEvent;
import android.util.StatsEventTestUtils;
import android.util.StatsLog;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.os.AtomsProto;
import com.android.server.LocalServices;
import com.android.server.signalcollector.SignalCollectorManagerInternal;

import com.google.protobuf.InvalidProtocolBufferException;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

import java.util.List;

/**
 * Tests for {@link com.android.server.stats.binder.BinderStatsConsumerService}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
@Presubmit
public class BinderStatsConsumerServiceTest {
    private BinderStatsConsumerService mService;
    private static final int SPAM_STATS_ATOM_ID = 1064;
    private static final int CALL_STATS_ATOM_ID = 1090;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();
    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).mockStatic(StatsLog.class).build();

    @Captor ArgumentCaptor<StatsEvent> mStatsEventCaptor;

    @Before
    public void setUp() {
        mService = new BinderStatsConsumerService();
        mStatsEventCaptor = ArgumentCaptor.forClass(StatsEvent.class);
    }

    private StatsEvent buildBinderCallsStats(BinderCallsStats stat) {
        StatsEvent.Builder builder = StatsEvent.newBuilder().setAtomId(CALL_STATS_ATOM_ID);
        builder.writeLong(stat.clientUid);
        builder.writeLong(Process.myUid());
        builder.writeString(stat.interfaceDescriptor);
        builder.writeString(stat.aidlMethod);
        builder.writeLong(stat.callCount);
        builder.writeLong(stat.durationSumMicros);
        builder.writeLong(stat.secondsWithAtLeast10Calls);
        builder.writeLong(stat.secondsWithAtLeast50Calls);
        builder.writeLong(stat.callDurationSumSquaredMicros);
        builder.writeLong(stat.cpuTimeCount);
        builder.writeLong(stat.cpuTimeSumMicros);
        builder.writeLong(stat.cpuTimeSumSquaredMicros);
        return builder.usePooledBuffer().build();
    }

    private StatsEvent buildBinderSpamStats(BinderSpamStats stat) {
        StatsEvent.Builder builder = StatsEvent.newBuilder().setAtomId(SPAM_STATS_ATOM_ID);
        builder.writeLong(stat.clientUid);
        builder.writeLong(Process.myUid());
        builder.writeString(stat.interfaceDescriptor);
        builder.writeString(stat.aidlMethod);
        builder.writeLong(stat.secondsWithAtLeast125Calls);
        return builder.usePooledBuffer().build();
    }

    private StatsEvent buildSingleSecondBinderCallsStats(SingleSecondBinderStats stat) {
        StatsEvent.Builder builder = StatsEvent.newBuilder().setAtomId(CALL_STATS_ATOM_ID);
        builder.writeLong(stat.clientUid);
        builder.writeLong(Process.myUid());
        builder.writeString(stat.interfaceDescriptor);
        builder.writeString(stat.aidlMethod);
        builder.writeLong(stat.callCount);
        builder.writeLong(stat.durationMicrosSum);
        builder.writeLong(stat.durationCount > 10 ? 1 : 0);
        builder.writeLong(stat.durationCount > 50 ? 1 : 0);
        builder.writeLong(stat.durationMicrosSquaredSum);
        builder.writeLong(stat.cpuTimeCount);
        builder.writeLong(stat.cpuTimeMicrosSum);
        builder.writeLong(stat.cpuTimeMicrosSquaredSum);
        return builder.usePooledBuffer().build();
    }

    private StatsEvent buildSingleSecondBinderSpamStats(SingleSecondBinderStats stat) {
        StatsEvent.Builder builder = StatsEvent.newBuilder().setAtomId(SPAM_STATS_ATOM_ID);
        builder.writeLong(stat.clientUid);
        builder.writeLong(Process.myUid());
        builder.writeString(stat.interfaceDescriptor);
        builder.writeString(stat.aidlMethod);
        builder.writeLong(stat.callCount >= 125 ? 1 : 0);
        builder.writeLong(stat.callCount >= 250 ? 1 : 0);
        return builder.usePooledBuffer().build();
    }

    @Test
    public void testReportCallStats() throws RemoteException, InvalidProtocolBufferException {
        BinderCallsStats[] stats = new BinderCallsStats[1];
        stats[0] = new BinderCallsStats();
        stats[0].clientUid = 1000;
        stats[0].interfaceDescriptor = "com.example.IFoo";
        stats[0].aidlMethod = "bar";
        stats[0].callCount = 10;
        stats[0].durationSumMicros = 100;
        stats[0].secondsWithAtLeast10Calls = 1;
        stats[0].secondsWithAtLeast50Calls = 0;
        stats[0].callDurationSumSquaredMicros = 112;
        stats[0].cpuTimeCount = 5;
        stats[0].cpuTimeSumMicros = 50;
        stats[0].cpuTimeSumSquaredMicros = 250;
        StatsEvent expectedEvent = buildBinderCallsStats(stats[0]);

        mService.reportCallStats(stats);

        verify(() -> StatsLog.write(mStatsEventCaptor.capture()));

        AtomsProto.Atom expectedAtom = StatsEventTestUtils.convertToAtom(expectedEvent);
        AtomsProto.Atom actualAtom =
                StatsEventTestUtils.convertToAtom(mStatsEventCaptor.getValue());
        assertEquals(expectedAtom, actualAtom);
    }

    @RequiresFlagsEnabled(android.os.profiling.anomaly.flags.Flags.FLAG_ANOMALY_DETECTOR_CORE)
    @Test
    public void testReportSpamStatsToSignalCollector() {
        SignalCollectorManagerInternal signalCollectorManagerInternal = mock(
                SignalCollectorManagerInternal.class);
        LocalServices.addService(SignalCollectorManagerInternal.class,
                signalCollectorManagerInternal);
        BinderSpamStats[] stats = new BinderSpamStats[1];
        stats[0] = new BinderSpamStats();
        stats[0].clientUid = 1000;
        stats[0].interfaceDescriptor = "com.example.IFoo";
        stats[0].aidlMethod = "bar";
        stats[0].secondsWithAtLeast125Calls = 1;

        mService.reportSpamStats(stats);

        verify(signalCollectorManagerInternal).reportBinderStats(stats);

        LocalServices.removeServiceForTest(SignalCollectorManagerInternal.class);
    }

    @Test
    public void testReportSpamStats() throws RemoteException, InvalidProtocolBufferException {
        BinderSpamStats[] stats = new BinderSpamStats[1];
        stats[0] = new BinderSpamStats();
        stats[0].clientUid = 1000;
        stats[0].interfaceDescriptor = "com.example.IFoo";
        stats[0].aidlMethod = "bar";
        stats[0].secondsWithAtLeast125Calls = 5;
        StatsEvent expectedEvent = buildBinderSpamStats(stats[0]);

        mService.reportSpamStats(stats);

        verify(() -> StatsLog.write(mStatsEventCaptor.capture()));

        AtomsProto.Atom expectedAtom = StatsEventTestUtils.convertToAtom(expectedEvent);
        AtomsProto.Atom actualAtom =
                StatsEventTestUtils.convertToAtom(mStatsEventCaptor.getValue());
        assertEquals(expectedAtom, actualAtom);
    }

    @Test
    public void testReportMultipleCallStatsAtoms()
            throws RemoteException, InvalidProtocolBufferException {
        BinderCallsStats[] stats = new BinderCallsStats[2];
        stats[0] = new BinderCallsStats();
        stats[0].clientUid = 1000;
        stats[0].interfaceDescriptor = "com.example.IFoo";
        stats[0].aidlMethod = "bar";
        stats[0].callCount = 10;
        stats[0].durationSumMicros = 100;
        stats[0].secondsWithAtLeast10Calls = 1;
        stats[0].secondsWithAtLeast50Calls = 0;
        stats[0].callDurationSumSquaredMicros = 112;
        stats[0].cpuTimeCount = 5;
        stats[0].cpuTimeSumMicros = 50;
        stats[0].cpuTimeSumSquaredMicros = 250;

        stats[1] = new BinderCallsStats();
        stats[1].clientUid = 1001;
        stats[1].interfaceDescriptor = "com.example.IBar";
        stats[1].aidlMethod = "foo";
        stats[1].callCount = 20;
        stats[1].durationSumMicros = 200;
        stats[1].secondsWithAtLeast10Calls = 2;
        stats[1].secondsWithAtLeast50Calls = 1;
        stats[1].callDurationSumSquaredMicros = 145;
        stats[1].cpuTimeCount = 8;
        stats[1].cpuTimeSumMicros = 74;
        stats[1].cpuTimeSumSquaredMicros = 932;

        StatsEvent expectedEvent1 = buildBinderCallsStats(stats[0]);
        StatsEvent expectedEvent2 = buildBinderCallsStats(stats[1]);

        mService.reportCallStats(stats);

        verify(() -> StatsLog.write(mStatsEventCaptor.capture()), times(2));

        AtomsProto.Atom expectedAtom1 = StatsEventTestUtils.convertToAtom(expectedEvent1);
        AtomsProto.Atom expectedAtom2 = StatsEventTestUtils.convertToAtom(expectedEvent2);
        List<StatsEvent> actualEvents = mStatsEventCaptor.getAllValues();
        AtomsProto.Atom actualAtom1 = StatsEventTestUtils.convertToAtom(actualEvents.get(0));
        AtomsProto.Atom actualAtom2 = StatsEventTestUtils.convertToAtom(actualEvents.get(1));
        assertEquals(expectedAtom1, actualAtom1);
        assertEquals(expectedAtom2, actualAtom2);
    }


    @RequiresFlagsEnabled(android.os.profiling.anomaly.flags.Flags.FLAG_ANOMALY_DETECTOR_CORE)
    @Test
    public void testReportMultipleSpamStatsToSignalCollector() {
        SignalCollectorManagerInternal signalCollectorManagerInternal = mock(
                SignalCollectorManagerInternal.class);
        LocalServices.addService(SignalCollectorManagerInternal.class,
                signalCollectorManagerInternal);
        BinderSpamStats[] stats = new BinderSpamStats[2];
        stats[0] = new BinderSpamStats();
        stats[0].clientUid = 1000;
        stats[0].interfaceDescriptor = "com.example.IFoo";
        stats[0].aidlMethod = "bar";
        stats[0].secondsWithAtLeast125Calls = 10;

        stats[1] = new BinderSpamStats();
        stats[1].clientUid = 1001;
        stats[1].interfaceDescriptor = "com.example.IBar";
        stats[1].aidlMethod = "foo";
        stats[1].secondsWithAtLeast125Calls = 20;

        mService.reportSpamStats(stats);

        verify(signalCollectorManagerInternal).reportBinderStats(stats);
        LocalServices.removeServiceForTest(SignalCollectorManagerInternal.class);
    }

    @Test
    public void testReportMultipleSpamStatsAtoms()
            throws RemoteException, InvalidProtocolBufferException {
        BinderSpamStats[] stats = new BinderSpamStats[2];
        stats[0] = new BinderSpamStats();
        stats[0].clientUid = 1000;
        stats[0].interfaceDescriptor = "com.example.IFoo";
        stats[0].aidlMethod = "bar";
        stats[0].secondsWithAtLeast125Calls = 5;

        stats[1] = new BinderSpamStats();
        stats[1].clientUid = 1001;
        stats[1].interfaceDescriptor = "com.example.IBar";
        stats[1].aidlMethod = "foo";
        stats[1].secondsWithAtLeast125Calls = 10;

        StatsEvent expectedEvent1 = buildBinderSpamStats(stats[0]);
        StatsEvent expectedEvent2 = buildBinderSpamStats(stats[1]);

        mService.reportSpamStats(stats);

        verify(() -> StatsLog.write(mStatsEventCaptor.capture()), times(2));

        AtomsProto.Atom expectedAtom1 = StatsEventTestUtils.convertToAtom(expectedEvent1);
        AtomsProto.Atom expectedAtom2 = StatsEventTestUtils.convertToAtom(expectedEvent2);
        List<StatsEvent> actualEvents = mStatsEventCaptor.getAllValues();
        AtomsProto.Atom actualAtom1 = StatsEventTestUtils.convertToAtom(actualEvents.get(0));
        AtomsProto.Atom actualAtom2 = StatsEventTestUtils.convertToAtom(actualEvents.get(1));
        assertEquals(expectedAtom1, actualAtom1);
        assertEquals(expectedAtom2, actualAtom2);
    }

    @Test
    public void testReportSingleSecondStats_binderCallsReported()
            throws RemoteException, InvalidProtocolBufferException {
        SingleSecondBinderStats[] stats = new SingleSecondBinderStats[1];
        stats[0] = new SingleSecondBinderStats();
        stats[0].clientUid = 1000;
        stats[0].interfaceDescriptor = "com.example.IFoo";
        stats[0].aidlMethod = "bar";
        stats[0].callCount = 10;
        stats[0].durationCount = 1;
        stats[0].durationMicrosSum = 100;
        stats[0].durationMicrosSquaredSum = 112;
        stats[0].cpuTimeCount = 5;
        stats[0].cpuTimeMicrosSum = 50;
        stats[0].cpuTimeMicrosSquaredSum = 250;
        StatsEvent expectedEvent = buildSingleSecondBinderCallsStats(stats[0]);

        mService.reportSecondGranularityStats(stats);

        verify(() -> StatsLog.write(mStatsEventCaptor.capture()));

        AtomsProto.Atom expectedAtom = StatsEventTestUtils.convertToAtom(expectedEvent);
        AtomsProto.Atom actualAtom =
                StatsEventTestUtils.convertToAtom(mStatsEventCaptor.getValue());
        assertEquals(expectedAtom, actualAtom);
    }

    @Test
    public void testReportSingleSecondStats_spamReported125()
            throws RemoteException, InvalidProtocolBufferException {
        SingleSecondBinderStats[] stats = new SingleSecondBinderStats[1];
        stats[0] = new SingleSecondBinderStats();
        stats[0].clientUid = 1000;
        stats[0].interfaceDescriptor = "com.example.IFoo";
        stats[0].aidlMethod = "bar";
        stats[0].callCount = 130;
        stats[0].durationCount = 1;
        StatsEvent expectedEvent = buildSingleSecondBinderSpamStats(stats[0]);

        mService.reportSecondGranularityStats(stats);

        verify(() -> StatsLog.write(mStatsEventCaptor.capture()), times(2));

        AtomsProto.Atom expectedAtom = StatsEventTestUtils.convertToAtom(expectedEvent);
        List<StatsEvent> actualEvents = mStatsEventCaptor.getAllValues();
        AtomsProto.Atom actualAtom = StatsEventTestUtils.convertToAtom(actualEvents.get(0));
        assertEquals(expectedAtom, actualAtom);
    }

    @Test
    public void testReportSingleSecondStats_spamReported250()
            throws RemoteException, InvalidProtocolBufferException {
        SingleSecondBinderStats[] stats = new SingleSecondBinderStats[1];
        stats[0] = new SingleSecondBinderStats();
        stats[0].clientUid = 1000;
        stats[0].interfaceDescriptor = "com.example.IFoo";
        stats[0].aidlMethod = "bar";
        stats[0].callCount = 260;
        stats[0].durationCount = 1;
        StatsEvent expectedEvent = buildSingleSecondBinderSpamStats(stats[0]);

        mService.reportSecondGranularityStats(stats);

        verify(() -> StatsLog.write(mStatsEventCaptor.capture()), times(2));

        AtomsProto.Atom expectedAtom = StatsEventTestUtils.convertToAtom(expectedEvent);
        List<StatsEvent> actualEvents = mStatsEventCaptor.getAllValues();
        AtomsProto.Atom actualAtom = StatsEventTestUtils.convertToAtom(actualEvents.get(0));
        assertEquals(expectedAtom, actualAtom);
    }

    @Test
    public void testReportSingleSecondStats_multipleStats()
            throws RemoteException, InvalidProtocolBufferException {
        SingleSecondBinderStats[] stats = new SingleSecondBinderStats[2];
        stats[0] = new SingleSecondBinderStats();
        stats[0].clientUid = 1000;
        stats[0].interfaceDescriptor = "com.example.IFoo";
        stats[0].aidlMethod = "bar";
        stats[0].callCount = 10;
        stats[0].durationCount = 1;
        stats[0].durationMicrosSum = 100;

        stats[1] = new SingleSecondBinderStats();
        stats[1].clientUid = 1001;
        stats[1].interfaceDescriptor = "com.example.IBar";
        stats[1].aidlMethod = "foo";
        stats[1].callCount = 20;
        stats[1].durationCount = 1;
        stats[1].durationMicrosSum = 200;

        StatsEvent expectedEvent1 = buildSingleSecondBinderCallsStats(stats[0]);
        StatsEvent expectedEvent2 = buildSingleSecondBinderCallsStats(stats[1]);

        mService.reportSecondGranularityStats(stats);

        verify(() -> StatsLog.write(mStatsEventCaptor.capture()), times(2));

        AtomsProto.Atom expectedAtom1 = StatsEventTestUtils.convertToAtom(expectedEvent1);
        AtomsProto.Atom expectedAtom2 = StatsEventTestUtils.convertToAtom(expectedEvent2);
        List<StatsEvent> actualEvents = mStatsEventCaptor.getAllValues();
        AtomsProto.Atom actualAtom1 = StatsEventTestUtils.convertToAtom(actualEvents.get(0));
        AtomsProto.Atom actualAtom2 = StatsEventTestUtils.convertToAtom(actualEvents.get(1));
        assertEquals(expectedAtom1, actualAtom1);
        assertEquals(expectedAtom2, actualAtom2);
    }

    @Test
    public void testReportSingleSecondStats_tooManyStats() throws RemoteException {
        SingleSecondBinderStats[] stats = new SingleSecondBinderStats[257];
        for (int i = 0; i < 257; ++i) {
            stats[i] = new SingleSecondBinderStats();
        }

        mService.reportSecondGranularityStats(stats);

        verify(() -> StatsLog.write(mStatsEventCaptor.capture()), times(0));
    }

    @Test
    public void testReportSingleSecondStats_interfaceTooLong() throws RemoteException {
        SingleSecondBinderStats[] stats = new SingleSecondBinderStats[1];
        stats[0] = new SingleSecondBinderStats();
        stats[0].clientUid = 1000;
        stats[0].interfaceDescriptor = new String(new char[257]);
        stats[0].aidlMethod = "bar";
        stats[0].callCount = 10;
        stats[0].durationCount = 1;

        mService.reportSecondGranularityStats(stats);

        verify(() -> StatsLog.write(mStatsEventCaptor.capture()), times(0));
    }

    @Test
    public void testReportSingleSecondStats_methodTooLong() throws RemoteException {
        SingleSecondBinderStats[] stats = new SingleSecondBinderStats[1];
        stats[0] = new SingleSecondBinderStats();
        stats[0].clientUid = 1000;
        stats[0].interfaceDescriptor = "com.example.IFoo";
        stats[0].aidlMethod = new String(new char[257]);
        stats[0].callCount = 10;
        stats[0].durationCount = 1;

        mService.reportSecondGranularityStats(stats);

        verify(() -> StatsLog.write(mStatsEventCaptor.capture()), times(0));
    }
}
