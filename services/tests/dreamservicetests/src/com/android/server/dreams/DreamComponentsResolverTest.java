/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.server.dreams;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.hardware.display.AmbientDisplayConfiguration;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.provider.Settings;
import android.service.dreams.DreamPlaylist;
import android.service.dreams.Flags;
import android.testing.TestableContext;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.pm.UserManagerInternal;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class DreamComponentsResolverTest {
    @Rule public final MockitoRule mMockitoRule = MockitoJUnit.rule();
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Rule
    public final TestableContext mContext =
            new TestableContext(
                    androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                            .getContext());

    @Mock private DreamValidator mDreamValidator;
    @Mock private AmbientDisplayConfiguration mDozeConfig;
    @Mock private UserManagerInternal mUserManagerInternal;

    private DreamComponentsResolver mResolver;

    private static final int USER_ID = 0;
    private static final ComponentName DREAM_COMPONENT =
            ComponentName.unflattenFromString("com.example/.Dream");
    private static final ComponentName DEFAULT_DREAM_COMPONENT =
            ComponentName.unflattenFromString("com.example/.DefaultDream");
    private static final ComponentName DOZE_COMPONENT =
            ComponentName.unflattenFromString("com.example/.Doze");

    @Before
    public void setUp() {
        when(mUserManagerInternal.getMainUserId()).thenReturn(USER_ID);
        mResolver =
                new DreamComponentsResolver(
                        mContext,
                        mDreamValidator,
                        mDozeConfig,
                        mUserManagerInternal,
                        /* dreamsOnlyEnabledForDockUser= */ false);
    }

    @Test
    public void resolve_doze_forceEnabled() {
        when(mDozeConfig.ambientDisplayComponent()).thenReturn(DOZE_COMPONENT.flattenToString());
        when(mDreamValidator.validate(DOZE_COMPONENT, USER_ID)).thenReturn(true);

        final ComponentName resolvedComponent =
                mResolver.resolve(
                        /* doze= */ true, USER_ID, /* forceAmbientDisplayEnabled= */ true, null);
        assertThat(resolvedComponent).isEqualTo(DOZE_COMPONENT);
    }

    @Test
    public void resolve_doze_configEnabled() {
        when(mDozeConfig.enabled(USER_ID)).thenReturn(true);
        when(mDozeConfig.ambientDisplayComponent()).thenReturn(DOZE_COMPONENT.flattenToString());
        when(mDreamValidator.validate(DOZE_COMPONENT, USER_ID)).thenReturn(true);

        final ComponentName resolvedComponent =
                mResolver.resolve(
                        /* doze= */ true, USER_ID, /* forceAmbientDisplayEnabled= */ false, null);
        assertThat(resolvedComponent).isEqualTo(DOZE_COMPONENT);
    }

    @Test
    public void resolve_doze_disabled() {
        when(mDozeConfig.enabled(USER_ID)).thenReturn(false);

        final ComponentName resolvedComponent =
                mResolver.resolve(
                        /* doze= */ true, USER_ID, /* forceAmbientDisplayEnabled= */ false, null);
        assertThat(resolvedComponent).isNull();
    }

    @Test
    public void resolve_systemDream() {
        final ComponentName resolvedComponent =
                mResolver.resolve(/* doze= */ false, USER_ID, false, DREAM_COMPONENT);
        assertThat(resolvedComponent).isEqualTo(DREAM_COMPONENT);
    }

    @Test
    public void resolve_userDream() {
        Settings.Secure.putStringForUser(
                mContext.getContentResolver(),
                Settings.Secure.SCREENSAVER_COMPONENTS,
                DREAM_COMPONENT.flattenToString(),
                USER_ID);
        when(mDreamValidator.validate(DREAM_COMPONENT, USER_ID)).thenReturn(true);

        final ComponentName resolvedComponent =
                mResolver.resolve(/* doze= */ false, USER_ID, false, null);
        assertThat(resolvedComponent).isEqualTo(DREAM_COMPONENT);
    }

    @Test
    public void resolve_userDream_fallbackToDefault() {
        Settings.Secure.putStringForUser(
                mContext.getContentResolver(),
                Settings.Secure.SCREENSAVER_COMPONENTS,
                "invalid_component",
                USER_ID);
        Settings.Secure.putStringForUser(
                mContext.getContentResolver(),
                Settings.Secure.SCREENSAVER_DEFAULT_COMPONENT,
                DEFAULT_DREAM_COMPONENT.flattenToString(),
                USER_ID);

        when(mDreamValidator.validate(DEFAULT_DREAM_COMPONENT, USER_ID)).thenReturn(true);

        final ComponentName resolvedComponent =
                mResolver.resolve(/* doze= */ false, USER_ID, false, null);
        assertThat(resolvedComponent).isEqualTo(DEFAULT_DREAM_COMPONENT);
    }

    @Test
    public void resolve_dreamsDisabledForUser() {
        // Dreams only enabled for main user (USER_ID = 0)
        mResolver =
                new DreamComponentsResolver(
                        mContext,
                        mDreamValidator,
                        mDozeConfig,
                        mUserManagerInternal,
                        /* dreamsOnlyEnabledForDockUser= */ true);

        int otherUser = 10;
        final ComponentName resolvedComponent =
                mResolver.resolve(/* doze= */ false, otherUser, false, null);
        assertThat(resolvedComponent).isNull();
    }

    @Test
    @EnableFlags(Flags.FLAG_DREAMS_SWITCHER)
    public void resolve_switcherEnabled_systemDream() {
        final ComponentName resolvedComponent =
                mResolver.resolve(/* doze= */ false, USER_ID, false, DREAM_COMPONENT);
        assertThat(resolvedComponent).isEqualTo(DREAM_COMPONENT);
    }

    @Test
    @EnableFlags(Flags.FLAG_DREAMS_SWITCHER)
    public void resolve_switcherEnabled_activeDream() {
        Settings.Secure.putStringForUser(
                mContext.getContentResolver(),
                Settings.Secure.SCREENSAVER_COMPONENTS,
                DREAM_COMPONENT.flattenToString() + "," + DEFAULT_DREAM_COMPONENT.flattenToString(),
                USER_ID);
        Settings.Secure.putStringForUser(
                mContext.getContentResolver(),
                Settings.Secure.SCREENSAVER_ACTIVE_COMPONENT,
                DEFAULT_DREAM_COMPONENT.flattenToString(),
                USER_ID);
        when(mDreamValidator.validate(DREAM_COMPONENT, USER_ID)).thenReturn(true);
        when(mDreamValidator.validate(DEFAULT_DREAM_COMPONENT, USER_ID)).thenReturn(true);

        final ComponentName resolvedComponent =
                mResolver.resolve(/* doze= */ false, USER_ID, false, null);
        assertThat(resolvedComponent).isEqualTo(DEFAULT_DREAM_COMPONENT);
    }

    @Test
    public void getDreamPlaylist_dreamsDisabled_returnsEmpty() {
        mResolver =
                new DreamComponentsResolver(
                        mContext,
                        mDreamValidator,
                        mDozeConfig,
                        mUserManagerInternal,
                        /* dreamsOnlyEnabledForDockUser= */ true);

        // USER_ID (0) is fine, but let's use another user to be sure it's disabled.
        // In setUp, mUserManagerInternal.getMainUserId() returns USER_ID (0).
        // dreamsEnabledForUser checks:
        // if (!mDreamsOnlyEnabledForDockUser) return true;
        // if (userId < 0) return false;
        // return userId == mainUserId;

        // So if we pass 10, it should be disabled.
        DreamPlaylist playlist = mResolver.getDreamPlaylist(10, null);
        assertThat(playlist).isNotNull();
        assertThat(playlist.getDreams()).isEmpty();
        assertThat(playlist.getActiveDream()).isNull();
    }

    @Test
    public void getDreamPlaylist_systemDream() {
        when(mDreamValidator.validate(DREAM_COMPONENT, USER_ID)).thenReturn(true);
        Settings.Secure.putStringForUser(
                mContext.getContentResolver(),
                Settings.Secure.SCREENSAVER_COMPONENTS,
                DREAM_COMPONENT.flattenToString(),
                USER_ID);
        DreamPlaylist playlist = mResolver.getDreamPlaylist(USER_ID, DEFAULT_DREAM_COMPONENT);
        // System dream (DEFAULT_DREAM_COMPONENT here) should be active and added to list
        assertThat(playlist.getActiveDream()).isEqualTo(DEFAULT_DREAM_COMPONENT);
        assertThat(playlist.getDreams()).containsExactly(DEFAULT_DREAM_COMPONENT);
    }

    @Test
    public void getDreamPlaylist_activeDream() {
        Settings.Secure.putStringForUser(
                mContext.getContentResolver(),
                Settings.Secure.SCREENSAVER_COMPONENTS,
                DREAM_COMPONENT.flattenToString() + "," + DEFAULT_DREAM_COMPONENT.flattenToString(),
                USER_ID);
        Settings.Secure.putStringForUser(
                mContext.getContentResolver(),
                Settings.Secure.SCREENSAVER_ACTIVE_COMPONENT,
                DEFAULT_DREAM_COMPONENT.flattenToString(),
                USER_ID);
        when(mDreamValidator.validate(DREAM_COMPONENT, USER_ID)).thenReturn(true);
        when(mDreamValidator.validate(DEFAULT_DREAM_COMPONENT, USER_ID)).thenReturn(true);

        DreamPlaylist playlist = mResolver.getDreamPlaylist(USER_ID, null);
        assertThat(playlist.getActiveDream()).isEqualTo(DEFAULT_DREAM_COMPONENT);
    }

    @Test
    public void getDreamPlaylist_defaultOrder() {
        Settings.Secure.putStringForUser(
                mContext.getContentResolver(),
                Settings.Secure.SCREENSAVER_COMPONENTS,
                DREAM_COMPONENT.flattenToString() + "," + DEFAULT_DREAM_COMPONENT.flattenToString(),
                USER_ID);
        when(mDreamValidator.validate(DREAM_COMPONENT, USER_ID)).thenReturn(true);
        when(mDreamValidator.validate(DEFAULT_DREAM_COMPONENT, USER_ID)).thenReturn(true);

        DreamPlaylist playlist = mResolver.getDreamPlaylist(USER_ID, null);
        // First one should be active
        assertThat(playlist.getActiveDream()).isEqualTo(DREAM_COMPONENT);
    }
}
