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

package com.prefabulated.bouncyball;

import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.Trace;
import android.util.Log;
import android.view.Display;
import android.view.Window;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;

import java.util.concurrent.Executors;

public class BouncyBallActivity extends AppCompatActivity {
    private static final String LOG_TAG = "BouncyBall";
    private static final float DESIRED_FRAME_RATE = 60.0f;

    private int mDisplayId = -1;
    private float mFrameRate;
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
                    mFrameRate = getDisplay().getMode().getRefreshRate();
                    Log.d(LOG_TAG, "Using frame rate " + mFrameRate + "Hz");
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
        Trace.endSection();
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
        mFrameRate = currentMode.getRefreshRate();
        if (mFrameRate == DESIRED_FRAME_RATE) {
            Log.d(LOG_TAG, "Already running at " + mFrameRate + "Hz");
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
        Log.d(LOG_TAG, "Changing preferred rate from " + mFrameRate + "Hz to "
                + preferredRate + "Hz");
        Window window = getWindow();
        WindowManager.LayoutParams params = window.getAttributes();
        params.preferredRefreshRate = preferredRate;
        window.setAttributes(params);
    }
}
