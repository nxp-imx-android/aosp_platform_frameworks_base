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

package com.android.server.companion.virtual.computercontrol;

import static com.android.server.companion.virtual.computercontrol.ComputerControlSessionProcessor.MAXIMUM_CONCURRENT_SESSIONS;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.companion.virtual.IVirtualDevice;
import android.companion.virtual.computercontrol.ComputerControlSession;
import android.companion.virtual.computercontrol.ComputerControlSessionParams;
import android.companion.virtual.computercontrol.IComputerControlSession;
import android.companion.virtual.computercontrol.IComputerControlSessionCallback;
import android.content.AttributionSource;
import android.content.Context;
import android.os.Binder;
import android.view.Surface;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class ComputerControlSessionProcessorTest {

    @Mock
    private ComputerControlSessionProcessor.VirtualDeviceFactory mVirtualDeviceFactory;
    @Mock
    private IVirtualDevice mVirtualDevice;
    @Mock
    private IComputerControlSessionCallback mComputerControlSessionCallback;
    @Captor
    private ArgumentCaptor<IComputerControlSession> mSessionArgumentCaptor;

    private final ComputerControlSessionParams mParams = new ComputerControlSessionParams.Builder()
            .setName(ComputerControlSessionTest.class.getSimpleName())
            .setDisplayDpi(100)
            .setDisplayHeightPx(200)
            .setDisplayWidthPx(300)
            .setDisplaySurface(new Surface())
            .setDisplayAlwaysUnlocked(true)
            .build();

    private final Context mContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();
    private ComputerControlSessionProcessor mProcessor;

    private AutoCloseable mMockitoSession;

    @Before
    public void setUp() {
        mMockitoSession = MockitoAnnotations.openMocks(this);

        when(mVirtualDeviceFactory.createVirtualDevice(any(), any(), any(), any()))
                .thenReturn(mVirtualDevice);
        when(mComputerControlSessionCallback.asBinder()).thenReturn(new Binder());
        mProcessor = new ComputerControlSessionProcessor(mContext, mVirtualDeviceFactory);
    }

    @After
    public void tearDown() throws Exception {
        mMockitoSession.close();
    }

    @Test
    public void maximumNumberOfSessions_isEnforced() throws Exception {
        try {
            for (int i = 0; i < MAXIMUM_CONCURRENT_SESSIONS; ++i) {
                mProcessor.processNewSessionRequest(AttributionSource.myAttributionSource(),
                        mParams, mComputerControlSessionCallback);
            }
            verify(mComputerControlSessionCallback, times(MAXIMUM_CONCURRENT_SESSIONS))
                    .onSessionCreated(mSessionArgumentCaptor.capture());

            mProcessor.processNewSessionRequest(AttributionSource.myAttributionSource(),
                    mParams, mComputerControlSessionCallback);
            verify(mComputerControlSessionCallback)
                    .onSessionCreationFailed(ComputerControlSession.ERROR_SESSION_LIMIT_REACHED);

            mSessionArgumentCaptor.getAllValues().getFirst().close();

            mProcessor.processNewSessionRequest(AttributionSource.myAttributionSource(),
                    mParams, mComputerControlSessionCallback);
            verify(mComputerControlSessionCallback,
                    times(MAXIMUM_CONCURRENT_SESSIONS + 1))
                    .onSessionCreated(mSessionArgumentCaptor.capture());
        } finally {
            for (IComputerControlSession session : mSessionArgumentCaptor.getAllValues()) {
                session.close();
            }
        }
    }
}
