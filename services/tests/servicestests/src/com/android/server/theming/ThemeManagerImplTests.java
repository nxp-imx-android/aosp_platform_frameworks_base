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

import static android.content.theming.FieldColorSource.VALUE_PRESET;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.ActivityManagerInternal;
import android.app.WallpaperColors;
import android.content.theming.IThemeChangedCallback;
import android.content.theming.IThemeSettingsCallback;
import android.content.theming.ThemeInfo;
import android.content.theming.ThemeSettings;
import android.content.theming.ThemeStyle;
import android.graphics.Color;
import android.os.FabricatedOverlayInternal;
import android.os.UserHandle;
import android.provider.Settings;
import android.testing.TestableContext;
import android.testing.TestableResources;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.internal.R;
import com.android.server.LocalServices;
import com.android.server.om.OverlayManagerInternal;
import com.android.server.pm.UserManagerInternal;
import com.android.server.wallpaper.WallpaperManagerInternal;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
@HardwareColors(color = "", options = {"*|TONAL_SPOT|#00FF00"})
public class ThemeManagerImplTests {
    @Rule
    public final HardwareColorRule mHardwareColorRule = new HardwareColorRule();

    private final IThemeSettingsCallback mSettingsCallback = new IThemeSettingsCallback.Stub() {
        @Override
        public void onSettingsChanged(ThemeSettings oldSettings, ThemeSettings newSettings) {
        }
    };

    private final IThemeChangedCallback mThemeChangedCallback = new IThemeChangedCallback.Stub() {
        @Override
        public void onThemeChanged(ThemeInfo newTheme) {
        }
    };

    private final int mUserId = 0;
    private ThemeManagerImpl mUnderTest;
    private TestableContext mContext;
    private ThemeSettingsManager mThemeSettingsManager;
    private ThemeEnvironment mEnvironment;

    private static final int TEST_SEED_COLOR = Color.BLUE;
    private static final float TEST_CONTRAST = 0.5f;
    private static final int TEST_STYLE = ThemeStyle.VIBRANT;

    @Mock
    private UserManagerInternal mUserManager;
    @Mock
    private OverlayManagerInternal mOverlayManager;
    @Mock
    private WallpaperManagerInternal mWallpaperManagerInternal;
    @Mock
    private ActivityManagerInternal mActivityManagerInternal;
    @Mock
    private ThemeOverlayHelper mOverlayHelper;

    private FakeScheduledExecutorService mSchedulerExecutor;
    private ThemeStateManager mStateManager;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        LocalServices.removeServiceForTest(OverlayManagerInternal.class);
        LocalServices.removeServiceForTest(UserManagerInternal.class);
        LocalServices.removeServiceForTest(WallpaperManagerInternal.class);
        LocalServices.removeServiceForTest(ActivityManagerInternal.class);

        LocalServices.addService(OverlayManagerInternal.class, mOverlayManager);
        LocalServices.addService(UserManagerInternal.class, mUserManager);
        LocalServices.addService(WallpaperManagerInternal.class, mWallpaperManagerInternal);
        LocalServices.addService(ActivityManagerInternal.class, mActivityManagerInternal);

        mContext = new TestableContext(InstrumentationRegistry.getTargetContext(), null);
        TestableResources testableResources = mContext.getOrCreateTestableResources();
        testableResources.addOverride(R.array.theming_defaults, mHardwareColorRule.options);

        Settings.Secure.putStringForUser(mContext.getContentResolver(),
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, null, mUserId);

        when(mUserManager.getProfileParentId(anyInt())).thenAnswer(
                invocation -> invocation.getArgument(0));
        when(mUserManager.getProfileIds(anyInt(), anyBoolean())).thenAnswer(invocation -> {
            int requestedUserId = invocation.getArgument(0);
            return new int[]{requestedUserId};
        });

        mEnvironment = new ThemeEnvironment(mContext, mUserManager,
                mHardwareColorRule.sysPropReader);

