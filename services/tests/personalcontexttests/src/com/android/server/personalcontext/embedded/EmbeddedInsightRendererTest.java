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


import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.service.personalcontext.RenderToken;
import android.service.personalcontext.embedded.IInsightSurfaceClient;
import android.service.personalcontext.embedded.InsightSurfaceClientInfo;
import android.service.personalcontext.insight.BundleInsight;
import android.view.View;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.UUID;

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
        mEmbeddedInsightRenderer.registerInsightSurfaceClient(client);
    }

    @Test
    public void testUnregisterInsightSurfaceClient() {
        final InsightSurfaceClientInfo client = createClient();
        when(mClientRegistry.removeClient(client.getId())).thenReturn(client);

        mEmbeddedInsightRenderer.registerInsightSurfaceClient(client);
        mEmbeddedInsightRenderer.unregisterInsightSurfaceClient(client.getId());
        verify(mClientRegistry).removeClient(client.getId());
    }

    @Test
    public void testRender() {
        final InsightSurfaceClientInfo client = createClient();
        final RenderToken renderToken = mEmbeddedInsightRenderer.mintRenderToken();
        when(mClientRegistry.isEmpty()).thenReturn(false);
        when(mClientRegistry.getClientForRenderToken(eq(renderToken))).thenReturn(client);
        when(mVisualizerRegistry.isEmpty()).thenReturn(false);

        final BundleInsight insight = new BundleInsight.Builder().build();
        mEmbeddedInsightRenderer.render(insight, renderToken);

        verify(mVisualizerRegistry).createVisualizationForClient(
                eq(insight), eq(client), eq(renderToken));
    }

    private InsightSurfaceClientInfo createClient() {
        final IInsightSurfaceClient client =
                IInsightSurfaceClient.Stub.asInterface(new android.os.Binder());
        return new InsightSurfaceClientInfo(
                UUID.randomUUID(),
                1,
                2,
                3,
                Color.valueOf(Color.RED),
                View.SCROLL_AXIS_NONE,
                false,
                false,
                Resources.ID_NULL,
                "package.name",
                new Configuration(),
                client);
    }
}
