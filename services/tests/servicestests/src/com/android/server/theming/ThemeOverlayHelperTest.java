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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import android.content.om.OverlayManagerTransaction;
import android.content.theming.ThemeStyle;
import android.graphics.Color;
import android.os.UserHandle;

import androidx.test.runner.AndroidJUnit4;

import com.android.server.om.OverlayManagerInternal;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RunWith(AndroidJUnit4.class)
public class ThemeOverlayHelperTest {

    private static final int PRIMARY_USER_ID = 10;
    private static final int PROFILE_USER_ID = 11;
    private static final int SYSTEM_USER_ID = UserHandle.USER_SYSTEM;

    private static final float CONTRAST_DEFAULT = 0.0f;
    private static final int SEED_COLOR_VALID = Color.BLUE;
    private static final int STYLE_VALID = ThemeStyle.TONAL_SPOT;

    @Mock
    private OverlayManagerInternal mOverlayManager;
    @Captor
    private ArgumentCaptor<OverlayManagerTransaction> mTransactionCaptor;

    private ThemeOverlayHelper mThemeOverlayHelper;

    @Before
    public void setup() {
        // This initializes all fields annotated with @Mock and @Captor
        MockitoAnnotations.initMocks(this);
        mThemeOverlayHelper = new ThemeOverlayHelper(mOverlayManager);
    }

    @Test
    public void applyCurrentStateOverlays_foregroundUser_enablesForSelfAndSystemAndProfiles() {
        // Setup: A primary user with an associated profile.
        ThemeStatePair statePair = new ThemeStatePair(PRIMARY_USER_ID, true, SEED_COLOR_VALID,
                CONTRAST_DEFAULT, STYLE_VALID);
        statePair.addProfile(PROFILE_USER_ID);
        ThemeStatePair.OverlaySnapshot snapshot = statePair.commitAndGetOverlayData();

        // Action: Pass true because this is simulating the Foreground User
        mThemeOverlayHelper.applyCurrentStateOverlays(snapshot, true /* applyToSystem */);

        // Verification
        verify(mOverlayManager).commit(mTransactionCaptor.capture());
        String transactionString = mTransactionCaptor.getValue().toString();

        final List<String> overlayNames = List.of("neutral", "accent", "dynamic");
        for (String overlayName : overlayNames) {
            // Check that overlays are enabled for the primary user, their profile, and system.
            assertSetEnabled(transactionString, overlayName, PRIMARY_USER_ID);
            assertSetEnabled(transactionString, overlayName, PROFILE_USER_ID);
            assertSetEnabled(transactionString, overlayName, SYSTEM_USER_ID);
        }
    }

    @Test
    public void applyCurrentStateOverlays_backgroundUser_doesNotEnableForSystem() {
        // Setup: A background user (simulated by passing false to the helper)
        ThemeStatePair statePair = new ThemeStatePair(PRIMARY_USER_ID, true, SEED_COLOR_VALID,
                CONTRAST_DEFAULT, STYLE_VALID);
        ThemeStatePair.OverlaySnapshot snapshot = statePair.commitAndGetOverlayData();

        // Action: Pass false because this is simulating a Background User
        mThemeOverlayHelper.applyCurrentStateOverlays(snapshot, false /* applyToSystem */);

        // Verification
        verify(mOverlayManager).commit(mTransactionCaptor.capture());
        String transactionString = mTransactionCaptor.getValue().toString();

        final List<String> overlayNames = List.of("neutral", "accent", "dynamic");
        for (String overlayName : overlayNames) {
            // Should be enabled for the user themselves
            assertSetEnabled(transactionString, overlayName, PRIMARY_USER_ID);
            // Should NOT be enabled for the system user
            assertNotSetEnabled(transactionString, overlayName, SYSTEM_USER_ID);
        }
    }

    @Test
    public void applyCurrentStateOverlays_whenUserIsSystem_enablesOnce() {
        // Setup: The user is the system user.
        ThemeStatePair statePair = new ThemeStatePair(SYSTEM_USER_ID, true, SEED_COLOR_VALID,
                CONTRAST_DEFAULT, STYLE_VALID);
        ThemeStatePair.OverlaySnapshot snapshot = statePair.commitAndGetOverlayData();

        // Is does not matter if we pass true/false here.
        mThemeOverlayHelper.applyCurrentStateOverlays(snapshot, true);

        // Verification
        verify(mOverlayManager).commit(mTransactionCaptor.capture());
        String transactionString = mTransactionCaptor.getValue().toString();

        final List<String> overlayNames = List.of("neutral", "accent", "dynamic");
        for (String overlayName : overlayNames) {
            // It should be enabled for the system user exactly once.
            String expectedSubstring = getSetEnabledSubstring(overlayName, SYSTEM_USER_ID);
            int count = countOccurrences(transactionString, expectedSubstring);
            assertThat(count).isEqualTo(1);
        }
    }

    @Test
    public void applyCurrentStateOverlays_handlesCommitExceptionGracefully() {
        // Setup a commit failure
        doThrow(new SecurityException("Test Exception")).when(mOverlayManager).commit(any());
        ThemeStatePair statePair = new ThemeStatePair(PRIMARY_USER_ID, true, SEED_COLOR_VALID,
                CONTRAST_DEFAULT, STYLE_VALID);
        ThemeStatePair.OverlaySnapshot snapshot = statePair.commitAndGetOverlayData();

        // Action & Verification (should not crash)
        mThemeOverlayHelper.applyCurrentStateOverlays(snapshot, true);

        // Verify commit was still attempted
        verify(mOverlayManager).commit(any(OverlayManagerTransaction.class));
    }

    /**
     * Builds the expected substring for a SET_ENABLED request to find in the
     * transaction's string representation.
     */
    private String getSetEnabledSubstring(String overlayName, int userId) {
        // Based on OverlayManager.Request.toString()
        String overlayId = "com.android.systemui:" + overlayName;
        // The format must match OverlayManagerTransaction.Request.toString()
        // e.g., "Request{type=0x00 (TYPE_SET_ENABLED), overlay=com.android.systemui:neutral,
        // userId=10, ...}"
        return String.format("Request{type=0x00 (TYPE_SET_ENABLED), overlay=%s, userId=%d",
                overlayId, userId);
    }

    /**
     * Asserts that a specific SET_ENABLED request is present in the transaction string.
     */
    private void assertSetEnabled(String transactionString, String overlayName, int userId) {
        String expectedSubstring = getSetEnabledSubstring(overlayName, userId);
        assertThat(transactionString).contains(expectedSubstring);
    }

    /**
     * Asserts that a specific SET_ENABLED request is NOT present in the transaction string.
     */
    private void assertNotSetEnabled(String transactionString, String overlayName, int userId) {
        String expectedSubstring = getSetEnabledSubstring(overlayName, userId);
        assertThat(transactionString).doesNotContain(expectedSubstring);
    }

    /**
     * Counts the number of times a substring appears in a source string.
     */
    private int countOccurrences(String source, String sub) {
        Pattern p = Pattern.compile(Pattern.quote(sub));
        Matcher m = p.matcher(source);
        int count = 0;
        while (m.find()) {
            count++;
        }
        return count;
    }
}
