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

package android.view.input;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.Handler;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.InputEventCompatProcessor;
import android.view.MotionEvent;

import java.util.List;

/**
 * This rewrites {@link MotionEvent} to have {@link MotionEvent#TOOL_TYPE_FINGER} and
 * {@link InputDevice.SOURCE_TOUCHSCREEN} if the event is from mouse (or touchpad) if per-app
 * overrides is enabled on the target application.
 *
 * @hide
 */
public class MouseToTouchProcessor extends InputEventCompatProcessor {
    private static final String TAG = MouseToTouchProcessor.class.getSimpleName();

    /**
     * Return {@code true} if this compatibility is required based on the given context.
     *
     * <p>For debugging, you can toggle this by the following command:
     * - adb shell am compat enable|disable OVERRIDE_MOUSE_TO_TOUCH [pkg_name]
     */
    public static boolean isCompatibilityNeeded(Context context) {
        if (!com.android.hardware.input.Flags.mouseToTouchPerAppCompat()) {
            return false;
        }

        return InputEventCompatHandler.isPcInputCompatibilityNeeded(
                context, ActivityInfo.OVERRIDE_MOUSE_TO_TOUCH);
    }

    public MouseToTouchProcessor(Context context, Handler handler) {
        super(context, handler);
    }

    @Override
    public List<InputEvent> processInputEventForCompatibility(@NonNull InputEvent event) {
        // TODO(b/413207127): Implement the feature.
        return null;
    }

    @Nullable
    @Override
    public InputEvent processInputEventBeforeFinish(@NonNull InputEvent inputEvent) {
        return inputEvent;
    }
}
