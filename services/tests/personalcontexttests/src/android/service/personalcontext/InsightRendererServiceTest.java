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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.os.ParcelUuid;
import android.os.RemoteException;
import android.service.personalcontext.insight.BundleInsight;
import android.service.personalcontext.insight.ContextInsight;
import android.service.personalcontext.insight.ContextInsightWrapper;
import android.service.personalcontext.renderer.IInsightRenderer;
import android.service.personalcontext.renderer.InsightRendererService;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;

import java.util.UUID;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class InsightRendererServiceTest {
    private UUID mComponentId;
    private InsightRendererService mService;
    private IInsightRenderer mBinder;

    @Before
    public void setup() throws RemoteException {
        mComponentId = UUID.randomUUID();
        mService = mock(InsightRendererService.class, Answers.CALLS_REAL_METHODS);
        mBinder = (IInsightRenderer) mService.onBind(null);
        mBinder.configure(new ParcelUuid(mComponentId));
    }

    @Test
    public void testOnRegisteredCalled() {
        verify(mService).onConnected();
    }

    @Test
    public void testOnRenderCalledWithoutRenderToken() throws RemoteException {
        final ContextInsight insight = new BundleInsight.Builder().build();
        mBinder.render(new ContextInsightWrapper(insight), null);

        verify(mService).onRender(eq(insight), isNull());
    }

    @Test
    public void testOnRenderCalledWithRenderToken() throws RemoteException {
        final ContextInsight insight = new BundleInsight.Builder().build();
        final RenderToken renderToken = mService.mintRenderToken();

        mBinder.render(new ContextInsightWrapper(insight), renderToken);

        verify(mService).onRender(eq(insight), eq(renderToken));
    }

    @Test
    public void testMintRenderToken() {
        RenderToken renderToken = mService.mintRenderToken();
        assertThat(renderToken.getRendererComponentId()).isEqualTo(mComponentId);
    }
}
