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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.RemoteException;
import android.service.personalcontext.PersonalContextManager;
import android.service.personalcontext.hint.BundleHint;
import android.service.personalcontext.hint.ContextHint;
import android.view.Display;
import android.view.SurfaceControlViewHost.SurfacePackage;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class InsightSurfaceClientTest {
    @Mock private Context mContext;
    @Mock private Display mDisplay;
    @Mock private Resources mResources;
    @Mock private Configuration mConfiguration;
    @Mock private SurfacePackage mSurfacePackage;
    @Mock private PersonalContextManager mPersonalContextManager;
    @Mock private InsightSurfaceClient.ClientCallback mConnectionCallbacks;
    private final Executor mExecutor = Runnable::run;
    private final BundleHint mHint = new BundleHint.Builder().build();
    private InsightSurfaceClient mClient;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);

        when(mContext.getDisplay()).thenReturn(mDisplay);
        when(mContext.getResources()).thenReturn(mResources);
        when(mResources.getConfiguration()).thenReturn(mConfiguration);

        when(mContext.getSystemService(PersonalContextManager.class))
                .thenReturn(mPersonalContextManager);

        mClient =
                new InsightSurfaceClient.Builder(mContext, mExecutor, mConnectionCallbacks)
                        .addHint(mHint)
                        .build();
    }

    @Test
    public void testSurfaceCreated() throws RemoteException {
        mClient.register(0, 0);
        final ArgumentCaptor<InsightSurfaceClientInfo> clientInfoCaptor =
                ArgumentCaptor.forClass(InsightSurfaceClientInfo.class);
        verify(mPersonalContextManager).registerInsightSurfaceClient(
                clientInfoCaptor.capture(), eq(List.of(mHint)));

        final IEmbeddedInsightSurfaceCallback callback = clientInfoCaptor.getValue().getCallback();
        callback.onSurfaceCreated(mSurfacePackage);
        verify(mConnectionCallbacks).onSurfaceCreated(mSurfacePackage);
    }

    @Test
    public void testSurfaceReleased() throws RemoteException {
        mClient.register(0, 0);
        mClient.unregister();
        final ArgumentCaptor<InsightSurfaceClientInfo> clientInfoCaptor =
                ArgumentCaptor.forClass(InsightSurfaceClientInfo.class);
        verify(mPersonalContextManager).unregisterInsightSurfaceClient(clientInfoCaptor.capture());

        final IEmbeddedInsightSurfaceCallback callback = clientInfoCaptor.getValue().getCallback();
        callback.onSurfaceReleased(mSurfacePackage);
        verify(mConnectionCallbacks).onSurfaceReleased(mSurfacePackage);
    }

    @Test
    public void testInsightSurfaceClientCreation() {
        final InsightSurfaceClient client =
                new InsightSurfaceClient.Builder(mContext, mConnectionCallbacks).build();

        assertThat(client.getHints()).isEmpty();
        assertThat(client.getReceivers()).isEmpty();
    }

    @Test
    public void testInsightSurfaceClientCreation_withHint() {
        final BundleHint hint = new BundleHint();

        final InsightSurfaceClient client =
                new InsightSurfaceClient.Builder(mContext, mConnectionCallbacks)
                        .addHint(hint).build();

        assertThat(client.getHints()).containsExactly(hint);
    }

    @Test
    public void testInsightSurfaceClientCreation_withReceiver() {
        final InsightSurfaceClient.InsightReceiver receiver = insight -> true;

        final InsightSurfaceClient client =
                new InsightSurfaceClient.Builder(mContext, mConnectionCallbacks)
                        .addReceiver(receiver)
                        .build();

        assertThat(client.getReceivers()).containsExactly(receiver);
    }

    @Test
    public void testPublishHints() {
        final InsightSurfaceClient client =
                new InsightSurfaceClient.Builder(mContext, mConnectionCallbacks).build();
        final Set<ContextHint> hints = Set.of(mHint);

        client.register(0, 0);
        client.publishHints(hints);

        verify(mPersonalContextManager).publishInsightSurfaceHints(
                eq(hints), any(InsightSurfaceClientInfo.class));
    }
}
