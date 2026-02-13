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
import android.provider.Settings;
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
}
