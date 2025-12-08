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
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.systemui.monet.ColorScheme;

import com.google.ux.material.libmonet.dynamiccolor.DynamicScheme;

import java.io.PrintWriter;
import java.util.Optional;

/**
 * Internal API implementation for {@link ThemeManagerService}.
 *
 * <p>Provides methods for other system services to interact with the theming
 * functionality.
 *
 * @hide
 */
public class ThemeManagerInternal {
    private static final String TAG = "ThemeManagerInternal";

    private final Context mContext;
    private final ThemeStateManager mStateManager;
    private final ThemeSettingsManager mThemeSettingsManager;
    private final SystemPropertiesReader mSystemPropertiesReader;
    private final ThemeOverlayHelper mOverlayHelper;

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private final SparseArray<RemoteCallbackList<IThemeSettingsCallback>> mSettingsListeners =
            new SparseArray<>();

    @GuardedBy("mLock")
    private final SparseArray<RemoteCallbackList<IThemeChangedCallback>> mThemeChangedListeners =
            new SparseArray<>();

    ThemeManagerInternal(Context context, ThemeSettingsManager themeSettingsManager,
            SystemPropertiesReader systemPropertiesReader, ThemeStateManager stateManager,
            ThemeOverlayHelper overlayHelper) {
        mContext = context;
        mStateManager = stateManager;
        mThemeSettingsManager = themeSettingsManager;
        mSystemPropertiesReader = systemPropertiesReader;
        mOverlayHelper = overlayHelper;
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
     * Generates dynamic overlays based on the current theme state of the user.
     *
     * <p>This method allows other system services to generate dynamic overlays
     * with specific seed color, style, and contrast values. If any of these
     * parameters are null, the current values from the user's theme state
     * will be used. If the userId is not a full user, it will throw an exception.
     *
     * @param userId  The ID of the user whose current theme state will be used as a base
     *                for any unspecified theme properties in {@code options}.
     * @param options The {@link ThemeInfo} with the desired seed color, style, and contrast.
     * @return The generated {@link FabricatedOverlayInternal}.
     */
    public FabricatedOverlayInternal generateDynamicColorOverlay(int userId, ThemeInfo options) {
        ThemeState state = mStateManager.getState(userId).getCurrentState();

        int newSeed = Optional.ofNullable(options.seedColor).map(Color::toArgb).orElse(
                state.seedColor());
        @ThemeStyle.Type int newStyle = Optional.ofNullable(options.style).orElse(state.style());
        float newContrast = Optional.ofNullable(options.contrast).orElse(state.contrast());

        ColorScheme newDarkScheme = new ColorScheme(newSeed, true, newStyle, newContrast);
        ColorScheme newLightScheme = new ColorScheme(newSeed, false, newStyle, newContrast);

        return mOverlayHelper.createDynamicOverlay(newLightScheme, newDarkScheme).getInternal();
    }

    /**
     * Returns the current {@link ThemeInfo} for a given user.
     *
     * @param userId The ID of a Full User for whom to retrieve the theme information.
     * @return The {@link ThemeInfo} containing the user's current theme settings.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public ThemeInfo getUserThemeInfo(int userId) {
        ThemeState state = mStateManager.getState(userId).getCurrentState();
        return new ThemeInfo(Color.valueOf(state.seedColor()), state.style(), state.contrast(),
                DynamicScheme.DEFAULT_SPEC_VERSION.name(), DynamicScheme.DEFAULT_PLATFORM.name());
    }

    /**
     * Notifies all registered listeners that the theme settings have changed for a specific user.
     *
     * @param userId The ID of a Full User for which the theme settings were changed.
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
     * Registers a callback to receive notifications of theme settings changes.
     *
     * <p>This method allows clients to register an {@link IThemeSettingsCallback}
     * to be notified whenever the theme settings for the specified user are changed.
     *
     * @param userId The ID of a Full User to register the callback for.
     * @param cb     The {@link IThemeSettingsCallback} to register.
     * @return {@code true} if the callback was successfully registered, {@code false} otherwise.
     */
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
     * Unregisters a previously registered theme settings change callback.
     *
     * <p>This method allows clients to unregister an {@link IThemeSettingsCallback}
     * that was previously registered using
     * {@link #registerThemeSettingsCallback(int, IThemeSettingsCallback)}}.
     *
     * @param userId The ID of a Full User to unregister the callback from.
     * @param cb     The {@link IThemeSettingsCallback} to unregister.
     * @return {@code true} if the callback was successfully unregistered, {@code false} otherwise.
     */
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
     * Registers a callback for theme changed events.
     *
     * @param userId   The ID of a Full User to register the callback for.
     * @param callback The {@link IThemeChangedCallback}  to add.
     */
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
     * Unregisters a callback for theme changed events.
     *
     * @param userId   The ID of a Full User to unregister the callback from.
     * @param callback The The {@link IThemeChangedCallback}  to remove.
     */
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
     * Updates the theme settings for the current user.
     *
     * <p>This method allows clients to update the theme settings for the current user. The
     * provided {@link ThemeSettings} object should contain the new theme settings. Any
     * settings not explicitly set in the {@link ThemeSettings} object will remain unchanged.
     *
     * <p>Use the {@link ThemeSettings}'s builder() to construct {@link ThemeSettings} objects.
     *
     * @param userId      The user ID to update theme settings for.
     * @param newSettings The {@link ThemeSettings} object containing the new theme settings.
     *                    If the userId is not a full user, it will throw an exception.
     */
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
     * Retrieves the theme settings for the specified user.
     *
     * <p>This method allows clients to retrieve the current theme settings for a given user.
     *
     * @param userId The ID of a Full User to retrieve theme settings for.
     * @return The {@link ThemeSettings} object containing the current theme settings,
     * or {@code null} if an error occurs or no settings are found.
     */
    public ThemeSettings getThemeSettings(@UserIdInt int userId) {
        return mThemeSettingsManager.getSettings(userId,
                mContext.createContextAsUser(UserHandle.of(userId),
                        Context.CONTEXT_IGNORE_SECURITY).getContentResolver());
    }

    /**
     * Retrieves the theme settings for the specified user, or the default settings if no
     * custom settings are found.
     *
     * @param userId The ID of a Full User to retrieve theme settings for.
     * @return The {@link ThemeSettings} object containing the current theme settings,
     * or the default settings if no custom settings are found.
     */
    public ThemeSettings getThemeSettingsOrDefault(int userId) {
        ThemeSettings storedSettings = getThemeSettings(userId);
        if (storedSettings != null) {
            return storedSettings;
        }
        return mThemeSettingsManager.createDefaultThemeSettings(mContext.getResources(),
                mSystemPropertiesReader, userId);
    }

    /**
     * Dumps the current state of the ThemeManagerInternal to the provided PrintWriter.
     *
     * @param pw The PrintWriter to dump the state to.
     */
    public void dump(PrintWriter pw) {
        pw.println("--- " + TAG + " ---");
        mStateManager.dump(pw);
        pw.println("--- " + TAG + " ---");
    }
}
