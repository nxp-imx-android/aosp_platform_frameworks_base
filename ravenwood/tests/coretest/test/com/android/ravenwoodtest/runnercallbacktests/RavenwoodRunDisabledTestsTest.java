/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.ravenwoodtest.runnercallbacktests;

import static android.platform.test.ravenwood.RavenwoodEnablementChecker.REALLY_DISABLED_PATTERN;
import static android.platform.test.ravenwood.RavenwoodEnablementChecker.RUN_DISABLED_TESTS;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.platform.test.annotations.DisabledOnRavenwood;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.regex.Pattern;

/**
 * Test for "RAVENWOOD_RUN_DISABLED_TESTS".
 */
@RunWith(AndroidJUnit4.class)
public class RavenwoodRunDisabledTestsTest {

    private static boolean sRunDisabledTests;
    private static Pattern sReallyDisabledPattern;

    private static boolean sDidEnabledTestRun = false;
    private static boolean sDidDisabledTestRun = false;
    private static boolean sDidReallyDisabledTestRun = false;

    @BeforeClass
    public static void beforeClass() {
        sRunDisabledTests = RUN_DISABLED_TESTS;
        sReallyDisabledPattern = REALLY_DISABLED_PATTERN;
        RUN_DISABLED_TESTS = true;
        REALLY_DISABLED_PATTERN = Pattern.compile("\\#testReallyDisabled$");
    }

    @AfterClass
    public static void afterClass() {
        RUN_DISABLED_TESTS = sRunDisabledTests;
        REALLY_DISABLED_PATTERN = sReallyDisabledPattern;
        assertTrue(sDidDisabledTestRun);
        assertFalse(sDidEnabledTestRun);
        assertFalse(sDidReallyDisabledTestRun);
    }

    /**
     * This will be executed due to it being "disabled".
     */
    @Test
    @DisabledOnRavenwood
    public void testDisabledTest() {
        sDidDisabledTestRun = true;
    }

    /**
     * This will not be executed due to it being "enabled".
     */
    @Test
    public void testEnabledTest() {
        sDidEnabledTestRun = true;
    }

    /**
     * This will still not be executed due to the "really disabled" pattern.
     */
    @Test
    @DisabledOnRavenwood
    public void testReallyDisabled() {
        sDidReallyDisabledTestRun = true;
    }
}
