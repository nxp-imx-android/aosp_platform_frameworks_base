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

import static android.content.theming.FieldColorSource.VALUE_HOME_WALLPAPER;
import static android.content.theming.FieldColorSource.VALUE_PRESET;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.content.ContentResolver;
import android.content.theming.FieldColor;
import android.content.theming.FieldColorSource;
import android.content.theming.FieldThemeStyle;
import android.content.theming.ThemeSettings;
import android.content.theming.ThemeSettingsField;
import android.content.theming.ThemeStyle;
import android.graphics.Color;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Pair;
import android.util.Slog;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Manages the loading, saving, and caching of theme settings.
 * This class handles the persistence of theme settings to and from the system settings,
 * and maintains an in-memory cache for quick access.
 *
 * @hide
 */
@FlaggedApi(android.server.Flags.FLAG_ENABLE_THEME_SERVICE)
public class ThemeSettingsManager {
    private static final String TAG = "ThemeSettingsManager";

    public static final String TIMESTAMP = "_applied_timestamp";
    private static final String KEY_PREFIX = "android.theme.customization.";
    public static final String OVERLAY_CATEGORY_ACCENT_COLOR = KEY_PREFIX + "accent_color";
    public static final String OVERLAY_CATEGORY_SYSTEM_PALETTE = KEY_PREFIX + "system_palette";
    public static final String OVERLAY_CATEGORY_THEME_STYLE = KEY_PREFIX + "theme_style";
    public static final String OVERLAY_COLOR_SOURCE = KEY_PREFIX + "color_source";
    private final ThemeWallpaperManager mWallpaperManager;
    private final SystemPropertiesReader mSystemPropertiesReader;
    private final String[] mDefaultThemeData;

    private static final ThemeSettings HARDCODED_FALLBACK = new ThemeSettings.Builder()
            .setThemeStyle(ThemeStyle.TONAL_SPOT)
            .setColorSource(VALUE_PRESET)
            .setSystemPalette(Color.valueOf(0xFF1b6ef3))
            .build();

    static final Map<String, ThemeSettingsField<?, ?>> ALL_FIELDS = Map.ofEntries(
            Map.entry(OVERLAY_CATEGORY_SYSTEM_PALETTE, new FieldColor()),
            Map.entry(OVERLAY_CATEGORY_ACCENT_COLOR, new FieldColor()),
            Map.entry(OVERLAY_COLOR_SOURCE, new FieldColorSource()),
            Map.entry(OVERLAY_CATEGORY_THEME_STYLE, new FieldThemeStyle()));

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private final SparseArray<ThemeSettings> mSettingsCache = new SparseArray<>();

    // Keeps track of all users that needed migration of the settings.
    // This will be used in the "color update on boot deadline", aka when boot animation closes, to
    // write updated settings to disk, avoiding having to verify settings again.
    @GuardedBy("mLock")
    private final Set<Integer> mMigratedUserIds = new HashSet<>();

    public ThemeSettingsManager(ThemeWallpaperManager wallpaperManager,
            SystemPropertiesReader systemPropertiesReader, String[] defaultThemeData) {
        mWallpaperManager = wallpaperManager;
        mSystemPropertiesReader = systemPropertiesReader;
        mDefaultThemeData = defaultThemeData;
    }

    /**
     * Retrieves the theme settings for the specified user, utilizing an in-memory cache.
     *
     * @param userId          The ID of the user.
     * @param contentResolver The content resolver to use if reading from disk is necessary.
     * @return The {@link ThemeSettings} for the user, or null if none exist.
     */
    @Nullable
    public ThemeSettings getSettings(@UserIdInt int userId,
            @NonNull ContentResolver contentResolver) {
        synchronized (mLock) {
            int idx = mSettingsCache.indexOfKey(userId);
            if (idx >= 0) {
                return mSettingsCache.valueAt(idx);
            }
        }

        // Read from disk outside the lock to avoid blocking other users if I/O is slow.
        ThemeSettings settings = readFromDisk(userId, contentResolver);

        synchronized (mLock) {
            mSettingsCache.put(userId, settings);
        }

        return settings;
    }

    /**
     * Retrieves the theme settings for the specified user, or the default settings if no
     * custom settings are found.
     *
     * @param userId          The ID of a Full User to retrieve theme settings for.
     * @param contentResolver The content resolver to use.
     * @return The {@link ThemeSettings} object containing the current or default theme settings.
     */
    @NonNull
    public ThemeSettings getSettingsOrDefault(int userId, ContentResolver contentResolver) {
        ThemeSettings storedSettings = getSettings(userId, contentResolver);
        if (storedSettings != null) {
            return storedSettings;
        }
        return createDefaultThemeSettings(userId);
    }

