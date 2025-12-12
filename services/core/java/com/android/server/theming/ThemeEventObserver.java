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

import android.Manifest;
import android.annotation.NonNull;
import android.annotation.RequiresPermission;
import android.app.ActivityManagerInternal;
import android.app.KeyguardManager;
import android.app.WallpaperColors;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.theming.ThemeSettings;
import android.content.theming.ThemeStyle;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.os.BackgroundThread;
import com.android.server.UiModeManagerInternal;
import com.android.systemui.monet.ColorScheme;

import java.util.Collection;
import java.util.concurrent.Executor;

/**
 * Observes system events and settings changes to trigger theme updates.
 * <p>
 * This class registers listeners for:
 * <ul>
 *     <li>Wallpaper color changes</li>
 *     <li>Theme setting changes (style, color source)</li>
 *     <li>User setup completion</li>
 *     <li>Device lock state changes</li>
 *     <li>Profile additions</li>
 *     <li>Overlay application results</li>
 * </ul>
 * It forwards these events to the {@link ThemeStateManager} or {@link ThemeManagerInternal}.
 *
 * @hide
 */
public class ThemeEventObserver {
    private static final String TAG = "ThemeEventObserver";

    private final Context mContext;
    private final ThemeStateManager mStateManager;
    private final ThemeManagerInternal mThemeManagerInternal;
    private final ThemeUserLifecycle mThemeUserLifecycle;

    private ThemeWallpaperManager mWallpaperManager;
    private ActivityManagerInternal mActivityManagerInternal;
    private UiModeManagerInternal mUiModeManagerInternal;
    private KeyguardManager mKeyguardManager;

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    ThemeEventObserver(Context context, ThemeStateManager stateManager,
            ThemeManagerInternal themeManagerInternal, ThemeUserLifecycle themeUserLifecycle) {
        mContext = context;
        mStateManager = stateManager;
        mThemeManagerInternal = themeManagerInternal;
        mThemeUserLifecycle = themeUserLifecycle;
    }

