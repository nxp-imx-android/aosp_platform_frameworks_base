/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.internal.jank;

import static android.view.SurfaceControl.JankData.JANK_APPLICATION;
import static android.view.SurfaceControl.JankData.JANK_COMPOSER;
import static android.view.SurfaceControl.JankData.JANK_NONE;

import static com.android.internal.jank.FrameTracker.SurfaceControlWrapper;
import static com.android.internal.jank.FrameTracker.ViewRootWrapper;
import static com.android.internal.jank.Cuj.CUJ_NOTIFICATION_SHADE_EXPAND_COLLAPSE;
import static com.android.internal.jank.Cuj.CUJ_WALLPAPER_TRANSITION;
import static com.android.internal.util.FrameworkStatsLog.UI_INTERACTION_FRAME_INFO_REPORTED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.AdditionalMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.animation.AnimationHandler;
import android.os.ConditionVariable;
import android.os.Handler;
import android.view.Choreographer;
import android.view.FrameMetrics;
import android.view.SurfaceControl;
import android.view.SurfaceControl.JankData;
import android.view.SurfaceControl.JankData.JankType;
import android.view.SurfaceControl.OnJankDataListener;
import android.view.View;
import android.view.ViewAttachTestActivity;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.filters.SmallTest;

import com.android.internal.jank.FrameTracker.ChoreographerWrapper;
import com.android.internal.jank.FrameTracker.FrameMetricsWrapper;
import com.android.internal.jank.FrameTracker.StatsLogWrapper;
import com.android.internal.jank.FrameTracker.ThreadedRendererWrapper;
import com.android.internal.jank.InteractionJankMonitor.Configuration;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Arrays;

@SmallTest
public class FrameTrackerTest {
    private static final String SESSION_NAME = "SessionName";
    private static final long FRAME_TIME_60Hz = (long) 1e9 / 60;
    private static final long FRAME_TIME_120Hz = (long) 1e9 / 120;
    private static final long REGULAR_FT = 8 * 1000000;
    private static final long JANK_FT = 14 * 1000000;
    private static final long SEVERE_JANK_FT = 30 * 1000000;
    private static final long REGULAR_60Hz_FT = 16 * 1000000;
    private static final long JANK_60Hz_FT = 20 * 1000000;

    private ViewAttachTestActivity mActivity;

    @Rule
    public ActivityScenarioRule<ViewAttachTestActivity> mRule =
            new ActivityScenarioRule<>(ViewAttachTestActivity.class);

    private ThreadedRendererWrapper mRenderer;
    private FrameMetricsWrapper mWrapper;
    private SurfaceControlWrapper mSurfaceControlWrapper;
    private ViewRootWrapper mViewRootWrapper;
    private ChoreographerWrapper mChoreographer;
    private StatsLogWrapper mStatsLog;
    private ArgumentCaptor<OnJankDataListener> mListenerCapture;
    private SurfaceControl.OnJankDataListenerRegistration mJankStatsRegistration;
    private SurfaceControl mSurfaceControl;
    private FrameTracker.FrameTrackerListener mTrackerListener;
    private ArgumentCaptor<Runnable> mRunnableArgumentCaptor;

    @Before
    public void setup() {
        // Prepare an activity for getting ThreadedRenderer later.
        mRule.getScenario().onActivity(activity -> mActivity = activity);
        View view = mActivity.getWindow().getDecorView();
        assertThat(view.isAttachedToWindow()).isTrue();

        mWrapper = Mockito.spy(new FrameMetricsWrapper());
        mRenderer = Mockito.spy(new ThreadedRendererWrapper(view.getThreadedRenderer()));
        doNothing().when(mRenderer).addObserver(any());
        doNothing().when(mRenderer).removeObserver(any());

        mSurfaceControl = new SurfaceControl.Builder().setName("Surface").build();
        mViewRootWrapper = mock(ViewRootWrapper.class);
        when(mViewRootWrapper.getSurfaceControl()).thenReturn(mSurfaceControl);
        doNothing().when(mViewRootWrapper).addSurfaceChangedCallback(any());
        doNothing().when(mViewRootWrapper).removeSurfaceChangedCallback(any());
        mSurfaceControlWrapper = mock(SurfaceControlWrapper.class);

        mListenerCapture = ArgumentCaptor.forClass(OnJankDataListener.class);
        mJankStatsRegistration = mock(SurfaceControl.OnJankDataListenerRegistration.class);
        doReturn(mJankStatsRegistration).when(mSurfaceControlWrapper).addJankStatsListener(
                mListenerCapture.capture(), any());
        doNothing().when(mJankStatsRegistration).flush();
        doNothing().when(mJankStatsRegistration).removeAfter(anyLong());

        mChoreographer = mock(ChoreographerWrapper.class);
        mStatsLog = mock(StatsLogWrapper.class);
        mRunnableArgumentCaptor = ArgumentCaptor.forClass(Runnable.class);
        mTrackerListener = mock(FrameTracker.FrameTrackerListener.class);
    }

