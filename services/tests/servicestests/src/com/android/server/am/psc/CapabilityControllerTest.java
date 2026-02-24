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

package com.android.server.am.psc;

import static android.app.ActivityManager.PROCESS_CAPABILITY_ALL;
import static android.app.ActivityManager.PROCESS_CAPABILITY_BFSL;
import static android.app.ActivityManager.PROCESS_CAPABILITY_CPU_TIME;
import static android.app.ActivityManager.PROCESS_CAPABILITY_FOREGROUND_AUDIO_CONTROL;
import static android.app.ActivityManager.PROCESS_CAPABILITY_FOREGROUND_CAMERA;
import static android.app.ActivityManager.PROCESS_CAPABILITY_FOREGROUND_LOCATION;
import static android.app.ActivityManager.PROCESS_CAPABILITY_FOREGROUND_MICROPHONE;
import static android.app.ActivityManager.PROCESS_CAPABILITY_IMPLICIT_CPU_TIME;
import static android.app.ActivityManager.PROCESS_CAPABILITY_INSTRUMENTATION_DEFAULTS;
import static android.app.ActivityManager.PROCESS_CAPABILITY_NONE;
import static android.app.ActivityManager.PROCESS_CAPABILITY_POWER_RESTRICTED_NETWORK;
import static android.app.ActivityManager.PROCESS_CAPABILITY_USER_RESTRICTED_NETWORK;
import static android.app.ActivityManager.PROCESS_STATE_BOUND_FOREGROUND_SERVICE;
import static android.app.ActivityManager.PROCESS_STATE_BOUND_TOP;
import static android.app.ActivityManager.PROCESS_STATE_FOREGROUND_SERVICE;
import static android.app.ActivityManager.PROCESS_STATE_IMPORTANT_FOREGROUND;
import static android.app.ActivityManager.PROCESS_STATE_PERSISTENT;
import static android.app.ActivityManager.PROCESS_STATE_PERSISTENT_UI;
import static android.app.ActivityManager.PROCESS_STATE_SERVICE;
import static android.app.ActivityManager.PROCESS_STATE_TOP;
import static android.app.ActivityManager.PROCESS_STATE_UNKNOWN;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;

import static com.android.server.am.psc.Constants.FOREGROUND_APP_ADJ;
import static com.android.server.am.psc.Constants.UNKNOWN_ADJ;
import static com.android.server.am.psc.OomAdjuster.ALL_CPU_TIME_CAPABILITIES;
import static com.android.server.am.psc.OomAdjuster.CPU_TIME_REASON_ALLOW_LIST;
import static com.android.server.am.psc.OomAdjuster.CPU_TIME_REASON_NONE;
import static com.android.server.am.psc.OomAdjuster.CPU_TIME_REASON_OTHER;
import static com.android.server.am.psc.OomAdjusterImpl.Connection.CPU_TIME_TRANSMISSION_LEGACY;
import static com.android.server.am.psc.OomAdjusterImpl.Connection.CPU_TIME_TRANSMISSION_NONE;
import static com.android.server.am.psc.OomAdjusterImpl.Connection.CPU_TIME_TRANSMISSION_NORMAL;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.ActivityManager.ProcessCapability;
import android.app.ActivityManager.ProcessState;
import android.content.pm.ServiceInfo.ForegroundServiceType;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import com.android.server.am.psc.OomAdjusterImpl.Connection.CpuTimeTransmissionType;
import com.android.server.tests.assertutils.FlagAssert;

import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;

/**
 * Unit tests for {@link CapabilityController}.
 * Build/Install/Run:
 * atest FrameworksServicesTests:CapabilityControllerTest
 * atest FrameworksServicesTestsRavenwood_ProcessStateController:CapabilityControllerTest
 */
@Presubmit
public class CapabilityControllerTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    public void testDefaultNode_GrantsNoCapabilities() {
        TestNode node = new TestNode.Builder().build();
        ProcessEdge edge = new ProcessEdge(node);

