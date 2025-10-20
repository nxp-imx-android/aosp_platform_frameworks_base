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


import static android.content.PermissionChecker.PERMISSION_GRANTED;
import static android.content.theming.FieldColorSource.VALUE_PRESET;

import static com.google.common.truth.Truth.assertThat;

import android.content.theming.IThemeSettingsCallback;
import android.content.theming.ThemeSettings;
import android.content.theming.ThemeStyle;
import android.graphics.Color;
import android.os.Binder;
import android.os.UserHandle;
import android.provider.Settings;
import android.testing.TestableContext;
import android.testing.TestablePermissions;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.wallpaper.WallpaperManagerInternal;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class ThemeBinderServiceTests {
    private final IThemeSettingsCallback mCallback = new IThemeSettingsCallback.Stub() {
        @Override
        public void onSettingsChanged(ThemeSettings oldSettings, ThemeSettings newSettings) {
        }
    };

    private int mUserId;
    private ThemeBinderService mUnderTest;
    private ThemeManagerInternal mInternal;
    private ThemeSettings mDefaultSettings;

    @Mock
    private WallpaperManagerInternal mMockWmi;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        TestableContext context = new TestableContext(InstrumentationRegistry.getTargetContext(),
                null);

        mUserId = UserHandle.getUserId(Binder.getCallingUid());

        Settings.Secure.putStringForUser(context.getContentResolver(),
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, null, mUserId);

        TestablePermissions perms = context.getTestablePermissions();
        perms.setPermission(android.Manifest.permission.INTERACT_ACROSS_USERS, PERMISSION_GRANTED);

        ThemeSettingsManager themeSettingsManager = new ThemeSettingsManager(mMockWmi);
        SystemPropertiesReader systemPropertiesReader = new SystemPropertiesReader() {
            @NonNull
            @Override
            public String get(@NonNull String key, @Nullable String def) {
                return "";
            }
        };
        mInternal = new ThemeManagerInternal(context, themeSettingsManager,
                systemPropertiesReader);
        mUnderTest = new ThemeBinderService(context, mInternal);
        mDefaultSettings = themeSettingsManager.createDefaultThemeSettings(context.getResources(),
                systemPropertiesReader, mUserId);
    }

    @Test
    public void testRegisterThemeSettingsCallback_success() {
        boolean result = mUnderTest.registerThemeSettingsCallback(mCallback);
        assertThat(result).isTrue();
    }

    @Test
    public void testRegisterThemeSettingsCallback_nullCallback_returnsFalse() {
        boolean result = mUnderTest.registerThemeSettingsCallback(null);
        assertThat(result).isFalse();
    }

    @Test
    public void testUnregisterThemeSettingsCallback_success() {
        mUnderTest.registerThemeSettingsCallback(mCallback);
        boolean result = mUnderTest.unregisterThemeSettingsCallback(mCallback);
        assertThat(result).isTrue();
    }

    @Test
    public void testUnregisterThemeSettingsCallback_callbackNotRegistered_returnsFalse() {
        boolean result = mUnderTest.unregisterThemeSettingsCallback(mCallback);
        assertThat(result).isFalse();
    }


    @Test
    public void testCallback_receivesNewValue() {
        final Color testColor = Color.valueOf(Color.parseColor("#FF0000"));
        final ThemeSettings newPayload = new ThemeSettings.Builder()
                .setThemeStyle(ThemeStyle.VIBRANT)
                .setColorSource(VALUE_PRESET)
                .setSystemPalette(testColor)
                .build();
        final ThemeSettings[] returnedOldSettings = {null};
        final ThemeSettings[] returnedNewSettings = {null};

        mUnderTest.registerThemeSettingsCallback(new IThemeSettingsCallback.Stub() {
            @Override
            public void onSettingsChanged(ThemeSettings oldSettings, ThemeSettings newSettings) {
                returnedOldSettings[0] = oldSettings;
                returnedNewSettings[0] = newSettings;
            }
        });

        // When no theme is set, oldSettings should be null.
        mInternal.notifySettingsChange(mUserId, newPayload);
        assertThat(returnedOldSettings[0]).isNull();
        assertThat(returnedNewSettings[0]).isEqualTo(newPayload);
    }

    @Test
    public void testCallback_receivesOldAndNewValue() {
        final Color oldColor = Color.valueOf(Color.BLUE);
        final ThemeSettings oldPayload = new ThemeSettings.Builder()
                .setThemeStyle(ThemeStyle.TONAL_SPOT)
                .setColorSource(VALUE_PRESET)
                .setSystemPalette(oldColor)
                .build();

        final Color newColor = Color.valueOf(Color.RED);
        final ThemeSettings newPayload = new ThemeSettings.Builder()
                .setThemeStyle(ThemeStyle.VIBRANT)
                .setColorSource(VALUE_PRESET)
                .setSystemPalette(newColor)
                .build();

        final ThemeSettings[] returnedOldSettings = {null};
        final ThemeSettings[] returnedNewSettings = {null};

        // Set an initial theme setting.
        mInternal.updateThemeSettings(mUserId, oldPayload);

        mUnderTest.registerThemeSettingsCallback(new IThemeSettingsCallback.Stub() {
            @Override
            public void onSettingsChanged(ThemeSettings oldSettings, ThemeSettings newSettings) {
                returnedOldSettings[0] = oldSettings;
                returnedNewSettings[0] = newSettings;
            }
        });

        // Notify with the new settings.
        mInternal.notifySettingsChange(mUserId, newPayload);

        // The callback should receive the new settings correctly.
        assertThat(returnedNewSettings[0]).isEqualTo(newPayload);

        // Verify the old payload field-by-field due to timestamp precision loss on save/load.
        ThemeSettings returnedOldPreset = returnedOldSettings[0];
        assertThat(returnedOldPreset).isNotNull();
        assertThat(returnedOldPreset.timeStamp().toEpochMilli()).isEqualTo(
                oldPayload.timeStamp().toEpochMilli());
        assertThat(returnedOldPreset.themeStyle()).isEqualTo(oldPayload.themeStyle());
        assertThat(returnedOldPreset.systemPalette()).isEqualTo(oldPayload.systemPalette());
    }

    @Test
    public void getThemeSettings_noSetting_returnsNull() {
        ThemeSettings settings = mUnderTest.getThemeSettings();
        assertThat(settings).isNull();
    }

    @Test
    public void getThemeSettings_withSetting_returnsStored() {
        final Color testColor = Color.valueOf(Color.RED);
        final ThemeSettings storedSettings = new ThemeSettings.Builder()
                .setThemeStyle(ThemeStyle.VIBRANT)
                .setColorSource(VALUE_PRESET)
                .setSystemPalette(testColor)
                .build();
        mInternal.updateThemeSettings(mUserId, storedSettings);

        ThemeSettings settings = mUnderTest.getThemeSettings();

        // Verify the loaded payload field-by-field due to timestamp precision loss on save/load.
        ThemeSettings returnedPreset = settings;
        assertThat(returnedPreset).isNotNull();
        assertThat(returnedPreset.timeStamp().toEpochMilli()).isEqualTo(
                storedSettings.timeStamp().toEpochMilli());
        assertThat(returnedPreset.themeStyle()).isEqualTo(storedSettings.themeStyle());
        assertThat(returnedPreset.systemPalette()).isEqualTo(storedSettings.systemPalette());
    }

    @Test
    public void getThemeSettingsOrDefault_noSetting_returnsDefault() {
        ThemeSettings settings = mUnderTest.getThemeSettingsOrDefault();
        assertThat(settings.themeStyle()).isEqualTo(mDefaultSettings.themeStyle());
        assertThat(settings.colorSource()).isEqualTo(mDefaultSettings.colorSource());
        assertThat(settings.systemPalette()).isEqualTo(mDefaultSettings.systemPalette());
        assertThat(settings.timeStamp().toEpochMilli()).isAtLeast(
                mDefaultSettings.timeStamp().toEpochMilli());
    }

    @Test
    public void getThemeSettingsOrDefault_withSetting_returnsStored() {
        final Color testColor = Color.valueOf(Color.RED);
        final ThemeSettings storedSettings = new ThemeSettings.Builder()
                .setThemeStyle(ThemeStyle.VIBRANT)
                .setColorSource(VALUE_PRESET)
                .setSystemPalette(testColor)
                .build();
        mInternal.updateThemeSettings(mUserId, storedSettings);

        ThemeSettings settings = mUnderTest.getThemeSettingsOrDefault();

        // Verify the loaded payload field-by-field due to timestamp precision loss on save/load.
        ThemeSettings returnedPreset = settings;
        assertThat(returnedPreset).isNotNull();
        assertThat(returnedPreset.timeStamp().toEpochMilli()).isEqualTo(
                storedSettings.timeStamp().toEpochMilli());
        assertThat(returnedPreset.themeStyle()).isEqualTo(storedSettings.themeStyle());
        assertThat(returnedPreset.systemPalette()).isEqualTo(storedSettings.systemPalette());
    }
}