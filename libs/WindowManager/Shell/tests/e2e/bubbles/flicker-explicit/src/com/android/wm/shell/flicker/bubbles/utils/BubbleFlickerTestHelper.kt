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

package com.android.wm.shell.flicker.bubbles.utils

import android.app.Instrumentation
import android.graphics.Point
import android.platform.systemui_tapl.ui.Bubble
import android.platform.systemui_tapl.ui.Root
import android.tools.Rotation
import android.tools.device.apphelpers.BrowserAppHelper
import android.tools.device.apphelpers.CalculatorAppHelper
import android.tools.device.apphelpers.ClockAppHelper
import android.tools.device.apphelpers.MapsAppHelper
import android.tools.device.apphelpers.MessagingAppHelper
import android.tools.device.apphelpers.StandardAppHelper
import android.tools.flicker.rules.ChangeDisplayOrientationRule
import android.tools.flicker.rules.RemoveAllTasksButHomeRule.Companion.removeAllTasksButHome
import android.tools.io.Reader
import android.tools.traces.ConditionsFactory
import android.tools.traces.component.ComponentNameMatcher.Companion.LAUNCHER
import android.tools.traces.monitors.PerfettoTraceMonitor
import android.tools.traces.monitors.ScreenRecorder
import android.tools.traces.monitors.events.EventLogMonitor
import android.tools.traces.monitors.withTracing
import android.tools.traces.parsers.WindowManagerStateHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.android.launcher3.tapl.LauncherInstrumentation
import com.android.server.wm.flicker.helpers.ImeAppHelper
import com.android.wm.shell.Flags
import com.google.common.truth.Truth.assertWithMessage

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
            ScreenRecorder(InstrumentationRegistry.getInstrumentation().targetContext),
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
    // Go to all apps to launch app into a bubble.
    tapl.goHome().switchToAllApps()
    launchAndWaitForBubbleAppExpanded(testApp, tapl, wmHelper)
}

/**
 * Launches [testApp] into bubble via dragging the icon from task bar to bubble bar location.
 *
 * @param testApp the test app to launch into bubble
 * @param tapl the [LauncherInstrumentation]
 * @param wmHelper the [WindowManagerStateHelper]
 */
fun launchBubbleViaDragToBubbleBar(
    testApp: StandardAppHelper,
    tapl: LauncherInstrumentation,
    wmHelper: WindowManagerStateHelper,
) {
    // Switch to overview to show task bar.
    val overview = tapl.goHome().switchToOverview()
    val taskBar = overview.taskbar ?: error("Can't find TaskBar")
    val taskBarAppIcon = taskBar.getAppIcon(testApp.appName)
    taskBarAppIcon.dragToBubbleBarLocation(false /* isBubbleBarLeftDropTarget */)

    waitAndAssertBubbleAppInExpandedState(testApp, wmHelper)
    tapl.launchedAppState.assertTaskbarHidden()
    assertWithMessage("The education must not show for Application bubble")
        .that(Root.get().bubble.isEducationVisible).isFalse()
}

/**
 * Collapses the bubble app [testApp] via back key.
 *
 * @param testApp the bubble app to collapse
 * @param tapl the [LauncherInstrumentation]
 * @param wmHelper the [WindowManagerStateHelper]
 */
fun collapseBubbleAppViaBackKey(
    testApp: StandardAppHelper,
    tapl: LauncherInstrumentation,
    wmHelper: WindowManagerStateHelper,
) {
    waitAndAssertBubbleAppInExpandedState(testApp, wmHelper)

    // Press back key to collapse bubble
    tapl.pressBack()

    waitAndAssertBubbleAppInCollapseState(wmHelper)
}

/**
 * Collapses the bubble app [testApp] via touching outside the bubble app.
 *
 * @param testApp the bubble app to collapse
 * @param wmHelper the [WindowManagerStateHelper]
 */
fun collapseBubbleAppViaTouchOutside(
    testApp: StandardAppHelper,
    wmHelper: WindowManagerStateHelper,
) {
    waitAndAssertBubbleAppInExpandedState(testApp, wmHelper)

    Root.get().expandedBubbleStack.closeByClickingOutside()

    waitAndAssertBubbleAppInCollapseState(wmHelper)
}