    /**
     * Saves the specified theme settings for the given user to persistent storage and updates
     * the cache.
     *
     * @param userId          The ID of the user.
     * @param contentResolver The content resolver to use.
     * @param newSettings     The {@link ThemeSettings} to save.
     * @return true if the settings were successfully written to storage.
     */
    public boolean setSettings(@UserIdInt int userId, @NonNull ContentResolver contentResolver,
            @NonNull ThemeSettings newSettings) {

        boolean success = writeToDisk(userId, contentResolver, newSettings);

        if (!success) return false;

        // Update cache immediately so subsequent reads see the new value.
        synchronized (mLock) {
            mSettingsCache.put(userId, newSettings);
        }

        return true;
    }

    /**
     * Invalidates the settings cache for a specific user.
     * This forces the next getSettings call to read from disk.
     */
    public void invalidateCache(@UserIdInt int userId) {
        synchronized (mLock) {
            mSettingsCache.remove(userId);
        }
    }

    /**
     * Writes any pending migrated settings to disk.
     */
    public void updateMigratedSettings(ContentResolver contentResolver) {
        final Set<Integer> usersToSave;
        synchronized (mLock) {
            if (mMigratedUserIds.isEmpty()) return;
            usersToSave = new HashSet<>(mMigratedUserIds);
            mMigratedUserIds.clear();
        }

        for (int userId : usersToSave) {
            ThemeSettings settings = getSettings(userId, contentResolver);
            if (settings != null) {
                writeToDisk(userId, contentResolver, settings);
                Slog.d(TAG, "Persisted migrated theme settings for user " + userId);
            }
        }
    }

    @Nullable
    private ThemeSettings readFromDisk(@UserIdInt int userId, ContentResolver contentResolver) {
        try {
            final String jsonString = Settings.Secure.getStringForUser(contentResolver,
                    Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, userId);
            return fromJson(jsonString, userId);
        } catch (Exception e) {
            Slog.w(TAG, "Error loading theme settings for user " + userId + ": " + e);
            return null;
        }
    }