        assertEquals(PROCESS_CAPABILITY_NONE, edge.evaluateCapabilityFilter());
        assertEquals(CPU_TIME_REASON_NONE, edge.getCpuTimeReasons());
    }

    @Test
    public void testEvaluateProcessEdgeFilter_NonRunningProcess_GrantsNoCapabilities() {
        // A non-running process should never have any capabilities, even if other conditions for
        // granting capabilities are met.
        TestNode node = new TestNode.Builder()
                .withProcessRunning(false)
                .withMaxAdj(FOREGROUND_APP_ADJ) // This would normally grant all capabilities.
                .withHasActiveInstrumentation(true) // This would normally grant BFSL.
                .build();
        ProcessEdge edge = new ProcessEdge(node);

        assertEquals(PROCESS_CAPABILITY_NONE, edge.evaluateCapabilityFilter());
    }

    @Test
    public void testEvaluateMaxAdjPolicy_MaxAdjForeground_GrantsAllCapabilities() {
        TestNode node = new TestNode.Builder().withMaxAdj(FOREGROUND_APP_ADJ).build();
        ProcessEdge edge = new ProcessEdge(node);

        assertEquals(PROCESS_CAPABILITY_ALL, edge.evaluateCapabilityFilter());
        FlagAssert.assertThat(edge.getCpuTimeReasons()).hasSet(CPU_TIME_REASON_OTHER);
    }

    @Test
    public void testEvaluateForegroundServicePolicy_RegularFgs_GrantsBfsl() {
        final TestNode node = new TestNode.Builder()
                .withHasForegroundServices(true)
                .withHasNonShortForegroundServices(true)
                .build();
        final ProcessEdge edge = new ProcessEdge(node);

        FlagAssert.assertThat(edge.evaluateCapabilityFilter()).hasSet(PROCESS_CAPABILITY_BFSL);
    }

    @Test
    public void testEvaluateForegroundServicePolicy_ShortFgs_DoesNotGrantBfsl() {
        final TestNode node = new TestNode.Builder()
                .withHasForegroundServices(true)
                .withHasNonShortForegroundServices(false)
                .build();
        final ProcessEdge edge = new ProcessEdge(node);

        FlagAssert.assertThat(edge.evaluateCapabilityFilter()).hasNotSet(PROCESS_CAPABILITY_BFSL);
    }

    @Test
    @RequiresFlagsEnabled(android.media.audio.Flags.FLAG_RO_FOREGROUND_AUDIO_CONTROL)
    public void testEvaluateForegroundServicePolicy_Fgs_GrantsAudioControl() {
        final TestServiceRecord service = new TestServiceRecord.Builder().build();
        final TestNode node = new TestNode.Builder()
                .withHasForegroundServices(true)
                .addService(service)
                .build();
        final ProcessEdge edge = new ProcessEdge(node);

        FlagAssert.assertThat(edge.evaluateCapabilityFilter()).hasSet(
                PROCESS_CAPABILITY_FOREGROUND_AUDIO_CONTROL);
    }

    @Test
    public void testEvaluateForegroundServicePolicy_FgsWithLocation_GrantsLocation() {
        final TestServiceRecord service = new TestServiceRecord.Builder()
                .withForegroundServiceType(FOREGROUND_SERVICE_TYPE_LOCATION)
                .build();
        final TestNode node = new TestNode.Builder()
                .withHasForegroundServices(true)
                .addService(service)
                .build();
        final ProcessEdge edge = new ProcessEdge(node);

        FlagAssert.assertThat(edge.evaluateCapabilityFilter()).hasSet(
                PROCESS_CAPABILITY_FOREGROUND_LOCATION);
    }

    @Test
    public void testEvaluateForegroundServicePolicy_FgsWithCameraMic_GrantsCameraMic() {
        final TestServiceRecord service = new TestServiceRecord.Builder()
                .withForegroundServiceType(
                        FOREGROUND_SERVICE_TYPE_CAMERA | FOREGROUND_SERVICE_TYPE_MICROPHONE)
                .build();
        final TestNode node = new TestNode.Builder()
                .withHasForegroundServices(true)
                .addService(service)
                .withCachedCompatChangeCameraMicrophoneCapability(true)
                .build();
        final ProcessEdge edge = new ProcessEdge(node);

        FlagAssert.assertThat(edge.evaluateCapabilityFilter()).hasSet(
                PROCESS_CAPABILITY_FOREGROUND_CAMERA | PROCESS_CAPABILITY_FOREGROUND_MICROPHONE);
    }

    @Test
    public void testEvaluateForegroundServicePolicy_FgsWithCompatDisabled_GrantsCameraMic() {
        // No specific FGS type, but compat change is off.
        final TestServiceRecord service = new TestServiceRecord.Builder().build();
        final TestNode node = new TestNode.Builder()
                .withHasForegroundServices(true)
                .addService(service)
                .withCachedCompatChangeCameraMicrophoneCapability(false)
                .build();
        final ProcessEdge edge = new ProcessEdge(node);

        FlagAssert.assertThat(edge.evaluateCapabilityFilter()).hasSet(
                PROCESS_CAPABILITY_FOREGROUND_CAMERA | PROCESS_CAPABILITY_FOREGROUND_MICROPHONE);
    }

    @Test
    @RequiresFlagsEnabled(android.media.audio.Flags.FLAG_RO_FOREGROUND_AUDIO_CONTROL)
    public void testEvaluateForegroundServicePolicy_MultipleServices_GrantsCombinedCapabilities() {
        // Service 1: Location
        final TestServiceRecord service1 = new TestServiceRecord.Builder()
                .withForegroundServiceType(FOREGROUND_SERVICE_TYPE_LOCATION)
                .build();

        // Service 2: Camera
        final TestServiceRecord service2 = new TestServiceRecord.Builder()
                .withForegroundServiceType(FOREGROUND_SERVICE_TYPE_CAMERA)
                .build();

        // Service 3: Microphone
        final TestServiceRecord service3 = new TestServiceRecord.Builder()
                .withForegroundServiceType(FOREGROUND_SERVICE_TYPE_MICROPHONE)
                .build();

        final TestNode node = new TestNode.Builder()
                .withHasForegroundServices(true)
                .addService(service1)
                .addService(service2)
                .addService(service3)
                .withCachedCompatChangeCameraMicrophoneCapability(true)
                .build();
        final ProcessEdge edge = new ProcessEdge(node);

        FlagAssert.assertThat(edge.evaluateCapabilityFilter()).hasSet(
                PROCESS_CAPABILITY_FOREGROUND_LOCATION
                        | PROCESS_CAPABILITY_FOREGROUND_CAMERA
                        | PROCESS_CAPABILITY_FOREGROUND_MICROPHONE
                        | PROCESS_CAPABILITY_FOREGROUND_AUDIO_CONTROL);
    }

    @Test
    public void testEvaluateProcStatePolicy_HasActiveInstrumentation_GrantsBfSl() {
        final TestNode node = new TestNode.Builder().withHasActiveInstrumentation(true).build();
        final ProcessEdge edge = new ProcessEdge(node);

        FlagAssert.assertThat(edge.evaluateCapabilityFilter()).hasSet(PROCESS_CAPABILITY_BFSL);
    }

    @Test
    public void testEvaluateProcStatePolicy_TopOrBetter_GrantsAll() {
        // States that are better than or equal to TOP.
        final @ProcessState int[] states = {
                PROCESS_STATE_PERSISTENT,
                PROCESS_STATE_PERSISTENT_UI,
                PROCESS_STATE_TOP,
        };
        for (final @ProcessState int state : states) {
            final TestNode node = new TestNode.Builder().withProcState(state).build();
            final ProcessEdge edge = new ProcessEdge(node);

            assertEquals(PROCESS_CAPABILITY_ALL, edge.evaluateCapabilityFilter());
            // No CPU time reason is granted in this case.
            assertEquals(CPU_TIME_REASON_NONE, edge.getCpuTimeReasons());
        }
    }

    @Test
    public void testEvaluateProcStatePolicy_BtopNoInstrumentation_GrantsBfsl() {
        final TestNode node = new TestNode.Builder().withProcState(PROCESS_STATE_BOUND_TOP).build();
        final ProcessEdge edge = new ProcessEdge(node);

        FlagAssert.assertThat(edge.evaluateCapabilityFilter()).hasSet(PROCESS_CAPABILITY_BFSL);
    }

    @Test
    public void testEvaluateProcStatePolicy_BtopWithInstrumentation_GrantsInstrDefaults() {
        final TestNode node = new TestNode.Builder()
                .withProcState(PROCESS_STATE_BOUND_TOP)
                .withHasActiveInstrumentation(true)
                .build();
        final ProcessEdge edge = new ProcessEdge(node);

        FlagAssert.assertThat(edge.evaluateCapabilityFilter())
                .hasSet(PROCESS_CAPABILITY_INSTRUMENTATION_DEFAULTS);
    }

    @Test
    public void testEvaluateProcStatePolicy_FgsWithInstrumentation_GrantsInstrDefaults() {
        final TestNode node = new TestNode.Builder()
                .withProcState(PROCESS_STATE_FOREGROUND_SERVICE)
                .withHasActiveInstrumentation(true)
                .build();
        final ProcessEdge edge = new ProcessEdge(node);

        FlagAssert.assertThat(edge.evaluateCapabilityFilter())
                .hasSet(PROCESS_CAPABILITY_INSTRUMENTATION_DEFAULTS);
    }

    @Test
    public void testEvaluateProcStatePolicy_BfgsOrBetter_GrantsNetworkCapabilities() {
        final @ProcessCapability int networkCapabilities =
                PROCESS_CAPABILITY_POWER_RESTRICTED_NETWORK
                        | PROCESS_CAPABILITY_USER_RESTRICTED_NETWORK;

        // States that are better than or equal to BFGS.
        final @ProcessState int[] states = {
                PROCESS_STATE_PERSISTENT,
                PROCESS_STATE_PERSISTENT_UI,
                PROCESS_STATE_TOP,
                PROCESS_STATE_BOUND_TOP,
                PROCESS_STATE_BOUND_FOREGROUND_SERVICE,
        };
        for (final @ProcessState int state : states) {
            final TestNode node = new TestNode.Builder().withProcState(state).build();
            final ProcessEdge edge = new ProcessEdge(node);

            FlagAssert.assertThat(edge.evaluateCapabilityFilter()).hasSet(networkCapabilities);
        }

        // Network capabilities should not be granted for other states.
        final TestNode node = new TestNode.Builder()
                .withProcState(PROCESS_STATE_IMPORTANT_FOREGROUND)
                .build();
        final ProcessEdge edge = new ProcessEdge(node);

        FlagAssert.assertThat(edge.evaluateCapabilityFilter()).hasNotSet(networkCapabilities);
    }

    @Test
    public void testEvaluateCpuTimePolicy_AllowListed_GrantsCpuTime() {
        final TestNode node = new TestNode.Builder().withCurAllowListed(true).build();
        final ProcessEdge edge = new ProcessEdge(node);

        FlagAssert.assertThat(edge.evaluateCapabilityFilter()).hasSet(PROCESS_CAPABILITY_CPU_TIME);
        FlagAssert.assertThat(edge.getCpuTimeReasons()).hasSet(CPU_TIME_REASON_ALLOW_LIST);
    }

    @Test
    public void testEvaluateCpuTimePolicy_HasForegroundActivities_GrantsCpuTime() {
        final TestNode node = new TestNode.Builder().withHasForegroundActivities(true).build();
        final ProcessEdge edge = new ProcessEdge(node);

        FlagAssert.assertThat(edge.evaluateCapabilityFilter()).hasSet(PROCESS_CAPABILITY_CPU_TIME);
        FlagAssert.assertThat(edge.getCpuTimeReasons()).hasSet(CPU_TIME_REASON_OTHER);
    }

    @Test
    public void testEvaluateCpuTimePolicy_HasExecutingServices_GrantsCpuTime() {
        final TestNode node = new TestNode.Builder().withHasExecutingServices(true).build();
        final ProcessEdge edge = new ProcessEdge(node);

        FlagAssert.assertThat(edge.evaluateCapabilityFilter()).hasSet(PROCESS_CAPABILITY_CPU_TIME);
        FlagAssert.assertThat(edge.getCpuTimeReasons()).hasSet(CPU_TIME_REASON_OTHER);
    }

    @Test
    public void testEvaluateCpuTimePolicy_HasForegroundServices_GrantsCpuTime() {
        final TestNode node = new TestNode.Builder().withHasForegroundServices(true).build();
        final ProcessEdge edge = new ProcessEdge(node);

        FlagAssert.assertThat(edge.evaluateCapabilityFilter()).hasSet(PROCESS_CAPABILITY_CPU_TIME);
        FlagAssert.assertThat(edge.getCpuTimeReasons()).hasSet(CPU_TIME_REASON_OTHER);
    }

    @Test
    public void testEvaluateCpuTimePolicy_IsReceivingBroadcast_GrantsCpuTime() {
        final TestNode node = new TestNode.Builder().withReceivingBroadcast(true).build();
        final ProcessEdge edge = new ProcessEdge(node);

        FlagAssert.assertThat(edge.evaluateCapabilityFilter()).hasSet(PROCESS_CAPABILITY_CPU_TIME);
        FlagAssert.assertThat(edge.getCpuTimeReasons()).hasSet(CPU_TIME_REASON_OTHER);
    }

    @Test
    public void testEvaluateCpuTimePolicy_HasActiveInstrumentation_GrantsCpuTime() {
        final TestNode node = new TestNode.Builder().withHasActiveInstrumentation(true).build();
        final ProcessEdge edge = new ProcessEdge(node);

        FlagAssert.assertThat(edge.evaluateCapabilityFilter()).hasSet(PROCESS_CAPABILITY_CPU_TIME);
        FlagAssert.assertThat(edge.getCpuTimeReasons()).hasSet(CPU_TIME_REASON_OTHER);
    }

    @Test
    public void testEvaluateCpuTimePolicy_PolicyNotSatisfied_DoesNotGrantCpuTime() {
        final TestServiceRecord service = new TestServiceRecord.Builder()
                .withForegroundService(false)
                .build();
        final TestNode node = new TestNode.Builder()
                .addService(service)
                .withHasForegroundServices(false)
                .withProcState(PROCESS_STATE_SERVICE)
                .build();
        final ProcessEdge edge = new ProcessEdge(node);

        FlagAssert.assertThat(edge.evaluateCapabilityFilter()).hasNotSet(
                PROCESS_CAPABILITY_CPU_TIME);
        assertEquals(CPU_TIME_REASON_NONE, edge.getCpuTimeReasons());
    }

    @Test
    public void testEvaluateImplicitCpuTimePolicy_IntrinsicImplicitCpuTime_GrantsImplicitCpuTime() {
        final TestNode node = new TestNode.Builder().withHasIntrinsicImplicitCpuTime(true).build();
        final ProcessEdge edge = new ProcessEdge(node);

        FlagAssert.assertThat(edge.evaluateCapabilityFilter()).hasSet(
                PROCESS_CAPABILITY_IMPLICIT_CPU_TIME);
    }

    @Test
    public void testDefaultProviderBindingEdge_GrantsOnlyBfslAndCpuTime() {
        final TestProviderBindingEdge edge = new TestProviderBindingEdge();
        assertEquals(PROCESS_CAPABILITY_BFSL | ALL_CPU_TIME_CAPABILITIES,
                edge.evaluateCapabilityFilter());
    }

    @Test
    public void testDefaultServiceBindingEdge_GrantsOnlyBfslAndAudioControl() {
        final TestServiceBindingEdge edge = new TestServiceBindingEdge.Builder().build();
        assertEquals(PROCESS_CAPABILITY_BFSL | PROCESS_CAPABILITY_FOREGROUND_AUDIO_CONTROL,
                edge.evaluateCapabilityFilter());
    }

    @Test
    public void testEvaluateServiceBindingCpuTimePolicy_TransmissionTypes() {
        final TestServiceBindingEdge edge1 = new TestServiceBindingEdge.Builder()
                .withCpuTimeTransmissionType(CPU_TIME_TRANSMISSION_NORMAL)
                .build();
        FlagAssert.assertThat(edge1.evaluateCapabilityFilter()).hasSet(ALL_CPU_TIME_CAPABILITIES);

        final TestServiceBindingEdge edge2 = new TestServiceBindingEdge.Builder()
                .withCpuTimeTransmissionType(CPU_TIME_TRANSMISSION_LEGACY)
                .build();
        FlagAssert.assertThat(edge2.evaluateCapabilityFilter()).hasSet(ALL_CPU_TIME_CAPABILITIES);

        final TestServiceBindingEdge edge3 = new TestServiceBindingEdge.Builder()
                .withCpuTimeTransmissionType(CPU_TIME_TRANSMISSION_NONE)
                .build();
        FlagAssert.assertThat(edge3.evaluateCapabilityFilter()).hasNotSet(
                ALL_CPU_TIME_CAPABILITIES);
    }

    private static class TestServiceRecord {
        final boolean mIsForegroundService;
        final boolean mIsFgsAllowedWiuForCapabilities;
        final @ForegroundServiceType int mForegroundServiceType;

        private TestServiceRecord(boolean isForegroundService,
                boolean isFgsAllowedWiuForCapabilities,
                @ForegroundServiceType int foregroundServiceType) {
            mIsForegroundService = isForegroundService;
            mIsFgsAllowedWiuForCapabilities = isFgsAllowedWiuForCapabilities;
            mForegroundServiceType = foregroundServiceType;
        }

        static class Builder {
            private boolean mIsForegroundService = true;
            private boolean mIsFgsAllowedWiuForCapabilities = true;
            private @ForegroundServiceType int mForegroundServiceType = 0;

            Builder withForegroundService(boolean isForegroundService) {
                mIsForegroundService = isForegroundService;
                return this;
            }

            Builder withFgsAllowedWiuForCapabilities(boolean isFgsAllowedWiuForCapabilities) {
                mIsFgsAllowedWiuForCapabilities = isFgsAllowedWiuForCapabilities;
                return this;
            }

            Builder withForegroundServiceType(@ForegroundServiceType int foregroundServiceType) {
                mForegroundServiceType = foregroundServiceType;
                return this;
            }

            TestServiceRecord build() {
                return new TestServiceRecord(mIsForegroundService, mIsFgsAllowedWiuForCapabilities,
                        mForegroundServiceType);
            }
        }
    }

    private static class TestNode extends GraphNode {
        private final boolean mIsProcessRunning;
        private final boolean mHasActiveInstrumentation;
        private final boolean mHasForegroundServices;
        private final boolean mHasNonShortForegroundServices;
        private final boolean mCachedCompatChangeCameraMicrophoneCapability;
        private final boolean mIsCurAllowListed;
        private final boolean mHasForegroundActivities;
        private final boolean mHasExecutingServices;
        private final boolean mIsReceivingBroadcast;
        private final boolean mHasIntrinsicImplicitCpuTime;
        private final int mMaxAdj;
        private final @ProcessState int mProcState;
        private final ArrayList<TestServiceRecord> mServices;

        private TestNode(boolean isProcessRunning, boolean hasActiveInstrumentation,
                boolean hasForegroundServices, boolean hasNonShortForegroundServices,
                boolean cachedCompatChangeCameraMicrophoneCapability, boolean isCurAllowListed,
                boolean hasForegroundActivities, boolean hasExecutingServices,
                boolean isReceivingBroadcast, boolean hasIntrinsicImplicitCpuTime, int maxAdj,
                @ProcessState int procState, ArrayList<TestServiceRecord> services) {
            super(mock(ProcessRecordInternal.class));
            mIsProcessRunning = isProcessRunning;
            mHasActiveInstrumentation = hasActiveInstrumentation;
            mHasForegroundServices = hasForegroundServices;
            mHasNonShortForegroundServices = hasNonShortForegroundServices;
            mCachedCompatChangeCameraMicrophoneCapability =
                    cachedCompatChangeCameraMicrophoneCapability;
            mIsCurAllowListed = isCurAllowListed;
            mHasForegroundActivities = hasForegroundActivities;
            mHasExecutingServices = hasExecutingServices;
            mIsReceivingBroadcast = isReceivingBroadcast;
            mHasIntrinsicImplicitCpuTime = hasIntrinsicImplicitCpuTime;
            mMaxAdj = maxAdj;
            mProcState = procState;
            mServices = services;
        }

        @Override
        boolean isProcessRunning() {
            return mIsProcessRunning;
        }

        @Override
        boolean hasActiveInstrumentation() {
            return mHasActiveInstrumentation;
        }

        @Override
        boolean hasForegroundServices() {
            return mHasForegroundServices;
        }

        @Override
        boolean hasNonShortForegroundServices() {
            return mHasNonShortForegroundServices;
        }

        @Override
        boolean getCachedCompatChangeCameraMicrophoneCapability() {
            return mCachedCompatChangeCameraMicrophoneCapability;
        }

        @Override
        boolean isCurAllowListed() {
            return mIsCurAllowListed;
        }

        @Override
        boolean hasForegroundActivities() {
            return mHasForegroundActivities;
        }

        @Override
        boolean hasExecutingServices() {
            return mHasExecutingServices;
        }

        @Override
        boolean isReceivingBroadcast() {
            return mIsReceivingBroadcast;
        }

        @Override
        boolean hasIntrinsicImplicitCpuTime() {
            return mHasIntrinsicImplicitCpuTime;
        }

        @Override
        int getMaxAdj() {
            return mMaxAdj;
        }

        @Override
        @ProcessState
        int getProcState() {
            return mProcState;
        }

        @Override
        int getNumberOfRunningServices() {
            return mServices.size();
        }

        @Override
        boolean isForegroundService(int index) {
            return mServices.get(index).mIsForegroundService;
        }

        @Override
        boolean isFgsAllowedWiuForCapabilities(int index) {
            return mServices.get(index).mIsFgsAllowedWiuForCapabilities;
        }

        @Override
        @ForegroundServiceType
        int getForegroundServiceType(int index) {
            return mServices.get(index).mForegroundServiceType;
        }

        static class Builder {
            private boolean mIsProcessRunning = true;
            private boolean mHasActiveInstrumentation = false;
            private boolean mHasForegroundServices = false;
            private boolean mHasNonShortForegroundServices = false;
            private boolean mCachedCompatChangeCameraMicrophoneCapability = false;
            private boolean mIsCurAllowListed = false;
            private boolean mHasForegroundActivities = false;
            private boolean mHasExecutingServices = false;
            private boolean mIsReceivingBroadcast = false;
            private boolean mHasIntrinsicImplicitCpuTime = false;
            private int mMaxAdj = UNKNOWN_ADJ;
            private @ProcessState int mProcState = PROCESS_STATE_UNKNOWN;
            private final ArrayList<TestServiceRecord> mServices = new ArrayList<>();

            Builder withProcessRunning(boolean isProcessRunning) {
                mIsProcessRunning = isProcessRunning;
                return this;
            }

            Builder withHasActiveInstrumentation(boolean hasActiveInstrumentation) {
                mHasActiveInstrumentation = hasActiveInstrumentation;
                return this;
            }

            Builder withHasForegroundServices(boolean hasForegroundServices) {
                mHasForegroundServices = hasForegroundServices;
                return this;
            }

            Builder withHasNonShortForegroundServices(boolean hasNonShortForegroundServices) {
                mHasNonShortForegroundServices = hasNonShortForegroundServices;
                return this;
            }

            Builder withCachedCompatChangeCameraMicrophoneCapability(boolean enabled) {
                mCachedCompatChangeCameraMicrophoneCapability = enabled;
                return this;
            }

            Builder withCurAllowListed(boolean isCurAllowListed) {
                mIsCurAllowListed = isCurAllowListed;
                return this;
            }

            Builder withHasForegroundActivities(boolean hasForegroundActivities) {
                mHasForegroundActivities = hasForegroundActivities;
                return this;
            }

            Builder withHasExecutingServices(boolean hasExecutingServices) {
                mHasExecutingServices = hasExecutingServices;
                return this;
            }

            Builder withReceivingBroadcast(boolean isReceivingBroadcast) {
                mIsReceivingBroadcast = isReceivingBroadcast;
                return this;
            }

            Builder withHasIntrinsicImplicitCpuTime(boolean hasIntrinsicImplicitCpuTime) {
                mHasIntrinsicImplicitCpuTime = hasIntrinsicImplicitCpuTime;
                return this;
            }

            Builder withMaxAdj(int maxAdj) {
                mMaxAdj = maxAdj;
                return this;
            }

            Builder withProcState(@ProcessState int procState) {
                mProcState = procState;
                return this;
            }

            Builder addService(TestServiceRecord service) {
                mServices.add(service);
                return this;
            }

            TestNode build() {
                return new TestNode(mIsProcessRunning, mHasActiveInstrumentation,
                        mHasForegroundServices, mHasNonShortForegroundServices,
                        mCachedCompatChangeCameraMicrophoneCapability, mIsCurAllowListed,
                        mHasForegroundActivities, mHasExecutingServices, mIsReceivingBroadcast,
                        mHasIntrinsicImplicitCpuTime, mMaxAdj, mProcState, mServices);
            }
        }
    }

    private static class TestProviderBindingEdge extends ProviderBindingEdge {
        TestProviderBindingEdge() {
            super(mock(ContentProviderConnectionInternal.class));
        }
    }

    private static class TestServiceBindingEdge extends ServiceBindingEdge {
        private final @CpuTimeTransmissionType int mCpuTimeTransmissionType;

        private TestServiceBindingEdge(@CpuTimeTransmissionType int cpuTimeTransmissionType) {
            super(buildMockConnectionRecord());
            mCpuTimeTransmissionType = cpuTimeTransmissionType;
        }

        private static ConnectionRecordInternal buildMockConnectionRecord() {
            final ConnectionRecordInternal conn = mock(ConnectionRecordInternal.class);
            when(conn.getService()).thenReturn(mock(ServiceRecordInternal.class));
            when(conn.getClient()).thenReturn(mock(ProcessRecordInternal.class));
            return conn;
        }

        @Override
        @CpuTimeTransmissionType
        int getCpuTimeTransmissionType() {
            return mCpuTimeTransmissionType;
        }

        static class Builder {
            private @CpuTimeTransmissionType int mCpuTimeTransmissionType =
                    CPU_TIME_TRANSMISSION_NONE;

            Builder withCpuTimeTransmissionType(
                    @CpuTimeTransmissionType int cpuTimeTransmissionType) {
                mCpuTimeTransmissionType = cpuTimeTransmissionType;
                return this;
            }

            TestServiceBindingEdge build() {
                return new TestServiceBindingEdge(mCpuTimeTransmissionType);
            }
        }
    }
}