    /**
     * Called when the system is booting up.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    @RequiresPermission(Manifest.permission.SUBSCRIBE_TO_KEYGUARD_LOCKED_STATE)
    public void onServicesReady(ThemeWallpaperManager wallpaperManager,
            ActivityManagerInternal activityManager,
            UiModeManagerInternal uiModeManager, KeyguardManager keyguardManager) {
        mWallpaperManager = wallpaperManager;
        mActivityManagerInternal = activityManager;
        mUiModeManagerInternal = uiModeManager;
        mKeyguardManager = keyguardManager;
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (Intent.ACTION_PROFILE_ADDED.equals(action)) {
                handleProfileAdded(intent);
            } else if (Intent.ACTION_OVERLAY_CHANGED.equals(action)) {
                handleOverlayChanged(intent);
            }
        }
    };


    /**
     * Registers various listeners for system events and settings changes.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    @RequiresPermission(Manifest.permission.SUBSCRIBE_TO_KEYGUARD_LOCKED_STATE)
    public void registerListeners() {
        Executor bgExecutor = BackgroundThread.getExecutor();
        Handler bgHandler = BackgroundThread.getHandler();

        // Profile and overlay changes
        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PROFILE_ADDED);
        filter.addAction(Intent.ACTION_OVERLAY_CHANGED);
        filter.addDataScheme("package");

        mContext.registerReceiver(mBroadcastReceiver, filter, null, bgHandler);

        // Wallpaper Color Change
        mWallpaperManager.addOnColorsChangedListener(
                (wallpaperColors, which, displayId, userId, fromForegroundApp) -> {
                    handleWallpaperColorsChanged(wallpaperColors, userId, fromForegroundApp);
                }, bgHandler);

        // Contrast Change
        mUiModeManagerInternal.addContrastListener(this::handleContrastChanged, bgExecutor);

        // Lock State Change
        mKeyguardManager.addKeyguardLockedStateListener(bgExecutor,
                this::handleKeyguardLockedStateChanged);

        // Settings Changes (Setup Complete & Theme Style/Color)
        ContentResolver resolver = mContext.getContentResolver();
        resolver.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.USER_SETUP_COMPLETE), false,
                mUserSetupObserver, UserHandle.USER_ALL);

        resolver.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES),
                false, mThemeSettingsObserver, UserHandle.USER_ALL);
    }

    // Event Handlers

    private void handleProfileAdded(Intent intent) {
        UserHandle newUserHandle = intent.getParcelableExtra(Intent.EXTRA_USER, UserHandle.class);
        int newUserOrProfileId = newUserHandle.getIdentifier();

        // Load the new user/profile's theme state immediately
        mThemeUserLifecycle.loadUserStateAndNotifyStateManager(newUserOrProfileId);

        Integer parentId = mStateManager.parentOf(newUserOrProfileId);
        if (parentId == null || shouldIgnoreEventForUser(parentId, "onProfileAdd")) {
            return;
        }

        Slog.d(TAG, "User: " + newUserOrProfileId + " added to parent: " + parentId);
        mStateManager.onProfileAdd(parentId, newUserOrProfileId);
    }

    private void handleOverlayChanged(Intent intent) {
        final Uri data = intent.getData();
        if (data == null) return;
        final String changedPackage = data.getSchemeSpecificPart();
        if ("android".equals(changedPackage)) {
            final int userId = intent.getIntExtra(Intent.EXTRA_USER_ID, UserHandle.USER_NULL);
            Slog.i(TAG, "Theme overlays successfully applied for user " + userId);
            mThemeManagerInternal.notifyThemeChanged(userId);
        }
    }

    private void handleWallpaperColorsChanged(WallpaperColors wallpaperColors, int userId,
            boolean fromForegroundApp) {
        if (shouldIgnoreEventForUser(userId, "onColorsChanged")) {
            return;
        }
        ThemeSettings userSettings = mThemeManagerInternal.getThemeSettingsOrDefault(userId);
        if (userSettings.colorSource().equals(VALUE_PRESET)) {
            Slog.d(TAG, "Wallpaper color change ignored due to preset color source");
            return;
        }

        if (wallpaperColors == null) {
            Slog.d(TAG,
                    "Wallpaper color change ignored due to WallpaperManager providing null "
                            + "WallpaperColors");
            return;
        }

        Slog.d(TAG, "User: " + userId + " changed wallpaper");
        mStateManager.onSeedColorChange(mActivityManagerInternal.getCurrentUserId(),
                ColorScheme.getSeedColor(wallpaperColors), fromForegroundApp);
    }

    private void handleContrastChanged(int userId, float contrast) {
        if (shouldIgnoreEventForUser(userId, "onContrastChange")) {
            return;
        }
        mStateManager.onContrastChange(userId, contrast);
    }

    private void handleKeyguardLockedStateChanged(boolean isKeyguardLocked) {
        if (isKeyguardLocked) {
            Slog.d(TAG, "Keyguard locked");
            mStateManager.onLockStateChange(true);
        }
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    final ContentObserver mUserSetupObserver = new ContentObserver(BackgroundThread.getHandler()) {
        @Override
        public void onChange(boolean selfChange, @NonNull Collection<Uri> uris, int flags,
                int userId) {
            handleUserSetupChanged(userId);
        }
    };

    private void handleUserSetupChanged(int userId) {
        if (shouldIgnoreEventForUser(userId, "onFinishSetup")) {
            return;
        }
        Slog.d(TAG, "User: " + userId + " setup complete");
        mStateManager.onFinishSetup(userId);
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    final ContentObserver mThemeSettingsObserver = new ContentObserver(
            BackgroundThread.getHandler()) {
        @Override
        public void onChange(boolean selfChange, @NonNull Collection<Uri> uris, int flags,
                int userId) {
            handleThemeCustomizationChanged(userId);
        }
    };

    private void handleThemeCustomizationChanged(int userId) {
        if (shouldIgnoreEventForUser(userId, "onThemeSettingsChanged")) {
            return;
        }
        // The settings have changed on disk, invalidate the local cache.
        mThemeManagerInternal.forceReloadSettings(userId);

        ThemeSettings userSettings = mThemeManagerInternal.getThemeSettingsOrDefault(userId);

        if (userSettings.colorSource().equals(VALUE_PRESET)) {
            int newSeed = userSettings.systemPalette().toArgb();
            Slog.d(TAG, "User: " + userId + " changed seed to " + Integer.toHexString(newSeed));
            mStateManager.onSeedColorChange(userId, newSeed, true);
        }

        Slog.d(TAG, "User: " + userId + " changed style to " + ThemeStyle.name(
                userSettings.themeStyle()));
        mStateManager.onStyleChange(userId, userSettings.themeStyle());
    }

    // Helper Methods

    private boolean shouldIgnoreEventForUser(int userId, String methodName) {
        if (mThemeUserLifecycle.loadUserStateAndNotifyStateManager(userId)) {
            return false;
        }

        Slog.d(TAG, "Ignoring '" + methodName + "' for user " + userId);
        return true;
    }
}