    private FrameTracker spyFrameTracker(boolean surfaceOnly) {
        Handler handler = mActivity.getMainThreadHandler();
        Configuration config = mock(Configuration.class);
        when(config.getSessionName()).thenReturn(SESSION_NAME);
        when(config.isSurfaceOnly()).thenReturn(surfaceOnly);
        when(config.getSurfaceControl()).thenReturn(mSurfaceControl);
        when(config.shouldDeferMonitor()).thenReturn(true);
        when(config.getDisplayId()).thenReturn(42);
        View view = mActivity.getWindow().getDecorView();
        Handler spyHandler = spy(new Handler(handler.getLooper()));
        when(config.getView()).thenReturn(surfaceOnly ? null : view);
        when(config.getHandler()).thenReturn(spyHandler);
        when(config.logToStatsd()).thenReturn(true);
        when(config.getStatsdInteractionType()).thenReturn(surfaceOnly
                ? Cuj.getStatsdInteractionType(CUJ_WALLPAPER_TRANSITION)
                : Cuj.getStatsdInteractionType(CUJ_NOTIFICATION_SHADE_EXPAND_COLLAPSE));
        FrameTracker frameTracker = Mockito.spy(
                new FrameTracker(config, mRenderer, mViewRootWrapper,
                        mSurfaceControlWrapper, mChoreographer, mWrapper, mStatsLog,
                        /* traceThresholdMissedFrames= */ 1,
                        /* traceThresholdFrameTimeMillis= */ -1,
                        mTrackerListener));
        doNothing().when(frameTracker).postTraceStartMarker(mRunnableArgumentCaptor.capture());
        return frameTracker;
    }

    @Test
    public void testOnlyFirstWindowFrameOverThreshold() {
        FrameTracker tracker = spyFrameTracker(/* surfaceOnly= */ false);

        // Just provide current timestamp anytime mWrapper asked for VSYNC_TIMESTAMP
        when(mWrapper.getMetric(FrameMetrics.VSYNC_TIMESTAMP))
                .then(unusedInvocation -> System.nanoTime());

        when(mChoreographer.getVsyncId()).thenReturn(100L);
        tracker.begin();
        mRunnableArgumentCaptor.getValue().run();
        verify(mRenderer, only()).addObserver(any());

        // frames marked first in window should be ignored.
        sendJankyFirstWindowFrame(tracker, 100L);
        sendOnTimeFrame(tracker, 101L);

        // end the trace session
        when(mChoreographer.getVsyncId()).thenReturn(102L);
        tracker.end(FrameTracker.REASON_END_NORMAL);
        sendOnTimeFrame(tracker, 102L);

        // frame after end() should be ignored.
        sendJankyFrame(tracker, 103L);

        verify(tracker).removeObservers();
        verify(mTrackerListener, never()).triggerPerfetto(any());
        verify(mStatsLog).write(eq(UI_INTERACTION_FRAME_INFO_REPORTED),
                eq(42), /* displayId */
                eq(DisplayRefreshRate.REFRESH_RATE_120_HZ),
                eq(Cuj.getStatsdInteractionType(CUJ_NOTIFICATION_SHADE_EXPAND_COLLAPSE)),
                eq(2L) /* totalFrames */,
                eq(0L) /* missedFrames */,
                eq(REGULAR_FT) /* maxFrameTimeNanos */,
                eq(0L) /* missedSfFramesCount */,
                eq(0L) /* missedAppFramesCount */,
                eq(0L) /* maxSuccessiveMissedFramesCount */,
                eq(16666666L) /* totalAnimationTime */,
                eq(0.0f) /* sfWeightedJank */,
                eq(0.0f) /* appWeightedJank */);
    }

