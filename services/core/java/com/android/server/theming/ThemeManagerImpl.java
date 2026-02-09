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

import android.Manifest;
import android.annotation.NonNull;
import android.annotation.RequiresPermission;
import android.annotation.UserIdInt;
import android.app.WallpaperColors;
import android.content.ContentResolver;
import android.content.Context;
import android.content.theming.IThemeChangedCallback;
import android.content.theming.IThemeSettingsCallback;
import android.content.theming.ThemeInfo;
import android.content.theming.ThemeSettings;
import android.content.theming.ThemeStyle;
import android.graphics.Color;
import android.os.FabricatedOverlayInternal;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Slog;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.LocalServices;
import com.android.server.UiModeManagerInternal;
import com.android.systemui.monet.ColorScheme;

import com.google.ux.material.libmonet.dynamiccolor.ColorSpec.SpecVersion;
import com.google.ux.material.libmonet.dynamiccolor.DynamicScheme.Platform;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Optional;

/**
 * Core logic implementation for the theming system.
 *
 * <p>This class handles the calculation of color schemes and notification management,
 * providing the functional base for the {@link ThemeManagerInternal} interface.
 *
 * @hide
 */
@VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
public class ThemeManagerImpl implements ThemeManagerInternal {
    private static final String TAG = "ThemeManagerInternal";

    private final Context mContext;
    private final ThemeStateManager mStateManager;
    private final ThemeSettingsManager mThemeSettingsManager;
    private final ThemeOverlayHelper mOverlayHelper;
    private final ThemeEnvironment mEnvironment;
    private final ThemeWallpaperManager mWallpaperManager;

    private ThemeUserLifecycle mUserLifecycle;

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private final SparseArray<RemoteCallbackList<IThemeSettingsCallback>> mSettingsListeners =
            new SparseArray<>();

    @GuardedBy("mLock")
    private final SparseArray<RemoteCallbackList<IThemeChangedCallback>> mThemeChangedListeners =
            new SparseArray<>();

    ThemeManagerImpl(Context context, ThemeSettingsManager themeSettingsManager,
            ThemeStateManager stateManager, ThemeOverlayHelper overlayHelper,
            ThemeEnvironment environment, ThemeWallpaperManager wallpaperManager) {
        mContext = context;
        mStateManager = stateManager;
        mThemeSettingsManager = themeSettingsManager;
        mOverlayHelper = overlayHelper;
        mEnvironment = environment;
        mWallpaperManager = wallpaperManager;
    }

    /**
     * Sets the dependent components that require this Impl to be created first.
     */
    void setup(ThemeUserLifecycle userLifecycle) {
        mUserLifecycle = userLifecycle;
    }

    // API METHODS

