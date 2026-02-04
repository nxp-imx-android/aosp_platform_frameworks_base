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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.SuppressLint;
import android.content.pm.UserInfo;
import android.content.theming.ThemeStyle;
import android.os.UserHandle;
import android.testing.TestableContext;
import android.testing.TestableResources;

import androidx.test.runner.AndroidJUnit4;

import com.android.internal.R;
import com.android.server.LocalServices;
import com.android.server.SystemService;
import com.android.server.UiModeManagerInternal;
import com.android.server.om.OverlayManagerInternal;
import com.android.server.pm.UserManagerInternal;
import com.android.server.wallpaper.WallpaperManagerInternal;

import com.google.common.collect.ImmutableMap;
import com.google.ux.material.libmonet.dynamiccolor.ColorSpec.SpecVersion;
import com.google.ux.material.libmonet.dynamiccolor.DynamicScheme.Platform;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.concurrent.Executor;

@RunWith(AndroidJUnit4.class)
public class ThemeManagerServiceTests {

    @Rule
    public final HardwareColorRule mHardwareColorRule = new HardwareColorRule();

    private static final int DEFAULT_USER_ID = 11;
    private static final int DEFAULT_SEED_COLOR = 0xFFFF0000; // RED
    private static final float DEFAULT_CONTRAST = 0.0f;
    private static final int DEFAULT_STYLE = ThemeStyle.TONAL_SPOT;
    private static final int PROFILE_ID = 10;

    @Mock
    private UserManagerInternal mUserManager;
    @Mock
    private OverlayManagerInternal mOverlayManager;

    @Mock
    private WallpaperManagerInternal mWallpaperManager;
    @Mock
    private UiModeManagerInternal mUiModeManagerInternal;
    @Mock
    private Executor mMainExecutor;

    @Mock
    private ThemeUserLifecycle mThemeUserLifecycle;
    @Mock
    private ThemeEventObserver mThemeEventObserver;

    @Rule
    public final TestableContext mMainContext = spy(
            new TestableContext(getInstrumentation().getContext(), null));
    @Rule
    public final TestableContext mUserContext = new TestableContext(
            getInstrumentation().getContext(), null);

    private ThemeManagerService mThemeManagerService;
    ThemeStateManager mThemeStateManager;

    private final HashMap<Integer, Object> mUserResourceOverrides = new HashMap<>(
            new ImmutableMap.Builder<Integer, Object>()
                    .put(R.color.system_accent1_500_light, 0xFF6476A5)
                    .put(R.color.system_accent2_500_light, 0xFF70778B)
                    .put(R.color.system_accent3_500_light, 0xFF836E99)
                    .put(R.color.system_neutral1_500_light, 0xFF76777C)
                    .put(R.color.system_neutral2_500_light, 0xFF757780)
                    .put(R.color.system_accent1_500_dark, 0xFF69769B)
                    .put(R.color.system_accent2_500_dark, 0xFF70778B)
                    .put(R.color.system_accent3_500_dark, 0xFF836E99)
                    .put(R.color.system_neutral1_500_dark, 0xFF76777C)
                    .put(R.color.system_neutral2_500_dark, 0xFF757780)
                    .put(R.color.system_outline_variant_dark, 0xFF454850)
                    .put(R.color.system_outline_variant_light, 0xFFB0B1BC)
                    .put(R.color.system_primary_container_dark, 0xFF445274)
                    .put(R.color.system_primary_container_light, 0xFFB9CBFF)
                    .build());


    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        LocalServices.removeServiceForTest(OverlayManagerInternal.class);
        LocalServices.removeServiceForTest(UserManagerInternal.class);
        LocalServices.removeServiceForTest(WallpaperManagerInternal.class);
        LocalServices.removeServiceForTest(UiModeManagerInternal.class);

