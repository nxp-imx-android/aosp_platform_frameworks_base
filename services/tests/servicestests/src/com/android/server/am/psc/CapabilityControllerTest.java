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
import static android.app.ActivityManager.PROCESS_CAPABILITY_FOREGROUND_AUDIO_CONTROL;
import static android.app.ActivityManager.PROCESS_CAPABILITY_FOREGROUND_CAMERA;
import static android.app.ActivityManager.PROCESS_CAPABILITY_FOREGROUND_LOCATION;
import static android.app.ActivityManager.PROCESS_CAPABILITY_FOREGROUND_MICROPHONE;
import static android.app.ActivityManager.PROCESS_CAPABILITY_NONE;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;

import static com.android.server.am.psc.Constants.FOREGROUND_APP_ADJ;
import static com.android.server.am.psc.Constants.UNKNOWN_ADJ;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import android.content.pm.ServiceInfo.ForegroundServiceType;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

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
    }

    @Test
    public void testEvaluateInstrumentationPolicy_HasActiveInstrumentation_GrantsBfSl() {
        TestNode node = new TestNode.Builder().withHasActiveInstrumentation(true).build();
        ProcessEdge edge = new ProcessEdge(node);

        FlagAssert.assertThat(edge.evaluateCapabilityFilter()).hasSet(PROCESS_CAPABILITY_BFSL);
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

        FlagAssert.assertThat(edge.evaluateCapabilityFilter())
                .hasNotSet(PROCESS_CAPABILITY_BFSL);
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
        private final int mMaxAdj;
        private final ArrayList<TestServiceRecord> mServices;

        private TestNode(boolean isProcessRunning, boolean hasActiveInstrumentation,
                boolean hasForegroundServices, boolean hasNonShortForegroundServices,
                boolean cachedCompatChangeCameraMicrophoneCapability,
                int maxAdj, ArrayList<TestServiceRecord> services) {
            super(mock(ProcessRecordInternal.class));
            mIsProcessRunning = isProcessRunning;
            mHasActiveInstrumentation = hasActiveInstrumentation;
            mHasForegroundServices = hasForegroundServices;
            mHasNonShortForegroundServices = hasNonShortForegroundServices;
            mCachedCompatChangeCameraMicrophoneCapability =
                    cachedCompatChangeCameraMicrophoneCapability;
            mMaxAdj = maxAdj;
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
        int getMaxAdj() {
            return mMaxAdj;
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
            private int mMaxAdj = UNKNOWN_ADJ;
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

            Builder withMaxAdj(int maxAdj) {
                mMaxAdj = maxAdj;
                return this;
            }

            Builder addService(TestServiceRecord service) {
                mServices.add(service);
                return this;
            }

            TestNode build() {
                return new TestNode(mIsProcessRunning, mHasActiveInstrumentation,
                        mHasForegroundServices, mHasNonShortForegroundServices,
                        mCachedCompatChangeCameraMicrophoneCapability, mMaxAdj, mServices);
            }
        }
    }
}
