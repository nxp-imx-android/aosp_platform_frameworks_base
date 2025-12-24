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
import static android.app.ActivityManager.PROCESS_CAPABILITY_NONE;

import static com.android.server.am.psc.Constants.FOREGROUND_APP_ADJ;
import static com.android.server.am.psc.Constants.UNKNOWN_ADJ;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import android.platform.test.annotations.Presubmit;

import org.junit.Test;

/**
 * Unit tests for {@link CapabilityController}.
 * Build/Install/Run:
 * atest FrameworksServicesTests:CapabilityControllerTest
 * atest FrameworksServicesTestsRavenwood_ProcessStateController:CapabilityControllerTest
 */
@Presubmit
public class CapabilityControllerTest {
    @Test
    public void testEvaluateProcessEdgeFilter_NonRunningProcess_GrantsNoCapabilities() {
        TestNode node = new TestNode.Builder().withProcessRunning(false).build();
        ProcessEdge edge = new ProcessEdge(node);

        assertEquals(PROCESS_CAPABILITY_NONE, edge.evaluateCapabilityFilter());
    }

    @Test
    public void testEvaluateMaxAdjPolicy_MaxAdjForeground_GrantsAllCapabilities() {
        TestNode node = new TestNode.Builder().withMaxAdj(FOREGROUND_APP_ADJ).build();
        ProcessEdge edge = new ProcessEdge(node);

        assertEquals(PROCESS_CAPABILITY_ALL, edge.evaluateCapabilityFilter());
    }

    private static class TestNode extends GraphNode {
        private final boolean mIsProcessRunning;
        private final int mMaxAdj;

        private TestNode(boolean isProcessRunning, int maxAdj) {
            super(mock(ProcessRecordInternal.class));
            mIsProcessRunning = isProcessRunning;
            mMaxAdj = maxAdj;
        }

        @Override
        boolean isProcessRunning() {
            return mIsProcessRunning;
        }

        @Override
        int getMaxAdj() {
            return mMaxAdj;
        }

        static class Builder {
            private boolean mIsProcessRunning = true;
            private int mMaxAdj = UNKNOWN_ADJ;

            Builder withProcessRunning(boolean isProcessRunning) {
                mIsProcessRunning = isProcessRunning;
                return this;
            }

            Builder withMaxAdj(int maxAdj) {
                mMaxAdj = maxAdj;
                return this;
            }

            TestNode build() {
                return new TestNode(mIsProcessRunning, mMaxAdj);
            }
        }
    }
}
