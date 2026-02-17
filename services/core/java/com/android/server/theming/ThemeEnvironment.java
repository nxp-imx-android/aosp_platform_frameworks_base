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
import android.content.Context;
import android.content.theming.FieldColorSource;
import android.content.theming.ThemeSettings;
import android.content.theming.ThemeStyle;
import android.graphics.Color;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Slog;

import com.android.internal.pm.RoSystemFeatures;
import com.android.server.pm.UserManagerInternal;

import com.google.ux.material.libmonet.dynamiccolor.ColorSpec.SpecVersion;
import com.google.ux.material.libmonet.dynamiccolor.DynamicScheme.Platform;

import java.util.Objects;

/**
 * Immutable system-wide configuration and environment signals for the Theme Service.
 *
 * <p>Calculated once at boot, these values provide a single source of truth for the
 * entire theming ecosystem.
 *
 * @hide
 */
public final class ThemeEnvironment {
    private static final String TAG = "ThemeEnvironment";
    private static final String KEY_COLOR_PALETTE_VERSION = "global_color_palette_version";

    UserManagerInternal mUserManager;

    // --- IMMUTABLE SYSTEM SIGNALS ---

    /** The target platform for color generation (e.g., PHONE, WATCH). */
    public final Platform platform;

    /** The version of the color specification being used. */
    public final SpecVersion specVersion;

    /** Whether the system palette is outdated and needs a forced refresh. */
    public final boolean isPaletteOutdated;

    // --- STATIC RESOURCE CONFIGURATION ---

    /** List of legacy overlay packages that should be cleaned up on boot. */
    public final String[] legacyOverlays;

    /** Device-specific default theme data from resources. */
    public final String[] defaultThemeData;

    /** The ultimate fallback theme settings when no user or device defaults are available. */
    public final ThemeSettings hardcodedFallback;

    // --- DYNAMIC GLOBAL STATE ---

    /**
     * Whether the system is currently in the boot phase.
     * Transitions from {@code true} to {@code false} exactly once during boot.
     */
    private volatile boolean mIsBooting = true;


    public ThemeEnvironment(@NonNull Context context,
            @NonNull UserManagerInternal userManager,
            @NonNull SystemPropertiesReader reader) {

        mUserManager = userManager;

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
    }

    /**
     * Returns whether the system is currently in the boot phase.
     */
    public boolean isBooting() {
        return mIsBooting;
    }

    /**
     * Marks the boot phase as complete. This should be called when the boot animation dismisses.
     */
    public void setBootingComplete() {
        Slog.d(TAG, "Boot phase complete.");
        mIsBooting = false;
    }

    /**
     * Returns true if the user is a valid target for theme updates based on system policy.
     * <p>
     * Returns false for the system user in HSUM, or for profiles (which are handled via their
     * parents).
     */
    public boolean isManagedUser(int userId) {
        if (mUserManager.isHeadlessSystemUserMode() && userId == UserHandle.USER_SYSTEM) {
            return false;
        }

        int parentId = mUserManager.getProfileParentId(userId);
        if (parentId != userId) {
            return false;
        }

        return true;
    }

    private boolean checkPaletteOutdated(Context context, SystemPropertiesReader reader) {
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
}
