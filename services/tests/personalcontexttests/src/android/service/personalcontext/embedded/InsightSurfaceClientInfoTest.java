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
import static org.mockito.Mockito.verify;

import android.content.res.Configuration;
import android.os.RemoteException;
import android.service.personalcontext.insight.BundleInsight;
import android.view.SurfaceControlViewHost.SurfacePackage;
import android.window.InputTransferToken;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class InsightSurfaceClientInfoTest {
    @Mock private IEmbeddedInsightSurfaceCallback mCallbacks;
    @Mock private SurfacePackage mSurfacePackage;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testInsightSurfaceClientInfoCreation() {
        final InputTransferToken token = new InputTransferToken();
        final int widthMeasureSpec = 1;
        final int heightMeasureSpec = 2;
        final int displayId = 3;
        final Configuration configuration = new Configuration();

        final InsightSurfaceClientInfo clientInfo =
                new InsightSurfaceClientInfo(
                        token,
                        displayId,
                        widthMeasureSpec,
                        heightMeasureSpec,
                        configuration,
                        mCallbacks);

        assertThat(clientInfo.getInputTransferToken()).isEqualTo(token);
        assertThat(clientInfo.getWidthMeasureSpec()).isEqualTo(widthMeasureSpec);
        assertThat(clientInfo.getHeightMeasureSpec()).isEqualTo(heightMeasureSpec);
        assertThat(clientInfo.getDisplayId()).isEqualTo(displayId);
        assertThat(clientInfo.getConfiguration()).isEqualTo(configuration);
    }

    @Test
    public void testOnSurfaceCreated() throws RemoteException {
        final InsightSurfaceClientInfo clientInfo =
                new InsightSurfaceClientInfo(null, 0, 0, 0, new Configuration(), mCallbacks);
        clientInfo.onSurfaceCreated(mSurfacePackage);
        verify(mCallbacks).onSurfaceCreated(mSurfacePackage);
    }

    @Test
    public void testOnReceiveInsight() throws RemoteException {
        final InsightSurfaceClientInfo clientInfo =
                new InsightSurfaceClientInfo(null, 0, 0, 0, new Configuration(), mCallbacks);
        clientInfo.onReceiveInsight(new BundleInsight.Builder().build());
        verify(mCallbacks).onReceiveInsight(any());
    }
}
