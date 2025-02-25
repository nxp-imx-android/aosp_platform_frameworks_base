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

package com.android.server.wm;

import android.annotation.NonNull;
import android.content.res.Configuration;
import android.graphics.Rect;

import java.io.PrintWriter;

/**
 * Encapsulate app compat policy logic related to a safe region.
 */
class AppCompatSafeRegionPolicy {
    @NonNull
    private final ActivityRecord mActivityRecord;
    // Whether the Activity needs to be in the safe region bounds.
    private boolean mNeedsSafeRegionBounds = false;
    // Denotes the latest safe region bounds. Can be empty if the activity or the ancestors do
    // not have any safe region bounds.
    @NonNull
    private final Rect mLatestSafeRegionBounds = new Rect();

    AppCompatSafeRegionPolicy(@NonNull ActivityRecord activityRecord) {
        mActivityRecord = activityRecord;
    }

    /**
     * Computes the latest safe region bounds in
     * {@link ActivityRecord#resolveOverrideConfiguration(Configuration)} since the activity has not
     * been attached to the parent container when the ActivityRecord is instantiated.
     *
     * @return latest safe region bounds as set on an ancestor window container.
     */
    public Rect getLatestSafeRegionBounds() {
        // Get the latest safe region bounds since the bounds could have changed
        final Rect latestSafeRegionBounds = mActivityRecord.getSafeRegionBounds();
        if (latestSafeRegionBounds != null) {
            mLatestSafeRegionBounds.set(latestSafeRegionBounds);
        } else {
            mLatestSafeRegionBounds.setEmpty();
        }
        return latestSafeRegionBounds;
    }

    /**
     * Computes bounds when letterboxing is required only for the safe region bounds.
     */
    public void resolveSafeRegionBoundsConfiguration(@NonNull Configuration resolvedConfig,
            @NonNull Configuration newParentConfig) {
        if (mLatestSafeRegionBounds.isEmpty()) {
            return;
        }
        resolvedConfig.windowConfiguration.setBounds(mLatestSafeRegionBounds);
        mActivityRecord.computeConfigByResolveHint(resolvedConfig, newParentConfig);
    }

    /**
     * @return {@code true} if the activity is letterboxed only due to the safe region being set on
     * the current or ancestor window container.
     */
    boolean isLetterboxedForSafeRegionOnly() {
        return !mActivityRecord.areBoundsLetterboxed() && getNeedsSafeRegionBounds()
                && getLatestSafeRegionBounds() != null;
    }

    /**
     * Set {@code true} if this activity needs to be within the safe region bounds, else false.
     */
    public void setNeedsSafeRegionBounds(boolean needsSafeRegionBounds) {
        mNeedsSafeRegionBounds = needsSafeRegionBounds;
    }

    /**
     * @return {@code true} if this activity needs to be within the safe region bounds.
     */
    public boolean getNeedsSafeRegionBounds() {
        return mNeedsSafeRegionBounds;
    }

    void dump(@NonNull PrintWriter pw, @NonNull String prefix) {
        if (mNeedsSafeRegionBounds) {
            pw.println(prefix + " mNeedsSafeRegionBounds=true");
        }
        pw.println(prefix + " isLetterboxedForSafeRegionOnly=" + isLetterboxedForSafeRegionOnly());
    }
}
