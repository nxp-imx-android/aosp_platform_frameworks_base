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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.annotation.SuppressLint;
import android.content.theming.FieldColorSource;
import android.content.theming.ThemeSettings;
import android.content.theming.ThemeStyle;
import android.graphics.Color;
import android.os.Parcel;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Instant;

@RunWith(AndroidJUnit4.class)
public class ThemeSettingsTests {
    @Test
    public void testBuilder_wallpaper() {
        Instant testStart = Instant.now();
        ThemeSettings settings = new ThemeSettings.Builder()
                .setThemeStyle(ThemeStyle.TONAL_SPOT)
                .setColorSource(FieldColorSource.VALUE_HOME_WALLPAPER)
                .setSystemPalette(Color.valueOf(Color.BLUE))
                .build();

        assertThat(settings.themeStyle()).isEqualTo(ThemeStyle.TONAL_SPOT);
        assertThat(settings.colorSource()).isEqualTo(FieldColorSource.VALUE_HOME_WALLPAPER);
        assertThat(settings.systemPalette()).isNotNull();
        assertThat(settings.timeStamp()).isAtLeast(testStart);
    }

    @Test
    public void testBuilder_preset() {
        Color testColor = Color.valueOf(0xFF1A73E8);
        Instant testStart = Instant.now();

        ThemeSettings settings = new ThemeSettings.Builder()
                .setThemeStyle(ThemeStyle.TONAL_SPOT)
                .setColorSource(FieldColorSource.VALUE_PRESET)
                .setSystemPalette(testColor)
                .build();

        assertThat(settings.themeStyle()).isEqualTo(ThemeStyle.TONAL_SPOT);
        assertThat(settings.colorSource()).isEqualTo(FieldColorSource.VALUE_PRESET);
        assertThat(settings.systemPalette()).isEqualTo(testColor);
        assertThat(settings.timeStamp()).isAtLeast(testStart);
    }

    @Test
    public void testParcel_roundTrip() {
        // Test wallpaper
        ThemeSettings originalWallpaper = new ThemeSettings.Builder()
                .setThemeStyle(ThemeStyle.VIBRANT)
                .setColorSource(FieldColorSource.VALUE_HOME_WALLPAPER)
                .setSystemPalette(Color.valueOf(Color.RED))
                .build();
        Parcel wallpaperParcel = Parcel.obtain();
        originalWallpaper.writeToParcel(wallpaperParcel, 0);
        wallpaperParcel.setDataPosition(0);
        ThemeSettings unparceledWallpaper = ThemeSettings.CREATOR.createFromParcel(
                wallpaperParcel);
        wallpaperParcel.recycle();
        assertThat(unparceledWallpaper.themeStyle()).isEqualTo(originalWallpaper.themeStyle());
        assertThat(unparceledWallpaper.colorSource()).isEqualTo(originalWallpaper.colorSource());
        assertThat(unparceledWallpaper.systemPalette())
                .isEqualTo(originalWallpaper.systemPalette());
        assertThat(unparceledWallpaper.timeStamp().toEpochMilli()).isAtLeast(
                originalWallpaper.timeStamp().toEpochMilli());

        // Test preset
        ThemeSettings originalPreset = new ThemeSettings.Builder()
                .setThemeStyle(ThemeStyle.TONAL_SPOT)
                .setColorSource(FieldColorSource.VALUE_PRESET)
                .setSystemPalette(Color.valueOf(0xFF1A73E8))
                .build();
        Parcel presetParcel = Parcel.obtain();
        originalPreset.writeToParcel(presetParcel, 0);
        presetParcel.setDataPosition(0);
        ThemeSettings unparceledPreset = ThemeSettings.CREATOR.createFromParcel(presetParcel);
        presetParcel.recycle();
        assertThat(unparceledPreset.themeStyle()).isEqualTo(originalPreset.themeStyle());
        assertThat(unparceledPreset.colorSource()).isEqualTo(originalPreset.colorSource());
        assertThat(unparceledPreset.systemPalette()).isEqualTo(originalPreset.systemPalette());
        assertThat(unparceledPreset.timeStamp().toEpochMilli()).isAtLeast(
                originalPreset.timeStamp().toEpochMilli());
    }

    @SuppressLint("WrongConstant")
    @Test
    public void testBuilder_preset_withInvalidStyle_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ThemeSettings.Builder()
                        .setThemeStyle(-1)
                        .setColorSource(FieldColorSource.VALUE_PRESET)
                        .setSystemPalette(Color.valueOf(Color.BLUE))
                        .build());
    }

    @Test
    public void testBuilder_preset_withInvalidColor_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ThemeSettings.Builder()
                        .setThemeStyle(ThemeStyle.TONAL_SPOT)
                        .setColorSource(FieldColorSource.VALUE_PRESET)
                        .setSystemPalette(Color.valueOf(Color.TRANSPARENT))
                        .build());
    }

    @SuppressLint("WrongConstant")
    @Test
    public void testBuilder_wallpaper_withInvalidStyle_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ThemeSettings.Builder()
                        .setThemeStyle(-1)
                        .setColorSource(FieldColorSource.VALUE_HOME_WALLPAPER)
                        .setSystemPalette(Color.valueOf(Color.RED))
                        .build());
    }
}