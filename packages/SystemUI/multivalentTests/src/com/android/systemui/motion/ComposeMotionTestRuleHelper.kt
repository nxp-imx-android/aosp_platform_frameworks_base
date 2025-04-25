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

package com.android.systemui.motion

import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.testScope
import org.junit.rules.RuleChain
import platform.test.motion.MotionTestRule
import platform.test.motion.compose.ComposeToolkit
import platform.test.motion.compose.FixedConfiguration
import platform.test.motion.compose.createFixedConfigurationComposeMotionTestRule
import platform.test.motion.testing.createGoldenPathManager
import platform.test.screenshot.DeviceEmulationSpec
import platform.test.screenshot.Displays
import platform.test.screenshot.PathConfig
import platform.test.screenshot.utils.compose.ComposeScreenshotTestRule

/**
 * Create a [MotionTestRule] for motion tests of Compose-based System UI.
 *
 * **NOTE**: This factory uses a DeviceEmulationSpec to set a fixed density at a device level. This
 * is known to not work reliably with Robolectric. Use
 * createSysUiComposeMotionTestRuleWithFixedConfig instead, where the density is overridden in
 * compose instead.
 */
@Deprecated(
    message =
        "Use createSysUiComposeMotionTestRuleWithFixedConfig() instead. This might not work with robolectric",
    level = DeprecationLevel.WARNING,
    replaceWith =
        ReplaceWith(
            "createSysUiComposeMotionTestRuleWithFixedConfig(kosmos, deviceEmulationSpec, pathConfig,)"
        ),
)
fun createSysUiComposeMotionTestRule(
    kosmos: Kosmos,
    deviceEmulationSpec: DeviceEmulationSpec = DeviceEmulationSpec(Displays.Phone),
    pathConfig: PathConfig = PathConfig(),
): MotionTestRule<ComposeToolkit> {
    val goldenPathManager =
        createGoldenPathManager("frameworks/base/packages/SystemUI/tests/goldens", pathConfig)
    val testScope = kosmos.testScope

    val composeScreenshotTestRule =
        ComposeScreenshotTestRule(deviceEmulationSpec, goldenPathManager)

    return MotionTestRule(
        ComposeToolkit(composeScreenshotTestRule.composeRule, testScope),
        goldenPathManager,
        bitmapDiffer = composeScreenshotTestRule,
        extraRules = RuleChain.outerRule(composeScreenshotTestRule),
    )
}

/**
 * Create a [MotionTestRule] with [FixedConfiguration] for motion tests of Compose-based System UI.
 *
 * **NOTE:** [FixedConfiguration] overrides the density value in compose to provide consistent UI
 * for testing. This affects the generation of goldens.
 */
fun createSysUiComposeMotionTestRuleWithFixedConfig(
    kosmos: Kosmos,
    pathConfig: PathConfig = PathConfig(),
): MotionTestRule<ComposeToolkit> {
    val goldenPathManager =
        createGoldenPathManager("frameworks/base/packages/SystemUI/tests/goldens", pathConfig)
    return createFixedConfigurationComposeMotionTestRule(goldenPathManager, kosmos.testScope)
}
