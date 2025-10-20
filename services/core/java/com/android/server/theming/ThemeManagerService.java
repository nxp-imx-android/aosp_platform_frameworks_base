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

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;

import androidx.annotation.VisibleForTesting;

import com.android.internal.os.BackgroundThread;
import com.android.server.LocalServices;
import com.android.server.SystemService;
import com.android.server.wallpaper.WallpaperManagerInternal;

import java.util.Collection;

/**
 * The ThemeService is a system service that manages the theming of the device.
 * It is responsible for loading and applying theme settings, and for notifying
 * other components of changes to the theme. It also handles the registration of
 * content observers for theme-related settings.
 */
@FlaggedApi(android.server.Flags.FLAG_ENABLE_THEME_SERVICE)
public class ThemeManagerService extends SystemService {
    private static final String TAG = "ThemeService";

    private final ThemeManagerInternal mInternal;
    private final ThemeBinderService mPublic;
    private final Context mContext;
    private final ThemeSettingsManager mThemeSettingsManager;
    private final SystemPropertiesReader mSystemPropertiesReader;

    public ThemeManagerService(@NonNull Context context) {
        this(context, new SystemPropertiesReaderImpl());
    }

    @VisibleForTesting
    ThemeManagerService(@NonNull Context context, @NonNull SystemPropertiesReader systemPropertiesReader) {
        super(context);
        mContext = context;
        WallpaperManagerInternal wallpaperManagerInternal = LocalServices.getService(
                WallpaperManagerInternal.class);
        mThemeSettingsManager = new ThemeSettingsManager(wallpaperManagerInternal);
        mSystemPropertiesReader = systemPropertiesReader;

        mInternal = new ThemeManagerInternal(mContext, mThemeSettingsManager,
                mSystemPropertiesReader);
        mPublic = new ThemeBinderService(mContext, mInternal);
    }

    @Override
    public void onStart() {
        publishLocalService(ThemeManagerInternal.class, mInternal);
        publishBinderService(Context.THEME_SERVICE, mPublic.asBinder());
    }

    @Override
    public void onBootPhase(@BootPhase int phase) {
        if (phase == SystemService.PHASE_SYSTEM_SERVICES_READY) {
            setupListeners();
        }
    }

    // HELPER METHODS

    private void setupListeners() {
        ContentResolver resolver = mContext.getContentResolver();
        Handler bgHandler = BackgroundThread.getHandler();

        // Style Change
        resolver.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES),
                false, new ContentObserver(bgHandler) {
                    @Override
                    public void onChange(boolean selfChange, @NonNull Collection<Uri> uris,
                            int flags, @UserIdInt int userId) {
                        Context userContext = mContext.createContextAsUser(UserHandle.of(userId),
                                Context.CONTEXT_IGNORE_SECURITY);

                        // notifies other listeners of the Theme Settings
                        mInternal.notifySettingsChange(userId,
                                mThemeSettingsManager.readSettings(userId,
                                        userContext.getContentResolver()));
                    }
                }, UserHandle.USER_ALL);
    }

    private static class SystemPropertiesReaderImpl implements SystemPropertiesReader {
        @Override
        @NonNull
        public String get(@NonNull String key, @Nullable String def) {
            return android.os.SystemProperties.get(key, def);
        }
    }
}