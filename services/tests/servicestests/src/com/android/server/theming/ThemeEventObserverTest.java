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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.KeyguardManager;
import android.app.WallpaperColors;
import android.content.Context;
import android.content.Intent;
import android.content.theming.ThemeSettings;
import android.content.theming.ThemeStyle;
import android.graphics.Color;
import android.net.Uri;
import android.testing.TestableContext;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.LocalServices;
import com.android.server.UiModeManagerInternal;
import com.android.server.pm.UserManagerInternal;
import com.android.server.wallpaper.WallpaperManagerInternal;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.Executor;

@RunWith(AndroidJUnit4.class)
public class ThemeEventObserverTest {

    private static final int USER_ID = 10;

    @Rule
    public final TestableContext mContext = new TestableContext(
            InstrumentationRegistry.getInstrumentation().getContext(), null);

    @Mock
    private ThemeStateManager mThemeStateManager;
    @Mock
    private ThemeSettingsManager mThemeSettingsManager;
    @Mock
    private ThemeUserLifecycle mThemeUserLifecycle;
    @Mock
    private ThemeWallpaperManager mThemeWallpaperManager;
    @Mock
    private UiModeManagerInternal mUiModeManagerInternal;
    @Mock
    private KeyguardManager mKeyguardManager;
    @Mock
    private UserManagerInternal mUserManagerInternal;

    @Mock
    private ThemeManagerImpl mThemeManagerImpl;

    @Captor
    private ArgumentCaptor<UiModeManagerInternal.ContrastListenerInternal> mContrastListenerCaptor;
    @Captor
    private ArgumentCaptor<KeyguardManager.KeyguardLockedStateListener> mKeyguardListenerCaptor;

    private ThemeEventObserver mThemeEventObserver;
    private WallpaperManagerInternal.ColorsChangedCallbackInternal mWallpaperListener;
    private ThemeEnvironment mEnvironment;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        LocalServices.removeServiceForTest(UiModeManagerInternal.class);
        LocalServices.addService(UiModeManagerInternal.class, mUiModeManagerInternal);

        mContext.addMockSystemService(Context.KEYGUARD_SERVICE, mKeyguardManager);

        org.mockito.Mockito.doAnswer(invocation -> {
            mWallpaperListener = invocation.getArgument(0);
            return null;
        }).when(mThemeWallpaperManager).addOnColorsChangedListener(any(), any());

