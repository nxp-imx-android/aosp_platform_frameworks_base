/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.settingslib.dream;


import static android.service.dreams.Flags.FLAG_ALLOW_DREAM_WHEN_POSTURED;
import static android.service.dreams.Flags.FLAG_USER_SELECTABLE_METADATA;

import static com.android.settingslib.dream.DreamBackend.COMPLICATION_TYPE_DATE;
import static com.android.settingslib.dream.DreamBackend.COMPLICATION_TYPE_HOME_CONTROLS;
import static com.android.settingslib.dream.DreamBackend.COMPLICATION_TYPE_TIME;
import static com.android.settingslib.dream.DreamBackend.WHILE_CHARGING;
import static com.android.settingslib.dream.DreamBackend.WHILE_CHARGING_OR_DOCKED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;

import android.platform.test.flag.junit.SetFlagsRule;
import com.android.internal.R;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.UserHandle;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.provider.Settings;
import android.service.dreams.DreamService;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowSettings;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RunWith(RobolectricTestRunner.class)
@Config(shadows = {ShadowSettings.ShadowSecure.class})
public final class DreamBackendTest {
    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    private static final String SELECTABLE_DREAM_LABEL = "Selectable Dream";
    private static final String NON_SELECTABLE_DREAM_LABEL = "Non Selectable Dream";

    private static final int[] SUPPORTED_DREAM_COMPLICATIONS =
            {COMPLICATION_TYPE_HOME_CONTROLS, COMPLICATION_TYPE_DATE,
                    COMPLICATION_TYPE_TIME};
    private static final List<Integer> SUPPORTED_DREAM_COMPLICATIONS_LIST = Arrays.stream(
            SUPPORTED_DREAM_COMPLICATIONS).boxed().collect(
            Collectors.toList());

