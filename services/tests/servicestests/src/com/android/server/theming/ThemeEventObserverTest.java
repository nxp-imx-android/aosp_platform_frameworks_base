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

import android.app.ActivityManagerInternal;
import android.app.KeyguardManager;
import android.app.WallpaperColors;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.theming.ThemeSettings;
import android.content.theming.ThemeStyle;
import android.graphics.Color;
import android.net.Uri;
import android.os.UserHandle;
import android.provider.Settings;
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
    private static final int PROFILE_ID = 11;

    @Rule
    public final TestableContext mContext = new TestableContext(
            InstrumentationRegistry.getInstrumentation().getContext(), null);

    @Mock
    private ThemeStateManager mThemeStateManager;
    @Mock
    private ThemeManagerInternal mThemeManagerInternal;
    @Mock
    private ThemeWallpaperManager mThemeWallpaperManager;
    @Mock
    private ThemeUserLifecycle mThemeUserLifecycle;
    @Mock
    private UserManagerInternal mUserManagerInternal;
    @Mock
    private ActivityManagerInternal mActivityManagerInternal;
    @Mock
    private UiModeManagerInternal mUiModeManagerInternal;
    @Mock
    private KeyguardManager mKeyguardManager;

    @Captor
    private ArgumentCaptor<UiModeManagerInternal.ContrastListenerInternal> mContrastListenerCaptor;
    @Captor
    private ArgumentCaptor<KeyguardManager.KeyguardLockedStateListener> mKeyguardListenerCaptor;

    private ThemeEventObserver mThemeEventObserver;
    private ContentResolver mContentResolver;
    private WallpaperManagerInternal.ColorsChangedCallbackInternal mWallpaperListener;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContentResolver = mContext.getContentResolver();

        LocalServices.removeServiceForTest(UserManagerInternal.class);
        LocalServices.removeServiceForTest(ActivityManagerInternal.class);
        LocalServices.removeServiceForTest(UiModeManagerInternal.class);

        LocalServices.addService(UserManagerInternal.class, mUserManagerInternal);
        LocalServices.addService(ActivityManagerInternal.class, mActivityManagerInternal);
        LocalServices.addService(UiModeManagerInternal.class, mUiModeManagerInternal);

        mContext.addMockSystemService(Context.KEYGUARD_SERVICE, mKeyguardManager);

        when(mActivityManagerInternal.getCurrentUserId()).thenReturn(USER_ID);
        when(mThemeUserLifecycle.loadUserStateAndNotifyStateManager(anyInt())).thenReturn(true);

        org.mockito.Mockito.doAnswer(invocation -> {
            mWallpaperListener = invocation.getArgument(0);
            return null;
        }).when(mThemeWallpaperManager).addOnColorsChangedListener(any(), any());

        mThemeEventObserver = new ThemeEventObserver(mContext, mThemeStateManager,
                mThemeManagerInternal, mThemeUserLifecycle);
        mThemeEventObserver.onServicesReady(mThemeWallpaperManager, mActivityManagerInternal,
                mUiModeManagerInternal, mKeyguardManager);
        mThemeEventObserver.registerListeners();
    }

    @After
    public void tearDown() {
        LocalServices.removeServiceForTest(UserManagerInternal.class);
        LocalServices.removeServiceForTest(ActivityManagerInternal.class);
        LocalServices.removeServiceForTest(UiModeManagerInternal.class);
    }

    @Test
    public void testProfileAdded_notifiesStateManager() {
        Intent intent = new Intent(Intent.ACTION_PROFILE_ADDED);
        intent.putExtra(Intent.EXTRA_USER, UserHandle.of(PROFILE_ID));
        when(mThemeStateManager.parentOf(PROFILE_ID)).thenReturn(USER_ID);
        // Simulate state already exists for parent
        when(mThemeStateManager.hasState(USER_ID)).thenReturn(true);

        mThemeEventObserver.mBroadcastReceiver.onReceive(mContext, intent);

        verify(mThemeUserLifecycle).loadUserStateAndNotifyStateManager(PROFILE_ID);
        verify(mThemeStateManager).onProfileAdd(USER_ID, PROFILE_ID);
    }

    @Test
    public void testProfileAdded_lazyLoadsState_ifMissing() {
        Intent intent = new Intent(Intent.ACTION_PROFILE_ADDED);
        intent.putExtra(Intent.EXTRA_USER, UserHandle.of(PROFILE_ID));
        when(mThemeStateManager.parentOf(PROFILE_ID)).thenReturn(USER_ID);
        // Simulate state missing initially for parent
        when(mThemeStateManager.hasState(USER_ID)).thenReturn(false);
        // Simulate successful load
        when(mThemeUserLifecycle.loadUserStateAndNotifyStateManager(USER_ID)).thenReturn(true);

        mThemeEventObserver.mBroadcastReceiver.onReceive(mContext, intent);

        verify(mThemeUserLifecycle).loadUserStateAndNotifyStateManager(PROFILE_ID);
        // Verify lazy load happened for parent
        verify(mThemeUserLifecycle).loadUserStateAndNotifyStateManager(USER_ID);
        verify(mThemeStateManager).onProfileAdd(USER_ID, PROFILE_ID);
    }

    @Test
    public void testProfileAdded_hsumSystemUser_ignored() {
        when(mThemeStateManager.parentOf(PROFILE_ID)).thenReturn(UserHandle.USER_SYSTEM);
        // Simulate that loading the system user state fails in HSUM
        when(mThemeUserLifecycle.loadUserStateAndNotifyStateManager(
                UserHandle.USER_SYSTEM)).thenReturn(false);

        Intent intent = new Intent(Intent.ACTION_PROFILE_ADDED);
        intent.putExtra(Intent.EXTRA_USER, UserHandle.of(PROFILE_ID));

        mThemeEventObserver.mBroadcastReceiver.onReceive(mContext, intent);

        verify(mThemeUserLifecycle).loadUserStateAndNotifyStateManager(PROFILE_ID);
        // Should NOT call onProfileAdd for system user parent in HSUM
        verify(mThemeStateManager, never()).onProfileAdd(UserHandle.USER_SYSTEM, PROFILE_ID);
    }

    @Test
    public void testOverlayChanged_androidPackage_notifiesThemeChanged() {
        Intent intent = new Intent(Intent.ACTION_OVERLAY_CHANGED);
        intent.setData(Uri.parse("package:android"));
        intent.putExtra(Intent.EXTRA_USER_ID, USER_ID);

        mThemeEventObserver.mBroadcastReceiver.onReceive(mContext, intent);

        verify(mThemeManagerInternal).notifyThemeChanged(USER_ID);
    }

    @Test
    public void testOverlayChanged_otherPackage_ignored() {
        Intent intent = new Intent(Intent.ACTION_OVERLAY_CHANGED);
        intent.setData(Uri.parse("package:com.other.package"));
        intent.putExtra(Intent.EXTRA_USER_ID, USER_ID);

        mThemeEventObserver.mBroadcastReceiver.onReceive(mContext, intent);

        verify(mThemeManagerInternal, never()).notifyThemeChanged(anyInt());
    }

    @Test
    public void testWallpaperColorsChanged_callsStateManager_ifNotPreset() {
        ThemeSettings settings = new ThemeSettings.Builder().setColorSource(
                VALUE_HOME_WALLPAPER).setSystemPalette(Color.valueOf(Color.BLUE)).setThemeStyle(
                ThemeStyle.TONAL_SPOT).build();
        when(mThemeManagerInternal.getThemeSettingsOrDefault(USER_ID)).thenReturn(settings);
        // Simulate state already exists
        when(mThemeStateManager.hasState(USER_ID)).thenReturn(true);

        WallpaperColors colors = new WallpaperColors(Color.valueOf(Color.RED), null, null);
        mWallpaperListener.onColorsChanged(colors, 0, 0, USER_ID, true);

        // Should use current user ID from ActivityManagerInternal
        verify(mThemeStateManager).onSeedColorChange(eq(USER_ID), anyInt(), eq(true));
    }

    @Test
    public void testWallpaperColorsChanged_ignored_ifPreset() {
        ThemeSettings settings = new ThemeSettings.Builder().setColorSource(
                VALUE_PRESET).setSystemPalette(Color.valueOf(Color.BLUE)).setThemeStyle(
                ThemeStyle.TONAL_SPOT).build();
        when(mThemeManagerInternal.getThemeSettingsOrDefault(USER_ID)).thenReturn(settings);
        // Simulate state already exists
        when(mThemeStateManager.hasState(USER_ID)).thenReturn(true);

        WallpaperColors colors = new WallpaperColors(Color.valueOf(Color.RED), null, null);
        mWallpaperListener.onColorsChanged(colors, 0, 0, USER_ID, true);

        verify(mThemeStateManager, never()).onSeedColorChange(anyInt(), anyInt(), anyBoolean());
    }

    @Test
    public void testContrastChanged_callsStateManager() {
        verify(mUiModeManagerInternal).addContrastListener(mContrastListenerCaptor.capture(),
                any(Executor.class));
        // Simulate state already exists
        when(mThemeStateManager.hasState(USER_ID)).thenReturn(true);

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
        // Trigger ContentObserver
        Settings.Secure.putIntForUser(mContentResolver, Settings.Secure.USER_SETUP_COMPLETE, 1,
                USER_ID);
        // Simulate state already exists
        when(mThemeStateManager.hasState(USER_ID)).thenReturn(true);

        mThemeEventObserver.mUserSetupObserver.onChange(false, null, 0, USER_ID);

        verify(mThemeStateManager).onFinishSetup(USER_ID);
    }

    @Test
    public void testUserSetupComplete_lazyLoadsState_ifMissing() {
        // Simulate state missing initially
        when(mThemeStateManager.hasState(USER_ID)).thenReturn(false);
        // Simulate successful load
        when(mThemeUserLifecycle.loadUserStateAndNotifyStateManager(USER_ID)).thenReturn(true);

        // Trigger ContentObserver
        Settings.Secure.putIntForUser(mContentResolver, Settings.Secure.USER_SETUP_COMPLETE, 1,
                USER_ID);
        mThemeEventObserver.mUserSetupObserver.onChange(false, null, 0, USER_ID);

        // Verify lazy load happened BEFORE onFinishSetup
        verify(mThemeUserLifecycle).loadUserStateAndNotifyStateManager(USER_ID);
        verify(mThemeStateManager).onFinishSetup(USER_ID);
    }

    @Test
    public void testUserSetupComplete_lazyLoadsState_fails_ignored() {
        // Simulate state missing initially
        when(mThemeStateManager.hasState(USER_ID)).thenReturn(false);
        // Simulate failed load (e.g. profile user)
        when(mThemeUserLifecycle.loadUserStateAndNotifyStateManager(USER_ID)).thenReturn(false);

        // Trigger ContentObserver
        Settings.Secure.putIntForUser(mContentResolver, Settings.Secure.USER_SETUP_COMPLETE, 1,
                USER_ID);
        mThemeEventObserver.mUserSetupObserver.onChange(false, null, 0, USER_ID);

        // Verify lazy load attempted
        verify(mThemeUserLifecycle).loadUserStateAndNotifyStateManager(USER_ID);
        // Verify onFinishSetup was NOT called to avoid crash
        verify(mThemeStateManager, never()).onFinishSetup(USER_ID);
    }

    @Test
    public void testThemeCustomizationChanged_styleChange() {
        ThemeSettings settings = new ThemeSettings.Builder().setColorSource(
                VALUE_HOME_WALLPAPER).setSystemPalette(Color.valueOf(Color.BLUE)).setThemeStyle(
                ThemeStyle.EXPRESSIVE).build();
        when(mThemeManagerInternal.getThemeSettingsOrDefault(USER_ID)).thenReturn(settings);
        // Simulate state already exists
        when(mThemeStateManager.hasState(USER_ID)).thenReturn(true);

        // Trigger ContentObserver
        Settings.Secure.putStringForUser(mContentResolver,
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, "{}", USER_ID);
        mThemeEventObserver.mThemeSettingsObserver.onChange(false, null, 0, USER_ID);

        verify(mThemeStateManager).onStyleChange(USER_ID, ThemeStyle.EXPRESSIVE);
        verify(mThemeStateManager).onSeedColorChange(USER_ID, Color.BLUE, true);
    }

    @Test
    public void testThemeCustomizationChanged_presetColorChange() {
        ThemeSettings settings = new ThemeSettings.Builder().setColorSource(
                VALUE_PRESET).setSystemPalette(Color.valueOf(Color.RED)).setThemeStyle(
                ThemeStyle.TONAL_SPOT).build();
        when(mThemeManagerInternal.getThemeSettingsOrDefault(USER_ID)).thenReturn(settings);
        // Simulate state already exists
        when(mThemeStateManager.hasState(USER_ID)).thenReturn(true);

        // Trigger ContentObserver
        Settings.Secure.putStringForUser(mContentResolver,
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, "{}", USER_ID);
        mThemeEventObserver.mThemeSettingsObserver.onChange(false, null, 0, USER_ID);

        verify(mThemeStateManager).onSeedColorChange(USER_ID, Color.RED, true);
        verify(mThemeStateManager).onStyleChange(USER_ID, ThemeStyle.TONAL_SPOT);
    }

    @Test
    public void testThemeCustomizationChanged_forcesReloadSettings() {
        ThemeSettings settings = new ThemeSettings.Builder().setColorSource(
                VALUE_PRESET).setSystemPalette(Color.valueOf(Color.RED)).setThemeStyle(
                ThemeStyle.TONAL_SPOT).build();
        when(mThemeManagerInternal.getThemeSettingsOrDefault(USER_ID)).thenReturn(settings);
        when(mThemeStateManager.hasState(USER_ID)).thenReturn(true);

        // Trigger ContentObserver
        Settings.Secure.putStringForUser(mContentResolver,
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, "{}", USER_ID);
        mThemeEventObserver.mThemeSettingsObserver.onChange(false, null, 0, USER_ID);

        verify(mThemeManagerInternal).forceReloadSettings(USER_ID);
    }
}