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

package com.android.server.am.psc;

import static android.app.ActivityManager.PROCESS_STATE_UNKNOWN;

import static com.android.server.am.psc.Constants.UNKNOWN_ADJ;
import static com.android.server.am.psc.OomAdjusterImpl.Connection.CPU_TIME_TRANSMISSION_NONE;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.ActivityManager.ProcessState;
import android.content.pm.ServiceInfo.ForegroundServiceType;

import com.android.server.am.psc.OomAdjusterImpl.Connection.CpuTimeTransmissionType;

import java.util.ArrayList;

/** Container class for test-specific implementations of process graph node and edges. */
final class TestGraphElements {
    /**
     * A test implementation of {@link GraphNode}. Uses a {@link Builder} pattern to set up various
     * fields.
     */
    static class TestNode extends GraphNode {
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

    /**
     * A test implementation of {@link ProviderBindingEdge}.
     */
    static class TestProviderBindingEdge extends ProviderBindingEdge {
        TestProviderBindingEdge() {
            super(mock(ContentProviderConnectionInternal.class));
        }
    }

    /**
     * A test implementation of {@link ServiceBindingEdge}. Uses a {@link Builder} pattern to set up
     * various fields.
     */
    static class TestServiceBindingEdge extends ServiceBindingEdge {
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

    /**
     * A test service record. Uses a {@link Builder} pattern to set up various fields.
     */
    static class TestServiceRecord {
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

    private TestGraphElements() {
    }
}
