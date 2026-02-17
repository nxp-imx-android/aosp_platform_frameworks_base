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

import static android.content.theming.FieldColorSource.VALUE_PRESET;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.theming.ThemeSettings;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.os.BackgroundThread;
import com.android.server.SystemService;
import com.android.server.UiModeManagerInternal;
import com.android.server.pm.UserManagerInternal;

/**
 * Handles user lifecycle events for the ThemeManagerService.
 * <p>
 * This class listens for when users start, switch, or complete actions.
 * It then tells the ThemeStateManager to update the theme if needed.
 *
 * @hide
 */
public class ThemeUserLifecycle {
    private static final String TAG = "ThemeUserLifecycle";

    private final Context mContext;
    private final ThemeStateManager mStateManager;
    private final ThemeSettingsManager mThemeSettingsManager;
    private final ThemeEnvironment mEnvironment;

    private ThemeWallpaperManager mWallpaperManager;
    private UserManagerInternal mUserManagerInternal;
    private UiModeManagerInternal mUiModeManagerInternal;

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public ThemeUserLifecycle(@NonNull Context context, @NonNull ThemeStateManager stateManager,
            @NonNull ThemeSettingsManager themeSettingsManager,
            @NonNull ThemeEnvironment environment) {
        mContext = context;
        mStateManager = stateManager;
        mThemeSettingsManager = themeSettingsManager;
        mEnvironment = environment;
    }

    /**
     * Called when all system services are ready.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void onServicesReady(UserManagerInternal userManager,
            UiModeManagerInternal uiModeManager, ThemeWallpaperManager wallpaperManager) {
        mUserManagerInternal = userManager;
        mUiModeManagerInternal = uiModeManager;
        mWallpaperManager = wallpaperManager;
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    public final BroadcastReceiver mUserLifecycleReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_PROFILE_ADDED.equals(intent.getAction())) {
                handleProfileAdded(intent);
            }
        }
    };

    /**
     * Registers for user-related system broadcasts.
     */
    public void registerListeners() {
        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PROFILE_ADDED);
        filter.addDataScheme("package");

        mContext.registerReceiver(mUserLifecycleReceiver, filter, null,
                BackgroundThread.getHandler());
    }

    /**
     * Called when a user starts.
     *
     * @param user The user that is starting.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void onUserStarting(@NonNull SystemService.TargetUser user) {
        int userId = user.getUserHandle().getIdentifier();

        if (shouldIgnoreUser(userId, "onUserStarting")) {
            return;
        }

        // check if seed color comes from wallpaper or preset
        ThemeSettings userSettings = mThemeSettingsManager.getSettingsOrDefault(userId,
                mContext.getContentResolver());

        int seedColor = getEffectiveSeedColor(userSettings, userId);

        Slog.d(TAG, "User: " + user.getUserIdentifier() + " starting");

        mStateManager.onUserStart(user.getUserHandle(),
                /*isSetup*/Settings.Secure.getIntForUser(mContext.getContentResolver(),
                        Settings.Secure.USER_SETUP_COMPLETE, 0, userId) == 1,
                /*seedColor*/ seedColor,
                /*contrast*/ mUiModeManagerInternal.getContrast(userId),
                /*style*/userSettings.themeStyle());
    }

    /**
     * Called when users switch.
     *
     * @param from The user being switched from.
     * @param to   The user being switched to.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void onUserSwitching(@Nullable SystemService.TargetUser from,
            @NonNull SystemService.TargetUser to) {
        if (shouldIgnoreUser(to.getUserIdentifier(), "onUserSwitching")) {
            return;
        }
        Slog.d(TAG, "User switch from:" + (from != null ? from.getUserIdentifier() : "-") + " to: "
                + to.getUserIdentifier());
        mStateManager.onUserSwitching(from != null ? from.getUserIdentifier() : null,
                to.getUserIdentifier());
    }

    /**
     * Called during boot complete to load all existing users' theme states.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void onBootCompleteLoadUsers() {
        int[] userIds = mUserManagerInternal.getUserIds();
        for (int userId : userIds) {
            loadUserStateAndNotifyStateManager(userId);
        }
    }

    /**
     * Loads a user's theme state and notifies the state manager.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public boolean loadUserStateAndNotifyStateManager(@UserIdInt int userId) {
        if (shouldIgnoreUser(userId, "loadUserStateAndNotifyStateManager")) {
            return false;
        }

        ThemeSettings userSettings = mThemeSettingsManager.getSettingsOrDefault(userId,
                mContext.getContentResolver());
        int seedColor = getEffectiveSeedColor(userSettings, userId);

        boolean isSetup = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.USER_SETUP_COMPLETE, 0, userId) == 1;

        mStateManager.onUserLoad(userId, isSetup, seedColor,
                mUiModeManagerInternal.getContrast(userId),
                userSettings.themeStyle());

        return mStateManager.hasState(userId);
    }

    private void handleProfileAdded(Intent intent) {
        UserHandle newUserHandle = intent.getParcelableExtra(Intent.EXTRA_USER, UserHandle.class);
        if (newUserHandle == null) return;
        int newUserOrProfileId = newUserHandle.getIdentifier();

        // Load the new user/profile's theme state immediately
        loadUserStateAndNotifyStateManager(newUserOrProfileId);

        Integer parentId = mStateManager.parentOf(newUserOrProfileId);
        if (parentId == null || !loadUserStateAndNotifyStateManager(parentId)) {
            return;
        }

        Slog.d(TAG, "User: " + newUserOrProfileId + " added to parent: " + parentId);
        mStateManager.onProfileAdd(parentId, newUserOrProfileId);
    }

    /**
     * Checks if we should ignore an event because of Headless System User Mode (HSUM)
     * or because the user is a profile.
     *
     * @param userId     The user ID to check.
     * @param methodName The name of the method calling this check, for logging.
     * @return {@code true} if we should ignore this event for this user.
     */
    private boolean shouldIgnoreUser(int userId, String methodName) {
        if (!mEnvironment.isManagedUser(userId)) {
            Slog.d(TAG, "Ignoring " + methodName + " for user " + userId + " per system policy.");
            return true;
        }

        return false;
    }

    private int getEffectiveSeedColor(ThemeSettings userSettings, int userId) {
        int seedColor;
        if (userSettings.colorSource().equals(VALUE_PRESET)) {
            seedColor = userSettings.systemPalette().toArgb();
        } else {
            Integer wallpaperSeed = mWallpaperManager.getSeedColor(userId);
            seedColor = wallpaperSeed != null ? wallpaperSeed
                    : userSettings.systemPalette().toArgb();
        }
        return seedColor;
    }
}