    @Mock
    private Context mContext;
    @Mock
    private ContentResolver mMockResolver;
    @Mock
    private PackageManager mPackageManager;
    private DreamBackend mBackend;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mContext.getApplicationContext()).thenReturn(mContext);
        when(mContext.getContentResolver()).thenReturn(mMockResolver);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);

        final Resources res = mock(Resources.class);
        when(mContext.getResources()).thenReturn(res);
        when(res.getIntArray(
                com.android.internal.R.array.config_supportedDreamComplications)).thenReturn(
                SUPPORTED_DREAM_COMPLICATIONS);
        when(res.getStringArray(
                com.android.internal.R.array.config_disabledDreamComponents)).thenReturn(
                new String[]{});
        when(res.getStringArray(
                com.android.internal.R.array.config_loggable_dream_prefixes)).thenReturn(
                new String[]{});
        when(res.getBoolean(com.android.internal.R.bool.config_dreamsActivatedOnSleepByDefault
                )).thenReturn(true);
        when(res.getBoolean(com.android.internal.R.bool.config_dreamsActivatedOnDockByDefault
                )).thenReturn(false);
        when(res.getBoolean(com.android.internal.R.bool.config_dreamsActivatedOnPosturedByDefault
                )).thenReturn(false);

        mBackend = new DreamBackend(mContext);
    }

    @After
    public void tearDown() {
        ShadowSettings.ShadowSecure.reset();
    }

    @Test
    public void testComplicationsEnabledByDefault() {
        setControlsEnabledOnLockscreen(true);
        assertThat(mBackend.getComplicationsEnabled(UserHandle.CURRENT)).isTrue();
        assertThat(mBackend.getEnabledComplications(UserHandle.CURRENT)).containsExactlyElementsIn(
                SUPPORTED_DREAM_COMPLICATIONS_LIST);
    }

    @Test
    public void testEnableComplicationExplicitly() {
        setControlsEnabledOnLockscreen(true);
        mBackend.setComplicationsEnabled(true);
        assertThat(mBackend.getEnabledComplications(UserHandle.CURRENT)).containsExactlyElementsIn(
                SUPPORTED_DREAM_COMPLICATIONS_LIST);
        assertThat(mBackend.getComplicationsEnabled(UserHandle.CURRENT)).isTrue();
    }

    @Test
    public void testDisableComplications() {
        setControlsEnabledOnLockscreen(true);
        mBackend.setComplicationsEnabled(false);
        assertThat(mBackend.getEnabledComplications(UserHandle.CURRENT))
                .containsExactly(COMPLICATION_TYPE_HOME_CONTROLS);
        assertThat(mBackend.getComplicationsEnabled(UserHandle.CURRENT)).isFalse();
    }

    @Test
    public void testResolveMissingWhenToStartOption() {
        mBackend.setWhenToDream(WHILE_CHARGING_OR_DOCKED);
        mBackend.setEnabled(true);
        assertThat(mBackend.getDefaultWhenToDreamSetting()).isEqualTo(WHILE_CHARGING);
        mBackend.resolveMissingWhenToDream(new int[]{WHILE_CHARGING});
        assertThat(mBackend.getWhenToDreamSetting()).isEqualTo(WHILE_CHARGING);
        assertThat(mBackend.isEnabled()).isFalse();
    }

    @Test
    public void testResolveMissingWhenToStartOptionWhenCompliant() {
        mBackend.setWhenToDream(WHILE_CHARGING_OR_DOCKED);
        mBackend.setEnabled(true);
        mBackend.resolveMissingWhenToDream(new int[]{WHILE_CHARGING, WHILE_CHARGING_OR_DOCKED});
        assertThat(mBackend.getWhenToDreamSetting()).isEqualTo(WHILE_CHARGING_OR_DOCKED);
        assertThat(mBackend.isEnabled()).isTrue();
    }

    @Test
    public void testHomeControlsDisabled_ComplicationsEnabled() {
        setControlsEnabledOnLockscreen(true);
        mBackend.setComplicationsEnabled(true);
        mBackend.setHomeControlsEnabled(false);
        // Home controls should not be enabled, only date and time.
        final List<Integer> enabledComplications =
                Arrays.asList(COMPLICATION_TYPE_DATE, COMPLICATION_TYPE_TIME);
        assertThat(mBackend.getEnabledComplications(UserHandle.CURRENT))
                .containsExactlyElementsIn(enabledComplications);
    }

    @Test
    public void testHomeControlsDisabled_ComplicationsDisabled() {
        setControlsEnabledOnLockscreen(true);
        mBackend.setComplicationsEnabled(false);
        mBackend.setHomeControlsEnabled(false);
        assertThat(mBackend.getEnabledComplications(UserHandle.CURRENT)).isEmpty();
    }

    @Test
    public void testHomeControlsEnabled_ComplicationsDisabled() {
        setControlsEnabledOnLockscreen(true);
        mBackend.setComplicationsEnabled(false);
        mBackend.setHomeControlsEnabled(true);
        final List<Integer> enabledComplications =
                Collections.singletonList(COMPLICATION_TYPE_HOME_CONTROLS);
        assertThat(mBackend.getEnabledComplications(UserHandle.CURRENT))
                .containsExactlyElementsIn(enabledComplications);
    }

    @Test
    public void testHomeControlsEnabled_ComplicationsEnabled() {
        setControlsEnabledOnLockscreen(true);
        mBackend.setComplicationsEnabled(true);
        mBackend.setHomeControlsEnabled(true);
        final List<Integer> enabledComplications =
                Arrays.asList(
                        COMPLICATION_TYPE_HOME_CONTROLS,
                        COMPLICATION_TYPE_DATE,
                        COMPLICATION_TYPE_TIME
                );
        assertThat(mBackend.getEnabledComplications(UserHandle.CURRENT))
                .containsExactlyElementsIn(enabledComplications);
    }

    @Test
    public void testHomeControlsEnabled_lockscreenDisabled() {
        setControlsEnabledOnLockscreen(false);
        mBackend.setComplicationsEnabled(true);
        mBackend.setHomeControlsEnabled(true);
        // Home controls should not be enabled, only date and time.
        final List<Integer> enabledComplications =
                Arrays.asList(
                        COMPLICATION_TYPE_DATE,
                        COMPLICATION_TYPE_TIME
                );
        assertThat(mBackend.getEnabledComplications(UserHandle.CURRENT))
                .containsExactlyElementsIn(enabledComplications);
    }

    @Test
    @EnableFlags(FLAG_ALLOW_DREAM_WHEN_POSTURED)
    public void testChargingAndPosturedBothEnabled() {
        Settings.Secure.putInt(
                mContext.getContentResolver(),
                Settings.Secure.SCREENSAVER_ACTIVATE_ON_SLEEP,
                1
        );
        Settings.Secure.putInt(
                mContext.getContentResolver(),
                Settings.Secure.SCREENSAVER_ACTIVATE_ON_POSTURED,
                1
        );

        assertThat(mBackend.getWhenToDreamSetting()).isEqualTo(DreamBackend.WHILE_CHARGING);
    }

    @Test
    public void testChargingAndDockedBothEnabled() {
        Settings.Secure.putInt(
                mContext.getContentResolver(),
                Settings.Secure.SCREENSAVER_ACTIVATE_ON_SLEEP,
                1
        );
        Settings.Secure.putInt(
                mContext.getContentResolver(),
                Settings.Secure.SCREENSAVER_ACTIVATE_ON_DOCK,
                1
        );

        assertThat(mBackend.getWhenToDreamSetting()).isEqualTo(
                DreamBackend.WHILE_CHARGING_OR_DOCKED);
    }

    @Test
    @EnableFlags(FLAG_ALLOW_DREAM_WHEN_POSTURED)
    public void testPosturedAndDockedBothEnabled() {
        Settings.Secure.putInt(
                mContext.getContentResolver(),
                Settings.Secure.SCREENSAVER_ACTIVATE_ON_POSTURED,
                1
        );
        Settings.Secure.putInt(
                mContext.getContentResolver(),
                Settings.Secure.SCREENSAVER_ACTIVATE_ON_SLEEP,
                0
        );
        Settings.Secure.putInt(
                mContext.getContentResolver(),
                Settings.Secure.SCREENSAVER_ACTIVATE_ON_DOCK,
                1
        );

        assertThat(mBackend.getWhenToDreamSetting()).isEqualTo(
                DreamBackend.WHILE_DOCKED);
    }

    @Test
    public void testSetRestrictedToWirelessCharging() {
        mBackend.setRestrictToWirelessCharging(true);
        assertThat(mBackend.getRestrictToWirelessCharging()).isTrue();
    }

    @Test
    public void testGetRestrictedToWirelessCharging() {
        Settings.Secure.putInt(
                mContext.getContentResolver(),
                Settings.Secure.SCREENSAVER_RESTRICT_TO_WIRELESS_CHARGING,
                1
        );

        assertThat(mBackend.getRestrictToWirelessCharging()).isTrue();
    }

    @Test
    public void testLowLightDisplayBehavior() {
        mBackend.setLowLightDisplayBehavior(100);
        assertThat(mBackend.getLowLightDisplayBehavior()).isEqualTo(100);
    }


    @Test
    public void testLowLightDisplayBehaviorEnabled() {
        mBackend.setLowLightDisplayBehaviorEnabled(false);
        assertThat(mBackend.getLowLightDisplayBehaviorEnabled()).isFalse();

        mBackend.setLowLightDisplayBehaviorEnabled(true);
        assertThat(mBackend.getLowLightDisplayBehaviorEnabled()).isTrue();
    }

    @Test
    @EnableFlags(FLAG_USER_SELECTABLE_METADATA)
    public void testGetDreamInfos_filtersNonUserSelectable_whenFlagEnabled() {
        // arrange
        setupAllDreamInfos();

        // act
        final List<DreamBackend.DreamInfo> dreamInfos = mBackend.getDreamInfos();

        // assert
        assertThat(dreamInfos).hasSize(1);
        assertThat(dreamInfos.get(0).caption.toString()).isEqualTo(SELECTABLE_DREAM_LABEL);
    }

    @Test
    @DisableFlags(FLAG_USER_SELECTABLE_METADATA)
    public void testGetDreamInfos_doesNotFilterNonUserSelectable_whenFlagDisabled() {
        // arrange
        setupAllDreamInfos();

        // act
        final List<DreamBackend.DreamInfo> dreamInfos = mBackend.getDreamInfos();

        // assert
        assertThat(dreamInfos).hasSize(2);
    }

    private void setControlsEnabledOnLockscreen(boolean enabled) {
        Settings.Secure.putIntForUser(
                mContext.getContentResolver(),
                Settings.Secure.LOCKSCREEN_SHOW_CONTROLS,
                enabled ? 1 : 0, UserHandle.USER_CURRENT);
    }

    private void setupAllDreamInfos() {
        final ResolveInfo selectableDream = createDreamInfo(SELECTABLE_DREAM_LABEL);
        final ResolveInfo nonSelectableDream = createDreamInfo(NON_SELECTABLE_DREAM_LABEL);

        when(mPackageManager.queryIntentServices(any(Intent.class), anyInt())).thenReturn(
                Arrays.asList(selectableDream, nonSelectableDream));

        mockDreamMetadata(selectableDream, /* userSelectable= */ true);
        mockDreamMetadata(nonSelectableDream, /* userSelectable= */ false);
    }

    private void mockDreamMetadata(ResolveInfo resolveInfo, boolean userSelectable) {
        final TypedArray metadata = mock(TypedArray.class);
        when(mPackageManager.extractPackageItemInfoAttributes(eq(resolveInfo.serviceInfo),
                any(), any(), any())).thenReturn(metadata);
        when(metadata.getBoolean(
                R.styleable.Dream_userSelectable,
                DreamService.DEFAULT_USER_SELECTABLE)).thenReturn(userSelectable);
    }

    private ResolveInfo createDreamInfo(String label) {
        final ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.serviceInfo = new ServiceInfo();
        resolveInfo.serviceInfo.packageName = "package";
        resolveInfo.serviceInfo.name = "name";

        resolveInfo.nonLocalizedLabel = label;
        return resolveInfo;
    }
}
