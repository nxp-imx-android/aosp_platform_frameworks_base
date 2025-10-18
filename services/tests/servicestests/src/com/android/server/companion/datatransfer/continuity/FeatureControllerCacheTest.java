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

package com.android.server.companion.datatransfer.continuity;

import static com.google.common.truth.Truth.assertThat;

import android.testing.AndroidTestingRunner;
import android.platform.test.annotations.Presubmit;
import com.android.server.companion.datatransfer.continuity.connectivity.TaskContinuityMessenger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@Presubmit
@RunWith(AndroidTestingRunner.class)
public class FeatureControllerCacheTest {

    @Mock private TaskContinuityMessenger mMockTaskContinuityMessenger;

    private class FakeFeatureControllerCache extends FeatureControllerCache<FakeFeatureController> {

        public int mCreateFeatureControllerForUserCallCount = 0;

        @Override
        protected FakeFeatureController createFeatureControllerForUser(int userId) {
            mCreateFeatureControllerForUserCallCount++;
            return new FakeFeatureController(userId, mMockTaskContinuityMessenger);
        }
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testGetOrCreateFeatureController_onlyCreatesOneFeatureControllerPerUser() {
        int userId = 1;
        FakeFeatureControllerCache cache = new FakeFeatureControllerCache();
        FakeFeatureController featureController1 = cache.getOrCreateFeatureController(userId);
        FakeFeatureController featureController2 = cache.getOrCreateFeatureController(userId);
        assertThat(featureController1).isSameInstanceAs(featureController2);
        assertThat(cache.mCreateFeatureControllerForUserCallCount).isEqualTo(1);
    }

    @Test
    public void testGetOrCreateFeatureController_createsFeatureControllerForDifferentUsers() {
        int userId1 = 1;
        int userId2 = 2;
        FakeFeatureControllerCache cache = new FakeFeatureControllerCache();
        FakeFeatureController featureController1 = cache.getOrCreateFeatureController(userId1);
        FakeFeatureController featureController2 = cache.getOrCreateFeatureController(userId2);
        assertThat(featureController1).isNotSameInstanceAs(featureController2);
        assertThat(cache.mCreateFeatureControllerForUserCallCount).isEqualTo(2);
    }
}
