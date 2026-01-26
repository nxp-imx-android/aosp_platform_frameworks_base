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

package com.android.server.personalcontext.embedded;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.service.personalcontext.embedded.IInsightSurfaceVisualizer;
import android.service.personalcontext.embedded.InsightSurfaceClientInfo;
import android.service.personalcontext.embedded.InsightSurfaceVisualizerService;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class VisualizerConnectionTest {
    @Mock
    private ComponentName mComponentName;
    @Mock
    private IInsightSurfaceVisualizer mVisualizer;
    @Mock
    private IBinder mBinder;
    @Mock
    private Context mContext;

    private final TestInjector mTestInjector = new TestInjector();
    private VisualizerConnection mVisualizerConnection;


    private class TestInjector implements VisualizerConnection.Injector {
        private ServiceConnection mServiceConnection;

        public ServiceConnection getServiceConnection() {
            return mServiceConnection;
        }

        @Override
        public boolean connectToService(Intent intent, ServiceConnection serviceConnection) {
            mServiceConnection = serviceConnection;
            serviceConnection.onServiceConnected(mComponentName, mBinder);
            return true;
        }

        @Override
        public void disconnectFromService(ServiceConnection serviceConnection) {
            mServiceConnection = null;
        }

        @Override
        public void executeAction(Runnable action) {
            action.run();
        }
    }

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        mVisualizerConnection = new VisualizerConnection(mComponentName, mTestInjector);
        when(IInsightSurfaceVisualizer.Stub.asInterface(mBinder)).thenReturn(mVisualizer);
    }

    @Test
    public void testGetComponentName() {
        assertThat(mVisualizerConnection.getComponentName()).isEqualTo(mComponentName);
    }

    @Test
    public void testOnRegistered() {
        mVisualizerConnection.onRegistered();
        assertThat(mTestInjector.getServiceConnection()).isNotNull();
    }

    @Test
    public void testOnUnregistered() {
        mVisualizerConnection.onRegistered();
        assertThat(mTestInjector.getServiceConnection()).isNotNull();
        mVisualizerConnection.onUnregistered();
        assertThat(mTestInjector.getServiceConnection()).isNull();
    }

    @Test
    public void testOnClientDisconnected() throws RemoteException {
        mVisualizerConnection.onRegistered();
        final InsightSurfaceClientInfo client = mock(InsightSurfaceClientInfo.class);
        mVisualizerConnection.onClientDisconnected(client);
        verify(mVisualizer).onClientDisconnected(client);
    }

    @Test
    public void testCreateVisualizationForClient() throws RemoteException {
        mVisualizerConnection.onRegistered();
        final InsightSurfaceClientInfo client = mock(InsightSurfaceClientInfo.class);
        mVisualizerConnection.createVisualizationForClient(List.of(), client, (success) -> {});
        verify(mVisualizer).createVisualizationForClient(any(), any(), any());
    }

    @Test
    public void onRegistered_withDefaultInjector_usesCorrectBindFlags() {
        // Use the constructor that creates a DefaultInjector to test its behavior.
        VisualizerConnection connection = new VisualizerConnection(
                mComponentName, mContext, Runnable::run);

        // Trigger the service binding.
        connection.onRegistered();

        // Capture the arguments passed to context.bindService to verify the flags.
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        ArgumentCaptor<Integer> flagsCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mContext).bindService(
                intentCaptor.capture(), any(ServiceConnection.class), flagsCaptor.capture());

        // Verify that the BIND_ALLOW_ACTIVITY_STARTS flag is included.
        int expectedFlags = Context.BIND_AUTO_CREATE | Context.BIND_ALLOW_ACTIVITY_STARTS;
        assertThat(flagsCaptor.getValue()).isEqualTo(expectedFlags);

        // Also verify the intent is constructed correctly.
        Intent capturedIntent = intentCaptor.getValue();
        assertThat(capturedIntent.getComponent()).isEqualTo(mComponentName);
        assertThat(capturedIntent.getAction())
                .isEqualTo(InsightSurfaceVisualizerService.SERVICE_INTERFACE);
    }
}