    private boolean writeToDisk(@UserIdInt int userId, ContentResolver contentResolver,
            ThemeSettings newSettings) {
        try {
            final String oldJsonString = Settings.Secure.getStringForUser(contentResolver,
                    Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, userId);
            final String newJsonString = toJson(newSettings, oldJsonString);
            return Settings.Secure.putStringForUser(contentResolver,
                    Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, newJsonString,
                    userId);
        } catch (Exception e) {
            Slog.w(TAG, "Error writing theme settings for user " + userId + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Creates the default theme settings for a given user based on device configuration.
     *
     * @param userId The ID of the user.
     * @return The default theme settings.
     */
    public ThemeSettings createDefaultThemeSettings(@UserIdInt int userId) {
        String deviceColorProperty = "ro.boot.hardware.color";

        // The 'theming_defaults' resource is a string array where each entry is formatted as:
        // "hardware_color_name|STYLE_NAME|#hex_color_or_home_wallpaper"
        HashMap<String, Pair<Integer, String>> themeMap = new HashMap<>();
        for (String themeEntry : mDefaultThemeData) {
            String[] themeComponents = themeEntry.split("\\|");
            if (themeComponents.length != 3) {
                continue;
            }
            try {
                themeMap.put(themeComponents[0],
                        new Pair<>(ThemeStyle.valueOf(themeComponents[1]), themeComponents[2]));
            } catch (IllegalArgumentException e) {
                Slog.w(TAG, "Invalid style in theming_defaults: " + themeComponents[1], e);
            }
        }

        Pair<Integer, String> fallbackTheme = themeMap.get("*");
        if (fallbackTheme == null) {
            // This is a device configuration error. A wildcard fallback is required.
            throw new IllegalStateException("Theming resource 'theming_defaults' must contain a"
                    + " wildcard ('*') entry for fallback.");
        }

        String deviceColorPropertyValue = mSystemPropertiesReader.get(deviceColorProperty, "");
        Pair<Integer, String> styleAndSource = themeMap.get(deviceColorPropertyValue);
        if (styleAndSource == null) {
            Slog.d(TAG, "Sysprop `" + deviceColorProperty + "` of value '"
                    + deviceColorPropertyValue
                    + "' not found in theming_defaults: " + Arrays.toString(mDefaultThemeData)
                    + ". Using wildcard fallback.");
            styleAndSource = fallbackTheme;
        }

        try {
            return buildSettingsFromConfig(styleAndSource, userId);
        } catch (Exception e) {
            Slog.w(TAG, "Could not build theme from device config, falling back to wildcard.", e);
            try {
                return buildSettingsFromConfig(fallbackTheme, userId);
            } catch (Exception e2) {
                Slog.e(TAG, "Wildcard fallback theme is also invalid! Using hardcoded default.",
                        e2);
                return HARDCODED_FALLBACK;
            }
        }
    }

    private ThemeSettings buildSettingsFromConfig(Pair<Integer, String> config,
            @UserIdInt int userId) {
        @ThemeStyle.Type int style = config.first;
        String colorSourceString = config.second;
        Color seedColor;
        String colorSource;

        if (colorSourceString.equals(VALUE_HOME_WALLPAPER)) {
            Integer wallpaperSeed = mWallpaperManager.getSeedColor(userId);
            if (wallpaperSeed == null) {
                Slog.i(TAG, "User's " + userId + " Wallpaper colors not yet available. "
                        + "Using fallback palette for HOME_WALLPAPER source.");
                seedColor = HARDCODED_FALLBACK.systemPalette();
            } else {
                seedColor = Color.valueOf(wallpaperSeed);
            }
            colorSource = VALUE_HOME_WALLPAPER;
        } else {
            seedColor = Color.valueOf(Color.parseColor(colorSourceString));
            colorSource = VALUE_PRESET;
        }

        return new ThemeSettings.Builder()
                .setThemeStyle(style)
                .setColorSource(colorSource)
                .setSystemPalette(seedColor)
                .build();
    }

    private static <T, J> T parseAndValidate(@NonNull JSONObject json, @NonNull String key,
            @Nullable T defaultValue) throws JSONException {
        if (!json.has(key)) {
            return defaultValue;
        }

        ThemeSettingsField<T, J> handler = (ThemeSettingsField<T, J>) ALL_FIELDS.get(key);
        if (handler == null) {
            // This can happen for unknown fields, which is fine. We just can't parse them.
            return defaultValue;
        }

        Object primitive = json.get(key);
        // Cast to J, the expected JSON primitive type for the handler.
        T parsedValue = handler.parse((J) primitive);

        if (parsedValue == null || !handler.validate(parsedValue)) {
            throw new IllegalArgumentException("Invalid value for key '" + key + "': " + primitive);
        }

        return parsedValue;
    }

    @Nullable
    private ThemeSettings fromJson(@Nullable String jsonString, @UserIdInt int userId)
            throws JSONException {
        if (TextUtils.isEmpty(jsonString)) {
            return null;
        }
        JSONObject json = new JSONObject(jsonString);

        Instant timestamp;
        if (json.has(TIMESTAMP)) {
            timestamp = Instant.ofEpochMilli(json.getLong(TIMESTAMP));
        } else {
            Slog.w(TAG, "JSON missing timestamp, using current time as fallback.");
            timestamp = Instant.EPOCH;
        }

        int themeStyle = parseAndValidate(json, OVERLAY_CATEGORY_THEME_STYLE,
                ThemeStyle.TONAL_SPOT);
        String colorSource = parseAndValidate(json, OVERLAY_COLOR_SOURCE, VALUE_HOME_WALLPAPER);

        Color systemPalette = parseAndValidate(json, OVERLAY_CATEGORY_SYSTEM_PALETTE, null);

        if (systemPalette == null && VALUE_HOME_WALLPAPER.equals(colorSource)) {
            Integer seed = mWallpaperManager.getSeedColor(userId);
            if (seed != null) {
                systemPalette = Color.valueOf(seed);
            } else {
                systemPalette = HARDCODED_FALLBACK.systemPalette();
                Slog.d(TAG, "Legacy settings for user " + userId + " missing palette. "
                        + "Wallpaper color missing, using fallback.");
            }
            synchronized (mLock) {
                mMigratedUserIds.add(userId);
            }
        }

        return new ThemeSettings.Builder()
                .setAppliedTimestamp(timestamp)
                .setThemeStyle(themeStyle)
                .setColorSource(colorSource)
                .setSystemPalette(systemPalette)
                .build();
    }

    @NonNull
    private String toJson(@NonNull ThemeSettings settings, @Nullable String oldJsonString) {
        JSONObject json;
        try {
            // Start with the original JSON data to preserve unknown fields.
            json = TextUtils.isEmpty(oldJsonString)
                    ? new JSONObject() : new JSONObject(oldJsonString);
        } catch (JSONException e) {
            Slog.w(TAG, "Failed to parse existing settings, overwriting", e);
            json = new JSONObject();
        }

        try {
            // Update the known fields with the current values.
            json.put(TIMESTAMP, settings.timeStamp().toEpochMilli());
            putSetting(json, OVERLAY_CATEGORY_THEME_STYLE, settings.themeStyle());
            putSetting(json, OVERLAY_COLOR_SOURCE, settings.colorSource());
            putSetting(json, OVERLAY_CATEGORY_SYSTEM_PALETTE, settings.systemPalette());
            // For backward compatibility, always write accent_color as well.
            putSetting(json, OVERLAY_CATEGORY_ACCENT_COLOR, settings.systemPalette());

            return json.toString();
        } catch (JSONException e) {
            // This should not happen with controlled inputs.
            throw new RuntimeException("Error creating JSON for ThemeSettings", e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void putSetting(JSONObject json, String key, T value) throws JSONException {
        if (value == null) {
            json.remove(key);
            return;
        }
        ThemeSettingsField<T, ?> field = (ThemeSettingsField<T, ?>) ALL_FIELDS.get(key);
        json.put(key, field.serialize(value));
    }
}
