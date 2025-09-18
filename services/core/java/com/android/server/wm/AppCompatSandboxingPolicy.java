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

import static android.app.WindowConfiguration.ROTATION_UNDEFINED;
import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;
import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import static android.content.res.Configuration.ORIENTATION_UNDEFINED;
import static android.view.Surface.ROTATION_270;
import static android.view.Surface.ROTATION_90;
import static android.window.DesktopModeFlags.EXCLUDE_CAPTION_FROM_APP_BOUNDS;

import static com.android.server.wm.AppCompatUtils.isInDesktopMode;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.WindowConfiguration.WindowingMode;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.graphics.Rect;
import android.view.DisplayInfo;

/**
 * Encapsulate logic related to sandboxing for app compatibility.
 */
class AppCompatSandboxingPolicy {

    @NonNull
    private final ActivityRecord mActivityRecord;

    AppCompatSandboxingPolicy(@NonNull ActivityRecord activityRecord) {
        mActivityRecord = activityRecord;
    }

    /**
     * In freeform, the container bounds are scaled with app bounds. Activity bounds can be
     * outside of its container bounds if insets are coupled with configuration outside of
     * freeform and maintained in freeform for size compat mode.
     *
     * <p>Sandbox activity bounds in freeform to app bounds to force app to display within the
     * container. This prevents UI cropping when activities can draw below insets which are
     * normally excluded from appBounds before targetSDK < 35
     * (see ConfigurationContainer#applySizeOverrideIfNeeded).
     */
    void sandboxBoundsIfNeeded(@NonNull Configuration resolvedConfig,
            @WindowingMode int windowingMode) {
        if (!EXCLUDE_CAPTION_FROM_APP_BOUNDS.isTrue()) {
            return;
        }

        if (isInDesktopMode(mActivityRecord.mAtmService.mContext, windowingMode)) {
            Rect appBounds = resolvedConfig.windowConfiguration.getAppBounds();
            if (appBounds == null || appBounds.isEmpty()) {
                // When there is no override bounds, the activity will inherit the bounds from
                // parent.
                appBounds = mActivityRecord.mResolveConfigHint.mParentAppBoundsOverride;
            }
            if (!resolvedConfig.windowConfiguration.getBounds().isEmpty()) {
                // Only set if there is a resolved override config.
                resolvedConfig.windowConfiguration.setBounds(appBounds);
            }
        }
    }

    /**
     * Contains sandboxed parent configuration important for resolving activity window
     * configuration within the sandboxed parent bounds. Original parent configuration is
     * unaffected.
     */
    static class ConfigOverrideHint {
        @Nullable DisplayInfo mTmpOverrideDisplayInfo;
        @Nullable AppCompatDisplayInsets mTmpCompatInsets;
        @Nullable Rect mParentAppBoundsOverride;
        @Nullable Rect mParentBoundsOverride;
        int mTmpOverrideConfigOrientation;
        boolean mUseOverrideInsetsForConfig;

        void resolveTmpOverrides(DisplayContent dc, Configuration parentConfig,
                boolean isFixedRotationTransforming, @Nullable Rect safeRegionBounds,
                boolean shouldApplyLegacyInsets) {
            mParentAppBoundsOverride = safeRegionBounds != null ? safeRegionBounds : new Rect(
                    parentConfig.windowConfiguration.getAppBounds());
            mParentBoundsOverride = safeRegionBounds != null ? safeRegionBounds : new Rect(
                    parentConfig.windowConfiguration.getBounds());
            mTmpOverrideConfigOrientation = parentConfig.orientation;
            Insets insets = Insets.NONE;
            if (safeRegionBounds != null) {
                // Modify orientation based on the parent app bounds if safe region bounds are set.
                mTmpOverrideConfigOrientation =
                        mParentAppBoundsOverride.height() >= mParentAppBoundsOverride.width()
                                ? ORIENTATION_PORTRAIT : ORIENTATION_LANDSCAPE;
            } else if (shouldApplyLegacyInsets && mUseOverrideInsetsForConfig && dc != null) {
                // Insets are decoupled from configuration by default from V+, use legacy
                // compatibility behaviour for apps targeting SDK earlier than 35
                // (see applySizeOverrideIfNeeded).
                int rotation = parentConfig.windowConfiguration.getRotation();
                if (rotation == ROTATION_UNDEFINED && !isFixedRotationTransforming) {
                    rotation = dc.getRotation();
                }
                final boolean rotated = (rotation == ROTATION_90 || rotation == ROTATION_270);
                final int dw = rotated ? dc.mBaseDisplayHeight : dc.mBaseDisplayWidth;
                final int dh = rotated ? dc.mBaseDisplayWidth : dc.mBaseDisplayHeight;
                DisplayPolicy.DecorInsets.Info decorInsets = dc.getDisplayPolicy()
                        .getDecorInsetsInfo(rotation, dw, dh);
                final Rect stableBounds = decorInsets.mOverrideConfigFrame;
                mTmpOverrideConfigOrientation = stableBounds.width() > stableBounds.height()
                        ? ORIENTATION_LANDSCAPE : ORIENTATION_PORTRAIT;
                insets = Insets.of(decorInsets.mOverrideNonDecorInsets);
            }
            mParentAppBoundsOverride.inset(insets);
        }

        void resetTmpOverrides() {
            mTmpOverrideDisplayInfo = null;
            mTmpCompatInsets = null;
            mTmpOverrideConfigOrientation = ORIENTATION_UNDEFINED;
        }
    }
}
