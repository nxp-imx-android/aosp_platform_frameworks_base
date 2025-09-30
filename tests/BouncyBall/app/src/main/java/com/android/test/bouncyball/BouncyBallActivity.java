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

package com.android.test.bouncyball;

import android.app.Activity;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Trace;
import android.util.Log;
import android.view.Choreographer;
import android.view.Display;
import android.view.FrameMetrics;
import android.view.Window;
import android.view.WindowManager;

import java.util.concurrent.Executors;

public class BouncyBallActivity extends Activity {
    // If true, log (to logcat) any frame drops detected in-app on a best-effort
    // basis (trace results are the most solid approach).  Note mild perf impact
    // as this requires an additional callback on the main loop.
    private static final boolean LOG_DROPPED_FRAMES = false;

    // If true, log every frame's duration.  Note the perf impact of an
    // additional callback on the main loop and spewing to logcat.
    // Setting this `true` implicitly sets LOG_DROPPED_FRAMES `true`.
    private static final boolean LOG_EVERY_FRAME = false;

    // To help with debugging and verifying behavior when frames are dropped,
    // this will drop one in every 64 frames.
    private static final boolean FORCE_DROPPED_FRAMES = false;

    // If the app fails an assumption we have for it (primarily that it is
    // the only foreground app), then its results cannot be trusted.  By
    // default, we choose to immediately exit in this situation.  Note that
    // dropping a frame is not considered an assumption failure.
    private static final boolean ASSUMPTION_FAILURE_FORCES_EXIT = true;

    private static final String LOG_TAG = "BouncyBall";

    // This is the bare minimum the app needs to run at for us to consider
    // this a valid test.  We'll generally use the maximum of
    // getSuggestedFrameRate() and the rate the system launches us at.  But
    // if that maximum is less than this minimum, we'll use this minimum.
    private static final float MINIMUM_TEST_FRAME_RATE = 60.0f;

    // This test focuses on sustained frame rate, so we want to ignore drops
    // right at app start.  We don't want to wait too long, though, lest we
    // miss drops due to clocks ramping down.  Unfortunately, some low end
    // devices take a while to give the app focus.  So we provide a range
    // here, starting our measurement as soon as we have focus within this
    // range (and failing if we don't get focus in time).
    private static final float INITIAL_MIN_TIME_TO_IGNORE_IN_SECONDS = 0.1f;

    // If we've been up and running, drawing frames, for half a second, and
    // we still don't have focus, that's not acceptable and we'll fail.
    // LINT.IfChange
    private static final float INITIAL_MAX_TIME_TO_IGNORE_IN_SECONDS = 0.5f;
    // LINT.ThenChange(/tests/BouncyBall/automation_config.pbtx)

    // The app itself can run "forever".  But for automated testing, we want
    // a consistent testing time.  We don't want to take too long, but we want
    // to wait sufficiently for CPUs/GPU to clock down to save power under our
    // basic load.
    // LINT.IfChange
    private static final int AUTOMATED_TEST_DURATION_IN_SECONDS = 120;
    // LINT.ThenChange(/tests/BouncyBall/automation_config.pbtx)

    // We use a trace counter to let trace analysis know if a frame is relevant.
    private static final String TRACE_COUNTER_RELEVANT_FRAME = "relevant_frame";

    // Initial state, before we've gotten focus and waited at least
    // INITIAL_MIN_TIME_TO_IGNORE_IN_SECONDS.
    private static final int TRACE_STATE_PRE_TEST_TIME = 1;

    // These are the relevant frames for automated testing.
    // LINT.IfChange
    private static final int TRACE_STATE_IN_TEST_TIME = 2;
    // LINT.ThenChange(/tests/BouncyBall/trace_metrics_v2_spec.pbtx)

    // This is after the time span for automated testing.
    private static final int TRACE_STATE_POST_TEST_TIME = 3;

    private int mDisplayId = -1;
    private boolean mHasFocus = false;
    private boolean mWarmedUp = false;
    private float mFrameRate;
    private int mFrameCount = 0;
    private int mMinFirstRelevantFrame = -1;
    private int mMaxFirstRelevantFrame = -1;
    private int mActualFirstRelevantFrame = -1;
    private int mLastRelevantFrame = -1;
    private int mTraceState = TRACE_STATE_PRE_TEST_TIME;
    private Choreographer mChoreographer;

    private final DisplayManager.DisplayListener mDisplayListener =
            new DisplayManager.DisplayListener() {

                @Override
                public void onDisplayAdded(int ignored) { /* Don't care. */ }

                @Override
                public void onDisplayRemoved(int ignored) { /* Don't care. */ }

                @Override
                public void onDisplayChanged(int displayId) {
                    if (displayId != mDisplayId) {
                        return;
                    }
                    float frameRate = getDisplay().getMode().getRefreshRate();
                    if (frameRate == mFrameRate) {
                        // On devices with API level < 36, we might get this
                        // called for other reasons (like brightness changing).
                        // We ignore anything but frame rate changes.
                        return;
                    }
                    setFrameRate(frameRate);
                    Log.i(LOG_TAG, "Using frame rate " + mFrameRate + "Hz");
                }
            };

