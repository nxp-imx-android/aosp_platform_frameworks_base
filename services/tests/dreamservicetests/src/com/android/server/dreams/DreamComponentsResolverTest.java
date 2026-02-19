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
import android.service.dreams.DreamItem;
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

    @Mock private DreamRepository mDreamRepository;
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
                        USER_ID,
                        mDozeConfig,
                        mUserManagerInternal,
                        /* dreamsOnlyEnabledForDockUser= */ false,
                        mDreamRepository);
    }

    @Test
    public void resolve_doze_forceEnabled() {
        when(mDozeConfig.ambientDisplayComponent()).thenReturn(DOZE_COMPONENT.flattenToString());
        mockDreamItem(DOZE_COMPONENT);

        final ComponentName resolvedComponent =
                mResolver.resolve(/* doze= */ true, /* forceAmbientDisplayEnabled= */ true, null);
        assertThat(resolvedComponent).isEqualTo(DOZE_COMPONENT);
    }

    @Test
    public void resolve_doze_configEnabled() {
        when(mDozeConfig.enabled(USER_ID)).thenReturn(true);
        when(mDozeConfig.ambientDisplayComponent()).thenReturn(DOZE_COMPONENT.flattenToString());
        mockDreamItem(DOZE_COMPONENT);

        final ComponentName resolvedComponent =
                mResolver.resolve(/* doze= */ true, /* forceAmbientDisplayEnabled= */ false, null);
        assertThat(resolvedComponent).isEqualTo(DOZE_COMPONENT);
    }

    @Test
    public void resolve_doze_disabled() {
        when(mDozeConfig.enabled(USER_ID)).thenReturn(false);

        final ComponentName resolvedComponent =
                mResolver.resolve(/* doze= */ true, /* forceAmbientDisplayEnabled= */ false, null);
        assertThat(resolvedComponent).isNull();
    }

    @Test
    public void resolve_systemDream() {
        mockDreamItem(DREAM_COMPONENT);
        final ComponentName resolvedComponent =
                mResolver.resolve(/* doze= */ false, false, DREAM_COMPONENT);
        assertThat(resolvedComponent).isEqualTo(DREAM_COMPONENT);
    }

    @Test
    public void resolve_userDream() {
        when(mDreamRepository.getDreamComponentsForUser(USER_ID))
                .thenReturn(new ComponentName[] {DREAM_COMPONENT});
        mockDreamItem(DREAM_COMPONENT);

        final ComponentName resolvedComponent = mResolver.resolve(/* doze= */ false, false, null);
        assertThat(resolvedComponent).isEqualTo(DREAM_COMPONENT);
    }

    @Test
    public void resolve_userDream_fallbackToDefault() {
        when(mDreamRepository.getDreamComponentsForUser(USER_ID))
                .thenReturn(new ComponentName[] {new ComponentName("invalid", "invalid")});
        when(mDreamRepository.getDefaultDreamComponentForUser(USER_ID))
                .thenReturn(DEFAULT_DREAM_COMPONENT);

        mockDreamItem(DEFAULT_DREAM_COMPONENT);

        final ComponentName resolvedComponent = mResolver.resolve(/* doze= */ false, false, null);
        assertThat(resolvedComponent).isEqualTo(DEFAULT_DREAM_COMPONENT);
    }

    @Test
    public void resolve_dreamsDisabledForUser() {
        // Dreams only enabled for main user (USER_ID = 0)
        int otherUser = 10;
        mResolver =
                new DreamComponentsResolver(
                        mContext,
                        otherUser,
                        mDozeConfig,
                        mUserManagerInternal,
                        /* dreamsOnlyEnabledForDockUser= */ true,
                        mDreamRepository);

        final ComponentName resolvedComponent = mResolver.resolve(/* doze= */ false, false, null);
        assertThat(resolvedComponent).isNull();
    }

    @Test
    @EnableFlags(Flags.FLAG_DREAMS_SWITCHER)
    public void resolve_switcherEnabled_systemDream() {
        mockDreamItem(DREAM_COMPONENT);
        final ComponentName resolvedComponent =
                mResolver.resolve(/* doze= */ false, false, DREAM_COMPONENT);
        assertThat(resolvedComponent).isEqualTo(DREAM_COMPONENT);
    }

    @Test
    @EnableFlags(Flags.FLAG_DREAMS_SWITCHER)
    public void resolve_switcherEnabled_activeDream() {
        when(mDreamRepository.getDreamComponentsForUser(USER_ID))
                .thenReturn(new ComponentName[] {DREAM_COMPONENT, DEFAULT_DREAM_COMPONENT});
        when(mDreamRepository.getActiveDreamComponentForUser(USER_ID))
                .thenReturn(DEFAULT_DREAM_COMPONENT);
        mockDreamItem(DREAM_COMPONENT);
        mockDreamItem(DEFAULT_DREAM_COMPONENT);

        final ComponentName resolvedComponent = mResolver.resolve(/* doze= */ false, false, null);
        assertThat(resolvedComponent).isEqualTo(DEFAULT_DREAM_COMPONENT);
    }

    @Test
    public void getDreamPlaylist_dreamsDisabled_returnsEmpty() {
        // USER_ID (0) is fine, but let's use another user to be sure it's disabled.
        // In setUp, mUserManagerInternal.getMainUserId() returns USER_ID (0).
        // dreamsEnabledForUser checks:
        // if (!mDreamsOnlyEnabledForDockUser) return true;
        // if (userId < 0) return false;
        // return userId == mainUserId;

        // So if we pass 10, it should be disabled.
        int otherUser = 10;
        mResolver =
                new DreamComponentsResolver(
                        mContext,
                        otherUser,
                        mDozeConfig,
                        mUserManagerInternal,
                        /* dreamsOnlyEnabledForDockUser= */ true,
                        mDreamRepository);

        DreamPlaylist playlist = mResolver.getDreamPlaylist(null);
        assertThat(playlist).isNotNull();
        assertThat(playlist.getDreams()).isEmpty();
        assertThat(playlist.getActiveDream()).isNull();
    }

    @Test
    public void getDreamPlaylist_systemDream() {
        mockDreamItem(DREAM_COMPONENT);
        mockDreamItem(DEFAULT_DREAM_COMPONENT);
        when(mDreamRepository.getDreamComponentsForUser(USER_ID))
                .thenReturn(new ComponentName[] {DREAM_COMPONENT});
        DreamPlaylist playlist = mResolver.getDreamPlaylist(DEFAULT_DREAM_COMPONENT);
        // System dream (DEFAULT_DREAM_COMPONENT here) should be active and added to list
        assertThat(playlist.getActiveDream().componentName).isEqualTo(DEFAULT_DREAM_COMPONENT);
        assertThat(playlist.getDreams()).containsExactly(createDreamItem(DEFAULT_DREAM_COMPONENT));
    }

    @Test
    public void getDreamPlaylist_activeDream() {
        when(mDreamRepository.getDreamComponentsForUser(USER_ID))
                .thenReturn(new ComponentName[] {DREAM_COMPONENT, DEFAULT_DREAM_COMPONENT});
        when(mDreamRepository.getActiveDreamComponentForUser(USER_ID))
                .thenReturn(DEFAULT_DREAM_COMPONENT);
        mockDreamItem(DREAM_COMPONENT);
        mockDreamItem(DEFAULT_DREAM_COMPONENT);

        DreamPlaylist playlist = mResolver.getDreamPlaylist(null);
        assertThat(playlist.getActiveDream().componentName).isEqualTo(DEFAULT_DREAM_COMPONENT);
    }

    @Test
    public void getDreamPlaylist_defaultOrder() {
        when(mDreamRepository.getDreamComponentsForUser(USER_ID))
                .thenReturn(new ComponentName[] {DREAM_COMPONENT, DEFAULT_DREAM_COMPONENT});
        mockDreamItem(DREAM_COMPONENT);
        mockDreamItem(DEFAULT_DREAM_COMPONENT);

        DreamPlaylist playlist = mResolver.getDreamPlaylist(null);
        // First one should be active
        assertThat(playlist.getActiveDream().componentName).isEqualTo(DREAM_COMPONENT);
    }

    @Test
    public void isValid_nullComponent_returnsFalse() {
        assertThat(mResolver.isValid(null)).isFalse();
    }

    private DreamItem createDreamItem(ComponentName component) {
        return new DreamItem.Builder(component).build();
    }

    private void mockDreamItem(ComponentName component) {
        when(mDreamRepository.getDreamItem(component))
                .thenReturn(java.util.Optional.of(new DreamItem.Builder(component).build()));
    }
}