    @Test
    public void testSfJank() {
        FrameTracker tracker = spyFrameTracker(/* surfaceOnly= */ false);

        when(mChoreographer.getVsyncId()).thenReturn(100L);
        tracker.begin();
        mRunnableArgumentCaptor.getValue().run();
        verify(mRenderer, only()).addObserver(any());

        sendOnTimeFrame(tracker, 100L);
        sendSfJankyFrame(tracker, 101L);

        // end the trace session
        when(mChoreographer.getVsyncId()).thenReturn(102L);
        tracker.end(FrameTracker.REASON_END_NORMAL);
        sendOnTimeFrame(tracker, 102L);

        verify(tracker).removeObservers();

        // We detected a janky frame - trigger Perfetto
        verify(mTrackerListener).triggerPerfetto(any());

        verify(mStatsLog).write(eq(UI_INTERACTION_FRAME_INFO_REPORTED),
                eq(42), /* displayId */
                eq(DisplayRefreshRate.REFRESH_RATE_120_HZ),
                eq(Cuj.getStatsdInteractionType(CUJ_NOTIFICATION_SHADE_EXPAND_COLLAPSE)),
                eq(2L) /* totalFrames */,
                eq(1L) /* missedFrames */,
                eq(REGULAR_FT) /* maxFrameTimeNanos */,
                eq(1L) /* missedSfFramesCount */,
                eq(0L) /* missedAppFramesCount */,
                eq(1L) /* maxSuccessiveMissedFramesCount */,
                eq(3 * FRAME_TIME_120Hz) /* totalAnimationTime */,
                eq(1.0f) /* sfWeightedJank */,
                eq(0.0f) /* appWeightedJank */);
    }

    @Test
    public void testFirstFrameJankyNoTrigger() {
        FrameTracker tracker = spyFrameTracker(/* surfaceOnly= */ false);

        when(mChoreographer.getVsyncId()).thenReturn(100L);
        tracker.begin();
        mRunnableArgumentCaptor.getValue().run();
        verify(mRenderer, only()).addObserver(any());

        sendJankyFrame(tracker, 100L);
        sendOnTimeFrame(tracker, 101L);

        // end the trace session
        when(mChoreographer.getVsyncId()).thenReturn(102L);
        tracker.end(FrameTracker.REASON_END_NORMAL);
        sendOnTimeFrame(tracker, 102L);

        verify(tracker).removeObservers();

        verify(mTrackerListener, never()).triggerPerfetto(any());

        verify(mStatsLog).write(eq(UI_INTERACTION_FRAME_INFO_REPORTED),
                eq(42), /* displayId */
                eq(DisplayRefreshRate.REFRESH_RATE_120_HZ),
                eq(Cuj.getStatsdInteractionType(CUJ_NOTIFICATION_SHADE_EXPAND_COLLAPSE)),
                eq(2L) /* totalFrames */,
                eq(0L) /* missedFrames */,
                eq(REGULAR_FT) /* maxFrameTimeNanos */,
                eq(0L) /* missedSfFramesCount */,
                eq(0L) /* missedAppFramesCount */,
                eq(0L) /* maxSuccessiveMissedFramesCount */,
                eq(2 * FRAME_TIME_120Hz) /* totalAnimationTime */,
                eq(0.0f) /* sfWeightedJank */,
                eq(0.0f) /* appWeightedJank */);
    }

    @Test
    public void testOtherFrameOverThreshold() {
        FrameTracker tracker = spyFrameTracker(/* surfaceOnly= */ false);

        when(mChoreographer.getVsyncId()).thenReturn(100L);
        tracker.begin();
        mRunnableArgumentCaptor.getValue().run();
        verify(mRenderer, only()).addObserver(any());

        sendOnTimeFrame(tracker, 100L);
        sendJankyFrame(tracker, 101L);

        // end the trace session
        when(mChoreographer.getVsyncId()).thenReturn(102L);
        tracker.end(FrameTracker.REASON_END_NORMAL);
        sendOnTimeFrame(tracker, 102L);

        verify(tracker).removeObservers();

        // We detected a janky frame - trigger Perfetto
        verify(mTrackerListener).triggerPerfetto(any());

        verify(mStatsLog).write(eq(UI_INTERACTION_FRAME_INFO_REPORTED),
                eq(42), /* displayId */
                eq(DisplayRefreshRate.REFRESH_RATE_120_HZ),
                eq(Cuj.getStatsdInteractionType(CUJ_NOTIFICATION_SHADE_EXPAND_COLLAPSE)),
                eq(2L) /* totalFrames */,
                eq(1L) /* missedFrames */,
                eq(JANK_FT) /* maxFrameTimeNanos */,
                eq(0L) /* missedSfFramesCount */,
                eq(1L) /* missedAppFramesCount */,
                eq(1L) /* maxSuccessiveMissedFramesCount */,
                eq(3 * FRAME_TIME_120Hz) /* totalAnimationTime */,
                eq(0.0f) /* sfWeightedJank */,
                eq(1.0f) /* appWeightedJank */);
    }

