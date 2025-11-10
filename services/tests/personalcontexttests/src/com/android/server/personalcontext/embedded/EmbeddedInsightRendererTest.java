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

import android.content.res.Configuration;
import android.service.personalcontext.RenderToken;
import android.service.personalcontext.embedded.IEmbeddedInsightSurfaceCallback;
import android.service.personalcontext.embedded.InsightSurfaceClientInfo;
import android.window.InputTransferToken;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class EmbeddedInsightRendererTest {
    private EmbeddedInsightRenderer mEmbeddedInsightRenderer;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        mEmbeddedInsightRenderer = new EmbeddedInsightRenderer();
    }

    @Test
    public void testRegisterInsightSurfaceClient() {
        final InsightSurfaceClientInfo client = createClient();
        final RenderToken renderToken =
                mEmbeddedInsightRenderer.registerInsightSurfaceClient(client);
        assertThat(renderToken).isNotNull();
        assertThat(mEmbeddedInsightRenderer.getClients().size()).isEqualTo(1);
    }

    @Test
    public void testUnregisterInsightSurfaceClient() {
        final InsightSurfaceClientInfo client = createClient();
        mEmbeddedInsightRenderer.registerInsightSurfaceClient(client);
        assertThat(mEmbeddedInsightRenderer.getClients().size()).isEqualTo(1);
        mEmbeddedInsightRenderer.unregisterInsightSurfaceClient(client.getId());
        assertThat(mEmbeddedInsightRenderer.getClients().size()).isEqualTo(0);
    }

    private InsightSurfaceClientInfo createClient() {
        final IEmbeddedInsightSurfaceCallback callback =
                IEmbeddedInsightSurfaceCallback.Stub.asInterface(new android.os.Binder());
        return new InsightSurfaceClientInfo(
                new InputTransferToken(), 1, 2, 3, new Configuration(), callback);
    }
}