/**
 * Expands the bubble app [testApp], which is previously collapsed via tapping on bubble stack.
 *
 * @param testApp the bubble app to expand
 * @param uiDevice the UI automator to get the bubble view [UiObject2]
 * @param wmHelper the [WindowManagerStateHelper]
 */
fun expandBubbleAppViaTapOnBubbleStack(
    uiDevice: UiDevice,
    testApp: StandardAppHelper,
    wmHelper: WindowManagerStateHelper,
) {
    // Ensure Bubble is in collapse state.
    waitAndAssertBubbleAppInCollapseState(wmHelper)

    // Click bubble to expand
    uiDevice.bubbleIcon?.click() ?: error("Can't find bubble view")

    waitAndAssertBubbleAppInExpandedState(testApp, wmHelper)
}

/**
 * Expands the bubble app [testApp], which is previously collapsed via tapping on bubble bar.
 * Note that this method only works on device with bubble bar.
 *
 * @param uiDevice the UI automator to get the bubble bar [UiObject2]
 * @param testApp the bubble app to expand
 * @param wmHelper the [WindowManagerStateHelper]
 */
fun expandBubbleAppViaBubbleBar(
    uiDevice: UiDevice,
    testApp: StandardAppHelper,
    wmHelper: WindowManagerStateHelper,
) {
    // Ensure Bubble is in collapse state.
    waitAndAssertBubbleAppInCollapseState(wmHelper)

    // Click bubble bar to expand
    uiDevice.bubbleBar?.click() ?: error("Can't find bubble bar")

    waitAndAssertBubbleAppInExpandedState(testApp, wmHelper)
}

/**
 * Dismisses the bubble app via dragging the bubble to dismiss view.
 *
 * @param uiDevice the UI automator to get the bubble view [UiObject2]
 * @param wmHelper the [WindowManagerStateHelper]
 */
fun dismissBubbleAppViaBubbleView(uiDevice: UiDevice, wmHelper: WindowManagerStateHelper) {
    // Checks bubble is showing.
    wmHelper
        .StateSyncBuilder()
        .add(ConditionsFactory.isWMStateComplete())
        .withAppTransitionIdle()
        .withBubbleShown()
        .waitForAndVerify()

    // Drag the bubble icon to the position of dismiss view to dismiss bubble app.
    uiDevice.bubbleIcon?.run {
        drag(Point(uiDevice.displayWidth / 2, uiDevice.displayHeight), 1000)
    }
}

/**
 * Waits and verifies the bubble (represented as bubble icon or bubble bar) is gone.
 */
fun waitAndVerifyBubbleGone(wmHelper: WindowManagerStateHelper) {
    wmHelper
        .StateSyncBuilder()
        .add(ConditionsFactory.isWMStateComplete())
        .withAppTransitionIdle()
        .withBubbleGone()
        .waitForAndVerify()
}

/**
 * Launches as many bubble apps as a bubble stack or a bubble bar can contain and collapse.
 *
 * @param tapl the [LauncherInstrumentation]
 * @param wmHelper the [WindowManagerStateHelper]
 * @return the [Bubble] icon objects of the launched bubble apps
 */
fun launchMultipleBubbleAppsViaBubbleMenuAndCollapse(
    tapl: LauncherInstrumentation,
    wmHelper: WindowManagerStateHelper,
): List<Bubble> {
    // Go to all apps to launch app into a bubble.
    tapl.goHome().switchToAllApps()

    bubbleApps.forEach { testApp ->
        launchAndWaitForBubbleAppExpanded(testApp, tapl, wmHelper)
        if (testApp != bubbleApps.last()) {
            Root.get().expandedBubbleStack.closeByClickingOutside()
        }
    }

    assertBubbleIconsAligned(tapl)

    val expandedBubbleStack = Root.get().expandedBubbleStack
    val bubbles = expandedBubbleStack.bubbles
    expandedBubbleStack.closeByClickingOutside()

    return bubbles
}

/**
 * Dismisses all bubble apps launched by [launchMultipleBubbleAppsViaBubbleMenuAndCollapse].
 */
fun dismissMultipleBubbles() {
    bubbleApps.forEach { app -> app.exit() }
}

