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

@file:JvmName("DesktopModeCommonAssertions")

package com.android.wm.shell.flicker.utils

import android.tools.flicker.legacy.LegacyFlickerTest
import android.tools.helpers.WindowUtils
import android.tools.traces.component.IComponentMatcher

// Common assertions for Desktop mode features.

fun LegacyFlickerTest.cascadingEffectAppliedAtEnd(component: IComponentMatcher) {
    assertWmEnd {
        val displayAppBounds = WindowUtils.getInsetDisplayBounds(scenario.startRotation)
        val windowBounds = visibleRegion(component).region.bounds

        val onRightSide = windowBounds.right == displayAppBounds.right
        val onLeftSide = windowBounds.left == displayAppBounds.left
        val onTopSide = windowBounds.top == displayAppBounds.top
        val onBottomSide = windowBounds.bottom == displayAppBounds.bottom
        val alignedOnCorners = onRightSide.xor(onLeftSide) and onTopSide.xor(onBottomSide)

        check { "window corner must meet display corner" }.that(alignedOnCorners).isEqual(true)
    }
}
