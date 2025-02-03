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

import android.annotation.Nullable;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.theming.ThemeStyle;
import android.os.UserHandle;
import android.util.Slog;
import android.util.SparseArray;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.LocalServices;
import com.android.server.om.OverlayManagerInternal;
import com.android.server.pm.UserManagerInternal;
import com.android.systemui.monet.ColorScheme;

import java.io.PrintWriter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * This class manages the application of theme overlays across the system for all users.
 * It handles changes in seed color, style, contrast, and user settings to ensure the
 * correct theme overlays are applied.
 * The ThemeStateManager class is responsible for:
 * <ol>
 * Maintaining theme state: It tracks the current and pending theme state for each user,
 * including seed color, style, contrast, and associated profile IDs.
 * </ol><ol>
 * Reacting to theme events: It listens for and processes events that affect the theme state,
 * such as wallpaper changes, user settings modifications, and system events
 * (e.g., user start, setup completion).
 * </ol><ol>
 * Applying theme overlays: It generates ColorSchemes based on the current theme state
 * and coordinates with {@link ThemeOverlayHelper} to apply them.
 * </ol><ol>
 * Debouncing overlay updates: It debounces rapid theme changes to prevent excessive
 * overlay updates, ensuring a smooth user experience.
 * </ol><ol>
 * Handling user lifecycle: It manages theme state for different users and profiles,
 * including adding new users, handling user switching, and applying overlays
 * to the appropriate profiles.
 * </ol><ol>
 * Verifying overlay application: It checks if the applied overlays match the current theme
 * settings and forces an update if necessary, especially during boot complete.
 * </ol>
 *
 * @hide
 */
public class ThemeStateManager {
    private static final String TAG = "ThemeStateManager";

    protected static final long DEBOUNCE_MS = 50;

    // We are storing states for users only. Profiles should target their parent users.
    private final SparseArray<ThemeStatePair> mThemeStates = new SparseArray<>();

    private final Context mContext;
    private final ScheduledExecutorService mSchedulerExecutor;

    private UserManagerInternal mUserManager;
    private OverlayManagerInternal mOverlayManager;
    private KeyguardManager mKeyguardManager;

    ThemeStateManager(Context context) {
        this(context, Executors.newSingleThreadScheduledExecutor());
    }

    @VisibleForTesting
    ThemeStateManager(Context context, ScheduledExecutorService schedulerExecutor) {
        mSchedulerExecutor = schedulerExecutor;
        mContext = context;
    }

    // HANDLERS

    /**
     * Called when all system services are ready.
     * Initializes the necessary internal components.
     */
    void onServicesReady() {
        mUserManager = LocalServices.getService(UserManagerInternal.class);
        mKeyguardManager = mContext.getSystemService(KeyguardManager.class);
        mOverlayManager = LocalServices.getService(OverlayManagerInternal.class);
    }

    /**
     * Called when the wallpaper colors change or user chooses a preset.
     *
     * @param userId            The ID of the user updating the theme.
     * @param seedColor         Seed color to generate palettes.
     * @param fromForegroundApp Boolean indicating if the event came from a foreground app.
     */
    void onSeedColorChange(int userId, int seedColor, boolean fromForegroundApp) {
        if (!fromForegroundApp && mKeyguardManager != null && !mKeyguardManager.isDeviceLocked()) {
            getState(userId).setDeferUpdatesOnLock(true);
            Slog.w(TAG, "Wallpaper changed from background app, deferring color change");
        }

        getState(userId).applySeedColor(seedColor);
        reevaluateSystemTheme();
    }

    /**
     * Called when the theming style is changed.
     *
     * @param userId    The ID of the user updating the theme.
     * @param userStyle The {@link ThemeStyle} to be applied.
     */
    void onStyleChange(int userId, @ThemeStyle.Type Integer userStyle) {
        getState(userId).applyStyle(userStyle);
        reevaluateSystemTheme();
    }

    /**
     * Called when the display contrast setting changes.
     *
     * @param userId The ID of the user updating the theme.
     * @param value  The new contrast value.
     */
    void onContrastChange(int userId, float value) {
        getState(userId).applyContrast(value);
        reevaluateSystemTheme();
    }

    /**
     * Called when the setup process is completed for a user.
     *
     * @param userId The ID of the user who completed setup.
     */
    void onFinishSetup(int userId) {
        getState(userId).applySetupComplete();
        reevaluateSystemTheme();
    }

    /**
     * Called when a new profile is added for a user.
     *
     * @param userId  The ID of the user the profile belongs to.
     * @param profile The ID of the new profile.
     */
    void onProfileAdd(int userId, int profile) {
        getState(userId).addProfile(profile);
        reevaluateSystemTheme();
    }

    /**
     * Called when the device locks or unlocks.
     *
     * @param isLocked {@code true} if the device is locked, {@code false} otherwise.
     */
    void onLockStateChange(boolean isLocked) {
        if (!isLocked) return;

        for (int i = 0; i < mThemeStates.size(); i++) {
            int key = mThemeStates.keyAt(i);
            ThemeStatePair statePair = getState(key);
            if (statePair.areUpdatesDeferredOnLock()) {
                statePair.setDeferUpdatesOnLock(false);
                Slog.w(TAG, "Applying deferred wallpaper color change");
            }
        }
        reevaluateSystemTheme();
    }

