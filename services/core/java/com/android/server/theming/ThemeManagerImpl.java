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
import android.annotation.UserIdInt;
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
import com.android.systemui.monet.ColorScheme;

import com.google.ux.material.libmonet.dynamiccolor.ColorSpec.SpecVersion;
import com.google.ux.material.libmonet.dynamiccolor.DynamicScheme.Platform;

import java.io.PrintWriter;
import java.util.Optional;

/**
 * Core logic implementation for the theming system.
 *
 * <p>This class handles the calculation of color schemes and notification management,
 * providing the functional base for the {@link ThemeManagerInternal} interface.
 *
 * @hide
 */
public class ThemeManagerImpl implements ThemeManagerInternal {
    private static final String TAG = "ThemeManagerInternal";

    private final Context mContext;
    private final ThemeStateManager mStateManager;
    private final ThemeSettingsManager mThemeSettingsManager;
    private final ThemeOverlayHelper mOverlayHelper;
    private final ThemeEnvironment mEnvironment;

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private final SparseArray<RemoteCallbackList<IThemeSettingsCallback>> mSettingsListeners =
            new SparseArray<>();

    @GuardedBy("mLock")
    private final SparseArray<RemoteCallbackList<IThemeChangedCallback>> mThemeChangedListeners =
            new SparseArray<>();

    public ThemeManagerImpl(Context context, ThemeSettingsManager themeSettingsManager,
            ThemeStateManager stateManager, ThemeOverlayHelper overlayHelper,
            ThemeEnvironment environment) {
        mContext = context;
        mStateManager = stateManager;
        mThemeSettingsManager = themeSettingsManager;
        mOverlayHelper = overlayHelper;
        mEnvironment = environment;
    }

    /**
     * Notifies all registered listeners that the theme has changed for a specific user.
     *
     * @param userId The ID of a Full User for which the theme was changed.
     */
    @VisibleForTesting
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
     * {@inheritDoc}
     */
    @Override
    public FabricatedOverlayInternal generateDynamicColorOverlay(int userId, ThemeInfo options) {
        ThemeInfo baseline = getUserThemeInfo(userId);

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
        ThemeState state = mStateManager.getState(userId).getCurrentState();
        return new ThemeInfo(Color.valueOf(state.seedColor()), state.style(), state.contrast(),
                mEnvironment.specVersion.name(), mEnvironment.platform.name());
    }

    /**
     * Notifies all registered listeners that the theme settings have changed for a specific user.
     *
     * @param userId      The ID of a Full User for which the theme settings were changed.
     * @param oldSettings The previous {@link ThemeSettings} before the change.
     * @param newSettings The new {@link ThemeSettings} after the change.
     */
    @VisibleForTesting
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
     * {@inheritDoc}
     */
    @Override
    public boolean registerThemeSettingsCallback(@UserIdInt int userId, IThemeSettingsCallback cb) {
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
            IThemeSettingsCallback cb) {
        synchronized (mLock) {
            RemoteCallbackList<IThemeSettingsCallback> userListeners = mSettingsListeners.get(
                    userId);

            if (cb == null || userListeners == null) return false;

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
    public void registerThemeChangedCallback(@UserIdInt int userId,
            @NonNull IThemeChangedCallback callback) {
        synchronized (mLock) {
            RemoteCallbackList<IThemeChangedCallback> userListeners = mThemeChangedListeners.get(
                    userId);
            if (userListeners == null) {
                userListeners = new RemoteCallbackList<>();
                mThemeChangedListeners.put(userId, userListeners);
            }
            userListeners.register(callback);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unregisterThemeChangedCallback(@UserIdInt int userId,
            @NonNull IThemeChangedCallback callback) {
        synchronized (mLock) {
            RemoteCallbackList<IThemeChangedCallback> userListeners = mThemeChangedListeners.get(
                    userId);
            if (userListeners != null) {
                userListeners.unregister(callback);
                if (userListeners.getRegisteredCallbackCount() == 0) {
                    mThemeChangedListeners.remove(userId);
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean updateThemeSettings(@UserIdInt int userId, ThemeSettings newSettings) {
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
        return mThemeSettingsManager.getSettings(userId,
                mContext.createContextAsUser(UserHandle.of(userId),
                        Context.CONTEXT_IGNORE_SECURITY).getContentResolver());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ThemeSettings getThemeSettingsOrDefault(int userId) {
        return mThemeSettingsManager.getSettingsOrDefault(userId, mContext.getContentResolver());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onBootAnimationDismissing() {
        mEnvironment.setBootingComplete();
        mThemeSettingsManager.updateMigratedSettings(mContext.getContentResolver());
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
}
