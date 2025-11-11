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

import static android.app.WallpaperManager.FLAG_SYSTEM;
import static android.content.theming.FieldColorSource.VALUE_HOME_WALLPAPER;
import static android.content.theming.FieldColorSource.VALUE_PRESET;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManagerInternal;
import android.app.KeyguardManager;
import android.app.WallpaperColors;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.UserInfo;
import android.content.theming.ThemeSettings;
import android.content.theming.ThemeStyle;
import android.graphics.Color;
import android.os.UserHandle;
import android.provider.Settings;
import android.testing.TestableContext;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.LocalServices;
import com.android.server.SystemService;
import com.android.server.UiModeManagerInternal;
import com.android.server.om.OverlayManagerInternal;
import com.android.server.pm.UserManagerInternal;
import com.android.server.wallpaper.WallpaperManagerInternal;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class ThemeUserLifecycleTest {

    private static final int USER_ID_1 = 10;
    private static final int USER_ID_2 = 11;

    @Rule
    public final TestableContext mContext = new TestableContext(
            InstrumentationRegistry.getInstrumentation().getContext(), null);

    private ThemeStateManager mThemeStateManager;
    @Mock
    private ThemeManagerInternal mThemeManagerInternal;
    @Mock
    private WallpaperManagerInternal mWallpaperManagerInternal;
    @Mock
    private UserManagerInternal mUserManagerInternal;
    @Mock
    private UiModeManagerInternal mUiModeManagerInternal;
    @Mock
    private ActivityManagerInternal mActivityManagerInternal;
    @Mock
    private OverlayManagerInternal mOverlayManagerInternal;
    @Mock
    private KeyguardManager mKeyguardManager;

    private ThemeUserLifecycle mThemeUserLifecycle;
    private ContentResolver mContentResolver;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContentResolver = mContext.getContentResolver();

        LocalServices.removeServiceForTest(UserManagerInternal.class);
        LocalServices.removeServiceForTest(UiModeManagerInternal.class);
        LocalServices.removeServiceForTest(ActivityManagerInternal.class);
        LocalServices.removeServiceForTest(OverlayManagerInternal.class);

        LocalServices.addService(UserManagerInternal.class, mUserManagerInternal);
        LocalServices.addService(UiModeManagerInternal.class, mUiModeManagerInternal);
        LocalServices.addService(ActivityManagerInternal.class, mActivityManagerInternal);
        LocalServices.addService(OverlayManagerInternal.class, mOverlayManagerInternal);

        mContext.addMockSystemService(Context.KEYGUARD_SERVICE, mKeyguardManager);

        // Stub UserManager to behave like a standard user (parent of self) by default
        when(mUserManagerInternal.getProfileParentId(anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mThemeStateManager = new ThemeStateManager(mContext, new FakeScheduledExecutorService());
        mThemeStateManager.onServicesReady();

        mThemeUserLifecycle = new ThemeUserLifecycle(mContext, mThemeStateManager,
                mThemeManagerInternal);
        mThemeUserLifecycle.onServicesReady(mUserManagerInternal, mUiModeManagerInternal,
                new ThemeWallpaperManager(mWallpaperManagerInternal));
    }

    @After
    public void tearDown() {
        LocalServices.removeServiceForTest(UserManagerInternal.class);
        LocalServices.removeServiceForTest(UiModeManagerInternal.class);
        LocalServices.removeServiceForTest(ActivityManagerInternal.class);
        LocalServices.removeServiceForTest(OverlayManagerInternal.class);
    }

    @Test
    public void onUserStarting_callsStateManagerWithCorrectValues_preset() {
        // Setup
        ThemeSettings settings = new ThemeSettings.Builder().setColorSource(
                VALUE_PRESET).setSystemPalette(Color.valueOf(Color.RED)).setThemeStyle(
                ThemeStyle.EXPRESSIVE).build();
        when(mThemeManagerInternal.getThemeSettingsOrDefault(USER_ID_1)).thenReturn(settings);
        when(mUiModeManagerInternal.getContrast(USER_ID_1)).thenReturn(0.5f);
        Settings.Secure.putIntForUser(mContentResolver, Settings.Secure.USER_SETUP_COMPLETE, 1,
                USER_ID_1);

        SystemService.TargetUser targetUser = new SystemService.TargetUser(
                new UserInfo(USER_ID_1, "test", 0));

        // Action
        mThemeUserLifecycle.onUserStarting(targetUser);

        // Verify state
        ThemeState state = mThemeStateManager.getState(USER_ID_1).getCurrentState();
        assertThat(state.userId()).isEqualTo(USER_ID_1);
        assertThat(state.isSetup()).isTrue();
        assertThat(state.seedColor()).isEqualTo(Color.RED);
        assertThat(state.contrast()).isEqualTo(0.5f);
        assertThat(state.style()).isEqualTo(ThemeStyle.EXPRESSIVE);
    }

    @Test
    public void onUserStarting_callsStateManagerWithCorrectValues_wallpaper() {
        // Setup
        ThemeSettings settings = new ThemeSettings.Builder().setColorSource(
                        VALUE_HOME_WALLPAPER).setSystemPalette(
                        Color.valueOf(Color.BLUE)) // This color is used by the builder validation
                .setThemeStyle(ThemeStyle.TONAL_SPOT).build();
        when(mThemeManagerInternal.getThemeSettingsOrDefault(USER_ID_1)).thenReturn(settings);
        WallpaperColors wallpaperColors = new WallpaperColors(Color.valueOf(Color.GREEN), null,
                null);
        when(mWallpaperManagerInternal.getWallpaperColors(FLAG_SYSTEM, USER_ID_1)).thenReturn(
                wallpaperColors);
        when(mUiModeManagerInternal.getContrast(USER_ID_1)).thenReturn(0.0f);
        Settings.Secure.putIntForUser(mContentResolver, Settings.Secure.USER_SETUP_COMPLETE, 0,
                USER_ID_1);

        SystemService.TargetUser targetUser = new SystemService.TargetUser(
                new UserInfo(USER_ID_1, "test", 0));

        // Action
        mThemeUserLifecycle.onUserStarting(targetUser);

        // Verify state
        ThemeState state = mThemeStateManager.getState(USER_ID_1).getCurrentState();
        assertThat(state.userId()).isEqualTo(USER_ID_1);
        assertThat(state.isSetup()).isFalse();
        assertThat(state.seedColor()).isEqualTo(Color.GREEN);
        assertThat(state.contrast()).isEqualTo(0.0f);
        assertThat(state.style()).isEqualTo(ThemeStyle.TONAL_SPOT);
    }

    @Test(expected = IllegalStateException.class)
    public void onUserStarting_hsumSystemUser_ignored() {
        // Setup HSUM
        when(mUserManagerInternal.isHeadlessSystemUserMode()).thenReturn(true);
        SystemService.TargetUser systemUser = new SystemService.TargetUser(
                new UserInfo(UserHandle.USER_SYSTEM, "system", UserInfo.FLAG_MAIN));

        // Action
        mThemeUserLifecycle.onUserStarting(systemUser);

        // Verify state: If we try to access the state for USER_SYSTEM, it should throw
        // IllegalStateException because the user was never loaded (ignored).
        mThemeStateManager.getState(UserHandle.USER_SYSTEM);
    }

    @Test
    public void onUserSwitching_callsStateManager() {
        // Setup - pre-load users so state exists
        int fromUserId = 10;
        int toUserId = 11;

        ThemeSettings settings = new ThemeSettings.Builder().setColorSource(
                VALUE_PRESET).setSystemPalette(Color.valueOf(Color.RED)).setThemeStyle(
                ThemeStyle.EXPRESSIVE).build();
        when(mThemeManagerInternal.getThemeSettingsOrDefault(anyInt())).thenReturn(settings);

        mThemeUserLifecycle.onUserStarting(
                new SystemService.TargetUser(new UserInfo(fromUserId, "from", 0)));
        mThemeUserLifecycle.onUserStarting(
                new SystemService.TargetUser(new UserInfo(toUserId, "to", 0)));

        SystemService.TargetUser fromUser = new SystemService.TargetUser(
                new UserInfo(fromUserId, "from", 0));
        SystemService.TargetUser toUser = new SystemService.TargetUser(
                new UserInfo(toUserId, "to", 0));

        // Capture initial timestamp
        long initialTimestamp = mThemeStateManager.getState(toUserId).getCurrentState().timeStamp();

        // Action
        mThemeUserLifecycle.onUserSwitching(fromUser, toUser);

        // Verify - User switching forces an update, so timestamp should change in pending state
        ThemeState pendingState = mThemeStateManager.getState(toUserId).getPendingState();
        assertThat(pendingState).isNotNull();
        assertThat(pendingState.timeStamp()).isNotEqualTo(initialTimestamp);
    }

    @Test
    public void onUserSwitching_hsumSystemUser_ignored() {
        // Setup HSUM
        when(mUserManagerInternal.isHeadlessSystemUserMode()).thenReturn(true);
        SystemService.TargetUser fromUser = new SystemService.TargetUser(
                new UserInfo(10, "from", 0));
        SystemService.TargetUser toSystemUser = new SystemService.TargetUser(
                new UserInfo(UserHandle.USER_SYSTEM, "system", UserInfo.FLAG_MAIN));

        // Action
        // If onUserSwitching wasn't ignored, it would try to call onUserSwitching on StateManager,
        // which would throw exception because system user is not loaded.
        mThemeUserLifecycle.onUserSwitching(fromUser, toSystemUser);
    }

    @Test
    public void loadUserStateAndNotifyStateManager_callsStateManagerWithCorrectValues() {
        // Setup
        ThemeSettings settings = new ThemeSettings.Builder()
                .setColorSource(VALUE_PRESET)
                .setSystemPalette(Color.valueOf(Color.GREEN))
                .setThemeStyle(ThemeStyle.VIBRANT)
                .build();
        when(mThemeManagerInternal.getThemeSettingsOrDefault(USER_ID_1)).thenReturn(settings);
        when(mUiModeManagerInternal.getContrast(USER_ID_1)).thenReturn(0.2f);
        Settings.Secure.putIntForUser(mContentResolver, Settings.Secure.USER_SETUP_COMPLETE, 1,
                USER_ID_1);

        // Action
        mThemeUserLifecycle.loadUserStateAndNotifyStateManager(USER_ID_1);

        // Verify state
        ThemeState state = mThemeStateManager.getState(USER_ID_1).getCurrentState();
        assertThat(state.userId()).isEqualTo(USER_ID_1);
        assertThat(state.isSetup()).isTrue();
        assertThat(state.seedColor()).isEqualTo(Color.GREEN);
        assertThat(state.contrast()).isEqualTo(0.2f);
        assertThat(state.style()).isEqualTo(ThemeStyle.VIBRANT);
    }

    @Test
    public void onBootCompleteLoadUsers_loadsAllUsers() {
        // Setup
        when(mUserManagerInternal.getUserIds()).thenReturn(new int[]{USER_ID_1, USER_ID_2});
        ThemeSettings settings = new ThemeSettings.Builder()
                .setColorSource(VALUE_PRESET)
                .setSystemPalette(Color.valueOf(Color.GREEN))
                .setThemeStyle(ThemeStyle.VIBRANT)
                .build();
        when(mThemeManagerInternal.getThemeSettingsOrDefault(anyInt())).thenReturn(settings);

        // Action
        mThemeUserLifecycle.onBootCompleteLoadUsers();

        // Verify that states are loaded for both users
        assertThat(mThemeStateManager.getState(USER_ID_1)).isNotNull();
        assertThat(mThemeStateManager.getState(USER_ID_2)).isNotNull();

        // Also verify getThemeSettingsOrDefault was called
        verify(mThemeManagerInternal).getThemeSettingsOrDefault(USER_ID_1);
        verify(mThemeManagerInternal).getThemeSettingsOrDefault(USER_ID_2);
    }
}
