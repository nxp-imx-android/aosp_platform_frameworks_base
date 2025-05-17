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

@file:JvmName("BubbleFlickerTestHelper")

package com.android.wm.shell.flicker.bubbles

import android.app.Instrumentation
import android.tools.Rotation
import android.tools.device.apphelpers.StandardAppHelper
import android.tools.flicker.rules.ChangeDisplayOrientationRule
import android.tools.flicker.rules.RemoveAllTasksButHomeRule.Companion.removeAllTasksButHome
import android.tools.io.Reader
import android.tools.traces.ConditionsFactory
import android.tools.traces.component.ComponentNameMatcher
import android.tools.traces.monitors.PerfettoTraceMonitor
import android.tools.traces.monitors.events.EventLogMonitor
import android.tools.traces.monitors.withTracing
import android.tools.traces.parsers.WindowManagerStateHelper
import com.android.launcher3.tapl.LauncherInstrumentation

// TODO(b/396020056): Verify bubble bar on the large screen devices.
/**
 * Called to initialize the device before the transition.
 */
fun setUpBeforeTransition(instrumentation: Instrumentation, wmHelper: WindowManagerStateHelper) {
    ChangeDisplayOrientationRule.setRotation(
        Rotation.ROTATION_0,
        instrumentation,
        clearCacheAfterParsing = false,
        wmHelper = wmHelper,
    )
    removeAllTasksButHome()
}

/**
 * A helper method to record the trace while [transition] is running.
 *
 * @sample com.android.wm.shell.flicker.bubbles.samples.runTransitionWithTraceSample
 *
 * @param transition the transition to verify.
 * @return a [Reader] that can read the trace data from.
 */
fun runTransitionWithTrace(transition: () -> Unit): Reader =
    withTracing(
        traceMonitors = listOf(
            PerfettoTraceMonitor.newBuilder()
                .enableTransitionsTrace()
                .enableLayersTrace()
                .enableWindowManagerTrace()
                .build(),
            EventLogMonitor()
        ),
        predicate = transition
    )

/**
 * Launches [testApp] into bubble via clicking bubble menu.
 *
 * @param testApp the test app to launch into bubble
 * @param tapl the [LauncherInstrumentation]
 * @param wmHelper the [WindowManagerStateHelper]
 */
fun launchBubbleViaBubbleMenu(
    testApp: StandardAppHelper,
    tapl: LauncherInstrumentation,
    wmHelper: WindowManagerStateHelper,
) {
    val allApps = tapl.goHome().switchToAllApps()
    val simpleAppIcon = allApps.getAppIcon(testApp.appName)
    // Open the bubble menu and click.
    simpleAppIcon.openMenu().bubbleMenuItem.click()

    // Wait for bubble shown.
    wmHelper
        .StateSyncBuilder()
        .add(ConditionsFactory.isWMStateComplete())
        .withAppTransitionIdle()
        .withTopVisibleApp(testApp)
        .waitForAndVerify()
}

/**
 * Launches [testApp] into bubble via clicking bubble menu.
 *
 * @param testApp the test app to launch into bubble
 * @param tapl the [LauncherInstrumentation]
 * @param wmHelper the [WindowManagerStateHelper]
 */
fun collapseBubbleViaBackKey(
    testApp: StandardAppHelper,
    tapl: LauncherInstrumentation,
    wmHelper: WindowManagerStateHelper,
) {
    // Ensure Bubble is shown and in expanded state.
    wmHelper
        .StateSyncBuilder()
        .add(ConditionsFactory.isWMStateComplete())
        .withAppTransitionIdle()
        .withTopVisibleApp(testApp)
        .withBubbleShown()
        .waitForAndVerify()

    // Press back key to collapse bubble
    tapl.pressBack()

    // Ensure Bubble is in the collapse state.
    wmHelper
        .StateSyncBuilder()
        .add(ConditionsFactory.isWMStateComplete())
        .withAppTransitionIdle()
        .withTopVisibleApp(ComponentNameMatcher.LAUNCHER)
        .waitForAndVerify()
}