    private final Window.OnFrameMetricsAvailableListener mFrameMetricsListener =
            new Window.OnFrameMetricsAvailableListener() {

                private int mNumFramesDropped = 0;

                @Override
                public void onFrameMetricsAvailable(Window window,
                                                    FrameMetrics frameMetrics,
                                                    int dropCountSinceLastInvocation) {

                    if (!mWarmedUp) {
                        // Ignore frame metrics before we're warmed up.
                        return;
                    }
                    long totalDuration = frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION);
                    long deadline = frameMetrics.getMetric(FrameMetrics.DEADLINE);
                    String totalDurationStr = nanosToMillis(totalDuration) + "ms";
                    String deadlineStr = nanosToMillis(deadline) + "ms";

                    if (totalDuration > deadline) {
                        mNumFramesDropped++;
                        String msg =  "DROPPED FRAME (total " + mNumFramesDropped
                                + "): Took " + totalDurationStr
                                + ", exceeding deadline " + deadlineStr;
                        Log.e(LOG_TAG, msg);
                    } else if (LOG_EVERY_FRAME) {
                        String msg = "Frame took " + totalDurationStr
                                + " with deadline " + deadlineStr;
                        Log.i(LOG_TAG, msg);
                    }
                    if (dropCountSinceLastInvocation != 0) {
                        String msg = "onFrameMetricsAvailable() dropped "
                                + dropCountSinceLastInvocation
                                + " frames; we might miss frame drops in logcat "
                                + "(but the trace will find them)";
                        Log.w(LOG_TAG, msg);
                    }
                }
            };

    private final Choreographer.FrameCallback mFrameCallback =
            new Choreographer.FrameCallback() {

                @Override
                public void doFrame(long frameTimeNanos) {
                    if (!mWarmedUp && isReadyToStartTesting()) {
                        mWarmedUp = true;
                        mTraceState = TRACE_STATE_IN_TEST_TIME;
                        // We chose not to log this state change to minimize
                        // system load during testing time.
                    } else if (mFrameCount == mLastRelevantFrame) {
                        mTraceState = TRACE_STATE_POST_TEST_TIME;
                        Log.i(LOG_TAG, "Done with frames for automated testing.");
                    }
                    Trace.setCounter(TRACE_COUNTER_RELEVANT_FRAME, mTraceState);
                    mFrameCount++;
                    if (FORCE_DROPPED_FRAMES) {
                        dropFrameSometimes();
                    }
                    // Request the next frame callback
                    mChoreographer.postFrameCallback(this);
                }

                private void dropFrameSometimes() {
                    if ((mFrameCount % 64) == 0) {
                        // We'll sleep for 1.5 frames worth of time to force a drop.
                        float overFrameInMillis = 1.5f * 1_000.0f / mFrameRate;
                        try {
                            Thread.sleep((long) overFrameInMillis);
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Trace.setCounter(TRACE_COUNTER_RELEVANT_FRAME, mTraceState);
        Trace.beginSection("BouncyBallActivity onCreate");
        setContentView(R.layout.activity_bouncy_ball);

        DisplayManager manager = getSystemService(DisplayManager.class);
        if (Build.VERSION.SDK_INT >= 36) {
            // We prefer this newer API, introduced at API level 36.
            manager.registerDisplayListener(Executors.newSingleThreadExecutor(),
                                            DisplayManager.EVENT_TYPE_DISPLAY_REFRESH_RATE,
                                            mDisplayListener);
        } else {
            // We don't need a separate Handler because our listener logic is
            // cheap, and for valid tests only gets invoked before we're looking
            // for dropped frames.
            manager.registerDisplayListener(mDisplayListener, null);
        }

        initFrameRate();
        mChoreographer = Choreographer.getInstance();
        mChoreographer.postFrameCallback(mFrameCallback);
        if (LOG_DROPPED_FRAMES || LOG_EVERY_FRAME) {
            Handler h = new Handler(Looper.getMainLooper());
            getWindow().addOnFrameMetricsAvailableListener(mFrameMetricsListener, h);
        }
        Trace.endSection();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if (mWarmedUp) {
            // After our initial frames, this app should always be in focus.
            String state = hasFocus ? "gain" : "loss";
            reportAssumptionFailure("Unexpected " + state + " of focus");
        }
        mHasFocus = hasFocus;
    }

    private void initFrameRate() {
        Display display = getDisplay();
        Display.Mode currentMode = display.getMode();
        mDisplayId = display.getDisplayId();
        float minimumFrameRate = MINIMUM_TEST_FRAME_RATE;
        if (Build.VERSION.SDK_INT >= 36) {
            // This API wasn't introduced until API level 36, so for testing
            // on older devices, we'll just stick with our MINIMUM.
            // TODO(b/442635053): Allow switching between NORMAL and HIGH here,
            //     so we can also test against the HIGH rate.
            minimumFrameRate =
                display.getSuggestedFrameRate(Display.FRAME_RATE_CATEGORY_NORMAL);
        }
        if (minimumFrameRate < MINIMUM_TEST_FRAME_RATE) {
            Log.w(LOG_TAG, "getSuggestedFrameRate (" + minimumFrameRate
                    + "Hz) is below our testing minimum (" + MINIMUM_TEST_FRAME_RATE
                    + "Hz); using the latter for our minimum.");
            minimumFrameRate = MINIMUM_TEST_FRAME_RATE;
        }
        setFrameRate(currentMode.getRefreshRate());
        if (mFrameRate >= minimumFrameRate) {
            // The default frame rate is sufficient for our testing.
            return;
        }

        String minRateStr = minimumFrameRate + "Hz";
        // Using a Warning here, because this seems unexpected that a device
        // defaults to running at below this rate.
        Log.w(LOG_TAG, "App launched with frame rate (" + mFrameRate
                  + "Hz), below the acceptable/expected minimum (" + minRateStr + ")");

        // If available at our current resolution, use 60Hz.  If not, use the
        // lowest refresh rate above 60Hz which is available.  Otherwise, throw
        // an exception which kills the app.
        float preferredRate = Float.POSITIVE_INFINITY;

        for (Display.Mode mode : display.getSupportedModes()) {
            if ((currentMode.getPhysicalHeight() != mode.getPhysicalHeight())
                    || (currentMode.getPhysicalWidth() != mode.getPhysicalWidth())) {
                // This is a different resolution; we'll skip it.
                continue;
            }
            float rate = mode.getRefreshRate();
            if (rate == minimumFrameRate) {
                // This is exactly what we were hoping for, so we can stop
                // looking.
                preferredRate = rate;
                break;
            }
            if ((rate > minimumFrameRate) && (rate < preferredRate)) {
                // This is the best rate we've seen so far in terms of being
                // closest to our desired rate without being under it.
                preferredRate = rate;
            }
        }
        if (preferredRate == Float.POSITIVE_INFINITY) {
            String msg = "No display mode with at least " + minRateStr;
            throw new RuntimeException(msg);
        }
        Log.i(LOG_TAG, "Requesting to run at " + preferredRate + "Hz");
        Window window = getWindow();
        WindowManager.LayoutParams params = window.getAttributes();
        params.preferredRefreshRate = preferredRate;
        window.setAttributes(params);
    }

    private void setFrameRate(float frameRate) {
        mFrameRate = frameRate;

        if (mTraceState != TRACE_STATE_PRE_TEST_TIME) {
            String msg = "Got new frame rate (" + frameRate + ") after "
                    + mFrameCount + " frames, later than first relevant frame "
                    + mActualFirstRelevantFrame;
            reportAssumptionFailure(msg);
        }
        Log.i(LOG_TAG, "Running at frame rate " + mFrameRate + "Hz");

        mMinFirstRelevantFrame =
            Math.round(INITIAL_MIN_TIME_TO_IGNORE_IN_SECONDS * mFrameRate);
        mMaxFirstRelevantFrame =
            Math.round(INITIAL_MAX_TIME_TO_IGNORE_IN_SECONDS * mFrameRate);
    }

    private float nanosToMillis(long nanos) {
        return nanos / (1_000_000.0f);
    }

    private void reportAssumptionFailure(String msg) {
        Log.e(LOG_TAG, "ASSUMPTION FAILURE.  " + msg);
        if (ASSUMPTION_FAILURE_FORCES_EXIT) {
            Log.e(LOG_TAG, "Exiting app due to assumption failure.");
            System.exit(1);
        }
    }

    private boolean isReadyToStartTesting() {
        // We should only be checking this when we're before the testing time.
        assert mTraceState == TRACE_STATE_PRE_TEST_TIME;

        if (mFrameCount < mMinFirstRelevantFrame) {
            return false;
        }
        if (mHasFocus) {
            // We have the focus and we've reached our min first frame.
            // Let's set our last frame and start testing.
            mActualFirstRelevantFrame = mFrameCount;
            mLastRelevantFrame = mActualFirstRelevantFrame
                    + (AUTOMATED_TEST_DURATION_IN_SECONDS * (int) mFrameRate);
            return true;
        }

        if (mFrameCount > mMaxFirstRelevantFrame) {
            String msg = "App does not have focus after " + mFrameCount + " frames";
            reportAssumptionFailure(msg);
        }
        return false;
    }
}
