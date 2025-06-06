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

package com.android.server.security.authenticationpolicy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.os.IBinder;
import android.os.ICancellationSignal;
import android.os.RemoteException;
import android.platform.test.annotations.Presubmit;
import android.proximity.IProximityProviderService;
import android.proximity.IProximityResultCallback;
import android.testing.TestableContext;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.internal.R;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@Presubmit
@SmallTest
@RunWith(AndroidJUnit4.class)
public class WatchRangingServiceTest {

    private static final long AUTHENTICATION_REQUEST_ID = 1;

    private final TestableContext mTestableContext = new TestableContext(
            InstrumentationRegistry.getInstrumentation().getContext(), null);

    @Rule
    public MockitoRule mockito = MockitoJUnit.rule();

    @Mock
    private IProximityProviderService mProximityProviderService;
    @Mock
    private IProximityResultCallback mProximityResultCallback;
    @Mock
    private IBinder mBinder;
    @Mock
    private ICancellationSignal mCancellationSignal;

    private WatchRangingService mWatchRangingService;

    @Before
    public void setUp() throws Exception {
        final String proximityComponentName = " ";

        mTestableContext.getOrCreateTestableResources().addOverride(
                R.string.proximity_provider_service_package_name, proximityComponentName);
        mTestableContext.getOrCreateTestableResources().addOverride(R
                .string.proximity_provider_service_class_name, proximityComponentName);
        mTestableContext.addMockService(
                new ComponentName(proximityComponentName, proximityComponentName), mBinder);

        mWatchRangingService = new WatchRangingService(mTestableContext,
                (binder) -> mProximityProviderService);
    }

    @Test
    public void testStartWatchRanging() throws RemoteException {
        mWatchRangingService.startWatchRangingForIdentityCheck(AUTHENTICATION_REQUEST_ID,
                mProximityResultCallback);

        verify(mProximityProviderService).anyWatchNearby(any(), eq(mProximityResultCallback));
    }

    @Test
    public void testStartWatchRanging_thenCancelSuccessfully() throws RemoteException {
        when(mProximityProviderService.anyWatchNearby(any(), any())).thenReturn(
                mCancellationSignal);

        mWatchRangingService.startWatchRangingForIdentityCheck(AUTHENTICATION_REQUEST_ID,
                mProximityResultCallback);

        verify(mProximityProviderService).anyWatchNearby(any(), eq(mProximityResultCallback));

        mWatchRangingService.cancelWatchRangingForRequestId(AUTHENTICATION_REQUEST_ID);

        verify(mCancellationSignal).cancel();
    }

    @Test
    public void testStartWatchRanging_thenCancelUnsuccessfully() throws RemoteException {
        final int incorrectAuthenticationRequestId = 2;

        when(mProximityProviderService.anyWatchNearby(any(), any())).thenReturn(
                mCancellationSignal);

        mWatchRangingService.startWatchRangingForIdentityCheck(AUTHENTICATION_REQUEST_ID,
                mProximityResultCallback);

        verify(mProximityProviderService).anyWatchNearby(any(), eq(mProximityResultCallback));

        mWatchRangingService.cancelWatchRangingForRequestId(incorrectAuthenticationRequestId);

        verify(mCancellationSignal, never()).cancel();
    }
}
