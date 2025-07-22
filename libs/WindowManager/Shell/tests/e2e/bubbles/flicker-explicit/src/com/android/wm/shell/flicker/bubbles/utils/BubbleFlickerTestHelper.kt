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

package com.android.wm.shell.flicker.bubbles.utils

import android.platform.systemui_tapl.ui.Bubble
import android.platform.systemui_tapl.ui.Root
import android.tools.device.apphelpers.BrowserAppHelper
import android.tools.device.apphelpers.CalculatorAppHelper
import android.tools.device.apphelpers.ClockAppHelper
import android.tools.device.apphelpers.MapsAppHelper
import android.tools.device.apphelpers.MessagingAppHelper
import android.tools.device.apphelpers.StandardAppHelper
import android.tools.traces.ConditionsFactory
import android.tools.traces.parsers.WindowManagerStateHelper
import androidx.annotation.IntRange
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.android.launcher3.tapl.AppIcon
import com.android.launcher3.tapl.LauncherInstrumentation
import com.android.server.wm.flicker.helpers.ImeAppHelper
import com.android.wm.shell.Flags
import com.android.wm.shell.flicker.utils.SplitScreenUtils
import com.google.common.truth.Truth.assertWithMessage

/**
 * A helper to build the bubble operations.
 */
internal object BubbleFlickerTestHelper {

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
        @IntRange(from = FIRST_APP_ICON_SOURCE.toLong(), to = LAST_APP_ICON_SOURCE.toLong())
        fromSource: Int = FROM_ALL_APPS,
    ) {
        val appName = testApp.appName
        // Go to all apps to launch app into a bubble.
        val appIcon = when (fromSource) {
            FROM_ALL_APPS -> tapl.goHome().switchToAllApps().getAppIcon(appName)
            FROM_TASK_BAR -> {
                SplitScreenUtils.createShortcutOnHotseatIfNotExist(tapl, appName)
                val overview = tapl.goHome().switchToOverview()
                val taskBar = overview.taskbar ?: error("Can't find TaskBar")
                taskBar.getAppIcon(testApp.appName)
            }
            FROM_HOME_SCREEN -> {
                val workspace = tapl.workspace
                val homeScreenIcon = workspace.tryGetWorkspaceAppIcon(testApp.appName)
                if (homeScreenIcon != null) {
                    // If there's an icon on the homeScreen, just use it.
                    homeScreenIcon
                } else {
                    // Here we do a trick:
                    // We move the app icon from all apps to hotseat, and then drag it to a new
                    // created empty page of home screen.
                    SplitScreenUtils.createShortcutOnHotseatIfNotExist(tapl, appName)
                    val hotseatIcon = workspace.getHotseatAppIcon(appName)
                    val pageDelta = workspace.pageCount - workspace.currentPage
                    workspace.dragIcon(hotseatIcon, pageDelta)
                    workspace.getWorkspaceAppIcon(appName)
                }
            }
            else -> error("Unknown fromSource: $fromSource")
        }
        launchAndWaitForBubbleAppExpanded(testApp, appIcon, wmHelper)
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
     * Launch bubble via clicking the overflow view.
     *
     * @param testApp the test app to launch into bubble
     * @param wmHelper the [WindowManagerStateHelper]
     */
    fun launchBubbleViaOverflow(testApp: StandardAppHelper, wmHelper: WindowManagerStateHelper) {
        val overflow = Root.get().expandedBubbleStack.openOverflow()
        overflow.verifyHasBubbles()
        overflow.openBubble()

        waitAndAssertBubbleAppInExpandedState(testApp, wmHelper)
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

        waitAndAssertBubbleAppInCollapseState(testApp, wmHelper)
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

        waitAndAssertBubbleAppInCollapseState(testApp, wmHelper)
    }

    /**
     * Expands the bubble app [testApp], which is previously collapsed via tapping on bubble stack.
     *
     * @param testApp the bubble app to expand
     * @param uiDevice the UI automator to get the bubble view [UiObject2]
     * @param wmHelper the [WindowManagerStateHelper]
     */
    fun expandBubbleAppViaTapOnBubbleStack(
        testApp: StandardAppHelper,
        uiDevice: UiDevice,
        wmHelper: WindowManagerStateHelper,
    ) {
        // Ensure Bubble is in collapse state.
        waitAndAssertBubbleAppInCollapseState(testApp, wmHelper)

        // Click bubble to expand
        uiDevice.bubbleIcon?.click() ?: error("Can't find bubble view")

        waitAndAssertBubbleAppInExpandedState(testApp, wmHelper)
    }

    /**
     * Expands the bubble app [testApp], which is previously collapsed via tapping on bubble bar.
     * Note that this method only works on device with bubble bar.
     *
     * @param testApp the bubble app to expand
     * @param uiDevice the UI automator to get the bubble bar [UiObject2]
     * @param wmHelper the [WindowManagerStateHelper]
     */
    fun expandBubbleAppViaBubbleBar(
        testApp: StandardAppHelper,
        uiDevice: UiDevice,
        wmHelper: WindowManagerStateHelper,
    ) {
        // Ensure Bubble is in collapse state.
        waitAndAssertBubbleAppInCollapseState(testApp, wmHelper)

        // Click bubble bar to expand
        uiDevice.bubbleBar?.click() ?: error("Can't find bubble bar")

        waitAndAssertBubbleAppInExpandedState(testApp, wmHelper)
    }

    /**
     * Dismisses the bubble app via dragging the bubble to dismiss view.
     *
     * @param wmHelper the [WindowManagerStateHelper]
     */
    fun dismissBubbleAppViaBubbleView(wmHelper: WindowManagerStateHelper) {
        // Checks bubble is showing.
        wmHelper
            .StateSyncBuilder()
            .add(ConditionsFactory.isWMStateComplete())
            .withAppTransitionIdle()
            .withBubbleShown()
            .waitForAndVerify()

        // Drag the bubble icon to the position of dismiss view to dismiss bubble app.
        Root.get().expandedBubbleStack.bubbles[0].dismiss()

        waitAndAssertBubbleAppDismissed(wmHelper)
    }

    /**
     * Dismisses the bubble app via dragging the bubble bar handle to dismiss view.
     *
     * @param testApp the bubble app to dismiss
     * @param wmHelper the [WindowManagerStateHelper]
     */
    fun dismissBubbleAppViaBubbleBarHandle(
        testApp: StandardAppHelper,
        wmHelper: WindowManagerStateHelper,
    ) {
        waitAndAssertBubbleAppInExpandedState(testApp, wmHelper)

        Root.get().expandedBubbleStack.bubbleBarHandle.dragToDismiss()

        waitAndAssertBubbleAppDismissed(wmHelper)
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

    fun launchMultipleBubbleAppsViaBubbleMenuAndCollapse(
        tapl: LauncherInstrumentation,
        wmHelper: WindowManagerStateHelper,
    ): List<Bubble> {
        // Go to all apps to launch app into a bubble.
        tapl.goHome().switchToAllApps()
        val allApps = tapl.allApps

        bubbleApps.forEach { testApp ->
            val appIcon = allApps.getAppIcon(testApp.appName)
            launchAndWaitForBubbleAppExpanded(testApp, appIcon, wmHelper)
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
        appIcon: AppIcon,
        wmHelper: WindowManagerStateHelper,
    ) {
        // Open the bubble menu and click.
        appIcon.openMenu().bubbleMenuItem.click()

        waitAndAssertBubbleAppInExpandedState(testApp, wmHelper)

        assertWithMessage("The education must not show for Application bubble")
            .that(Root.get().bubble.isEducationVisible).isFalse()
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

    private fun waitAndAssertBubbleAppInCollapseState(
        testApp: StandardAppHelper,
        wmHelper: WindowManagerStateHelper,
    ) {
        wmHelper
            .StateSyncBuilder()
            .add(ConditionsFactory.isWMStateComplete())
            .withAppTransitionIdle()
            .withWindowSurfaceDisappeared(testApp)
            .withBubbleShown()
            .waitForAndVerify()
    }

    private fun waitAndAssertBubbleAppDismissed(wmHelper: WindowManagerStateHelper) {
        wmHelper
            .StateSyncBuilder()
            .add(ConditionsFactory.isWMStateComplete())
            .withAppTransitionIdle()
            .withBubbleGone()
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

    /** Launches the bubble from all apps page. */
    const val FROM_ALL_APPS = 0
    /** Launches the bubble from home screen page. */
    const val FROM_HOME_SCREEN = 1
    /** Launches the bubble from the task bar. */
    const val FROM_TASK_BAR = 2

    private const val FIRST_APP_ICON_SOURCE = FROM_ALL_APPS
    private const val LAST_APP_ICON_SOURCE = FROM_TASK_BAR

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
}