    @Test
    public void testSevereJank() {
        FrameTracker tracker = spyFrameTracker(/* surfaceOnly= */ false);

        when(mChoreographer.getVsyncId()).thenReturn(100L);
        tracker.begin();
        mRunnableArgumentCaptor.getValue().run();
        verify(mRenderer, only()).addObserver(any());

        sendOnTimeFrame(tracker, 100L);
        sendSevereJankyFrame(tracker, 101L);

        // end the trace session
        when(mChoreographer.getVsyncId()).thenReturn(102L);
        tracker.end(FrameTracker.REASON_END_NORMAL);
        sendOnTimeFrame(tracker, 102L);

        verify(tracker).removeObservers();

        // We detected a janky frame - trigger Perfetto
        verify(mTrackerListener).triggerPerfetto(any());

        verify(mStatsLog).write(eq(UI_INTERACTION_FRAME_INFO_REPORTED),
                eq(42), /* displayId */
                eq(DisplayRefreshRate.REFRESH_RATE_120_HZ),
                eq(Cuj.getStatsdInteractionType(CUJ_NOTIFICATION_SHADE_EXPAND_COLLAPSE)),
                eq(2L) /* totalFrames */,
                eq(1L) /* missedFrames */,
                eq(SEVERE_JANK_FT) /* maxFrameTimeNanos */,
                eq(0L) /* missedSfFramesCount */,
                eq(1L) /* missedAppFramesCount */,
                eq(1L) /* maxSuccessiveMissedFramesCount */,
                eq(5 * FRAME_TIME_120Hz) /* totalAnimationTime */,
                eq(0.0f) /* sfWeightedJank */,
                // severe jank should have a wheight of 2.
                eq(2.0f) /* appWeightedJank */);
    }

    @Test
    public void test60HzJank() {
        FrameTracker tracker = spyFrameTracker(/* surfaceOnly= */ false);

        when(mChoreographer.getVsyncId()).thenReturn(100L);
        tracker.begin();
        mRunnableArgumentCaptor.getValue().run();
        verify(mRenderer, only()).addObserver(any());

        send60HzFrame(tracker, 100L, false);
        send60HzFrame(tracker, 101L, true);

        // end the trace session
        when(mChoreographer.getVsyncId()).thenReturn(102L);
        tracker.end(FrameTracker.REASON_END_NORMAL);
        send60HzFrame(tracker, 102L, false);

        verify(tracker).removeObservers();

        // We detected a janky frame - trigger Perfetto
        verify(mTrackerListener).triggerPerfetto(any());

        verify(mStatsLog).write(eq(UI_INTERACTION_FRAME_INFO_REPORTED),
                eq(42), /* displayId */
                eq(DisplayRefreshRate.REFRESH_RATE_60_HZ),
                eq(Cuj.getStatsdInteractionType(CUJ_NOTIFICATION_SHADE_EXPAND_COLLAPSE)),
                eq(2L) /* totalFrames */,
                eq(1L) /* missedFrames */,
                eq(JANK_60Hz_FT) /* maxFrameTimeNanos */,
                eq(0L) /* missedSfFramesCount */,
                eq(1L) /* missedAppFramesCount */,
                eq(1L) /* maxSuccessiveMissedFramesCount */,
                eq(3 * FRAME_TIME_60Hz) /* totalAnimationTime */,
                eq(0.0f) /* sfWeightedJank */,
                // 60Hz jank should have a wheight of sqrt(120/60).
                eq((float) Math.sqrt(2.0), 0.001f) /* appWeightedJank */);
    }

    @Test
    public void testLastFrameOverThresholdBeforeEnd() {
        FrameTracker tracker = spyFrameTracker(/* surfaceOnly= */ false);

        when(mChoreographer.getVsyncId()).thenReturn(100L);
        tracker.begin();
        mRunnableArgumentCaptor.getValue().run();
        verify(mRenderer, only()).addObserver(any());

        // send first frame - not janky
        sendOnTimeFrame(tracker, 100L);
        sendOnTimeFrame(tracker, 101L);

        // end the trace session.
        when(mChoreographer.getVsyncId()).thenReturn(102L);
        tracker.end(FrameTracker.REASON_END_NORMAL);
        sendJankyFrame(tracker, 102L);

        // One more callback with VSYNC after the end() vsync id.
        sendOnTimeFrame(tracker, 103L);

        verify(tracker).removeObservers();

        // We detected a janky frame - trigger Perfetto
        verify(mTrackerListener).triggerPerfetto(any());

        verify(mStatsLog).write(eq(UI_INTERACTION_FRAME_INFO_REPORTED),
                eq(42), /* displayId */
                eq(DisplayRefreshRate.REFRESH_RATE_120_HZ),
                eq(Cuj.getStatsdInteractionType(CUJ_NOTIFICATION_SHADE_EXPAND_COLLAPSE)),
                eq(2L) /* totalFrames */,
                eq(1L) /* missedFrames */,
                eq(JANK_FT) /* maxFrameTimeNanos */,
                eq(0L) /* missedSfFramesCount */,
                eq(1L) /* missedAppFramesCount */,
                eq(1L) /* maxSuccessiveMissedFramesCount */,
                eq(3 * FRAME_TIME_120Hz) /* totalAnimationTime */,
                eq(0.0f) /* sfWeightedJank */,
                eq(1.0f) /* appWeightedJank */);
    }