    /**
     * Called when a user starts (logs in or unlocks their profile).
     *
     * @param userHandle The {@link UserHandle} of the user starting.
     * @param isSetup    {@code true} if the user has completed setup, {@code false} otherwise.
     * @param seedColor  The initial seed color for the user's theme.
     * @param contrast   The initial contrast value for the user's theme.
     * @param style      The initial style for the user's theme.
     */
    void onUserStart(UserHandle userHandle, boolean isSetup, int seedColor, float contrast,
            @ThemeStyle.Type Integer style) {
        int userId = userHandle.getIdentifier();

        if (mThemeStates.contains(userId)) {
            throw new IllegalStateException("ThemeStatePair already exists for user " + userId);
        }

        Integer parentId = parentOf(userId);

        if (parentId != null) {
            Slog.d(TAG, "Skipping State creation for profile '" + userId + "' with parent '"
                    + parentId + "'. Only states for top-level users are allowed.");
            getState(parentId).addProfile(userId);
            return;
        }

        mThemeStates.put(userId, new ThemeStatePair(userId, isSetup, seedColor, contrast, style));
    }

    /**
     * Called when switching between users.
     *
     * @param from The ID of the previous user, or {@code null} if there was none.
     * @param to   The ID of the new user.
     */
    void onUserSwitching(@Nullable Integer from, int to) {
        Slog.d(TAG, "User switching from " + from + " to " + to + ". Re-applying theme.");
        getState(to).forceUpdate();
        reevaluateSystemTheme();
    }

    /**
     * Called when the boot process is complete. This method checks if the applied
     * overlays match the current theme settings and forces an update if necessary.
     */
    void onBootComplete() {
        boolean shouldEvaluateOnBoot = false;
        for (int i = 0; i < mThemeStates.size(); i++) {
            int key = mThemeStates.keyAt(i);
            ThemeStatePair statePair = getState(key);

            if (!statePair.isColorSchemeApplied(mContext)) {
                Slog.d(TAG, "Color palette does not match user " + statePair.userId
                        + " settings, requesting update.");
                statePair.forceUpdate();
                shouldEvaluateOnBoot = true;
            } else {
                Slog.d(TAG,
                        "Applied color palette for user " + statePair.userId
                                + " matches settings.");
            }
        }

        if (shouldEvaluateOnBoot) {
            Slog.d(TAG, "One or more users have outdated color palettes; update requested.");
            reevaluateSystemTheme();
        }
    }


    ThemeStatePair getState(int userId) {
        if (!mThemeStates.contains(userId)) {
            throw new IllegalStateException(
                    "State not found for user " + userId);
        }

        return mThemeStates.get(userId);
    }

    /**
     * Re-evaluates the current system theme for all users and updates overlays if necessary.
     */
    void reevaluateSystemTheme() {
        for (int i = 0; i < mThemeStates.size(); i++) {
            final int key = mThemeStates.keyAt(i);
            final ThemeStatePair statePair = getState(key);

            if (!statePair.shouldUpdate()) {
                continue;
            }

            if (statePair.getFuture() != null) {
                statePair.getFuture().cancel(true);
                Slog.d(TAG, "Debouncing update for user " + statePair.userId);
            }

            ScheduledFuture<?> scheduled = mSchedulerExecutor.schedule(
                    () -> {
                        Slog.d(TAG, "Updating user " + statePair.userId + " with "
                                + statePair.getPendingState().toString());

                        long beginT = System.currentTimeMillis();

                        final ColorScheme newDarkScheme;
                        final ColorScheme newLightScheme;

                        if (statePair.shouldUpdateOverlays()) {
                            newDarkScheme = statePair.generatePendingScheme(true);
                            newLightScheme = statePair.generatePendingScheme(false);
                            Slog.d(TAG, "User " + statePair.userId + " has new overlays");
                        } else {
                            newDarkScheme = statePair.getDarkScheme();
                            newLightScheme = statePair.getLightScheme();
                        }
                        // Always update the state to commit the pending changes.
                        statePair.update(newDarkScheme, newLightScheme);

                        // If only profiles changed, we still apply overlays. The helper will get
                        // the current (and correct) schemes from the statePair.
                        ThemeOverlayHelper.applyCurrentStateOverlays(mOverlayManager, statePair);

                        statePair.clearTimer();

                        Slog.d(TAG,
                                "Overlay application for user " + statePair.userId
                                        + " completed in "
                                        + (System.currentTimeMillis() - beginT) + "ms");
                    },
                    DEBOUNCE_MS, TimeUnit.MILLISECONDS);

            statePair.setFuture(scheduled);
        }
    }

    @Nullable
    Integer parentOf(int userId) {
        int possibleParentID = mUserManager.getProfileParentId(userId);
        return possibleParentID == userId ? null : possibleParentID;
    }

    /**
     * Dumps the current state of the ThemeStateManager to the provided PrintWriter.
     *
     * @param pw The PrintWriter to dump the state to.
     */
    public void dump(PrintWriter pw) {
        pw.println("In-Memory Theme States per User:");
        for (int i = 0; i < mThemeStates.size(); i++) {
            int userId = mThemeStates.keyAt(i);
            ThemeStatePair statePair = mThemeStates.valueAt(i);
            pw.println("  User " + userId + ":");
            statePair.dump(pw);
        }
    }
}
