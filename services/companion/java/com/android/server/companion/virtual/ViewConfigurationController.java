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

package com.android.server.companion.virtual;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.companion.virtual.ViewConfigurationParams;
import android.content.Context;
import android.content.om.FabricatedOverlay;
import android.content.om.OverlayConstraint;
import android.content.om.OverlayIdentifier;
import android.content.om.OverlayManager;
import android.content.om.OverlayManagerTransaction;
import android.os.Binder;
import android.util.Slog;
import android.util.TypedValue;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.util.List;

/**
 * Controls the application of {@link ViewConfigurationParams} for a virtual device.
 */
@VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
public class ViewConfigurationController {

    private static final String TAG = "ViewConfigurationController";
    private static final String FRAMEWORK_PACKAGE_NAME = "android";

    private static final String TAP_TIMEOUT_RESOURCE_NAME = "integer/config_tapTimeoutMillis";
    private static final String DOUBLE_TAP_TIMEOUT_RESOURCE_NAME =
            "integer/config_doubleTapTimeoutMillis";
    private static final String DOUBLE_TAP_MIN_TIME_RESOURCE_NAME =
            "integer/config_doubleTapMinTimeMillis";
    private static final String TOUCH_SLOP_RESOURCE_NAME =
            "dimen/config_viewConfigurationTouchSlop";
    private static final String MIN_FLING_VELOCITY_RESOURCE_NAME =
            "dimen/config_viewMinFlingVelocity";
    private static final String MAX_FLING_VELOCITY_RESOURCE_NAME =
            "dimen/config_viewMaxFlingVelocity";
    private static final String SCROLL_FRICTION_RESOURCE_NAME = "dimen/config_scrollFriction";

    private final OverlayManager mOverlayManager;
    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private OverlayIdentifier mOverlayIdentifier = null;

    public ViewConfigurationController(@NonNull Context context) {
        mOverlayManager = context.getSystemService(OverlayManager.class);
    }

    /**
     * Applies given {@link ViewConfigurationParams} for the given {@code deviceId}.
     */
    public void applyViewConfigurationParams(int deviceId,
            @Nullable ViewConfigurationParams viewConfigurationParams) {
        if (viewConfigurationParams == null) {
            return;
        }

        FabricatedOverlay overlay = new FabricatedOverlay.Builder(
                FRAMEWORK_PACKAGE_NAME /* owningPackage */,
                "vdOverlay" + deviceId /* overlayName */,
                FRAMEWORK_PACKAGE_NAME /* targetPackage */)
                .build();
        OverlayIdentifier overlayIdentifier = overlay.getIdentifier();
        setResourceDpValue(overlay, TOUCH_SLOP_RESOURCE_NAME,
                viewConfigurationParams.getTouchSlopDp());
        setResourceDpValue(overlay, MIN_FLING_VELOCITY_RESOURCE_NAME,
                viewConfigurationParams.getMinimumFlingVelocityDpPerSecond());
        setResourceDpValue(overlay, MAX_FLING_VELOCITY_RESOURCE_NAME,
                viewConfigurationParams.getMaximumFlingVelocityDpPerSecond());
        setResourceFloatValue(overlay, SCROLL_FRICTION_RESOURCE_NAME,
                viewConfigurationParams.getScrollFriction());
        setResourceIntValue(overlay, TAP_TIMEOUT_RESOURCE_NAME,
                (int) viewConfigurationParams.getTapTimeoutDuration().toMillis());
        setResourceIntValue(overlay, DOUBLE_TAP_TIMEOUT_RESOURCE_NAME,
                (int) viewConfigurationParams.getDoubleTapTimeoutDuration().toMillis());
        setResourceIntValue(overlay, DOUBLE_TAP_MIN_TIME_RESOURCE_NAME,
                (int) viewConfigurationParams.getDoubleTapMinTimeDuration().toMillis());

        Binder.withCleanCallingIdentity(() -> {
            mOverlayManager.commit(
                    new OverlayManagerTransaction.Builder()
                            .registerFabricatedOverlay(overlay)
                            .setEnabled(overlayIdentifier, true /* enable */,
                                    List.of(new OverlayConstraint(OverlayConstraint.TYPE_DEVICE_ID,
                                            deviceId)))
                            .build());
            synchronized (mLock) {
                mOverlayIdentifier = overlayIdentifier;
            }
        });
    }

    /**
     * Clears the applied {@link ViewConfigurationParams}.
     */
    public void close() {
        OverlayManagerTransaction transaction;
        synchronized (mLock) {
            if (mOverlayIdentifier == null) {
                return;
            }

            transaction = new OverlayManagerTransaction.Builder().unregisterFabricatedOverlay(
                            mOverlayIdentifier).build();
        }

        Binder.withCleanCallingIdentity(() -> mOverlayManager.commit(transaction));
    }

    private static void setResourceDpValue(@NonNull FabricatedOverlay overlay,
            @NonNull String resourceName, float value) {
        if (value == ViewConfigurationParams.INVALID_VALUE) {
            return;
        }

        if (!android.content.res.Flags.dimensionFrro()) {
            Slog.e(TAG, "Dimension resource overlay is not supported");
            return;
        }

        overlay.setResourceValue(resourceName, value, TypedValue.COMPLEX_UNIT_DIP,
                null /* configuration */);
    }

    private static void setResourceFloatValue(@NonNull FabricatedOverlay overlay,
            @NonNull String resourceName, float value) {
        if (value == ViewConfigurationParams.INVALID_VALUE) {
            return;
        }

        if (!android.content.res.Flags.dimensionFrro()) {
            Slog.e(TAG, "Dimension resource overlay is not supported");
            return;
        }

        overlay.setResourceValue(resourceName, value, null /* configuration */);
    }

    private static void setResourceIntValue(@NonNull FabricatedOverlay overlay,
            @NonNull String resourceName, int value) {
        if (value == ViewConfigurationParams.INVALID_VALUE) {
            return;
        }

        overlay.setResourceValue(resourceName, TypedValue.TYPE_INT_DEC, value,
                null /* configuration */);
    }
}
