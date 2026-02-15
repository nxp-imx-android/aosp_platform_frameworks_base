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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.service.personalcontext.RenderToken;
import android.service.personalcontext.insight.BundleInsight;
import android.service.personalcontext.insight.PublishedContextInsight;
import android.service.personalcontext.insight.PublishedContextInsightWrapper;
import android.view.Display;
import android.view.SurfaceControlViewHost;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.UUID;
import java.util.concurrent.Executor;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class InsightSurfaceVisualizerServiceTest {
    @Mock private InsightSurfaceClientInfo mClientInfo;
    @Mock private TestInsightSurfaceVisualizerService.Monitor mMonitor;
    @Mock private IVisualizationResult mResult;
    @Mock private Context mContext;
    @Mock private Display mDisplay;
    private final PublishedContextInsightWrapper mInsight =
            new PublishedContextInsightWrapper(new PublishedContextInsight(
                    new BundleInsight.Builder().build(), UUID.randomUUID()));
    private final RenderToken mRenderToken = new RenderToken(UUID.randomUUID(), null);

    private final InsightSurfaceVisualizerService.Injector mInjector =
            new InsightSurfaceVisualizerService.Injector() {
                @Override
                public Context getDisplayContext() {
                    return mContext;
                }

                @Override
                public Display getDisplay() {
                    return mDisplay;
                }

                @Override
                public Executor getExecutor() {
                    return Runnable::run;
                }

                @Override
                public InsightSurfaceVisualizerService.SurfaceControlViewHostFactory
                        getSurfaceControlViewHostFactory() {
                    return (context, display, inputTransferToken) ->
                            mock(SurfaceControlViewHost.class);
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
        private final View mView;

        TestInsightSurfaceVisualizerService(Monitor monitor, Injector injector, View view) {
            super(injector);
            mMonitor = monitor;
            mView = view;
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
                @NonNull PublishedContextInsight insights,
                @NonNull RenderToken renderToken,
                @NonNull InsightSurfaceClientInfo client) {
            mMonitor.onCreateEmbeddedView(client);
            return mView;
        }
    }

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
    }


    @Test
    public void testOnBind() {
        final TestInsightSurfaceVisualizerService service =
                new TestInsightSurfaceVisualizerService(mMonitor, mInjector, null);
        service.onCreate();

        final Intent serviceIntent = new Intent(InsightSurfaceVisualizerService.SERVICE_INTERFACE);
        assertThat(service.onBind(serviceIntent)).isNotNull();

        Intent wrongIntent = new Intent("wrongIntent");
        assertThat(service.onBind(wrongIntent)).isNull();
    }

    @Test
    public void testOnClientConnectedCalled() throws RemoteException {
        final IInsightSurfaceVisualizer visualizer = createVisualizer(mock(View.class));
        visualizer.createVisualizationForClient(mInsight, mClientInfo, mRenderToken, mResult);
        verify(mMonitor).onClientConnected(mClientInfo);
    }

    @Test
    public void testOnClientDisconnectedCalled() throws RemoteException {
        final IInsightSurfaceVisualizer visualizer = createVisualizer(mock(View.class));
        visualizer.createVisualizationForClient(mInsight, mClientInfo, mRenderToken, mResult);
        visualizer.onClientDisconnected(mClientInfo);
        verify(mMonitor).onClientDisconnected(mClientInfo);
    }

    @Test
    public void testOnCreateEmbeddedView() throws RemoteException {
        final IInsightSurfaceVisualizer visualizer = createVisualizer();

        visualizer.createVisualizationForClient(mInsight, mClientInfo, mRenderToken, mResult);
        verify(mMonitor).onCreateEmbeddedView(mClientInfo);
    }

    @Test
    public void testCallbackOnResult_false() throws RemoteException {
        // By not passing a View to createVisualizer(), we cause it to call the callback with
        // "false" (because it has now View to return).
        final IInsightSurfaceVisualizer visualizer = createVisualizer();

        visualizer.createVisualizationForClient(mInsight, mClientInfo, mRenderToken, mResult);
        verify(mMonitor).onCreateEmbeddedView(mClientInfo);
        // Since the visualizer was created without a View to create, it will call the callback
        // with "false" to indicate that View creation failed.
        verify(mResult).onResult(false);
    }

    @Test
    public void testCallbackOnResult_true() throws RemoteException {
        // By passing a View to createVisualizer(), we cause it to call the callback with "true"
        // (because it has a View to return).
        final IInsightSurfaceVisualizer visualizer = createVisualizer(mock(View.class));

        visualizer.createVisualizationForClient(mInsight, mClientInfo, mRenderToken, mResult);
        verify(mMonitor).onCreateEmbeddedView(mClientInfo);
        // Since the visualizer was created with a View to create, it will call the callback
        // with "true" to indicate that View creation succeeded.
        verify(mResult).onResult(true);
    }

    private IInsightSurfaceVisualizer createVisualizer() {
        return createVisualizer(null);
    }

    private IInsightSurfaceVisualizer createVisualizer(View view) {
        final TestInsightSurfaceVisualizerService service =
                new TestInsightSurfaceVisualizerService(mMonitor, mInjector, view);
        service.onCreate();
        final IBinder binder = service.onBind(
                new Intent(InsightSurfaceVisualizerService.SERVICE_INTERFACE));
        return IInsightSurfaceVisualizer.Stub.asInterface(binder);
    }
}