private fun assertBubbleIconsAligned(tapl: LauncherInstrumentation) {
    val isBubbleIconsAligned = Root.get().expandedBubbleStack.bubbles.stream()
        .mapToInt { bubbleIcon: Bubble ->
            if (tapl.isTablet && !Flags.enableBubbleBar()) {
                // For large screen devices without bubble bar, the bubble icons are aligned
                // vertically.
                bubbleIcon.visibleCenter.x
            } else {
                // Otherwise, the bubble icons are aligned horizontally.
                bubbleIcon.visibleCenter.y
            }
        }
        .distinct()
        .count() == 1L


    val bubblePositions = StringBuilder()
    if (!isBubbleIconsAligned) {
        Root.get().expandedBubbleStack.bubbles.forEach { bubble ->
            bubblePositions.append(
                "{${bubble.contentDescription()} center: ${bubble.visibleCenter}}, "
            )
        }
    }
    assertWithMessage("The bubble icons must be aligned, but was $bubblePositions")
        .that(isBubbleIconsAligned)
        .isTrue()
}

private fun launchAndWaitForBubbleAppExpanded(
    testApp: StandardAppHelper,
    tapl: LauncherInstrumentation,
    wmHelper: WindowManagerStateHelper,
) {
    val allApps = tapl.allApps
    val simpleAppIcon = allApps.getAppIcon(testApp.appName)
    // Open the bubble menu and click.
    simpleAppIcon.openMenu().bubbleMenuItem.click()

    waitAndAssertBubbleAppInExpandedState(testApp, wmHelper)

    // Don't check bubble icons if the testApp is IME because IME occludes the overflow.
    if (testApp !is ImeAppHelper) {
        assertWithMessage("The education must not show for Application bubble")
            .that(Root.get().bubble.isEducationVisible).isFalse()
    }
}

private fun waitAndAssertBubbleAppInExpandedState(
    testApp: StandardAppHelper,
    wmHelper: WindowManagerStateHelper,
) {
    wmHelper
        .StateSyncBuilder()
        .add(ConditionsFactory.isWMStateComplete())
        .withAppTransitionIdle()
        .withTopVisibleApp(testApp)
        .withBubbleShown()
        .waitForAndVerify()

    // Don't check the overflow if the testApp is IME because IME occludes the overflow.
    if (testApp !is ImeAppHelper) {
        Root.get().expandedBubbleStack.verifyBubbleOverflowIsVisible()
    }
}

private fun waitAndAssertBubbleAppInCollapseState(wmHelper: WindowManagerStateHelper) {
    wmHelper
        .StateSyncBuilder()
        .add(ConditionsFactory.isWMStateComplete())
        .withAppTransitionIdle()
        .withTopVisibleApp(LAUNCHER)
        .withBubbleShown()
        .waitForAndVerify()
}

private val UiDevice.bubbleIcon: UiObject2?
    get() = wait(Until.findObject(sysUiSelector(RES_ID_BUBBLE_VIEW)), FIND_OBJECT_TIMEOUT)

private val UiDevice.bubbleBar: UiObject2?
    get() = wait(Until.findObject(launcherSelector(RES_ID_BUBBLE_BAR)), FIND_OBJECT_TIMEOUT)

private fun sysUiSelector(resourcesId: String): BySelector =
    By.pkg(SYSUI_PACKAGE).res(SYSUI_PACKAGE, resourcesId)

private fun UiDevice.launcherSelector(resourcesId: String): BySelector =
    By.pkg(launcherPackageName).res(launcherPackageName, resourcesId)

private const val FIND_OBJECT_TIMEOUT = 4000L
private const val SYSUI_PACKAGE = "com.android.systemui"
private const val RES_ID_BUBBLE_VIEW = "bubble_view"
private const val RES_ID_BUBBLE_BAR = "taskbar_bubbles"

// TODO(b/396020056): The max number of bubbles is 5. Make the test more flexible
//  if the max number could be overridden.
private val bubbleApps = listOf(
    CalculatorAppHelper(),
    BrowserAppHelper(),
    MapsAppHelper(),
    MessagingAppHelper(),
    ClockAppHelper(),
)