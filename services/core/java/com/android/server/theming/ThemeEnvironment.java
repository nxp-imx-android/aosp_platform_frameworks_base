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

package com.android.server.theming;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManagerInternal;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.theming.FieldColorSource;
import android.content.theming.ThemeSettings;
import android.content.theming.ThemeStyle;
import android.graphics.Color;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.pm.RoSystemFeatures;
import com.android.server.LocalServices;
import com.android.server.pm.UserManagerInternal;

import com.google.ux.material.libmonet.dynamiccolor.ColorSpec.SpecVersion;
import com.google.ux.material.libmonet.dynamiccolor.DynamicScheme.Platform;

import java.util.Objects;

/**
 * Centralized configuration, system signals, and policy for the Theme Service.
 *
 * <p>This class encapsulates:
 * <ul>
 *   <li>Immutable system configuration determined at service start (e.g., platform, specs).</li>
 *   <li>Dynamic system state queries (e.g., boot status, device lock state).</li>
 *   <li>Shared policy decisions (e.g., whether to process user events).</li>
 * </ul>
 *
 * <p>It serves as a single source of truth for environmental factors affecting theming logic.
 *
 * @hide
 */
@VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
public final class ThemeEnvironment {
    private static final String TAG = "ThemeEnvironment";
    private static final String KEY_COLOR_PALETTE_VERSION = "global_color_palette_version";

    private ThemeUserLifecycle mThemeUserLifecycle;

    private final UserManagerInternal mUserManager;
    private final ActivityManagerInternal mActivityManager;
    private KeyguardManager mKeyguardManager;

    // --- IMMUTABLE SYSTEM SIGNALS ---

    /** The target platform for color generation (e.g., PHONE, WATCH). */
    final Platform platform;

    /** The version of the color specification being used. */
    final SpecVersion specVersion;

    /** Whether the system palette is outdated and needs a forced refresh. */
    final boolean isPaletteOutdated;

    /** The hardware color code for the device. */
    final String hardwareColorCode;

    // --- STATIC RESOURCE CONFIGURATION ---

    /** List of legacy overlay packages that should be cleaned up on boot. */
    final String[] legacyOverlays;

    /** Device-specific default theme data from resources. */
    final String[] defaultThemeData;

    /** The ultimate fallback theme settings when no user or device defaults are available. */
    final ThemeSettings hardcodedFallback;

    // --- DYNAMIC GLOBAL STATE ---

    /**
     * Whether the system is currently in the boot phase.
     * Transitions from {@code true} to {@code false} exactly once during boot.
     */
    private volatile boolean mIsBooting = true;

    ThemeEnvironment(@NonNull Context context,
            @NonNull SystemPropertiesReader reader) {

        mUserManager = LocalServices.getService(UserManagerInternal.class);
        mActivityManager = LocalServices.getService(ActivityManagerInternal.class);

        // 1. Detect Hardware/Software Capabilities
        platform = RoSystemFeatures.hasFeatureWatch(context)
                ? Platform.WATCH : Platform.PHONE;

        specVersion = context.getResources().getIdentifier(
                "system_primary_dim_light", "color", "android") != 0
                ? SpecVersion.SPEC_2025 : SpecVersion.SPEC_2021;

        // 2. Load Static Configurations
        legacyOverlays = context.getResources().getStringArray(
                com.android.internal.R.array.theming_legacy_overlays);
        defaultThemeData = context.getResources().getStringArray(
                com.android.internal.R.array.theming_defaults);

        hardcodedFallback = new ThemeSettings.Builder()
                .setThemeStyle(ThemeStyle.TONAL_SPOT)
                .setColorSource(FieldColorSource.VALUE_PRESET)
                .setSystemPalette(Color.valueOf(0xFF1B6EF3))
                .build();

        // 3. One-time check for version updates (determined at start)
        isPaletteOutdated = checkPaletteOutdated(context, reader);

        // 4. Read hardware color code
        hardwareColorCode = reader.get("ro.boot.hardware.color", "");
    }

    /**
     * Returns whether the system is currently in the boot phase.
     */
    boolean isBooting() {
        return mIsBooting;
    }

    /**
     * Marks the boot phase as complete. This should be called when the boot animation dismisses.
     */
    void setBootingComplete(ThemeUserLifecycle userLifecycle) {
        if (!isBooting()) return;
        mThemeUserLifecycle = userLifecycle;
        mIsBooting = false;
        Slog.d(TAG, "Boot phase complete.");
    }

    void onServicesReady(KeyguardManager keyguardManager) {
        mKeyguardManager = keyguardManager;
    }

    int getCurrentUserId() {
        return mActivityManager.getCurrentUserId();
    }

    /**
     * Returns true if the user is a valid target for theme updates based on system policy.
     * <p>
     * Returns false for the system user in HSUM, or for profiles (which are handled via their
     * parents).
     */
    boolean isManagedUser(int userId) {
        if (mUserManager.isHeadlessSystemUserMode() && userId == UserHandle.USER_SYSTEM) {
            return false;
        }

        int parentId = mUserManager.getProfileParentId(userId);
        if (parentId != userId) {
            return false;
        }

        return true;
    }

    /**
     * Determines if a theme-related event should be ignored for a specific user.
     *
     * @param userId     The ID of the user to check.
     * @param methodName The name of the calling method for logging purposes.
     * @return {@code true} if the event should be ignored because the user is not managed
     * or their state could not be loaded; {@code false} otherwise.
     */
    boolean shouldIgnoreEventForUser(int userId, String methodName) {
        return shouldIgnoreEventForUser(userId, methodName, false /* skipLazyLoad */);
    }

    boolean shouldIgnoreEventForUser(int userId, String methodName, boolean skipLazyLoad) {
        if (isBooting()) {
            return true;
        }

        // Use unified environment policy
        if (!isManagedUser(userId)) {
            Slog.d(TAG,
                    "Bypassing '" + methodName + "' for user " + userId + " per system policy.");
            return true;
        }

        if (!skipLazyLoad && mThemeUserLifecycle != null) {
            // Lazy load the user state if it's missing. This handles race conditions where
            // settings/events for a user arrive before the user lifecycle "START" event.
            if (!mThemeUserLifecycle.loadUserStateAndNotifyStateManager(userId)) {
                Slog.d(TAG, "Ignoring '" + methodName + "' for user " + userId
                        + " (State load failed)");
                return true;
            }
        }

        return false;
    }

    @Nullable
    Integer parentOf(int userId) {
        int possibleParentID = mUserManager.getProfileParentId(userId);
        return possibleParentID == userId ? null : possibleParentID;
    }

    private static boolean checkPaletteOutdated(Context context, SystemPropertiesReader reader) {
        String storedVersion = Settings.Global.getString(context.getContentResolver(),
                KEY_COLOR_PALETTE_VERSION);
        String currentVersion = reader.get("ro.build.date.utc", null);

        if (TextUtils.isEmpty(currentVersion)) {
            Slog.i(TAG, "Palette version missing. Refreshing overlays");
            return true;
        }

        if (storedVersion != null && Objects.equals(storedVersion, currentVersion)) {
            return false;
        }

        Slog.i(TAG, "Palette version bumped from " + storedVersion + " to " + currentVersion);
        Settings.Global.putString(context.getContentResolver(), KEY_COLOR_PALETTE_VERSION,
                currentVersion);
        return true;
    }

    boolean isDeviceLocked() {
        return mKeyguardManager.isDeviceLocked();
    }
}