    /**
     * b/223787365
     */
    @Test
    public void testNoOvercountingAfterEnd() {
        FrameTracker tracker = spyFrameTracker(/* surfaceOnly= */ false);

        when(mChoreographer.getVsyncId()).thenReturn(100L);
        tracker.begin();
        mRunnableArgumentCaptor.getValue().run();
        verify(mRenderer, only()).addObserver(any());

        sendOnTimeFrame(tracker, 100L);
        sendOnTimeFrame(tracker, 101L);

        // end the trace session
        when(mChoreographer.getVsyncId()).thenReturn(102L);
        tracker.end(FrameTracker.REASON_END_NORMAL);

        // Send incomplete callback for 102L
        sendSfFrame(tracker, 4, 0, 102L, JANK_NONE, true);

        // Send janky but complete callbck fo 103L
        sendJankyFrame(tracker, 103L);

        verify(tracker).removeObservers();
        verify(mTrackerListener, never()).triggerPerfetto(any());
        verify(mStatsLog).write(eq(UI_INTERACTION_FRAME_INFO_REPORTED),
                eq(42), /* displayId */
                eq(DisplayRefreshRate.REFRESH_RATE_120_HZ),
                eq(Cuj.getStatsdInteractionType(CUJ_NOTIFICATION_SHADE_EXPAND_COLLAPSE)),
                eq(2L) /* totalFrames */,
                eq(0L) /* missedFrames */,
                eq(REGULAR_FT) /* maxFrameTimeNanos */,
                eq(0L) /* missedSfFramesCount */,
                eq(0L) /* missedAppFramesCount */,
                eq(0L) /* maxSuccessiveMissedFramesCount */,
                eq(2 * FRAME_TIME_120Hz) /* totalAnimationTime */,
                eq(0.0f) /* sfWeightedJank */,
                eq(0.0f) /* appWeightedJank */);
    }

    @Test
    public void testBeginCancel() {
        FrameTracker tracker = spyFrameTracker(/* surfaceOnly= */ false);

        when(mChoreographer.getVsyncId()).thenReturn(100L);
        tracker.begin();
        mRunnableArgumentCaptor.getValue().run();
        verify(mRenderer).addObserver(any());

        sendOnTimeFrame(tracker, 100L);
        sendOnTimeFrame(tracker, 101L);
        sendJankyFrame(tracker, 102L);

        tracker.cancel(FrameTracker.REASON_CANCEL_NORMAL);
        verify(tracker).removeObservers();
        // Since the tracker has been cancelled, shouldn't trigger perfetto.
        verify(mTrackerListener, never()).triggerPerfetto(any());
    }

    @Test
    public void testCancelIfEndVsyncIdEqualsToBeginVsyncId() {
        FrameTracker tracker = spyFrameTracker(/* surfaceOnly= */ false);

        when(mChoreographer.getVsyncId()).thenReturn(100L);
        tracker.begin();
        mRunnableArgumentCaptor.getValue().run();
        verify(mRenderer, only()).addObserver(any());

        // end the trace session
        when(mChoreographer.getVsyncId()).thenReturn(101L);
        tracker.end(FrameTracker.REASON_END_NORMAL);

        // Since the begin vsync id (101) equals to the end vsync id (101), will be treat as cancel.
        verify(tracker).cancel(FrameTracker.REASON_CANCEL_SAME_VSYNC);

        // Observers should be removed in this case, or FrameTracker object will be leaked.
        verify(tracker).removeObservers();

        // Should never trigger Perfetto since it is a cancel.
        verify(mTrackerListener, never()).triggerPerfetto(any());
    }

    @Test
    public void testCancelIfEndVsyncIdLessThanBeginVsyncId() {
        FrameTracker tracker = spyFrameTracker(/* surfaceOnly= */ false);

        when(mChoreographer.getVsyncId()).thenReturn(100L);
        tracker.begin();
        mRunnableArgumentCaptor.getValue().run();
        verify(mRenderer, only()).addObserver(any());

        // end the trace session at the same vsync id, end vsync id will less than the begin one.
        // Because the begin vsync id is supposed to the next frame,
        tracker.end(FrameTracker.REASON_END_NORMAL);

        // The begin vsync id (101) is larger than the end one (100), will be treat as cancel.
        verify(tracker).cancel(FrameTracker.REASON_CANCEL_SAME_VSYNC);

        // Observers should be removed in this case, or FrameTracker object will be leaked.
        verify(tracker).removeObservers();

        // Should never trigger Perfetto since it is a cancel.
        verify(mTrackerListener, never()).triggerPerfetto(any());
    }

