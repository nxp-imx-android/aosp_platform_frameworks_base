/*
 * Copyright (C) 2024 The Android Open Source Project
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

package androidx.window.extensions.layout;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.app.WindowConfiguration;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Rect;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.SetFlagsRule;
import android.view.Display;
import android.view.DisplayInfo;
import android.view.Surface;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.window.common.DeviceStateManagerFoldingFeatureProducer;
import androidx.window.common.layout.CommonFoldingFeature;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Test class for {@link WindowLayoutComponentImpl}.
 *
 * Build/Install/Run:
 *  atest WMJetpackUnitTests:WindowLayoutComponentImplTest
 */
@Presubmit
@SmallTest
@RunWith(AndroidJUnit4.class)
public class WindowLayoutComponentImplTest {

    @Rule
    public final SetFlagsRule setFlagsRule = new SetFlagsRule();

    private final Context mAppContext = ApplicationProvider.getApplicationContext();

    @NonNull
    private WindowLayoutComponentImpl mWindowLayoutComponent;

    @Mock
    private DeviceStateManagerFoldingFeatureProducer mMockFoldingFeatureProducer;
    @Mock
    private WindowLayoutComponentImpl.DisplayStateProvider mMockDisplayStateProvider;
    @Mock
    private WindowManager mMockWindowManager;
    @Mock
    private Activity mMockActivity;


    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mMockActivity.isUiContext()).thenReturn(true);
        when(mMockActivity.getAssociatedDisplayId()).thenReturn(Display.DEFAULT_DISPLAY);
        when(mMockActivity.getResources()).thenReturn(mAppContext.getResources());
        when(mMockActivity.getApplicationContext()).thenReturn(mAppContext);
    }

    @Test
    public void testOnDisplayFeaturesChanged_withListener_doesNotCrash() {
        mWindowLayoutComponent = new WindowLayoutComponentImpl(
                mAppContext, mMockFoldingFeatureProducer, mMockDisplayStateProvider);
        final Context testUiContext = new TestUiContext(mAppContext);

        mWindowLayoutComponent.addWindowLayoutInfoListener(testUiContext, info -> {});

        mWindowLayoutComponent.onDisplayFeaturesChanged(Collections.emptyList());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddWindowLayoutListener_nonUiContext_throwsError() {
        mWindowLayoutComponent = new WindowLayoutComponentImpl(
                mAppContext, mMockFoldingFeatureProducer, mMockDisplayStateProvider);
        mWindowLayoutComponent.addWindowLayoutInfoListener(mAppContext, info -> {});
    }

    @Test
    public void testAddWindowLayoutListener_activityConsumer_receivesUpdates() {
        mWindowLayoutComponent = new WindowLayoutComponentImpl(
                mAppContext, mMockFoldingFeatureProducer, mMockDisplayStateProvider);

        WindowLayoutInfo[] receivedInfo = new WindowLayoutInfo[1];
        java.util.function.Consumer<WindowLayoutInfo> consumer = (info) -> receivedInfo[0] = info;

        mWindowLayoutComponent.addWindowLayoutInfoListener(mMockActivity, consumer);

        // Trigger an update
        mWindowLayoutComponent.onDisplayFeaturesChanged(Collections.emptyList());

        assertThat(receivedInfo[0]).isNotNull();
        assertThat(receivedInfo[0].getDisplayFeatures()).isEmpty();

        // Now remove the listener and check it doesn't get updates.
        receivedInfo[0] = null;
        mWindowLayoutComponent.removeWindowLayoutInfoListener(consumer);
        mWindowLayoutComponent.onDisplayFeaturesChanged(Collections.emptyList());
        assertThat(receivedInfo[0]).isNull();
    }

    @Test
    public void testGetCurrentWindowLayoutInfo_noFoldingFeature_returnsEmptyList() {
        mWindowLayoutComponent = new WindowLayoutComponentImpl(
                mAppContext, mMockFoldingFeatureProducer, mMockDisplayStateProvider);
        final Context testUiContext = new TestUiContext(mAppContext);

        final WindowLayoutInfo layoutInfo =
                mWindowLayoutComponent.getCurrentWindowLayoutInfo(testUiContext);

        assertThat(layoutInfo.getDisplayFeatures()).isEmpty();
    }

    @Test
    public void testGetCurrentWindowLayoutInfo_hasFoldingFeature_returnsWindowLayoutInfo() {
        final Context testUiContext = new TestUiContext(mAppContext);
        final WindowConfiguration windowConfiguration =
                testUiContext.getResources().getConfiguration().windowConfiguration;
        final Rect featureRect = windowConfiguration.getBounds();
        // Mock DisplayStateProvider to control rotation and DisplayInfo, preventing dependency on
        // the real device orientation or display configuration. This improves test reliability on
        // devices like foldables or tablets that might have varying configurations.
        final WindowLayoutComponentImpl.DisplayStateProvider displayStateProvider =
                new WindowLayoutComponentImpl.DisplayStateProvider() {
                    @Override
                    public int getDisplayRotation(
                            @NonNull WindowConfiguration windowConfiguration) {
                        return Surface.ROTATION_0;
                    }

                    @NonNull
                    @Override
                    public DisplayInfo getDisplayInfo(int displayId) {
                        final DisplayInfo displayInfo = new DisplayInfo();
                        displayInfo.logicalWidth = featureRect.width();
                        displayInfo.logicalHeight = featureRect.height();
                        return displayInfo;
                    }
                };
        mWindowLayoutComponent = new WindowLayoutComponentImpl(
                mAppContext,
                mMockFoldingFeatureProducer,
                displayStateProvider
        );
        final CommonFoldingFeature foldingFeature = new CommonFoldingFeature(
                CommonFoldingFeature.COMMON_TYPE_HINGE,
                CommonFoldingFeature.COMMON_STATE_FLAT,
                featureRect
        );
        mWindowLayoutComponent.onDisplayFeaturesChanged(List.of(foldingFeature));

        final WindowLayoutInfo layoutInfo =
                mWindowLayoutComponent.getCurrentWindowLayoutInfo(testUiContext);

        assertThat(layoutInfo.getDisplayFeatures()).containsExactly(new FoldingFeature(
                featureRect, FoldingFeature.TYPE_HINGE, FoldingFeature.STATE_FLAT));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetCurrentWindowLayoutInfo_nonUiContext_throwsError() {
        mWindowLayoutComponent = new WindowLayoutComponentImpl(
                mAppContext, mMockFoldingFeatureProducer, mMockDisplayStateProvider);
        mWindowLayoutComponent.getCurrentWindowLayoutInfo(mAppContext);
    }

    @Test
    @DisableFlags(com.android.window.flags.Flags.FLAG_DEVICE_ENGAGEMENT_MODE)
    public void testGetCurrentWindowLayoutInfo_engagementModeDisabled_returnsDefaultMode() {
        mWindowLayoutComponent = new WindowLayoutComponentImpl(
                mAppContext, mMockFoldingFeatureProducer, mMockDisplayStateProvider);
        final Context testUiContext = new TestUiContext(mAppContext);
        final WindowLayoutInfo layoutInfo =
                mWindowLayoutComponent.getCurrentWindowLayoutInfo(testUiContext);

        assertThat(layoutInfo.getEngagementModeFlags()).isEqualTo(
                EngagementModeClient.DEFAULT_ENGAGEMENT_MODE);
    }

    @Test
    @EnableFlags(com.android.window.flags.Flags.FLAG_DEVICE_ENGAGEMENT_MODE)
    public void testGetCurrentWindowLayoutInfo_systemApiEnabled_returnsDefaultMode() {
        Context context = getMockContext();
        mWindowLayoutComponent = new WindowLayoutComponentImpl(
                context,
                mMockFoldingFeatureProducer,
                mMockDisplayStateProvider
        );
        final Context testUiContext = new TestUiContext(context, mMockWindowManager);
        final WindowLayoutInfo layoutInfo =
                mWindowLayoutComponent.getCurrentWindowLayoutInfo(testUiContext);

        assertThat(layoutInfo.getEngagementModeFlags()).isEqualTo(
                EngagementModeClient.DEFAULT_ENGAGEMENT_MODE);
    }

    @Test
    @EnableFlags(com.android.window.flags.Flags.FLAG_DEVICE_ENGAGEMENT_MODE)
    public void testAddWindowLayoutListener_systemApiAvailable_registersCallback() {
        Context context = getMockContext();
        mWindowLayoutComponent = new WindowLayoutComponentImpl(
                context,
                mMockFoldingFeatureProducer,
                mMockDisplayStateProvider
        );
        final Context testUiContext = new TestUiContext(context, mMockWindowManager);
        mWindowLayoutComponent.addWindowLayoutInfoListener(testUiContext, info -> {});

        verify(mMockWindowManager).registerDisplayEngagementModeCallback(
                any(Executor.class), any(Consumer.class));
    }



    @Test
    @EnableFlags(com.android.window.flags.Flags.FLAG_DEVICE_ENGAGEMENT_MODE)
    public void testOnEngagementModeChanged_systemApiAvailable_updatesLayoutInfo() {
        Context context = getMockContext();
        mWindowLayoutComponent = new WindowLayoutComponentImpl(
                context,
                mMockFoldingFeatureProducer,
                mMockDisplayStateProvider
        );
        final Context testUiContext = new TestUiContext(context, mMockWindowManager);
        final ArgumentCaptor<WindowLayoutInfo> layoutInfoCaptor =
                ArgumentCaptor.forClass(WindowLayoutInfo.class);
        final androidx.window.extensions.core.util.function.Consumer<WindowLayoutInfo> consumer =
                mock(androidx.window.extensions.core.util.function.Consumer.class);
        mWindowLayoutComponent.addWindowLayoutInfoListener(testUiContext, consumer);

        final ArgumentCaptor<Consumer> callbackCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(mMockWindowManager).registerDisplayEngagementModeCallback(
                any(Executor.class), callbackCaptor.capture());
        final Consumer callback = callbackCaptor.getValue();
        final int expectedMode = WindowLayoutInfo.ENGAGEMENT_MODE_FLAG_VISUALS_ON;
        final int displayId = testUiContext.getAssociatedDisplayId();

        final WindowManager.DisplayEngagementModeState mockEngagementModeState =
                mock(WindowManager.DisplayEngagementModeState.class);
        when(mockEngagementModeState.getDisplayId()).thenReturn(displayId);
        when(mockEngagementModeState.getEngagementModeFlags()).thenReturn(expectedMode);

        callback.accept(mockEngagementModeState);

        verify(consumer, atLeastOnce()).accept(layoutInfoCaptor.capture());
        final WindowLayoutInfo lastLayoutInfo =
                layoutInfoCaptor.getValue();
        assertThat(lastLayoutInfo.getEngagementModeFlags()).isEqualTo(expectedMode);
    }

    @Test
    @EnableFlags(com.android.window.flags.Flags.FLAG_DEVICE_ENGAGEMENT_MODE)
    public void testRemoveWindowLayoutListener_systemApiAvailable_unregistersCallback() {
        Context context = getMockContext();
        mWindowLayoutComponent = new WindowLayoutComponentImpl(
                context,
                mMockFoldingFeatureProducer,
                mMockDisplayStateProvider
        );
        final Context testUiContext = new TestUiContext(context, mMockWindowManager);
        final androidx.window.extensions.core.util.function.Consumer<WindowLayoutInfo> consumer =
                mock(androidx.window.extensions.core.util.function.Consumer.class);
        mWindowLayoutComponent.addWindowLayoutInfoListener(testUiContext, consumer);
        final ArgumentCaptor<Consumer> callbackCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(mMockWindowManager).registerDisplayEngagementModeCallback(
                any(Executor.class), callbackCaptor.capture());
        final Consumer callback = callbackCaptor.getValue();

        mWindowLayoutComponent.removeWindowLayoutInfoListener(consumer);

        verify(mMockWindowManager).unregisterDisplayEngagementModeCallback(callback);
    }



    private Context getMockContext() {
        return new ContextWrapper(mAppContext) {
            @Override
            public Object getSystemService(String name) {
                if (Context.WINDOW_SERVICE.equals(name)) {
                    return mMockWindowManager;
                }
                return super.getSystemService(name);
            }
        };
    }

    /**
     * A {@link Context} that simulates a UI context specifically for testing purposes.
     * This class overrides {@link Context#getAssociatedDisplayId()} to return
     * {@link Display#DEFAULT_DISPLAY}, ensuring the context is tied to the default display,
     * and {@link Context#isUiContext()} to always return {@code true}, simulating a UI context.
     */
    private static class TestUiContext extends ContextWrapper {

        private final WindowManager mWindowManager;

        TestUiContext(Context base) {
            this(base, null);
        }

        TestUiContext(Context base, WindowManager windowManager) {
            super(base);
            mWindowManager = windowManager;
        }

        @Override
        public int getAssociatedDisplayId() {
            return Display.DEFAULT_DISPLAY;
        }

        @Override
        public boolean isUiContext() {
            return true;
        }

        @Override
        public Object getSystemService(String name) {
            if (WINDOW_SERVICE.equals(name)) {
                return mWindowManager;
            }
            return super.getSystemService(name);
        }
    }
}