        LocalServices.addService(OverlayManagerInternal.class, mOverlayManager);
        LocalServices.addService(UserManagerInternal.class, mUserManager);
        LocalServices.addService(WallpaperManagerInternal.class, mWallpaperManager);
        LocalServices.addService(UiModeManagerInternal.class, mUiModeManagerInternal);

        // create main testable resources and apply overrides
        TestableResources mainResources = mMainContext.getOrCreateTestableResources();
        mainResources.addOverride(R.array.theming_defaults, mHardwareColorRule.options);
        // We're just providing an empty array for this test to avoid ResourceNotFoundException
        mainResources.addOverride(R.array.theming_legacy_overlays, new String[0]);

        // create user testable resources and apply overrides
        TestableResources userResources = mUserContext.getOrCreateTestableResources();
        mUserResourceOverrides.forEach(userResources::addOverride);
        userResources.addOverride(R.array.theming_defaults, mHardwareColorRule.options);

        doReturn(mUserContext).when(mMainContext).createContextAsUser(any(UserHandle.class),
                anyInt());
        doReturn(mMainExecutor).when(mMainContext).getMainExecutor();

        when(mUserManager.getProfileParentId(eq(DEFAULT_USER_ID))).thenReturn(DEFAULT_USER_ID);
        when(mUserManager.getProfileParentId(eq(PROFILE_ID))).thenReturn(DEFAULT_USER_ID);
        when(mUserManager.getUserIds()).thenReturn(new int[]{DEFAULT_USER_ID});

        FakeScheduledExecutorService schedulerExecutor = new FakeScheduledExecutorService();
        mThemeStateManager = new ThemeStateManager(mMainContext, schedulerExecutor,
                Platform.PHONE, SpecVersion.SPEC_2025);
        mThemeStateManager.onServicesReady();
    }

    @Test
    @HardwareColors(color = "", options = {"*|TONAL_SPOT|#00FF00"})
    @SuppressLint("MissingPermission")
    public void test_onBootPhase_complete_colorSchemeNotApplied_shouldForceUpdate() {
        mThemeManagerService = testableServiceStart();

        // creates user with seed color red, not the same as the default google_blue
        ThemeStatePair pair = startProvisionedUser();

        // Simulate boot phases
        mThemeManagerService.onBootPhase(SystemService.PHASE_SYSTEM_SERVICES_READY);
        mThemeManagerService.onBootPhase(SystemService.PHASE_ACTIVITY_MANAGER_READY);

        assertThat(pair.getPendingState()).isNotNull(); // there is an update
        assertThat(pair.getPendingState().timeStamp()).isNotEqualTo(
                pair.getCurrentState().timeStamp());

        verify(mThemeUserLifecycle).onServicesReady(any(), any(), any());
        verify(mThemeEventObserver).onServicesReady(any(), any(), any());
        verify(mThemeEventObserver).registerListeners();
    }

    @Test
    public void test_onUserStarting_delegatesToLifecycle() {
        mThemeManagerService = testableServiceStart();
        SystemService.TargetUser user = new SystemService.TargetUser(new UserInfo(10, "test", 0));

        mThemeManagerService.onUserStarting(user);

        verify(mThemeUserLifecycle).onUserStarting(user);
    }

    private ThemeStatePair startProvisionedUser() {
        mThemeStateManager.onUserStart(UserHandle.of(DEFAULT_USER_ID), true, DEFAULT_SEED_COLOR,
                DEFAULT_CONTRAST, DEFAULT_STYLE);
        return mThemeStateManager.getState(DEFAULT_USER_ID);
    }

    private ThemeManagerService testableServiceStart() {
        // The context used here should match the one used for resource overrides.
        return new ThemeManagerService(mMainContext, mHardwareColorRule.sysPropReader,
                mThemeStateManager, mWallpaperManager, mOverlayManager, mThemeUserLifecycle,
                mThemeEventObserver, Platform.PHONE, SpecVersion.SPEC_2025);
    }
}
