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

package android.service.personalcontext.embedded;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.service.personalcontext.insight.ContextInsight;
import android.view.Display;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.concurrent.Executor;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class InsightSurfaceVisualizerServiceTest {
    @Mock private InsightSurfaceClientInfo mClientInfo;
    @Mock private TestInsightSurfaceVisualizerService.Monitor mMonitor;
    @Mock private Context mContext;
    @Mock private Display mDisplay;

    private InsightSurfaceVisualizerService.Injector mInjector =
            new InsightSurfaceVisualizerService.Injector() {
                @Override
                public Context createDisplayContext() {
                    return mContext;
                }

                @Override
                public Display getDisplay() {
                    return mDisplay;
                }

                @Override
                public Executor createExecutor() {
                    return Runnable::run;
                }
            };

    private static class TestInsightSurfaceVisualizerService
            extends InsightSurfaceVisualizerService {
        /**
         * An interface implemented to be informed when the corresponding methods in
         * {@link TestInsightSurfaceVisualizerService} are invoked.
         */
        interface Monitor {
            void onClientConnected(InsightSurfaceClientInfo client);
            void onClientDisconnected(InsightSurfaceClientInfo client);
            void onCreateEmbeddedView(InsightSurfaceClientInfo client);
        }

        private final Monitor mMonitor;

        TestInsightSurfaceVisualizerService(Monitor monitor, Injector injector) {
            super(injector);
            mMonitor = monitor;
        }

        @Override
        public void onClientConnected(@NonNull InsightSurfaceClientInfo client) {
            mMonitor.onClientConnected(client);
        }

        @Override
        public void onClientDisconnected(@NonNull InsightSurfaceClientInfo client) {
            mMonitor.onClientDisconnected(client);
        }

        @Override
        public View onCreateEmbeddedView(
                @NonNull Context context,
                @NonNull List<ContextInsight> insights,
                @NonNull InsightSurfaceClientInfo client) {
            mMonitor.onCreateEmbeddedView(client);
            return null;
        }
    }

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
    }


    @Test
    public void testOnBind() {
        final TestInsightSurfaceVisualizerService service =
                new TestInsightSurfaceVisualizerService(mMonitor, mInjector);
        service.onCreate();

        final Intent serviceIntent = new Intent(InsightSurfaceVisualizerService.SERVICE_INTERFACE);
        assertThat(service.onBind(serviceIntent)).isNotNull();

        Intent wrongIntent = new Intent("wrongIntent");
        assertThat(service.onBind(wrongIntent)).isNull();
    }

    @Test
    public void testOnClientConnectedCalled() throws RemoteException {
        final IEmbeddedInsightSurfaceVisualizer visualizer = createVisualizer();
        visualizer.onClientConnected(mClientInfo);
        verify(mMonitor).onClientConnected(mClientInfo);
    }

    @Test
    public void testOnClientDisconnectedCalled() throws RemoteException {
        final IEmbeddedInsightSurfaceVisualizer visualizer = createVisualizer();
        visualizer.onClientDisconnected(mClientInfo);
        verify(mMonitor).onClientDisconnected(mClientInfo);
    }

    @Test
    public void testOnCreateEmbeddedView() throws RemoteException {
        final IEmbeddedInsightSurfaceVisualizer visualizer = createVisualizer();

        visualizer.createVisualizationForClient(List.of(), mClientInfo);
        verify(mMonitor).onCreateEmbeddedView(mClientInfo);
    }


    private IEmbeddedInsightSurfaceVisualizer createVisualizer() {
        final TestInsightSurfaceVisualizerService service =
                new TestInsightSurfaceVisualizerService(mMonitor, mInjector);
        service.onCreate();
        final IBinder binder = service.onBind(
                new Intent(InsightSurfaceVisualizerService.SERVICE_INTERFACE));
        return IEmbeddedInsightSurfaceVisualizer.Stub.asInterface(binder);
    }
}
