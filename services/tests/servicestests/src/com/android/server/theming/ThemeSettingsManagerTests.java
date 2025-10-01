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

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import android.app.WallpaperColors;
import android.content.ContentResolver;
import android.content.theming.FieldColorSource;
import android.content.theming.ThemeSettings;
import android.content.theming.ThemeStyle;
import android.graphics.Color;
import android.provider.Settings;
import android.testing.TestableContext;
import android.testing.TestableResources;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.runner.AndroidJUnit4;

import com.android.internal.R;
import com.android.server.wallpaper.WallpaperManagerInternal;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class ThemeSettingsManagerTests {
    private final int mUserId = 0;

    @Rule
    public final HardwareColorRule mHardwareColorRule = new HardwareColorRule();

    @Mock
    private WallpaperManagerInternal mMockWmi;

    private final SystemPropertiesReader mSystemPropertiesReader = new SystemPropertiesReader() {
        @NonNull
        @Override
        public String get(@NonNull String key, @Nullable String def) {
            return mHardwareColorRule.color;
        }
    };

    @Rule
    public final TestableContext mContext = new TestableContext(
            getInstrumentation().getTargetContext(), null);

    private ContentResolver mContentResolver;
    private ThemeSettingsManager mManager;
    private final Color mDefaultWallpaperColor = Color.valueOf(Color.BLUE);

    private static final String UNKNOWN_FIELDS_JSON = """
                    {
                      "_applied_timestamp": 1749626671504,
                      "android.theme.customization.color_source": "preset",
                      "android.theme.customization.theme_style": "TONAL_SPOT",
                      "android.theme.customization.system_palette": "FF1A73E8",
                      "exotic_property": "some_value",
                      "another_one": { "nested": true }
                    }
            """;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mContentResolver = mContext.getContentResolver();
        mManager = new ThemeSettingsManager(mMockWmi);

        Settings.Secure.putStringForUser(mContentResolver,
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, null, mUserId);

        TestableResources userResources = mContext.getOrCreateTestableResources();
        userResources.addOverride(R.array.theming_defaults, mHardwareColorRule.options);
    }

    @Test
    public void loadSettings_noSettings_returnsNull() {
        ThemeSettings settings = mManager.readSettings(mUserId, mContentResolver);
        assertThat(settings).isNull();
    }

    @Test
    public void loadSettings_emptyJSON_returnsNull() {
        Settings.Secure.putStringForUser(mContentResolver,
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, "{}", mUserId);
        ThemeSettings settings = mManager.readSettings(mUserId, mContentResolver);
        assertThat(settings).isNull();
    }

    @Test
    public void loadSettings_invalidJSON_returnsNull() {
        Settings.Secure.putStringForUser(mContentResolver,
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, "{invalid_json", mUserId);
        ThemeSettings settings = mManager.readSettings(mUserId, mContentResolver);
        assertThat(settings).isNull();
    }

    @Test
    public void writeSettings_writesPresetToProvider() throws Exception {
        long currentTime = System.currentTimeMillis();
        ThemeSettings presetSettings = new ThemeSettings.Builder()
                .setThemeStyle(ThemeStyle.MONOCHROMATIC)
                .setColorSource(FieldColorSource.VALUE_PRESET)
                .setSystemPalette(Color.valueOf(0xFF112233))
                .build();

        boolean success = mManager.writeSettings(mUserId, mContentResolver, presetSettings);
        assertThat(success).isTrue();

        String settingsString = Settings.Secure.getStringForUser(mContentResolver,
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, mUserId);

        JSONObject settingsJson = new JSONObject(settingsString);

        assertThat(settingsJson.has(ThemeSettingsManager.TIMESTAMP)).isTrue();
        assertThat(settingsJson.getLong(ThemeSettingsManager.TIMESTAMP)).isAtLeast(currentTime);
        assertThat(settingsJson.getString(ThemeSettingsManager.OVERLAY_CATEGORY_SYSTEM_PALETTE))
                .isEqualTo("FF112233");
        // For backward compatibility, accent_color must be written with the same value.
        assertThat(settingsJson.getString(ThemeSettingsManager.OVERLAY_CATEGORY_ACCENT_COLOR))
                .isEqualTo("FF112233");
        assertThat(settingsJson.getString(ThemeSettingsManager.OVERLAY_COLOR_SOURCE))
                .isEqualTo(FieldColorSource.VALUE_PRESET);
        assertThat(settingsJson.getString(ThemeSettingsManager.OVERLAY_CATEGORY_THEME_STYLE))
                .isEqualTo(ThemeStyle.toString(ThemeStyle.MONOCHROMATIC));

        assertThat(settingsJson.length()).isEqualTo(5);
    }

    @Test
    public void writeSettings_writesWallpaperToProvider() throws Exception {
        ThemeSettings wallpaperSettings = new ThemeSettings.Builder()
                .setThemeStyle(ThemeStyle.VIBRANT)
                .setColorSource(FieldColorSource.VALUE_HOME_WALLPAPER)
                .setSystemPalette(Color.valueOf(Color.BLUE))
                .build();

        boolean success = mManager.writeSettings(mUserId, mContentResolver, wallpaperSettings);
        assertThat(success).isTrue();

        String settingsString = Settings.Secure.getStringForUser(mContentResolver,
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, mUserId);
        JSONObject settingsJson = new JSONObject(settingsString);

        assertThat(settingsJson.has(ThemeSettingsManager.TIMESTAMP)).isTrue();
        assertThat(settingsJson.getLong(ThemeSettingsManager.TIMESTAMP)).isEqualTo(
                wallpaperSettings.timeStamp().toEpochMilli());
        assertThat(settingsJson.getString(ThemeSettingsManager.OVERLAY_COLOR_SOURCE)).isEqualTo(
                FieldColorSource.VALUE_HOME_WALLPAPER);
        assertThat(settingsJson.getString(
                ThemeSettingsManager.OVERLAY_CATEGORY_THEME_STYLE)).isEqualTo(
                ThemeStyle.toString(ThemeStyle.VIBRANT));

        assertThat(settingsJson.has(ThemeSettingsManager.OVERLAY_CATEGORY_SYSTEM_PALETTE)).isTrue();
        assertThat(settingsJson.has(ThemeSettingsManager.OVERLAY_CATEGORY_ACCENT_COLOR)).isTrue();
        assertThat(settingsJson.length()).isEqualTo(5);
    }

    @Test
    public void writeAndReadSettings_persistsAndReadsCorrectly() {
        // Test Wallpaper case
        ThemeSettings originalWallpaper = new ThemeSettings.Builder()
                .setThemeStyle(ThemeStyle.EXPRESSIVE)
                .setColorSource(FieldColorSource.VALUE_HOME_WALLPAPER)
                .setSystemPalette(Color.valueOf(Color.RED))
                .build();

        mManager.writeSettings(mUserId, mContentResolver, originalWallpaper);
        ThemeSettings loadedWallpaper = mManager.readSettings(mUserId, mContentResolver);

        assertThat(loadedWallpaper).isNotNull();
        assertThat(loadedWallpaper.timeStamp().toEpochMilli()).isEqualTo(
                originalWallpaper.timeStamp().toEpochMilli());
        assertThat(loadedWallpaper.themeStyle()).isEqualTo(originalWallpaper.themeStyle());
        assertThat(loadedWallpaper.colorSource()).isEqualTo(originalWallpaper.colorSource());
        assertThat(loadedWallpaper.systemPalette()).isEqualTo(originalWallpaper.systemPalette());

        // Test Preset case
        ThemeSettings originalPreset = new ThemeSettings.Builder()
                .setThemeStyle(ThemeStyle.SPRITZ)
                .setColorSource(FieldColorSource.VALUE_PRESET)
                .setSystemPalette(Color.valueOf(Color.GREEN))
                .build();

        mManager.writeSettings(mUserId, mContentResolver, originalPreset);
        ThemeSettings loadedPreset = mManager.readSettings(mUserId, mContentResolver);

        assertThat(loadedPreset).isNotNull();
        assertThat(loadedPreset.timeStamp().toEpochMilli()).isEqualTo(
                originalPreset.timeStamp().toEpochMilli());
        assertThat(loadedPreset.themeStyle()).isEqualTo(originalPreset.themeStyle());
        assertThat(loadedPreset.colorSource()).isEqualTo(originalPreset.colorSource());
        assertThat(loadedPreset.systemPalette()).isEqualTo(originalPreset.systemPalette());
    }

    @Test
    public void writeAndReadSettings_preservesUnknownFields() throws Exception {
        // Manually write JSON with unknown fields
        Settings.Secure.putStringForUser(mContentResolver,
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, UNKNOWN_FIELDS_JSON, mUserId);

        // Read settings, then write them back without modification
        ThemeSettings settings = mManager.readSettings(mUserId, mContentResolver);
        assertThat(settings).isNotNull();
        mManager.writeSettings(mUserId, mContentResolver, settings);

        // Read the raw JSON again and verify unknown fields are still present
        String finalJsonString = Settings.Secure.getStringForUser(mContentResolver,
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, mUserId);
        JSONObject finalJson = new JSONObject(finalJsonString);

        assertThat(finalJson.getString("exotic_property")).isEqualTo("some_value");
        assertThat(finalJson.getJSONObject("another_one").getBoolean("nested")).isTrue();
        // Verify known fields are also correct
        assertThat(finalJson.getString(ThemeSettingsManager.OVERLAY_COLOR_SOURCE)).isEqualTo(
                "preset");
    }

    @Test
    @HardwareColors(color = "RED_DEV", options = {
            "RED_DEV|VIBRANT|#FF0000",
            "*|TONAL_SPOT|#00FF00"
    })
    public void createDefaultThemeSettings_matchesHardwareColor() {
        ThemeSettings defaultSettings = mManager.createDefaultThemeSettings(
                mContext.getResources(), mSystemPropertiesReader, mUserId);

        assertThat(defaultSettings.colorSource()).isEqualTo(FieldColorSource.VALUE_PRESET);
        assertThat(defaultSettings.themeStyle()).isEqualTo(ThemeStyle.VIBRANT);
        assertThat(defaultSettings.systemPalette().toArgb()).isEqualTo(
                Color.parseColor("#FFFF0000"));
    }

    @Test
    @HardwareColors(color = "BLUE_DEV", options = {
            "RED_DEV|VIBRANT|#FF0000",
            "*|TONAL_SPOT|#00FF00"
    })
    public void createDefaultThemeSettings_usesWildcardFallback_preset() {
        ThemeSettings defaultSettings = mManager.createDefaultThemeSettings(
                mContext.getResources(), mSystemPropertiesReader, mUserId);

        assertThat(defaultSettings.colorSource()).isEqualTo(FieldColorSource.VALUE_PRESET);
        assertThat(defaultSettings.themeStyle()).isEqualTo(ThemeStyle.TONAL_SPOT);
        assertThat(defaultSettings.systemPalette().toArgb()).isEqualTo(
                Color.parseColor("#FF00FF00"));
    }

    @Test
    @HardwareColors(color = "BLUE_DEV", options = {
            "RED_DEV|VIBRANT|#FF0000",
            "*|EXPRESSIVE|home_wallpaper"
    })
    public void createDefaultThemeSettings_usesWildcardFallback_wallpaper() {
        Color cyan = Color.valueOf(Color.CYAN);
        WallpaperColors wallpaperColors = new WallpaperColors(cyan, null, null);
        when(mMockWmi.getWallpaperColors(anyInt(), anyInt())).thenReturn(wallpaperColors);

        ThemeSettings defaultSettings = mManager.createDefaultThemeSettings(
                mContext.getResources(), mSystemPropertiesReader, mUserId);

        assertThat(defaultSettings.colorSource()).isEqualTo(FieldColorSource.VALUE_HOME_WALLPAPER);
        assertThat(defaultSettings.themeStyle()).isEqualTo(ThemeStyle.EXPRESSIVE);
        assertThat(defaultSettings.systemPalette()).isEqualTo(Color.valueOf(Color.CYAN));
    }

    @Test
    @HardwareColors(color = "BLUE_DEV", options = {
            "RED_DEV|VIBRANT|#FF0000",
            "*|EXPRESSIVE|home_wallpaper"
    })
    public void createDefaultThemeSettings_wallpaperNoColors_usesWildcardFallback() {
        when(mMockWmi.getWallpaperColors(anyInt(), anyInt())).thenReturn(null);

        ThemeSettings defaultSettings = mManager.createDefaultThemeSettings(
                mContext.getResources(), mSystemPropertiesReader, mUserId);

        // Since the primary source (wallpaper) failed, it should fall back to the wildcard,
        // which is also wallpaper. Since that will also fail, it uses the hardcoded fallback.
        assertThat(defaultSettings.colorSource()).isEqualTo(FieldColorSource.VALUE_PRESET);
        assertThat(defaultSettings.themeStyle()).isEqualTo(ThemeStyle.TONAL_SPOT);
        assertThat(defaultSettings.systemPalette()).isEqualTo(Color.valueOf(0xFF1b6ef3));
    }

    @Test
    @HardwareColors(color = "ANY", options = {
            "RED_DEV|VIBRANT|#FF0000"
            // No wildcard
    })
    public void createDefaultThemeSettings_noWildcard_throwsException() {
        assertThrows(IllegalStateException.class,
                () -> mManager.createDefaultThemeSettings(
                        mContext.getResources(), mSystemPropertiesReader, mUserId));
    }

    @Test
    @HardwareColors(color = "ANY", options = {
            "*|TONAL_SPOT|invalid-color"
    })
    public void createDefaultThemeSettings_malformedColor_fallsBackToHardcoded() {
        ThemeSettings defaultSettings = mManager.createDefaultThemeSettings(
                mContext.getResources(), mSystemPropertiesReader, mUserId);
        assertThat(defaultSettings.colorSource()).isEqualTo(FieldColorSource.VALUE_PRESET);
        assertThat(defaultSettings.themeStyle()).isEqualTo(ThemeStyle.TONAL_SPOT);
        assertThat(defaultSettings.systemPalette()).isEqualTo(Color.valueOf(0xFF1b6ef3));
    }
}