        ThemeWallpaperManager themeWallpaperManager = new ThemeWallpaperManager(
                mWallpaperManagerInternal);
        mThemeSettingsManager = new ThemeSettingsManager(themeWallpaperManager,
                mHardwareColorRule.sysPropReader, mEnvironment);
        mSchedulerExecutor = new FakeScheduledExecutorService();

        mStateManager = new ThemeStateManager(mContext, mSchedulerExecutor, mEnvironment);
        mUnderTest = new ThemeManagerImpl(mContext, mThemeSettingsManager,
                mStateManager, mOverlayHelper, mEnvironment) {
            @Override
            public void onBootAnimationDismissing() {
            }
        };

        mStateManager.onServicesReady();
    }

    private void startUser(int userId, boolean isSetup, int seedColor, float contrast, int style) {
        mStateManager.onUserStart(UserHandle.of(userId), isSetup, seedColor, contrast, style);
        mSchedulerExecutor.fastForwardTime(ThemeStateManager.DEBOUNCE_MS + 100L);
    }

    @Test
    public void testRegisterThemeSettingsCallback_success() {
        boolean result = mUnderTest.registerThemeSettingsCallback(mUserId, mSettingsCallback);
        assertThat(result).isTrue();
    }

    @Test
    public void testRegisterThemeSettingsCallback_nullCallback_returnsFalse() {
        boolean result = mUnderTest.registerThemeSettingsCallback(mUserId, null);
        assertThat(result).isFalse();
    }

    @Test
    public void testUnregisterThemeSettingsCallback_success() {
        boolean didRegister = mUnderTest.registerThemeSettingsCallback(mUserId, mSettingsCallback);
        assertThat(didRegister).isTrue();
        boolean result = mUnderTest.unregisterThemeSettingsCallback(mUserId, mSettingsCallback);
        assertThat(result).isTrue();
    }

    @Test
    public void testUnregisterThemeSettingsCallback_callbackNotRegistered_returnsFalse() {
        boolean result = mUnderTest.unregisterThemeSettingsCallback(mUserId, mSettingsCallback);
        assertThat(result).isFalse();
    }

    @Test
    public void testUnregisterThemeSettingsCallback_nullCallback_returnsFalse() {
        boolean result = mUnderTest.unregisterThemeSettingsCallback(mUserId, null);
        assertThat(result).isFalse();
    }

    @Test
    public void testSettingsCallback_receivesNewValue() {
        final Color testColor = Color.valueOf(Color.parseColor("#FF0000"));
        final ThemeSettings newPayload = new ThemeSettings.Builder()
                .setThemeStyle(ThemeStyle.VIBRANT)
                .setColorSource(VALUE_PRESET)
                .setSystemPalette(testColor)
                .build();
        final ThemeSettings[] returnedOldSettings = {null};
        final ThemeSettings[] returnedNewSettings = {null};

        boolean didRegister = mUnderTest.registerThemeSettingsCallback(mUserId,
                new IThemeSettingsCallback.Stub() {
                    @Override
                    public void onSettingsChanged(ThemeSettings oldSettings,
                            ThemeSettings newSettings) {
                        returnedOldSettings[0] = oldSettings;
                        returnedNewSettings[0] = newSettings;
                    }
                });

        assertThat(didRegister).isTrue();
        // When no theme is set, oldSettings should be null.
        mUnderTest.notifySettingsChange(mUserId, null, newPayload);
        assertThat(returnedOldSettings[0]).isNull();
        assertThat(returnedNewSettings[0]).isEqualTo(newPayload);
    }

    @Test
    public void testSettingsCallback_receivesOldAndNewValue() {
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
        mUnderTest.updateThemeSettings(mUserId, oldPayload);

        boolean didRegister = mUnderTest.registerThemeSettingsCallback(mUserId,
                new IThemeSettingsCallback.Stub() {
                    @Override
                    public void onSettingsChanged(ThemeSettings oldSettings,
                            ThemeSettings newSettings) {
                        returnedOldSettings[0] = oldSettings;
                        returnedNewSettings[0] = newSettings;
                    }
                });
        assertThat(didRegister).isTrue();

        // Notify with the new settings.
        mUnderTest.notifySettingsChange(mUserId, oldPayload, newPayload);

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
    public void testUnregisterThemeChangedCallback_success() {
        mUnderTest.registerThemeChangedCallback(mUserId, mThemeChangedCallback);
        mUnderTest.unregisterThemeChangedCallback(mUserId, mThemeChangedCallback);
    }

    @Test
    public void testThemeChangedCallback_receivesNewValue() {
        startUser(mUserId, true, TEST_SEED_COLOR, TEST_CONTRAST, TEST_STYLE);
        final ThemeInfo[] returnedValue = {null};
        mUnderTest.registerThemeChangedCallback(mUserId, new IThemeChangedCallback.Stub() {
            @Override
            public void onThemeChanged(ThemeInfo newTheme) {
                returnedValue[0] = newTheme;
            }
        });

        mUnderTest.notifyThemeChanged(mUserId);

        assertThat(returnedValue[0]).isNotNull();
        assertThat(returnedValue[0].seedColor.toArgb()).isEqualTo(TEST_SEED_COLOR);
        assertThat(returnedValue[0].style).isEqualTo(TEST_STYLE);
        assertThat(returnedValue[0].contrast).isEqualTo(TEST_CONTRAST);
        assertThat(returnedValue[0].specVersion).isEqualTo(mEnvironment.specVersion.name());
        assertThat(returnedValue[0].platform).isEqualTo(mEnvironment.platform.name());
    }

    @Test
    public void getThemeSettings_noSetting_returnsNull() {
        ThemeSettings settings = mUnderTest.getThemeSettings(mUserId);
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
        mUnderTest.updateThemeSettings(mUserId, storedSettings);

        ThemeSettings settings = mUnderTest.getThemeSettings(mUserId);

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
        Color cyan = Color.valueOf(Color.CYAN);
        WallpaperColors wallpaperColors = new WallpaperColors(cyan, null, null);
        when(mWallpaperManagerInternal.getWallpaperColors(anyInt(), anyInt()))
                .thenReturn(wallpaperColors);

        ThemeSettings expectedDefault = mThemeSettingsManager.createDefaultThemeSettings(mUserId);
        ThemeSettings settings = mUnderTest.getThemeSettingsOrDefault(mUserId);

        assertThat(settings.themeStyle()).isEqualTo(expectedDefault.themeStyle());
        assertThat(settings.colorSource()).isEqualTo(expectedDefault.colorSource());
        assertThat(settings.systemPalette()).isEqualTo(expectedDefault.systemPalette());
        assertThat(settings.timeStamp().toEpochMilli()).isAtLeast(
                expectedDefault.timeStamp().toEpochMilli());
    }

    @Test
    public void getThemeSettingsOrDefault_withSetting_returnsStored() {
        final Color testColor = Color.valueOf(Color.RED);
        final ThemeSettings storedSettings = new ThemeSettings.Builder()
                .setThemeStyle(ThemeStyle.VIBRANT)
                .setColorSource(VALUE_PRESET)
                .setSystemPalette(testColor)
                .build();
        mUnderTest.updateThemeSettings(mUserId, storedSettings);

        ThemeSettings settings = mUnderTest.getThemeSettingsOrDefault(mUserId);

        // Verify the loaded payload field-by-field due to timestamp precision loss on save/load.
        ThemeSettings returnedPreset = settings;
        assertThat(returnedPreset).isNotNull();
        assertThat(returnedPreset.timeStamp().toEpochMilli()).isEqualTo(
                storedSettings.timeStamp().toEpochMilli());
        assertThat(returnedPreset.themeStyle()).isEqualTo(storedSettings.themeStyle());
        assertThat(returnedPreset.systemPalette()).isEqualTo(storedSettings.systemPalette());
    }

    @Test
    public void getUserThemeInfo_returnsCorrectInfo() {
        startUser(mUserId, true, TEST_SEED_COLOR, TEST_CONTRAST, TEST_STYLE);

        ThemeInfo info = mUnderTest.getUserThemeInfo(mUserId);

        assertThat(info).isNotNull();
        assertThat(info.seedColor.toArgb()).isEqualTo(TEST_SEED_COLOR);
        assertThat(info.style).isEqualTo(TEST_STYLE);
        assertThat(info.contrast).isEqualTo(TEST_CONTRAST);
        assertThat(info.specVersion).isEqualTo(mEnvironment.specVersion.name());
        assertThat(info.platform).isEqualTo(mEnvironment.platform.name());
    }

    @Test
    public void generateDynamicColorOverlay_withAllOptions_isNotNull() {
        startUser(mUserId, true, Color.RED, 0.0f, ThemeStyle.TONAL_SPOT);
        ThemeInfo options = new ThemeInfo.Builder()
                .setSeedColor(Color.valueOf(Color.GREEN))
                .setStyle(ThemeStyle.VIBRANT)
                .setContrast(0.8f)
                .build();

        // Mock the overlay helper to return a dummy overlay
        FabricatedOverlayInternal mockOverlay = new FabricatedOverlayInternal();
        android.content.om.FabricatedOverlay mockFabricatedOverlay =
                mock(android.content.om.FabricatedOverlay.class);
        when(mockFabricatedOverlay.getInternal()).thenReturn(mockOverlay);
        doReturn(mockFabricatedOverlay).when(mOverlayHelper).createDynamicOverlay(any(), any(),
                anyInt());

        FabricatedOverlayInternal overlay = mUnderTest.generateDynamicColorOverlay(mUserId,
                options);

        assertThat(overlay).isNotNull();
    }

    @Test
    public void generateDynamicColorOverlay_withNullOptions_isNotNull() {
        startUser(mUserId, true, Color.RED, 0.0f, ThemeStyle.TONAL_SPOT);
        ThemeInfo options = new ThemeInfo.Builder().build();

        // Mock the overlay helper to return a dummy overlay
        FabricatedOverlayInternal mockOverlay = new FabricatedOverlayInternal();
        android.content.om.FabricatedOverlay mockFabricatedOverlay =
                mock(android.content.om.FabricatedOverlay.class);
        when(mockFabricatedOverlay.getInternal()).thenReturn(mockOverlay);
        doReturn(mockFabricatedOverlay).when(mOverlayHelper).createDynamicOverlay(any(), any(),
                anyInt());

        FabricatedOverlayInternal overlay = mUnderTest.generateDynamicColorOverlay(mUserId,
                options);

        assertThat(overlay).isNotNull();
    }

    @Test
    public void generateDynamicColorOverlay_withMixedOptions_isNotNull() {
        startUser(mUserId, true, Color.RED, 0.0f, ThemeStyle.TONAL_SPOT);
        ThemeInfo options = new ThemeInfo.Builder()
                .setSeedColor(Color.valueOf(Color.GREEN))
                .setContrast(0.8f)
                .build();

        // Mock the overlay helper to return a dummy overlay
        FabricatedOverlayInternal mockOverlay = new FabricatedOverlayInternal();
        android.content.om.FabricatedOverlay mockFabricatedOverlay =
                mock(android.content.om.FabricatedOverlay.class);
        when(mockFabricatedOverlay.getInternal()).thenReturn(mockOverlay);
        doReturn(mockFabricatedOverlay).when(mOverlayHelper).createDynamicOverlay(any(), any(),
                anyInt());

        FabricatedOverlayInternal overlay = mUnderTest.generateDynamicColorOverlay(mUserId,
                options);

        assertThat(overlay).isNotNull();
    }


}