    /**
     * {@inheritDoc}
     */
    @Override
    @RequiresPermission(Manifest.permission.SUBSCRIBE_TO_KEYGUARD_LOCKED_STATE)
    public boolean onBootAnimationDismissing() {
        return initializeThemingSystem("BootAnimation");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FabricatedOverlayInternal generateDynamicColorOverlay(@UserIdInt int userId,
            @NonNull ThemeInfo options) {
        ThemeInfo baseline = getUserThemeInfo(userId);
        if (baseline == null) return null;

        int newSeed = Optional.ofNullable(options.seedColor).orElse(baseline.seedColor).toArgb();
        @ThemeStyle.Type int newStyle = Optional.ofNullable(options.style).orElse(baseline.style);
        float newContrast = Optional.ofNullable(options.contrast).orElse(baseline.contrast);

        Platform platform;
        SpecVersion specVersion;

        try {
            platform = Platform.valueOf(baseline.platform);
            if (options.platform != null) {
                try {
                    platform = Platform.valueOf(options.platform);
                } catch (IllegalArgumentException e) {
                    Slog.w(TAG, "Invalid platform: " + options.platform, e);
                }
            }

            specVersion = SpecVersion.valueOf(baseline.specVersion);
            if (options.specVersion != null) {
                try {
                    specVersion = SpecVersion.valueOf(options.specVersion);
                } catch (IllegalArgumentException e) {
                    Slog.w(TAG, "Invalid specVersion: " + options.specVersion, e);
                }
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid Baseline Object", e);
        }

        ColorScheme newDarkScheme = new ColorScheme(newSeed, true, newStyle, newContrast,
                specVersion, platform);
        ColorScheme newLightScheme = new ColorScheme(newSeed, false, newStyle, newContrast,
                specVersion, platform);

        if (mEnvironment.platform == Platform.WATCH) {
            newLightScheme = newDarkScheme;
        }

        return mOverlayHelper.createDynamicOverlay(newLightScheme, newDarkScheme, userId)
                .getInternal();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ThemeInfo getUserThemeInfo(int userId) {
        if (mEnvironment.shouldIgnoreEventForUser(userId, "getUserThemeInfo")) return null;

        ThemeState state = mStateManager.getState(userId).getCurrentState();
        return new ThemeInfo(Color.valueOf(state.seedColor()), state.style(), state.contrast(),
                mEnvironment.specVersion.name(), mEnvironment.platform.name());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean registerThemeSettingsCallback(@UserIdInt int userId,
            @androidx.annotation.NonNull IThemeSettingsCallback cb) {
        if (!mEnvironment.isManagedUser(userId)) {
            return false;
        }

        synchronized (mLock) {
            if (cb == null) return false;

            RemoteCallbackList<IThemeSettingsCallback> userListeners = mSettingsListeners.get(
                    userId);
            if (userListeners == null) {
                userListeners = new RemoteCallbackList<>();
                mSettingsListeners.put(userId, userListeners);
            }

            // Ensure settings are loaded into cache so there is a baseline for oldSettings
            getThemeSettings(userId);

            return userListeners.register(cb);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean unregisterThemeSettingsCallback(@UserIdInt int userId,
            @NonNull IThemeSettingsCallback cb) {
        if (!mEnvironment.isManagedUser(userId)) {
            return false;
        }

        synchronized (mLock) {
            RemoteCallbackList<IThemeSettingsCallback> userListeners = mSettingsListeners.get(
                    userId);

            if (userListeners == null) return false;

            boolean didRemove = userListeners.unregister(cb);

            if (userListeners.getRegisteredCallbackCount() == 0) {
                mSettingsListeners.remove(userId);
                // Deliberately keeping cache for future `oldSettings` baseline
            }

            return didRemove;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean registerThemeChangedCallback(@UserIdInt int userId,
            @NonNull IThemeChangedCallback callback) {
        if (!mEnvironment.isManagedUser(userId)) {
            return false;
        }

        synchronized (mLock) {
            RemoteCallbackList<IThemeChangedCallback> userListeners = mThemeChangedListeners.get(
                    userId);
            if (userListeners == null) {
                userListeners = new RemoteCallbackList<>();
                mThemeChangedListeners.put(userId, userListeners);
            }
            return userListeners.register(callback);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean unregisterThemeChangedCallback(@UserIdInt int userId,
            @NonNull IThemeChangedCallback cb) {
        if (!mEnvironment.isManagedUser(userId)) {
            return false;
        }


        synchronized (mLock) {
            RemoteCallbackList<IThemeChangedCallback> userListeners = mThemeChangedListeners.get(
                    userId);

            if (userListeners == null) return false;

            boolean didRemove = userListeners.unregister(cb);

            if (userListeners.getRegisteredCallbackCount() == 0) {
                mThemeChangedListeners.remove(userId);
            }
            return didRemove;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean updateThemeSettings(@UserIdInt int userId, ThemeSettings newSettings) {
        if (mEnvironment.shouldIgnoreEventForUser(userId, "updateThemeSettings")) return false;

        ContentResolver resolver = mContext.createContextAsUser(UserHandle.of(userId),
                Context.CONTEXT_IGNORE_SECURITY).getContentResolver();
        ThemeSettings oldSettings = mThemeSettingsManager.getSettings(userId, resolver);
        boolean success = mThemeSettingsManager.setSettings(userId, resolver, newSettings);
        if (success) {
            notifySettingsChange(userId, oldSettings, newSettings);
        }
        return success;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ThemeSettings getThemeSettings(@UserIdInt int userId) {
        if (mEnvironment.shouldIgnoreEventForUser(userId, "getThemeSettings")) return null;

        return mThemeSettingsManager.getSettings(userId,
                mContext.createContextAsUser(UserHandle.of(userId),
                        Context.CONTEXT_IGNORE_SECURITY).getContentResolver());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ThemeSettings getThemeSettingsOrDefault(int userId) {
        if (mEnvironment.shouldIgnoreEventForUser(userId, "getThemeSettingsOrDefault")) return null;

        return mThemeSettingsManager.getSettingsOrDefault(userId, mContext.getContentResolver());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void dump(PrintWriter pw) {
        pw.println("--- " + TAG + " ---");
        mStateManager.dump(pw);
        pw.println("--- " + TAG + " ---");
    }

    // Internal Logic Methods

    /**
     * Initializes the theming system.
     * Can be called opportunistically (e.g. on wallpaper change) or when boot animation dismisses.
     */
    @RequiresPermission(Manifest.permission.SUBSCRIBE_TO_KEYGUARD_LOCKED_STATE)
    public boolean initializeThemingSystem(String reason) {
        if (!mEnvironment.isBooting()) return false;
        Slog.i(TAG, "Starting theming system initialization. Triggered by " + reason);

        // 1. Resolve and store device-wide default settings
        mThemeSettingsManager.initializeDefaults();

        // 2. Cleanup legacy overlays
        mOverlayHelper.cleanupLegacyOverlays(Arrays.asList(mEnvironment.legacyOverlays));

        // 3. Mark environment as ready (Transitions isBooting from true -> false)
        if (mUserLifecycle != null) mEnvironment.setBootingComplete(mUserLifecycle);

        // 4. Load the current user theme state (persistence/migration handled internally)
        mUserLifecycle.loadCurrentUser();

        // 5. Force color evaluation for everyone (handles OTAs and initial boot).
        mStateManager.evaluateAllUsers(mEnvironment.isPaletteOutdated, /*isSynchronous*/ true);

        // 6. Log completion (listeners registered in onBootPhase)
        Slog.i(TAG, "Theming system initialization complete.");

        return true;
    }

    /**
     * Notifies all registered listeners that the theme has changed for a specific user.
     *
     * @param userId The ID of a Full User for which the theme was changed.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void notifyThemeChanged(int userId) {
        final RemoteCallbackList<IThemeChangedCallback> userListeners;
        synchronized (mLock) {
            userListeners = mThemeChangedListeners.get(userId);
        }

        if (userListeners != null) {
            final int count = userListeners.beginBroadcast();
            for (int i = 0; i < count; i++) {
                try {
                    userListeners.getBroadcastItem(i).onThemeChanged(getUserThemeInfo(userId));
                } catch (RemoteException e) {
                    // The RemoteCallbackList will take care of removing the dead listener.
                }
            }
            userListeners.finishBroadcast();
        }
    }

    /**
     * Notifies all registered listeners that the theme settings have changed for a specific user.
     *
     * @param userId      The ID of a Full User for which the theme settings were changed.
     * @param oldSettings The previous {@link ThemeSettings} before the change.
     * @param newSettings The new {@link ThemeSettings} after the change.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void notifySettingsChange(@UserIdInt int userId, ThemeSettings oldSettings,
            ThemeSettings newSettings) {
        final RemoteCallbackList<IThemeSettingsCallback> userListeners;
        synchronized (mLock) {
            userListeners = mSettingsListeners.get(userId);
        }

        if (userListeners != null) {
            try {
                final int count = userListeners.beginBroadcast();
                for (int i = 0; i < count; i++) {
                    userListeners.getBroadcastItem(i).onSettingsChanged(oldSettings, newSettings);
                }
            } catch (RemoteException e) {
                // This is not expected to happen for local services.
                throw new RuntimeException(e);
            } finally {
                userListeners.finishBroadcast();
            }
        }
    }

    /**
     * Called when a user is starting.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void onUserStart(int userId) {
        if (mEnvironment.shouldIgnoreEventForUser(userId, "onUserStart", true)) {
            return;
        }

        ContentResolver resolver = mContext.createContextAsUser(UserHandle.of(userId),
                Context.CONTEXT_IGNORE_SECURITY).getContentResolver();

        ThemeSettings userSettings = mThemeSettingsManager.getSettings(userId, resolver);

        if (userSettings == null) {
            userSettings = mThemeSettingsManager.createDefaultThemeSettings(userId);
            mThemeSettingsManager.setSettings(userId, resolver, userSettings);
        }

        int seedColor = getEffectiveSeedColor(userSettings, userId);

        boolean isSetup = android.provider.Settings.Secure.getIntForUser(resolver,
                android.provider.Settings.Secure.USER_SETUP_COMPLETE, 0, userId) == 1;

        UiModeManagerInternal uiModeManager = LocalServices.getService(UiModeManagerInternal.class);
        float contrast = uiModeManager != null ? uiModeManager.getContrast(userId) : 0f;

        mStateManager.onUserStart(UserHandle.of(userId), isSetup, seedColor, contrast,
                userSettings.themeStyle());
    }

    /**
     * Called when the device is locked.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void onDeviceLocked() {
        mStateManager.onLockStateChange(true);
    }

    /**
     * Called when theme settings change.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void onThemeSettingsChanged(int userId) {
        mThemeSettingsManager.invalidateCache(userId);
        ContentResolver resolver = mContext.createContextAsUser(UserHandle.of(userId),
                Context.CONTEXT_IGNORE_SECURITY).getContentResolver();
        ThemeSettings userSettings = mThemeSettingsManager.getSettingsOrDefault(userId, resolver);

        int newSeed = userSettings.systemPalette().toArgb();
        mStateManager.onSeedColorChange(userId, newSeed, true);
        mStateManager.onStyleChange(userId, userSettings.themeStyle());
    }

    /**
     * Called when wallpaper colors change.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void onWallpaperColorsChanged(int userId, WallpaperColors colors,
            boolean fromForegroundApp) {
        ContentResolver resolver = mContext.createContextAsUser(UserHandle.of(userId),
                Context.CONTEXT_IGNORE_SECURITY).getContentResolver();
        ThemeSettings settings = mThemeSettingsManager.getSettingsOrDefault(userId, resolver);
        if (android.content.theming.FieldColorSource.VALUE_HOME_WALLPAPER.equals(
                settings.colorSource())) {
            int seedColor = com.android.systemui.monet.ColorScheme.getSeedColor(colors);
            mStateManager.onSeedColorChange(userId, seedColor, fromForegroundApp);
        }
    }

    /**
     * Called when contrast changes.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void onContrastChanged(int userId, float contrast) {
        mStateManager.onContrastChange(userId, contrast);
    }

    /**
     * Called when a profile is added.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void onProfileAdded(int parentId, int profileId) {
        if (mEnvironment.shouldIgnoreEventForUser(parentId, "onProfileAdded")) {
            return;
        }
        mStateManager.onProfileAdd(parentId, profileId);
    }

    /**
     * Called when users switch.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void onUserSwitching(int fromUserId, int toUserId) {
        if (mEnvironment.shouldIgnoreEventForUser(toUserId, "onUserSwitching")) {
            return;
        }
        mStateManager.onUserSwitching(fromUserId, toUserId);
    }

    private int getEffectiveSeedColor(ThemeSettings userSettings, int userId) {
        int seedColor;
        if (android.content.theming.FieldColorSource.VALUE_PRESET.equals(
                userSettings.colorSource())) {
            seedColor = userSettings.systemPalette().toArgb();
        } else {
            Integer wallpaperSeed = mWallpaperManager.getSeedColor(userId);
            seedColor = wallpaperSeed != null ? wallpaperSeed
                    : userSettings.systemPalette().toArgb();
        }
        return seedColor;
    }
}