    @Test
    public void testCancelWhenSessionNeverBegun() {
        FrameTracker tracker = spyFrameTracker(/* surfaceOnly= */ false);

        tracker.cancel(FrameTracker.REASON_CANCEL_NORMAL);
        verify(tracker).removeObservers();
    }

    @Test
    public void testEndWhenSessionNeverBegun() {
        FrameTracker tracker = spyFrameTracker(/* surfaceOnly= */ false);

        tracker.end(FrameTracker.REASON_END_NORMAL);
        verify(tracker).removeObservers();
    }

    @Test
    public void testSurfaceOnlyOtherFrameJanky() {
        FrameTracker tracker = spyFrameTracker(/* surfaceOnly= */ true);

        when(mChoreographer.getVsyncId()).thenReturn(100L);
        tracker.begin();
        mRunnableArgumentCaptor.getValue().run();
        verify(mSurfaceControlWrapper).addJankStatsListener(any(), any());

        sendSurfaceOnlyFrame(tracker, JANK_NONE, 100L);
        sendSurfaceOnlyFrame(tracker, JANK_NONE, 101L);
        sendSurfaceOnlyFrame(tracker, JANK_APPLICATION, 102L);

        when(mChoreographer.getVsyncId()).thenReturn(102L);
        tracker.end(FrameTracker.REASON_CANCEL_NORMAL);

        // an extra frame to trigger finish
        sendSurfaceOnlyFrame(tracker, JANK_NONE, 103L);

        verify(mJankStatsRegistration).removeAfter(anyLong());
        verify(mTrackerListener).triggerPerfetto(any());

        verify(mStatsLog).write(eq(UI_INTERACTION_FRAME_INFO_REPORTED),
                eq(42), /* displayId */
                eq(DisplayRefreshRate.REFRESH_RATE_120_HZ),
                eq(Cuj.getStatsdInteractionType(CUJ_WALLPAPER_TRANSITION)),
                eq(2L) /* totalFrames */,
                eq(1L) /* missedFrames */,
                eq(0L) /* maxFrameTimeNanos */,
                eq(0L) /* missedSfFramesCount */,
                eq(1L) /* missedAppFramesCount */,
                eq(1L) /* maxSuccessiveMissedFramesCount */,
                eq(3 * FRAME_TIME_120Hz) /* totalAnimationTime */,
                eq(0.0f) /* sfWeightedJank */,
                eq(1.0f) /* appWeightedJank */);
    }

    @Test
    public void testSurfaceOnlyFirstFrameJanky() {
        FrameTracker tracker = spyFrameTracker(/* surfaceOnly= */ true);

        when(mChoreographer.getVsyncId()).thenReturn(100L);
        tracker.begin();
        mRunnableArgumentCaptor.getValue().run();
        verify(mSurfaceControlWrapper).addJankStatsListener(any(), any());

        sendSurfaceOnlyFrame(tracker, JANK_APPLICATION, 100L);
        sendSurfaceOnlyFrame(tracker, JANK_NONE, 101L);
        sendSurfaceOnlyFrame(tracker, JANK_NONE, 102L);

        when(mChoreographer.getVsyncId()).thenReturn(102L);
        tracker.end(FrameTracker.REASON_CANCEL_NORMAL);

        sendSurfaceOnlyFrame(tracker, JANK_NONE, 103L);

        verify(mJankStatsRegistration).removeAfter(anyLong());
        verify(mTrackerListener, never()).triggerPerfetto(any());

        verify(mStatsLog).write(eq(UI_INTERACTION_FRAME_INFO_REPORTED),
                eq(42), /* displayId */
                eq(DisplayRefreshRate.REFRESH_RATE_120_HZ),
                eq(Cuj.getStatsdInteractionType(CUJ_WALLPAPER_TRANSITION)),
                eq(2L) /* totalFrames */,
                eq(0L) /* missedFrames */,
                eq(0L) /* maxFrameTimeNanos */,
                eq(0L) /* missedSfFramesCount */,
                eq(0L) /* missedAppFramesCount */,
                eq(0L) /* maxSuccessiveMissedFramesCount */,
                eq(2 * FRAME_TIME_120Hz) /* totalAnimationTime */,
                eq(0.0f) /* sfWeightedJank */,
                eq(0.0f) /* appWeightedJank */);
    }

