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

package com.android.server.job;

import static org.junit.Assert.assertEquals;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mock;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spy;
import static org.mockito.Mockito.when;

import android.app.job.JobInfo;
import android.content.ComponentName;
import com.android.server.job.controllers.JobStatus;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

public class JobStoreTest {

    private static final int TEST_UID = 1000;
    private static final int NON_MATCHING_SOURCE_UID = 2000;
    private JobStore.JobSet mJobSet;
    private MockitoSession mMockingSession;

    @Before
    public void setUp() {
        mMockingSession =
                mockitoSession().initMocks(this).strictness(Strictness.LENIENT).startMocking();
        mJobSet = new JobStore.JobSet();
    }

    private JobStatus createJobStatus(int jobId, int uid, int sourceUid, String batteryName) {
        final JobInfo jobInfo = new JobInfo.Builder(jobId,
                new ComponentName("foo", batteryName)).build();
        return JobStatus.createFromJobInfo(jobInfo, uid, "foo", sourceUid,
                null, batteryName);
    }

    @After
    public void tearDown() {
        if (mMockingSession != null) {
            mMockingSession.finishMocking();
        }
    }

    @Test
    public void testGetTopJobsDebugStringForUid_empty() {
        assertEquals("", mJobSet.getTopJobsDebugStringForUid(TEST_UID));
    }

    @Test
    public void testGetTopJobsDebugStringForUid_noMatchingSourceUid() {
        final JobStatus job = createJobStatus(1, TEST_UID, TEST_UID, "TestJobName1");
        final JobStatus spiedJob = spy(job);
        doReturn(NON_MATCHING_SOURCE_UID).when(spiedJob).getSourceUid();
        mJobSet.add(spiedJob);
        assertEquals("", mJobSet.getTopJobsDebugStringForUid(TEST_UID));
    }

    @Test
    public void testGetTopJobsDebugStringForUid_variousJobs() {
        final int uid = TEST_UID;
        int jobId = 0;

        // 6 jobs for TestJobName1
        for (int i = 0; i < 6; i++) {
            mJobSet.add(createJobStatus(jobId++, uid, uid, "TestJobName1"));
        }
        // 5 jobs for TestJobName2
        for (int i = 0; i < 5; i++) {
            mJobSet.add(createJobStatus(jobId++, uid, uid, "TestJobName2"));
        }
        // 4 jobs for TestJobName3
        for (int i = 0; i < 4; i++) {
            mJobSet.add(createJobStatus(jobId++, uid, uid, "TestJobName3"));
        }
        // 3 jobs for TestJobName4
        for (int i = 0; i < 3; i++) {
            mJobSet.add(createJobStatus(jobId++, uid, uid, "TestJobName4"));
        }
        // 2 jobs for TestJobName5
        for (int i = 0; i < 2; i++) {
            mJobSet.add(createJobStatus(jobId++, uid, uid, "TestJobName5"));
        }
        // 1 job for TestJobName6
        mJobSet.add(createJobStatus(jobId++, uid, uid, "TestJobName6"));

        // Add some noise that should be filtered out
        mJobSet.add(createJobStatus(jobId++, uid, uid + 1, "noise_job"));
        mJobSet.add(createJobStatus(jobId++, uid + 1, uid + 1, "noise_job2"));

        final String expected = "foo/TestJobName1:6,foo/TestJobName2:5,foo/TestJobName3:4,"
                + "foo/TestJobName4:3,foo/TestJobName5:2";
        assertEquals(expected, mJobSet.getTopJobsDebugStringForUid(uid));
    }

    @Test
    public void testGetTopJobsDebugStringForUid_lessThanFive() {
        final int uid = TEST_UID;
        int jobId = 0;

        // 3 jobs for TestJobName1
        for (int i = 0; i < 3; i++) {
            mJobSet.add(createJobStatus(jobId++, uid, uid, "TestJobName1"));
        }
        // 2 jobs for TestJobName2
        for (int i = 0; i < 2; i++) {
            mJobSet.add(createJobStatus(jobId++, uid, uid, "TestJobName2"));
        }

        final String expected = "foo/TestJobName1:3,foo/TestJobName2:2";
        assertEquals(expected, mJobSet.getTopJobsDebugStringForUid(uid));
    }

    @Test
    public void testGetTopJobsDebugStringForUid_oneJobName() {
        final int uid = TEST_UID;
        mJobSet.add(createJobStatus(1, uid, uid, "TestJobName1"));
        mJobSet.add(createJobStatus(2, uid, uid, "TestJobName1"));
        assertEquals("foo/TestJobName1:2", mJobSet.getTopJobsDebugStringForUid(uid));
    }
}
