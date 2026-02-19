/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.internal.policy;

import android.annotation.NonNull;
import android.graphics.Rect;
import android.util.Size;

public final class DesktopModeLaunchBoundsUtils {
    /**
     * Proportion of window height top offset with respect to bottom offset, used for central task
     * positioning. Should be kept in sync with constant in
     * {@link com.android.wm.shell.desktopmode.DesktopTaskPosition}
     */
    public static final float WINDOW_HEIGHT_PROPORTION = 0.375f;

    /**
     * Adjusts bounds to be positioned in the middle of the screen.
     *
     * @param desiredSize the size of the window to be centered
     * @param screenBounds the bounds of the screen
     * @return the centered bounds
     */
    @NonNull
    public static Rect centerInScreen(@NonNull Size desiredSize,
            @NonNull Rect screenBounds) {
        final int heightOffset = (int)
                ((screenBounds.height() - desiredSize.getHeight()) * WINDOW_HEIGHT_PROPORTION);
        final int widthOffset = (screenBounds.width() - desiredSize.getWidth()) / 2;
        final Rect resultBounds = new Rect(0, 0,
                desiredSize.getWidth(), desiredSize.getHeight());
        resultBounds.offset(screenBounds.left + widthOffset, screenBounds.top + heightOffset);
        return resultBounds;
    }

    /**
     * Checks whether two rects are close enough to be considered same bounds.
     *
     * @param cascadingOffset the offset used for cascading windows
     * @param a the first rect
     * @param b the second rect
     * @return {@code true} if the two rects are close enough, {@code false} otherwise
     */
    public static boolean haveSameBoundsWithThreshold(int cascadingOffset, Rect a, Rect b) {
        // The threshold is set this way as this is especially useful for checking whether the
        // bounds of the new window is closer to the bounds of the previous window or the cascaded
        // bounds.
        int thresholdPx = cascadingOffset / 2;

        boolean leftClose = Math.abs(b.left - a.left) < thresholdPx;
        boolean topClose = Math.abs(b.top - a.top) < thresholdPx;
        boolean rightClose = Math.abs(b.right - a.right) < thresholdPx;
        boolean bottomClose = Math.abs(b.bottom - a.bottom) < thresholdPx;

        return leftClose && topClose && rightClose && bottomClose;
    }

    /**
     * Offsets the bounds by the given amount in both x and y directions.
     *
     * @param offset the amount to offset by
     * @param bounds the bounds to be offset
     */
    public static void cascadeOneStep(int offset, Rect bounds) {
        bounds.offset(offset, offset);
    }
}