    @Test
    public void testSurfaceOnlyLastFrameJanky() {
        FrameTracker tracker = spyFrameTracker(/* surfaceOnly= */ true);

        when(mChoreographer.getVsyncId()).thenReturn(100L);
        tracker.begin();
        mRunnableArgumentCaptor.getValue().run();
        verify(mSurfaceControlWrapper).addJankStatsListener(any(), any());

        sendSurfaceOnlyFrame(tracker, JANK_NONE, 100L);
        sendSurfaceOnlyFrame(tracker, JANK_NONE, 101L);
        sendSurfaceOnlyFrame(tracker, JANK_NONE, 102L);

        when(mChoreographer.getVsyncId()).thenReturn(102L);
        tracker.end(FrameTracker.REASON_CANCEL_NORMAL);

        sendSurfaceOnlyFrame(tracker, JANK_APPLICATION, 103L);

        verify(mJankStatsRegistration).removeAfter(anyLong());
        verify(mTrackerListener, never()).triggerPerfetto(any());

        verify(mStatsLog).write(eq(UI_INTERACTION_FRAME_INFO_REPORTED),
                eq(42), /* displayId */
                eq(DisplayRefreshRate.REFRESH_RATE_120_HZ),
                eq(Cuj.getStatsdInteractionType(CUJ_WALLPAPER_TRANSITION)),
                eq(2L) /* totalFrames */,
                eq(0L) /* missedFrames */,
                eq(0L) /* maxFrameTimeNanos */,
                eq(0L) /* missedSfFramesCount */,
                eq(0L) /* missedAppFramesCount */,
                eq(0L) /* maxSuccessiveMissedFramesCount */,
                eq(2 * FRAME_TIME_120Hz) /* totalAnimationTime */,
                eq(0.0f) /* sfWeightedJank */,
                eq(0.0f) /* appWeightedJank */);
    }

    @Test
    public void testEndAnimationWithLastFrameSyncId() {
        final long[] expectedLastAnimationFrameVsyncId = { 0 };
        final long[] lastAnimationFrameVsyncId = { 0 };
        final long[] endAnimationVsyncId = { 0 };
        final ConditionVariable condition = new ConditionVariable();
        final FrameTracker tracker = spyFrameTracker(/* surfaceOnly= */ false);
        mActivity.runOnUiThread(() -> {
            final AnimationHandler animationHandler = AnimationHandler.getInstance();
            final Choreographer realChoreographer = Choreographer.getInstance();
            // 3 v-sync ids:
            //  1. Begin mocked vsyncId = 0
            //  2. Current real vsyncId = last animation frame
            //  3. Posted real vsyncId = end animation
            when(mChoreographer.getVsyncId()).thenReturn(0L);
            tracker.begin();
            mRunnableArgumentCaptor.getValue().run();
            when(mChoreographer.getVsyncId()).thenAnswer(a -> realChoreographer.getVsyncId());
            expectedLastAnimationFrameVsyncId[0] = realChoreographer.getVsyncId();
            // Simulate ending multiple animators. Their callbacks should run in a batch.
            animationHandler.postEndAnimationCallback(() -> {
                endAnimationVsyncId[0] = realChoreographer.getVsyncId();
                lastAnimationFrameVsyncId[0] =
                        animationHandler.getLastAnimationFrameVsyncId(endAnimationVsyncId[0]);
            });
            animationHandler.postEndAnimationCallback(() -> {
                tracker.end(FrameTracker.REASON_END_NORMAL);
                condition.open();
            });
        });

        condition.block(1000L /* timeoutMs */);
        assertThat(lastAnimationFrameVsyncId[0]).isEqualTo(expectedLastAnimationFrameVsyncId[0]);
        assertThat(endAnimationVsyncId[0]).isGreaterThan(lastAnimationFrameVsyncId[0]);
        // Verifies that FrameTracker#mEndVsyncId uses the vsyncId from AnimationHandler.
        verify(mJankStatsRegistration).removeAfter(eq(lastAnimationFrameVsyncId[0]));
    }

