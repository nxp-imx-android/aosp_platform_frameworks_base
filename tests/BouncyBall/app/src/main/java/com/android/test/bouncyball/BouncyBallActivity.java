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

import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.Trace;
import android.util.Log;
import android.view.Choreographer;
import android.view.Display;
import android.view.Window;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;

import java.util.concurrent.Executors;

public class BouncyBallActivity extends AppCompatActivity {
    // Since logging (to logcat) takes system resources, we chose not to log
    // data every frame by default.
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

    private static final float DESIRED_FRAME_RATE = 60.0f;

    // Our focus isn't smoothness on startup; it's smoothness once we're
    // running.  So we ignore frame drops in the first 0.1 seconds.
    private static final int INITIAL_FRAMES_TO_IGNORE = 6;

    private int mDisplayId = -1;
    private boolean mHasFocus = false;
    private boolean mWarmedUp = false;
    private float mFrameRate;
    private long mFrameMaxDurationNanos;
    private int mNumFramesDropped = 0;
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
                    setFrameRate(getDisplay().getMode().getRefreshRate());
                    Log.i(LOG_TAG, "Using frame rate " + mFrameRate + "Hz");
                }
            };

    private final Choreographer.FrameCallback mFrameCallback =
            new Choreographer.FrameCallback() {

                private long mLastFrameTimeNanos = -1;
                private int mFrameCount = 0;

                @Override
                public void doFrame(long frameTimeNanos) {
                    if (mFrameCount == INITIAL_FRAMES_TO_IGNORE) {
                        mWarmedUp = true;
                        if (!mHasFocus) {
                            String msg = "App does not have focus after "
                                    + mFrameCount + " frames";
                            reportAssumptionFailure(msg);
                        }
                    }
                    if (mWarmedUp) {
                        long elapsedNanos = frameTimeNanos - mLastFrameTimeNanos;
                        if (elapsedNanos > mFrameMaxDurationNanos) {
                            mNumFramesDropped++;
                            Log.e(LOG_TAG, "FRAME DROPPED (total " + mNumFramesDropped
                                    + "): Took " + nanosToMillis(elapsedNanos) + "ms");
                        } else if (LOG_EVERY_FRAME) {
                            Log.d(LOG_TAG, "Frame " + mFrameCount + " took "
                                    + nanosToMillis(elapsedNanos) + "ms");
                        }
                    }
                    mLastFrameTimeNanos = frameTimeNanos;
                    mFrameCount++;
                    if (FORCE_DROPPED_FRAMES) {
                        dropFrameSometimes();
                    }
                    // Request the next frame callback
                    mChoreographer.postFrameCallback(this);
                }

                private void dropFrameSometimes() {
                    if ((mFrameCount % 64) == 0) {
                        try {
                            Thread.sleep((long) nanosToMillis(mFrameMaxDurationNanos) + 1);
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Trace.beginSection("BouncyBallActivity onCreate");
        setContentView(R.layout.activity_bouncy_ball);

        DisplayManager manager = getSystemService(DisplayManager.class);
        manager.registerDisplayListener(Executors.newSingleThreadExecutor(),
                                        DisplayManager.EVENT_TYPE_DISPLAY_REFRESH_RATE,
                                        mDisplayListener);

        setFrameRatePreference();
        mChoreographer = Choreographer.getInstance();
        mChoreographer.postFrameCallback(mFrameCallback);
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

    // If available at our current resolution, use 60Hz.  If not, use the
    // lowest refresh rate above 60Hz which is available.  Otherwise, throw
    // an exception which kills the app.
    //
    // The philosophy is that, for now, we only require this test to run
    // solidly at 60Hz.  If a device has a higher refresh rate than that,
    // we slow it down to 60Hz to make this easier to pass.  If a device
    // isn't able to go all the way down to 60Hz, we use the lowest refresh
    // rate above 60Hz.  If the device only supports below 30Hz, that's below
    // our standards so we abort.
    private void setFrameRatePreference() {
        float preferredRate = Float.POSITIVE_INFINITY;

        Display display = getDisplay();
        Display.Mode currentMode = display.getMode();
        mDisplayId = display.getDisplayId();
        setFrameRate(currentMode.getRefreshRate());
        if (mFrameRate == DESIRED_FRAME_RATE) {
            Log.i(LOG_TAG, "Already running at " + mFrameRate + "Hz");
            // We're already using what we want.  Nothing to do here.
            return;
        }

        for (Display.Mode mode : display.getSupportedModes()) {
            if ((currentMode.getPhysicalHeight() != mode.getPhysicalHeight())
                    || (currentMode.getPhysicalWidth() != mode.getPhysicalWidth())) {
                // This is a different resolution; we'll skip it.
                continue;
            }
            float rate = mode.getRefreshRate();
            if (rate == DESIRED_FRAME_RATE) {
                // This is exactly what we were hoping for, so we can stop
                // looking.
                preferredRate = rate;
                break;
            }
            if ((rate > DESIRED_FRAME_RATE) && (rate < preferredRate)) {
                // This is the best rate we've seen so far in terms of being
                // closest to our desired rate without being under it.
                preferredRate = rate;
            }
        }
        if (preferredRate == Float.POSITIVE_INFINITY) {
            String msg = "No display mode with at least " + DESIRED_FRAME_RATE + "Hz";
            throw new RuntimeException(msg);
        }
        Log.i(LOG_TAG, "Changing preferred rate from " + mFrameRate + "Hz to "
                + preferredRate + "Hz");
        Window window = getWindow();
        WindowManager.LayoutParams params = window.getAttributes();
        params.preferredRefreshRate = preferredRate;
        window.setAttributes(params);
    }

    private void setFrameRate(float frameRate) {
        mFrameRate = frameRate;
        float frameMaxDurationMillis = 1_000.0f / mFrameRate;
        // There is a little +/- of when our callback is called.  So we allow
        // up to 25% beyond this before considering it a frame drop.  Since
        // a frame drop should mean getting a value near double (or higher),
        // allowing 25% shouldn't have us missing legitimate drops.
        frameMaxDurationMillis *= 1.25f;
        // We store as nanoseconds, to avoid per-frame floating point math in
        // the common case.
        mFrameMaxDurationNanos = ((long) frameMaxDurationMillis) * 1_000_000;
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
}
