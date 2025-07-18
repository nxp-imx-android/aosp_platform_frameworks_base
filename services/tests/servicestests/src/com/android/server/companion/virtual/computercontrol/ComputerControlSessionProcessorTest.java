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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;

import android.companion.virtual.IVirtualDevice;
import android.companion.virtual.computercontrol.ComputerControlSessionParams;
import android.companion.virtual.computercontrol.IComputerControlSession;
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
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;

@RunWith(AndroidJUnit4.class)
public class ComputerControlSessionProcessorTest {

    @Mock
    private ComputerControlSessionProcessor.VirtualDeviceFactory mVirtualDeviceFactory;
    @Mock
    private IVirtualDevice mVirtualDevice;

    private final ComputerControlSessionParams mParams = new ComputerControlSessionParams();
    private final Context mContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();
    private ComputerControlSessionProcessor mProcessor;

    private AutoCloseable mMockitoSession;

    @Before
    public void setUp() {
        mMockitoSession = MockitoAnnotations.openMocks(this);

        mParams.displayDpi = 100;
        mParams.displayHeightPx = 200;
        mParams.displayWidthPx = 300;
        mParams.displaySurface = new Surface();
        mParams.isDisplayAlwaysUnlocked = true;
        mParams.name = ComputerControlSessionTest.class.getSimpleName();

        when(mVirtualDeviceFactory.createVirtualDevice(any(), any(), any(), any()))
                .thenReturn(mVirtualDevice);
        mProcessor = new ComputerControlSessionProcessor(mContext, mVirtualDeviceFactory);
    }

    @After
    public void tearDown() throws Exception {
        mMockitoSession.close();
    }

    @Test
    public void maximumNumberOfSessions_isEnforced() throws Exception {
        ArrayList<IComputerControlSession> sessions = new ArrayList<>();

        try {
            for (int i = 0; i < ComputerControlSessionProcessor.MAXIMUM_CONCURRENT_SESSIONS; ++i) {
                sessions.add(mProcessor.processNewSession(
                        new Binder(), AttributionSource.myAttributionSource(), mParams));
            }

            assertThrows(UnsupportedOperationException.class, () -> mProcessor.processNewSession(
                            new Binder(), AttributionSource.myAttributionSource(), mParams));

            sessions.remove(0).close();

            sessions.add(mProcessor.processNewSession(
                    new Binder(), AttributionSource.myAttributionSource(), mParams));
        } finally {
            for (IComputerControlSession session : sessions) {
                session.close();
            }
        }
    }
}