    @Test
    public void testMaxSuccessiveMissedFramesCount() {
        FrameTracker tracker = spyFrameTracker(/* surfaceOnly= */ false);
        when(mChoreographer.getVsyncId()).thenReturn(100L);
        tracker.begin();
        mRunnableArgumentCaptor.getValue().run();
        verify(mSurfaceControlWrapper).addJankStatsListener(any(), any());

        sendSfJankyFrame(tracker, 100L);
        sendSfJankyFrame(tracker, 101L);
        sendJankyFrame(tracker, 102L);

        sendOnTimeFrame(tracker, 103L);

        sendJankyFrame(tracker, 104L);
        sendJankyFrame(tracker, 105L);

        when(mChoreographer.getVsyncId()).thenReturn(106L);
        tracker.end(FrameTracker.REASON_END_NORMAL);
        sendSfJankyFrame(tracker, 106L);
        sendSfJankyFrame(tracker, 107L);

        verify(mJankStatsRegistration).removeAfter(anyLong());
        verify(mTrackerListener).triggerPerfetto(any());
        verify(mStatsLog).write(eq(UI_INTERACTION_FRAME_INFO_REPORTED),
                eq(42), /* displayId */
                eq(DisplayRefreshRate.REFRESH_RATE_120_HZ),
                eq(Cuj.getStatsdInteractionType(CUJ_NOTIFICATION_SHADE_EXPAND_COLLAPSE)),
                eq(6L) /* totalFrames */,
                eq(5L) /* missedFrames */,
                eq(JANK_FT) /* maxFrameTimeNanos */,
                eq(2L) /* missedSfFramesCount */,
                eq(3L) /* missedAppFramesCount */,
                eq(3L) /* maxSuccessiveMissedFramesCount */,
                eq(11 * FRAME_TIME_120Hz) /* totalAnimationTime */,
                eq(2.0f) /* sfWeightedJank */,
                eq(3.0f) /* appWeightedJank */);
    }

    private void sendJankyFirstWindowFrame(FrameTracker tracker, long vsyncId) {
        sendFrame(tracker, JANK_APPLICATION, JANK_FT, FRAME_TIME_120Hz, vsyncId, true);
    }

    private void sendJankyFrame(FrameTracker tracker, long vsyncId) {
        sendFrame(tracker, JANK_APPLICATION, JANK_FT, FRAME_TIME_120Hz, vsyncId, false);
    }

    private void sendSevereJankyFrame(FrameTracker tracker, long vsyncId) {
        sendFrame(tracker, JANK_APPLICATION, SEVERE_JANK_FT, 3 * FRAME_TIME_120Hz, vsyncId, false);
    }

    private void sendSfJankyFrame(FrameTracker tracker, long vsyncId) {
        sendFrame(tracker, JANK_COMPOSER, REGULAR_FT, FRAME_TIME_120Hz, vsyncId, false);
    }

    private void sendOnTimeFrame(FrameTracker tracker, long vsyncId) {
        sendFrame(tracker, JANK_NONE, REGULAR_FT, 0, vsyncId, false);
    }

    private void sendSurfaceOnlyFrame(FrameTracker tracker, @JankType int jankType, long vsyncId) {
        assertThat(tracker.mSurfaceOnly).isTrue();
        long delay = (jankType == JANK_NONE) ? 0 : FRAME_TIME_120Hz;
        sendFrame(tracker, jankType, /* durationNs */ -1, delay, vsyncId, /* first= */ false);
    }

    private void sendFrame(FrameTracker tracker, @JankType int jankType, long durationNs,
            long delayNs, long vsyncId, boolean first) {
        if (!tracker.mSurfaceOnly) {
            sendHwuiFrame(tracker, durationNs, vsyncId, first);
        }
        sendSfFrame(tracker, durationNs, delayNs, vsyncId, jankType, true);
    }

    private void send60HzFrame(FrameTracker tracker, long vsyncId, boolean janky) {
        assertThat(tracker.mSurfaceOnly).isFalse();
        long duration = janky ? JANK_60Hz_FT : REGULAR_60Hz_FT;
        long delay = janky ? FRAME_TIME_60Hz : 0;
        int type = janky ? JANK_APPLICATION : JANK_NONE;

        sendHwuiFrame(tracker, duration, vsyncId, false);
        sendSfFrame(tracker, duration, delay, vsyncId, type, false);
    }

    private void sendHwuiFrame(FrameTracker tracker, long durationNs, long vsyncId, boolean first) {
        when(mWrapper.getTiming()).thenReturn(new long[]{0, vsyncId});
        doReturn(first ? 1L : 0L).when(mWrapper).getMetric(FrameMetrics.FIRST_DRAW_FRAME);
        doReturn(durationNs).when(mWrapper).getMetric(FrameMetrics.TOTAL_DURATION);
        final ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        doNothing().when(tracker).postCallback(captor.capture());
        tracker.onFrameMetricsAvailable(0);
        captor.getValue().run();
    }

    private void sendSfFrame(FrameTracker tracker, long durationNs, long delayNs, long vsyncId,
            @JankType int jankType, boolean is120Hz) {
        final ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        doNothing().when(tracker).postCallback(captor.capture());
        long frameTime = is120Hz ? FRAME_TIME_120Hz : FRAME_TIME_60Hz;
        mListenerCapture.getValue().onJankDataAvailable(Arrays.asList(
            new JankData(vsyncId, jankType, frameTime, frameTime, durationNs, delayNs)));
        captor.getValue().run();
    }
}
