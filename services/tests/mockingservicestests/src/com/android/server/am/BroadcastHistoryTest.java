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

package com.android.server.am;

import static com.google.common.truth.Truth.assertThat;

import android.os.SystemClock;
import android.util.Pair;

import androidx.test.filters.SmallTest;

import com.android.server.am.BaseBroadcastQueueTest.BroadcastRecordBuilder;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;

/**
 * Tests for {@link BroadcastHistory}.
 */
@SmallTest
@RunWith(MockitoJUnitRunner.class)
public class BroadcastHistoryTest {
    private static final int TEST_UID1 = 1001;
    private static final int TEST_UID2 = 1002;
    private static final int TEST_UID3 = 1003;

    private static final String TEST_PKG1 = "com.example.one";
    private static final String TEST_PKG2 = "com.example.two";

    private static final String TEST_ACTION = "com.example.action";
    private static final String TEST_ACTION1 = "com.example.action_1";
    private static final String TEST_ACTION2 = "com.example.action_2";

    private BroadcastHistory mBroadcastHistory;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        final BroadcastConstants constants = new BroadcastConstants("bcast_constants");
        mBroadcastHistory = new BroadcastHistory(constants);
    }

    private BroadcastRecord createBroadcastRecord(int callingUid, String callerPackage,
            String action, long enqueueTime) {
        final BroadcastRecord br = new BroadcastRecordBuilder()
                .setIntentAction(action)
                .setCallingUid(callingUid)
                .setCallerPackage(callerPackage)
                .build();
        br.enqueueTime = enqueueTime;
        return br;
    }

    @Test
    public void testGetPendingBroadcastCountForUid() {
        mBroadcastHistory.onBroadcastEnqueuedLocked(
                createBroadcastRecord(TEST_UID1, TEST_PKG1, TEST_ACTION1, 1000));
        mBroadcastHistory.onBroadcastEnqueuedLocked(
                createBroadcastRecord(TEST_UID1, TEST_PKG1, TEST_ACTION2, 2000));
        mBroadcastHistory.onBroadcastEnqueuedLocked(
                createBroadcastRecord(TEST_UID2, TEST_PKG2, TEST_ACTION1, 3000));

        assertThat(mBroadcastHistory.getPendingBroadcastCountForSenderUid(TEST_UID1)).isEqualTo(2);
        assertThat(mBroadcastHistory.getPendingBroadcastCountForSenderUid(TEST_UID2)).isEqualTo(1);
        assertThat(mBroadcastHistory.getPendingBroadcastCountForSenderUid(TEST_UID3)).isEqualTo(0);
    }

    @Test
    public void testGetOldestPendingBroadcastEnqueueTime() {
        mBroadcastHistory.onBroadcastEnqueuedLocked(
                createBroadcastRecord(TEST_UID1, TEST_PKG1, TEST_ACTION1, 2000));
        mBroadcastHistory.onBroadcastEnqueuedLocked(
                createBroadcastRecord(TEST_UID1, TEST_PKG1, TEST_ACTION2, 1000));
        mBroadcastHistory.onBroadcastEnqueuedLocked(
                createBroadcastRecord(TEST_UID2, TEST_PKG2, TEST_ACTION1, 3000));

        assertThat(mBroadcastHistory.getOldestPendingBroadcastEnqueueTime(TEST_UID1))
                .isEqualTo(1000);
        assertThat(mBroadcastHistory.getOldestPendingBroadcastEnqueueTime(TEST_UID2))
                .isEqualTo(3000);
        assertThat(mBroadcastHistory.getOldestPendingBroadcastEnqueueTime(TEST_UID3))
                .isEqualTo(Long.MAX_VALUE);
    }

    @Test
    public void testAppendPendingBroadcastsSummaryForUid() {
        final int intentActionCounts = 10;
        final ArrayList<Pair<String, Integer>> broadcastCounts = new ArrayList();
        for (int i = 1; i <= intentActionCounts; i++) {
            broadcastCounts.add(Pair.create(TEST_ACTION + "_" + i, i));
        }

        for (int i = 0; i < broadcastCounts.size(); i++) {
            final String action = broadcastCounts.get(i).first;
            final int count = broadcastCounts.get(i).second;
            for (int j = 0; j < count; ++j) {
                mBroadcastHistory.onBroadcastEnqueuedLocked(createBroadcastRecord(
                        TEST_UID1, TEST_PKG1, action, SystemClock.uptimeMillis()));
            }
        }

        final StringBuilder sb = new StringBuilder();
        mBroadcastHistory.appendPendingBroadcastsSummaryForUid(sb, TEST_UID1);
        final String summary = sb.toString();
        final int broadcastCountsSize = broadcastCounts.size();
        for (int i = 0; i < BroadcastHistory.TOP_N_INTENTS_TO_DUMP; i++) {
            // Since the actions with the highest count will be dumped, query the expected action
            // and the corresponding count from the end of the list.
            final String expectedAction = broadcastCounts.get(broadcastCountsSize - 1 - i).first;
            final int expectedCount = broadcastCounts.get(broadcastCountsSize - 1 - i).second;
            assertThat(summary).contains(expectedAction + ": " + expectedCount);
        }
    }
}
