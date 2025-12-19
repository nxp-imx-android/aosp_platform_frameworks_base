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

package com.android.server.personalcontext.embedded;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.res.Configuration;
import android.service.personalcontext.RenderToken;
import android.service.personalcontext.embedded.IEmbeddedInsightSurfaceCallback;
import android.service.personalcontext.embedded.InsightSurfaceClientInfo;
import android.service.personalcontext.insight.BundleInsight;
import android.window.InputTransferToken;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.atomic.AtomicReference;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class EmbeddedInsightRendererTest {
    @Mock
    private ClientRegistry mClientRegistry;
    @Mock
    private VisualizerRegistry mVisualizerRegistry;
    private EmbeddedInsightRenderer mEmbeddedInsightRenderer;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        mEmbeddedInsightRenderer =
                new EmbeddedInsightRenderer(mClientRegistry, mVisualizerRegistry, Runnable::run);
    }

    @Test
    public void testRegisterInsightSurfaceClient() {
        final InsightSurfaceClientInfo client = createClient();
        AtomicReference<RenderToken> renderToken = new AtomicReference<>();
        mEmbeddedInsightRenderer.registerInsightSurfaceClient(client, renderToken::set);
        assertThat(renderToken).isNotNull();
        verify(mClientRegistry).addClient(client, renderToken.get());
        assertThat(renderToken.get()).isNotNull();
    }

    @Test
    public void testUnregisterInsightSurfaceClient() {
        final InsightSurfaceClientInfo client = createClient();
        when(mClientRegistry.removeClient(client.getId())).thenReturn(client);

        AtomicReference<RenderToken> renderToken = new AtomicReference<>();
        mEmbeddedInsightRenderer.registerInsightSurfaceClient(client, renderToken::set);
        mEmbeddedInsightRenderer.unregisterInsightSurfaceClient(client.getId());
        verify(mClientRegistry).removeClient(client.getId());
    }

    @Test
    public void testRender() {
        final InsightSurfaceClientInfo client = createClient();
        when(mClientRegistry.isEmpty()).thenReturn(false);
        when(mClientRegistry.getClientForRenderToken(any())).thenReturn(client);
        when(mVisualizerRegistry.isEmpty()).thenReturn(false);

        final BundleInsight insight = new BundleInsight.Builder().build();
        mEmbeddedInsightRenderer.render(insight);

        verify(mVisualizerRegistry).createVisualizationForClient(
                argThat(insights -> insights.contains(insight)), eq(client));
    }

    private InsightSurfaceClientInfo createClient() {
        final IEmbeddedInsightSurfaceCallback callback =
                IEmbeddedInsightSurfaceCallback.Stub.asInterface(new android.os.Binder());
        return new InsightSurfaceClientInfo(
                new InputTransferToken(), 1, 2, 3, new Configuration(), callback);
    }
}
