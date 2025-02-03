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
import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.ContentResolver;
import android.content.res.Resources;
import android.content.theming.FieldColor;
import android.content.theming.FieldColorSource;
import android.content.theming.FieldThemeStyle;
import android.content.theming.ThemeSettings;
import android.content.theming.ThemeSettingsField;
import android.content.theming.ThemeStyle;
import android.graphics.Color;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import com.android.server.wallpaper.WallpaperManagerInternal;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages the loading and saving of theme settings. This class handles the persistence of theme
 * settings to and from the system settings. It utilizes a collection of {@link ThemeSettingsField}
 * objects to represent individual theme setting fields.
 *
 * @hide
 */
@FlaggedApi(android.server.Flags.FLAG_ENABLE_THEME_SERVICE)
class ThemeSettingsManager {
    private static final String TAG = "ThemeSettingsManager";

    public static final String TIMESTAMP = "_applied_timestamp";
    private static final String KEY_PREFIX = "android.theme.customization.";
    public static final String OVERLAY_CATEGORY_ACCENT_COLOR = KEY_PREFIX + "accent_color";
    public static final String OVERLAY_CATEGORY_SYSTEM_PALETTE = KEY_PREFIX + "system_palette";
    public static final String OVERLAY_CATEGORY_THEME_STYLE = KEY_PREFIX + "theme_style";
    public static final String OVERLAY_COLOR_SOURCE = KEY_PREFIX + "color_source";
    private final WallpaperManagerInternal mWallpaperManagerInternal;
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

    ThemeSettingsManager(WallpaperManagerInternal wallpaperManagerInternal) {
        mWallpaperManagerInternal = wallpaperManagerInternal;
    }

    /**
     * Loads the theme settings for the specified user.
     *
     * @param userId          The ID of the user.
     * @param contentResolver The content resolver to use.
     * @return The loaded {@link ThemeSettings}.
     */
    @Nullable
    ThemeSettings readSettings(@UserIdInt int userId, ContentResolver contentResolver) {
        try {
            final String jsonString = Settings.Secure.getStringForUser(contentResolver,
                    Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, userId);
            return fromJson(jsonString);
        } catch (Exception e) {
            Log.w(TAG, "Error loading theme settings: " + e);
            return null;
        }
    }

    /**
     * Saves the specified theme settings for the given user.
     *
     * @param userId          The ID of the user.
     * @param contentResolver The content resolver to use.
     * @param newSettings     The {@link ThemeSettings} to save.
     */
    boolean writeSettings(@UserIdInt int userId, ContentResolver contentResolver,
            ThemeSettings newSettings) {
        try {
            final String oldJsonString = Settings.Secure.getStringForUser(contentResolver,
                    Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, userId);
            final String newJsonString = toJson(newSettings, oldJsonString);
            Settings.Secure.putStringForUser(contentResolver,
                    Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, newJsonString,
                    userId);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Error writings theme settings:" + e.getMessage());
            return false;
        }
    }

    ThemeSettings createDefaultThemeSettings(Resources resources,
            SystemPropertiesReader systemPropertiesReader, @UserIdInt int userId) {
        String deviceColorProperty = "ro.boot.hardware.color";
        String[] themeData = resources.getStringArray(
                com.android.internal.R.array.theming_defaults);

        // The 'theming_defaults' resource is a string array where each entry is formatted as:
        // "hardware_color_name|STYLE_NAME|#hex_color_or_home_wallpaper"
        HashMap<String, Pair<Integer, String>> themeMap = new HashMap<>();
        for (String themeEntry : themeData) {
            String[] themeComponents = themeEntry.split("\\|");
            if (themeComponents.length != 3) {
                continue;
            }
            try {
                themeMap.put(themeComponents[0],
                        new Pair<>(ThemeStyle.valueOf(themeComponents[1]), themeComponents[2]));
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Invalid style in theming_defaults: " + themeComponents[1], e);
            }
        }

        Pair<Integer, String> fallbackTheme = themeMap.get("*");
        if (fallbackTheme == null) {
            // This is a device configuration error. A wildcard fallback is required.
            throw new IllegalStateException("Theming resource 'theming_defaults' must contain a"
                    + " wildcard ('*') entry for fallback.");
        }

        String deviceColorPropertyValue = systemPropertiesReader.get(deviceColorProperty, "");
        Pair<Integer, String> styleAndSource = themeMap.get(deviceColorPropertyValue);
        if (styleAndSource == null) {
            Log.d(TAG, "Sysprop `" + deviceColorProperty + "` of value '"
                    + deviceColorPropertyValue
                    + "' not found in theming_defaults: " + Arrays.toString(themeData)
                    + ". Using wildcard fallback.");
            styleAndSource = fallbackTheme;
        }

        try {
            return buildSettingsFromConfig(styleAndSource, userId);
        } catch (Exception e) {
            Log.w(TAG, "Could not build theme from device config, falling back to wildcard.", e);
            try {
                return buildSettingsFromConfig(fallbackTheme, userId);
            } catch (Exception e2) {
                Log.e(TAG, "Wildcard fallback theme is also invalid! Using hardcoded default.", e2);
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
            WallpaperColors wallpaperColors = mWallpaperManagerInternal.getWallpaperColors(
                    WallpaperManager.FLAG_SYSTEM, userId);
            if (wallpaperColors == null) {
                throw new IllegalStateException("Wallpaper colors could not be retrieved.");
            }
            seedColor = wallpaperColors.getPrimaryColor();
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
    private ThemeSettings fromJson(@Nullable String jsonString)
            throws JSONException {
        if (TextUtils.isEmpty(jsonString)) {
            return null;
        }
        JSONObject json = new JSONObject(jsonString);

        if (!json.has(TIMESTAMP)) {
            Log.w(TAG, "JSON missing timestamp");
        }

        Instant timestamp = Instant.ofEpochMilli(json.getLong(TIMESTAMP));
        int themeStyle = parseAndValidate(json, OVERLAY_CATEGORY_THEME_STYLE,
                ThemeStyle.TONAL_SPOT);
        String colorSource = parseAndValidate(json, OVERLAY_COLOR_SOURCE, VALUE_HOME_WALLPAPER);

        Color systemPalette = parseAndValidate(json, OVERLAY_CATEGORY_SYSTEM_PALETTE, null);

        return new ThemeSettings.Builder()
                .setAppliedTimestamp(timestamp)
                .setThemeStyle(themeStyle)
                .setColorSource(colorSource)
                .setSystemPalette(systemPalette)
                .build();
    }

    @NonNull
    private String toJson(@NonNull ThemeSettings settings, @Nullable String oldJsonString) {
        try {
            // Start with the original JSON data to preserve unknown fields.
            JSONObject json = TextUtils.isEmpty(oldJsonString)
                    ? new JSONObject() : new JSONObject(oldJsonString);

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