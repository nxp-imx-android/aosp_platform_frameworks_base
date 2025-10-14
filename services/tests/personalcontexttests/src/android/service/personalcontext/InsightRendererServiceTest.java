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

package android.service.personalcontext;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.service.personalcontext.insight.BundleInsight;
import android.service.personalcontext.insight.ContextInsight;
import android.service.personalcontext.insight.ContextInsightWrapper;
import android.service.personalcontext.renderer.IInsightRenderer;
import android.service.personalcontext.renderer.InsightRendererService;
import android.service.personalcontext.renderer.RendererFilter;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.UUID;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class InsightRendererServiceTest {
    private static class TestInsightRendererService extends InsightRendererService {
        /**
         * An interface implemented to be informed when the corresponding methods in
         * {@link TestInsightRendererService} are invoked.
         */
        interface Monitor {
            void onRegistered();
            void onRender(List<ContextInsight> insights, boolean isFirst);
        }

        private final Monitor mMonitor;

        TestInsightRendererService(Monitor monitor) {
            super();
            mMonitor = monitor;
        }

        @Override
        public RendererFilter onRegistered() {
            mMonitor.onRegistered();
            return new RendererFilter.Builder().build();
        }

        @Override
        public void onRender(List<ContextInsight> insights, boolean isFirst) {
            mMonitor.onRender(insights, isFirst);
        }
    }

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
    }


    @Test
    public void testOnBind() throws Exception {
        final TestInsightRendererService.Monitor monitor = Mockito.mock(
                TestInsightRendererService.Monitor.class);
        final TestInsightRendererService service = new TestInsightRendererService(monitor);

        final Intent serviceIntent = new Intent(InsightRendererService.SERVICE_INTERFACE);
        assertThat(service.onBind(serviceIntent)).isNotNull();

        Intent wrongIntent = new Intent("wrongIntent");
        assertThat(service.onBind(wrongIntent)).isNull();
    }

    @Test
    public void testOnRegisteredCalled() throws RemoteException {
        final TestInsightRendererService.Monitor monitor = Mockito.mock(
                TestInsightRendererService.Monitor.class);
        final TestInsightRendererService service = new TestInsightRendererService(monitor);
        final IBinder binder = service.onBind(new Intent(InsightRendererService.SERVICE_INTERFACE));
        final IInsightRenderer renderer = IInsightRenderer.Stub.asInterface(binder);
        renderer.onRegister(UUID.randomUUID().toString());
        verify(monitor).onRegistered();
    }

    @Test
    public void testOnRenderCalled() throws RemoteException {
        final TestInsightRendererService.Monitor monitor = Mockito.mock(
                TestInsightRendererService.Monitor.class);
        final TestInsightRendererService service = new TestInsightRendererService(monitor);
        final IBinder binder = service.onBind(new Intent(InsightRendererService.SERVICE_INTERFACE));
        final IInsightRenderer renderer = IInsightRenderer.Stub.asInterface(binder);

        final ContextInsight insight = new BundleInsight.Builder().build();
        final List<ContextInsight> insights = List.of(insight);
        final List<ContextInsightWrapper> wrappedInsights =
                List.of(new ContextInsightWrapper(insight));
        renderer.render(wrappedInsights, true);
        verify(monitor).onRender(eq(insights), eq(true));
    }

    @Test
    public void testMindRenderToken() throws RemoteException {
        final TestInsightRendererService.Monitor monitor = Mockito.mock(
                TestInsightRendererService.Monitor.class);
        final TestInsightRendererService service = new TestInsightRendererService(monitor);
        final IBinder binder = service.onBind(new Intent(InsightRendererService.SERVICE_INTERFACE));
        final IInsightRenderer renderer = IInsightRenderer.Stub.asInterface(binder);

        final UUID id = UUID.randomUUID();
        renderer.onRegister(id.toString());

        final RenderToken token = renderer.mintRenderToken();
        assertThat(token.getRendererComponentId()).isEqualTo(id);
    }
}
