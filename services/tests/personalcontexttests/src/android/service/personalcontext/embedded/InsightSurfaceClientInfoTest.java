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
import android.graphics.Color;
import android.os.RemoteException;
import android.service.personalcontext.insight.BundleInsight;
import android.view.SurfaceControlViewHost.SurfacePackage;
import android.view.View;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.UUID;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class InsightSurfaceClientInfoTest {
    @Mock private IInsightSurfaceClient mClient;
    @Mock private IInsightSurfaceSession mSession;
    @Mock private SurfacePackage mSurfacePackage;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testInsightSurfaceClientInfoCreation() {
        final UUID clientId = UUID.randomUUID();
        final int widthMeasureSpec = 1;
        final int heightMeasureSpec = 2;
        final int displayId = 3;
        final Color backgroundColor = Color.valueOf(Color.RED);
        final int nestedScrollingAxes = View.SCROLL_AXIS_HORIZONTAL | View.SCROLL_AXIS_VERTICAL;
        final boolean nestedScrollAxisLocked = true;
        final boolean shouldBlur = true;
        final String themeResourceName = "theme";
        final String packageName = "package.name";
        final Configuration configuration = new Configuration();

        final InsightSurfaceClientInfo clientInfo =
                new InsightSurfaceClientInfo(
                        clientId,
                        displayId,
                        widthMeasureSpec,
                        heightMeasureSpec,
                        backgroundColor,
                        nestedScrollingAxes,
                        nestedScrollAxisLocked,
                        shouldBlur,
                        themeResourceName,
                        packageName,
                        configuration,
                        mClient);

        assertThat(clientInfo.getId()).isEqualTo(clientId);
        assertThat(clientInfo.getMeasureSpecWidth()).isEqualTo(widthMeasureSpec);
        assertThat(clientInfo.getMeasureSpecHeight()).isEqualTo(heightMeasureSpec);
        assertThat(clientInfo.getDisplayId()).isEqualTo(displayId);
        assertThat(clientInfo.getConfiguration()).isEqualTo(configuration);
        assertThat(clientInfo.getBackgroundColor()).isEqualTo(backgroundColor);
        assertThat(clientInfo.getNestedScrollAxes()).isEqualTo(nestedScrollingAxes);
        assertThat(clientInfo.shouldBlur()).isEqualTo(shouldBlur);
        assertThat(clientInfo.getThemeResourceName()).isEqualTo(themeResourceName);
        assertThat(clientInfo.getPackageName()).isEqualTo(packageName);
        assertThat(clientInfo.getNestedScrollAxisLocked()).isEqualTo(nestedScrollAxisLocked);
    }

    @Test
    public void testOnSurfaceCreated() throws RemoteException {
        final InsightSurfaceClientInfo clientInfo =
                new InsightSurfaceClientInfo(
                        UUID.randomUUID(),
                        0,
                        0,
                        0,
                        Color.valueOf(Color.BLACK),
                        View.SCROLL_AXIS_NONE,
                        false,
                        false,
                        null,
                        "package.name",
                        new Configuration(),
                        mClient);
        clientInfo.onSurfaceCreated(mSurfacePackage, mSession);
        verify(mClient).onSurfaceCreated(mSurfacePackage, mSession);
    }

    @Test
    public void testOnReceiveInsight() throws RemoteException {
        final InsightSurfaceClientInfo clientInfo =
                new InsightSurfaceClientInfo(
                        UUID.randomUUID(),
                        0,
                        0,
                        0,
                        Color.valueOf(Color.BLACK),
                        View.SCROLL_AXIS_NONE,
                        false,
                        false,
                        null,
                        "package.name",
                        new Configuration(),
                        mClient);
        clientInfo.onReceiveInsight(new BundleInsight.Builder().build());
        verify(mClient).onReceiveInsight(any());
    }
}
