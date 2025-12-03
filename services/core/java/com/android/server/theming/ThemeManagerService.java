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
import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.app.ActivityManagerInternal;
import android.app.KeyguardManager;
import android.content.Context;
import android.os.SystemProperties;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Slog;

import androidx.annotation.VisibleForTesting;

import com.android.server.LocalServices;
import com.android.server.SystemService;
import com.android.server.UiModeManagerInternal;
import com.android.server.om.OverlayManagerInternal;
import com.android.server.pm.UserManagerInternal;
import com.android.server.wallpaper.WallpaperManagerInternal;

import java.util.Objects;

/**
 * ThemeManagerService is the main entry point for the theming system in Android.
 * <p>
 * Its purpose is to ensure that the system theme (colors, shapes, etc.) correctly reflects
 * the user's wallpaper, settings, and other system states (like Dark Mode).
 * <p>
 * Internally, it acts as a coordinator that initializes and connects the specialized components
 * that perform the actual work:
 * <ul>
 *     <li>{@link ThemeStateManager}: Manages the current and pending theme state for each user
 *     .</li>
 *     <li>{@link ThemeUserLifecycle}: Handles user-related events (start, switch, setup).</li>
 *     <li>{@link ThemeEventObserver}: Listens for system events (wallpaper, settings, lock
 *     state).</li>
 *     <li>{@link ThemeManagerInternal}: Provides a local interface for other system services.</li>
 *     <li>{@link ThemeBinderService}: Provides the public Binder interface for apps.</li>
 * </ul>
 *
 * @hide
 */
@FlaggedApi(android.server.Flags.FLAG_ENABLE_THEME_SERVICE)
public class ThemeManagerService extends SystemService {
    private static final String TAG = "ThemeManagerService";

    private static final String KEY_COLOR_PALETTE_VERSION = "global_color_palette_version";

    private final ThemeManagerInternal mInternal;
    private final ThemeBinderService mPublic;
    private final Context mContext;
    private final ThemeSettingsManager mThemeSettingsManager;
    private final ThemeStateManager mStateManager;
    private final ThemeUserLifecycle mUserLifecycle;
    private final ThemeEventObserver mEventObserver;
    private final ThemeOverlayHelper mOverlayHelper;
    private final SystemPropertiesReader mSystemPropertiesReader;
    private final ThemeWallpaperManager mThemeWallpaperManager;


    public ThemeManagerService(@NonNull Context context) {
        this(context, SystemProperties::get, new ThemeStateManager(context),
                LocalServices.getService(WallpaperManagerInternal.class),
                LocalServices.getService(OverlayManagerInternal.class), null, null);
    }

    @VisibleForTesting
    ThemeManagerService(@NonNull Context context,
            @NonNull SystemPropertiesReader systemPropertiesReader,
            ThemeStateManager themeStateManager, WallpaperManagerInternal wallpaperManagerInternal,
            OverlayManagerInternal overlayManagerInternal,
            @Nullable ThemeUserLifecycle userLifecycle,
            @Nullable ThemeEventObserver eventObserver) {
        super(context);
        mContext = context;
        mStateManager = themeStateManager;
        mThemeWallpaperManager = new ThemeWallpaperManager(wallpaperManagerInternal);
        mThemeSettingsManager = new ThemeSettingsManager(mThemeWallpaperManager);
        mSystemPropertiesReader = systemPropertiesReader;
        mOverlayHelper = new ThemeOverlayHelper(overlayManagerInternal);

        mInternal = new ThemeManagerInternal(mContext, mThemeSettingsManager,
                mSystemPropertiesReader, mStateManager, mOverlayHelper);
        mPublic = new ThemeBinderService(mContext, mInternal);

        mUserLifecycle = userLifecycle != null ? userLifecycle : new ThemeUserLifecycle(mContext,
                mStateManager, mInternal);
        mEventObserver = eventObserver != null ? eventObserver : new ThemeEventObserver(mContext,
                mStateManager, mInternal, mUserLifecycle);
    }

    @Override
    public void onStart() {
        publishLocalService(ThemeManagerInternal.class, mInternal);
        publishBinderService(Context.THEME_SERVICE, mPublic.asBinder());
    }

    @Override
    @RequiresPermission(Manifest.permission.SUBSCRIBE_TO_KEYGUARD_LOCKED_STATE)
    public void onBootPhase(@BootPhase int phase) {
        Slog.d(TAG, "onBootPhase: " + phase);
        if (phase == SystemService.PHASE_SYSTEM_SERVICES_READY) {
            mStateManager.onServicesReady();
            mUserLifecycle.onServicesReady(LocalServices.getService(UserManagerInternal.class),
                    LocalServices.getService(UiModeManagerInternal.class), mThemeWallpaperManager);
            mUserLifecycle.onBootCompleteLoadUsers();
            mEventObserver.onServicesReady(mThemeWallpaperManager,
                    LocalServices.getService(UserManagerInternal.class),
                    LocalServices.getService(ActivityManagerInternal.class),
                    LocalServices.getService(UiModeManagerInternal.class),
                    mContext.getSystemService(KeyguardManager.class));
            mEventObserver.registerListeners();
        }

        if (phase == SystemService.PHASE_ACTIVITY_MANAGER_READY) {
            mStateManager.onBootComplete(/*isPaletteOutdated*/ shouldForceReloadForVersion());
        }

    }

    @Override
    public void onUserStarting(@NonNull TargetUser user) {
        mUserLifecycle.onUserStarting(user);
    }

    @Override
    public void onUserSwitching(@Nullable TargetUser from, @NonNull TargetUser to) {
        mUserLifecycle.onUserSwitching(from, to);
    }


    private boolean shouldForceReloadForVersion() {
        String storedVersion = Settings.Global.getString(mContext.getContentResolver(),
                KEY_COLOR_PALETTE_VERSION);
        String currentVersion = mSystemPropertiesReader.get("ro.build.date.utc", null);

        if (TextUtils.isEmpty(currentVersion)) {
            Slog.i(TAG, "Palette version missing. Refreshing overlays");
            return true;
        }

        if (storedVersion != null && Objects.equals(storedVersion, currentVersion)) return false;

        Slog.i(TAG, "Palette version bumped from " + storedVersion + " to " + currentVersion);
        Settings.Global.putString(mContext.getContentResolver(), KEY_COLOR_PALETTE_VERSION,
                currentVersion);
        return true;
    }
}
