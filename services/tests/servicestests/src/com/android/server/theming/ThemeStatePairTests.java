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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import android.app.ActivityManagerInternal;
import android.content.Context;
import android.content.theming.ThemeStyle;
import android.graphics.Color;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.pm.UserManagerInternal;
import com.android.systemui.monet.ColorScheme;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class ThemeStatePairTests {
    private static final float CONTRAST_DEFAULT = 0.0f;
    private static final float CONTRAST_MEDIUM = 0.5f;

    private static final int SEED_COLOR_VALID = Color.BLUE;
    private static final int SEED_COLOR_RED = Color.RED;

    private static final int STYLE_VALID = ThemeStyle.TONAL_SPOT;

    private static final int USER_ID = 0;

    private ThemeStatePair mStatePair;
    private ThemeEnvironment mEnvironment;
    @Mock
    private ActivityManagerInternal mActivityManagerInternal;
    @Mock
    private UserManagerInternal mUserManagerInternal;
    @Mock
    private ThemeUserLifecycle mThemeUserLifecycle;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        when(mUserManagerInternal.isHeadlessSystemUserMode()).thenReturn(false);

        // Ensure services are registered for internal use by Environment if any (though
        // Environment just uses context/reader here)
        // But ThemeUserLifecycle might be needed if we set booting complete.
        // Here we just need the environment instance.
        mEnvironment = new ThemeEnvironment(context, (key, def) -> def);
        mEnvironment.setBootingComplete(mThemeUserLifecycle); // Ensure not booting by default
        mStatePair = new ThemeStatePair(USER_ID, true, SEED_COLOR_VALID,
                CONTRAST_DEFAULT, STYLE_VALID, mEnvironment);
    }

    @Test
    public void testShouldUpdateOverlays_noChanges() {
        assertFalse(mStatePair.shouldUpdateOverlays());
    }

    @Test
    public void testShouldUpdateOverlays_onlyTimeStampChanges() {
        mStatePair.forceUpdate();
        assertFalse(mStatePair.shouldUpdateOverlays());
    }

    @Test
    public void testShouldUpdateOverlays_seedColorChanges() {
        mStatePair.applySeedColor(SEED_COLOR_RED);
        assertTrue(mStatePair.shouldUpdateOverlays());
    }

    @Test
    public void testShouldUpdateOverlays_contrastChanges() {
        mStatePair.applyContrast(CONTRAST_MEDIUM);
        assertTrue(mStatePair.shouldUpdateOverlays());
    }

    @Test
    public void testShouldUpdateOverlays_styleChanges() {
        mStatePair.applyStyle(ThemeStyle.EXPRESSIVE);
        assertTrue(mStatePair.shouldUpdateOverlays());
    }

    @Test
    public void testShouldUpdateOverlays_everythingChanges() {
        mStatePair.applyStyle(ThemeStyle.EXPRESSIVE);
        mStatePair.applyContrast(CONTRAST_MEDIUM);
        mStatePair.applySeedColor(SEED_COLOR_RED);

        assertTrue(mStatePair.shouldUpdateOverlays());
    }

    @Test
    public void testSetDeferUpdatesOnLock() {
        mStatePair.setDeferUpdatesOnLock(true);
        assertTrue(mStatePair.areUpdatesDeferredOnLock());

        mStatePair.setDeferUpdatesOnLock(false);
        assertFalse(mStatePair.areUpdatesDeferredOnLock());
    }

    @Test
    public void testCommitAndGetSnapshot_updatesStateAndReturnsOverlayData() {
        // Initial state
        ColorScheme initialDark = mStatePair.getDarkScheme();
        ColorScheme initialLight = mStatePair.getLightScheme();

        // Apply changes
        mStatePair.applySeedColor(SEED_COLOR_RED);
        assertTrue(mStatePair.shouldUpdateOverlays());

        // Commit and get snapshot
        ThemeStatePair.OverlaySnapshot snapshot = mStatePair.commitAndGetOverlayData();

        // Verify state is updated
        assertEquals(SEED_COLOR_RED, mStatePair.getCurrentState().seedColor());
        assertFalse(mStatePair.shouldUpdateOverlays());

        // Verify snapshot contains new schemes
        assertNotEquals(initialDark, snapshot.darkScheme());
        assertNotEquals(initialLight, snapshot.lightScheme());
        assertEquals(SEED_COLOR_RED, snapshot.darkScheme().getSeed());
        assertEquals(SEED_COLOR_RED, snapshot.lightScheme().getSeed());
    }

    @Test
    public void testCommitAndGetOverlayData_noOverlayUpdateNeeded_returnsSameSchemes() {
        // Initial state
        ColorScheme initialDark = mStatePair.getDarkScheme();
        ColorScheme initialLight = mStatePair.getLightScheme();

        // Apply change that doesn't affect overlays (timestamp only)
        mStatePair.forceUpdate();
        assertFalse(mStatePair.shouldUpdateOverlays());

        // Commit and get snapshot
        ThemeStatePair.OverlaySnapshot snapshot = mStatePair.commitAndGetOverlayData();

        // Verify snapshot contains same schemes
        assertEquals(initialDark, snapshot.darkScheme());
        assertEquals(initialLight, snapshot.lightScheme());
    }

    @Test
    public void testShouldUpdate_differentTimestamp_shouldUpdate() {
        mStatePair.forceUpdate();
        assertTrue(mStatePair.shouldUpdate());
    }

    @Test
    public void testShouldUpdate_sameState_shouldNotUpdate() {
        assertFalse(mStatePair.shouldUpdate());
    }

    @Test
    public void testShouldUpdate_userNotSetup_shouldNotUpdate() {
        mStatePair = new ThemeStatePair(USER_ID, false, SEED_COLOR_VALID, CONTRAST_DEFAULT,
                STYLE_VALID, mEnvironment);
        assertFalse(mStatePair.shouldUpdate());
    }

    @Test
    public void testShouldUpdate_backgroundChangesDeferred_shouldNotUpdate() {
        mStatePair.setDeferUpdatesOnLock(true);
        mStatePair.applySeedColor(SEED_COLOR_RED);
        assertFalse(mStatePair.shouldUpdate());
    }

    @Test
    public void testShouldUpdate_backgroundChangesDeferredButForced_shouldUpdate() {
        mStatePair.setDeferUpdatesOnLock(true);
        mStatePair.forceUpdate(); // Force the update by changing the timestamp
        assertTrue(mStatePair.shouldUpdate());
    }

    @Test
    public void testShouldUpdate_overlaysShouldUpdate_shouldUpdate() {
        mStatePair.applySeedColor(SEED_COLOR_RED);
        assertTrue(mStatePair.shouldUpdate());
    }

    @Test
    public void testShouldUpdate_noChanges_shouldUpdate() {
        mStatePair.forceUpdate(); // This ensures the timestamps are different
        assertTrue(mStatePair.shouldUpdate());
    }

    @Test
    public void testShouldUpdate_booting_shouldUpdateEvenIfNotSetup() {
        // Create a fresh environment that is still booting
        ThemeEnvironment bootingEnv = new ThemeEnvironment(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                (key, def) -> def);

        mStatePair = new ThemeStatePair(USER_ID, false /* isSetup */, SEED_COLOR_VALID,
                CONTRAST_DEFAULT,
                STYLE_VALID, bootingEnv);
        // Apply a change so pending != current, but do NOT use forceUpdate() because
        // that bypasses the setup check.
        mStatePair.applySeedColor(SEED_COLOR_RED);

        // User not setup, normally returns false.
        // But if booting, should return true.
        assertTrue(mStatePair.shouldUpdate());
    }
}
