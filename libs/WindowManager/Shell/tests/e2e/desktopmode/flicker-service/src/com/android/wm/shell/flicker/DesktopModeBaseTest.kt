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

package com.android.wm.shell.flicker

import android.tools.flicker.legacy.LegacyFlickerTest
import android.tools.flicker.rules.ChangeDisplayOrientationRule

import org.junit.Assume
import org.junit.Before

/**
 * The base class that all Desktop Mode Flicker tests should inherit from.
 *
 * This will ensure that all the appropriate methods are called before running the tests.
 */
abstract class DesktopModeBaseTest(flicker: LegacyFlickerTest) : BaseTest(flicker) {
    @Before
    fun setUp() {
        tapl.setExpectedRotation(flicker.scenario.startRotation.value)
        ChangeDisplayOrientationRule.setRotation(flicker.scenario.startRotation)
        Assume.assumeTrue(tapl.isTablet)
    }
}