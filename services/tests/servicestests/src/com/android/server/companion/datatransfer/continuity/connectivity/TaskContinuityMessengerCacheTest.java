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

package com.android.server.companion.datatransfer.continuity.connectivity;

import static com.google.common.truth.Truth.assertThat;

import android.companion.CompanionDeviceManager;
import android.companion.ICompanionDeviceManager;
import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;
import android.content.pm.PackageManagerInternal;

import com.android.server.LocalServices;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.Executor;

@Presubmit
@RunWith(AndroidTestingRunner.class)
public class TaskContinuityMessengerCacheTest {

    @Mock
    private ICompanionDeviceManager mMockCompanionDeviceManagerService;
    @Mock
    private PackageManagerInternal mMockPackageManagerInternal;

    private final Executor mExecutor = Runnable::run;

    private TaskContinuityMessengerCache mTaskContinuityMessengerCache;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        LocalServices.addService(PackageManagerInternal.class, mMockPackageManagerInternal);
        CompanionDeviceManager companionDeviceManager = new CompanionDeviceManager(
                mMockCompanionDeviceManagerService, ApplicationProvider.getApplicationContext());
        mTaskContinuityMessengerCache =
                new TaskContinuityMessengerCache(companionDeviceManager, mExecutor);
    }

    @After
    public void tearDown() {
        LocalServices.removeServiceForTest(PackageManagerInternal.class);
    }

    @Test
    public void getOrCreateResource_returnsSameTaskContinuityMessengerForSameUser() {
        assertThat(mTaskContinuityMessengerCache.getOrCreateResource(1))
                .isSameInstanceAs(mTaskContinuityMessengerCache.getOrCreateResource(1));
    }

    @Test
    public void getOrCreateResource_returnsDifferentTaskContinuityMessengerForDifferentUsers() {
        assertThat(mTaskContinuityMessengerCache.getOrCreateResource(1))
                .isNotSameInstanceAs(mTaskContinuityMessengerCache.getOrCreateResource(2));
    }
}