        when(mUserManagerInternal.getProfileParentId(anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mEnvironment = new ThemeEnvironment(mContext, mUserManagerInternal, (key, def) -> def);

        mThemeEventObserver = new ThemeEventObserver(mContext, mThemeStateManager,
                mThemeSettingsManager, mThemeUserLifecycle, mThemeManagerImpl, mEnvironment);
        mThemeEventObserver.onServicesReady(mThemeWallpaperManager,
                mUiModeManagerInternal, mKeyguardManager);
        mThemeEventObserver.registerListeners();

        // Ground truth behavior: assume user is parent (not profile) and can be loaded
        when(mThemeStateManager.parentOf(USER_ID)).thenReturn(null);
        when(mThemeUserLifecycle.loadUserStateAndNotifyStateManager(USER_ID)).thenReturn(true);
    }

    @After
    public void tearDown() {
        LocalServices.removeServiceForTest(UiModeManagerInternal.class);
    }

    @Test
    public void testOverlayChanged_androidPackage_notifiesThemeChanged() {
        Intent intent = new Intent(Intent.ACTION_OVERLAY_CHANGED);
        intent.setData(Uri.parse("package:android"));
        intent.putExtra(Intent.EXTRA_USER_ID, USER_ID);

        mThemeEventObserver.mOverlayReceiver.onReceive(mContext, intent);

        verify(mThemeManagerImpl).notifyThemeChanged(USER_ID);
    }

    @Test
    public void testOverlayChanged_otherPackage_ignored() {
        Intent intent = new Intent(Intent.ACTION_OVERLAY_CHANGED);
        intent.setData(Uri.parse("package:com.other.package"));
        intent.putExtra(Intent.EXTRA_USER_ID, USER_ID);

        mThemeEventObserver.mOverlayReceiver.onReceive(mContext, intent);

        verify(mThemeManagerImpl, never()).notifyThemeChanged(anyInt());
    }

    @Test
    public void testWallpaperColorsChanged_callsStateManager_ifNotPreset() {
        ThemeSettings settings = new ThemeSettings.Builder()
                .setColorSource(VALUE_HOME_WALLPAPER)
                .setThemeStyle(ThemeStyle.TONAL_SPOT)
                .setSystemPalette(Color.valueOf(Color.BLUE))
                .build();
        when(mThemeSettingsManager.getSettingsOrDefault(eq(USER_ID), any())).thenReturn(settings);

        WallpaperColors colors = new WallpaperColors(Color.valueOf(Color.RED), null, null);
        mWallpaperListener.onColorsChanged(colors, 0, 0, USER_ID, true);

        verify(mThemeStateManager).onSeedColorChange(eq(USER_ID), anyInt(), eq(true));
    }

    @Test
    public void testWallpaperColorsChanged_ignored_ifPreset() {
        ThemeSettings settings = new ThemeSettings.Builder()
                .setColorSource(VALUE_PRESET)
                .setThemeStyle(ThemeStyle.TONAL_SPOT)
                .setSystemPalette(Color.valueOf(Color.BLUE))
                .build();
        when(mThemeSettingsManager.getSettingsOrDefault(eq(USER_ID), any())).thenReturn(settings);

        WallpaperColors colors = new WallpaperColors(Color.valueOf(Color.RED), null, null);
        mWallpaperListener.onColorsChanged(colors, 0, 0, USER_ID, true);

        verify(mThemeStateManager, never()).onSeedColorChange(anyInt(), anyInt(), anyBoolean());
    }

    @Test
    public void testContrastChanged_callsStateManager() {
        verify(mUiModeManagerInternal).addContrastListener(mContrastListenerCaptor.capture(),
                any(Executor.class));

        mContrastListenerCaptor.getValue().onContrastChange(USER_ID, 0.5f);

        verify(mThemeStateManager).onContrastChange(USER_ID, 0.5f);
    }

    @Test
    public void testKeyguardLockedStateChanged_callsStateManager() {
        verify(mKeyguardManager).addKeyguardLockedStateListener(any(Executor.class),
                mKeyguardListenerCaptor.capture());

        mKeyguardListenerCaptor.getValue().onKeyguardLockedStateChanged(true);

        verify(mThemeStateManager).onLockStateChange(true);
    }

    @Test
    public void testUserSetupComplete_callsStateManager() {
        mThemeEventObserver.mUserSetupObserver.onChange(false, null, 0, USER_ID);

        verify(mThemeStateManager).onFinishSetup(USER_ID);
    }

    @Test
    public void testUserSetupComplete_lazyLoadsState_ifMissing() {
        // Mockito verify will check that loadUserStateAndNotifyStateManager was called
        mThemeEventObserver.mUserSetupObserver.onChange(false, null, 0, USER_ID);

        verify(mThemeUserLifecycle).loadUserStateAndNotifyStateManager(USER_ID);
        verify(mThemeStateManager).onFinishSetup(USER_ID);
    }

    @Test
    public void testUserSetupComplete_lazyLoadsState_fails_ignored() {
        // Override default stub for this specific test
        when(mThemeUserLifecycle.loadUserStateAndNotifyStateManager(USER_ID)).thenReturn(false);

        mThemeEventObserver.mUserSetupObserver.onChange(false, null, 0, USER_ID);

        verify(mThemeUserLifecycle).loadUserStateAndNotifyStateManager(USER_ID);
        verify(mThemeStateManager, never()).onFinishSetup(USER_ID);
    }

    @Test
    public void testThemeCustomizationChanged_styleChange() {
        ThemeSettings settings = new ThemeSettings.Builder()
                .setThemeStyle(ThemeStyle.EXPRESSIVE)
                .setColorSource(VALUE_HOME_WALLPAPER)
                .setSystemPalette(Color.valueOf(Color.BLUE))
                .build();
        when(mThemeSettingsManager.getSettingsOrDefault(eq(USER_ID), any())).thenReturn(settings);

        mThemeEventObserver.handleThemeCustomizationChanged(USER_ID);

        verify(mThemeSettingsManager).invalidateCache(USER_ID);
        verify(mThemeStateManager).onStyleChange(USER_ID, ThemeStyle.EXPRESSIVE);
    }

    @Test
    public void testThemeCustomizationChanged_presetColorChange() {
        ThemeSettings settings = new ThemeSettings.Builder()
                .setThemeStyle(ThemeStyle.TONAL_SPOT)
                .setColorSource(VALUE_PRESET)
                .setSystemPalette(Color.valueOf(Color.RED))
                .build();
        when(mThemeSettingsManager.getSettingsOrDefault(eq(USER_ID), any())).thenReturn(settings);

        mThemeEventObserver.handleThemeCustomizationChanged(USER_ID);

        verify(mThemeStateManager).onSeedColorChange(USER_ID, Color.RED, true);
    }

    @Test
    public void testThemeCustomizationChanged_forcesReloadSettings() {
        ThemeSettings settings = new ThemeSettings.Builder()
                .setThemeStyle(ThemeStyle.TONAL_SPOT)
                .setColorSource(VALUE_PRESET)
                .setSystemPalette(Color.valueOf(Color.BLUE))
                .build();
        when(mThemeSettingsManager.getSettingsOrDefault(eq(USER_ID), any())).thenReturn(settings);

        mThemeEventObserver.handleThemeCustomizationChanged(USER_ID);

        verify(mThemeSettingsManager).invalidateCache(USER_ID);
    }
}
