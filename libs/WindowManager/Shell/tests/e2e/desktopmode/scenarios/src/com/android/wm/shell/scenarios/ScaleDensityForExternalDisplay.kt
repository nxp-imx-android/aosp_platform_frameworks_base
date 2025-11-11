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

package com.android.wm.shell.scenarios

import android.app.ActivityManager
import android.app.Instrumentation
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.UserHandle
import android.platform.test.annotations.RequiresFlagsEnabled
import android.provider.Settings
import android.view.IWindowManager
import android.view.WindowManagerGlobal
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By.desc
import androidx.test.uiautomator.By.text
import androidx.test.uiautomator.Condition
import androidx.test.uiautomator.UiDevice
import com.android.compatibility.common.util.UiAutomatorUtils2.waitFindObject
import com.android.window.flags.Flags
import com.google.common.truth.Truth.assertThat
import junit.framework.Assert.fail
import org.junit.After
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import platform.test.desktop.SimulatedConnectedDisplayTestRule

@Ignore("Test Base Class")
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
abstract class ScaleDensityForExternalDisplay : TestScenarioBase() {

    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val wm: IWindowManager = requireNotNull(WindowManagerGlobal.getWindowManagerService())
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val displayManager = context.getSystemService(DisplayManager::class.java)
    private val activityManager: ActivityManager? =
        instrumentation.context.getSystemService(ActivityManager::class.java)

    private val settingsResources =
        instrumentation.context.packageManager.getResourcesForApplication(SETTINGS_PACKAGE_NAME)
    private val externalDisplaySettings = getSettingsString(EXTERNAL_DISPLAY_SETTING_RES)
    private val increaseDensityDescription = getSettingsString(INCREASE_DENSITY_DESCRIPTION_RES)
    private val decreaseDensityDescription = getSettingsString(DECREASE_DENSITY_DESCRIPTION_RES)

    @get:Rule val connectedDisplayRule = SimulatedConnectedDisplayTestRule()

    @Test
    fun increaseDensity() {
        val connectedDisplayId = connectedDisplayRule.setupTestDisplay()
        val displayName = displayManager.getDisplay(connectedDisplayId).name
        device.waitForIdle()
        wm.clearForcedDisplayDensityForUser(connectedDisplayId, UserHandle.myUserId())
        val initialDensity = wm.getBaseDisplayDensity(connectedDisplayId)

        instrumentation.context.startActivity(
            Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                .addCategory(Intent.CATEGORY_DEFAULT)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )

        waitFindObject(text(externalDisplaySettings)).click()
        waitFindObject(text(displayName)).click()
        waitFindObject(desc(increaseDensityDescription)).click()
        val currentDensity =
            waitForDensityCondition(
                connectedDisplayId,
                "Density Increased",
                predicate = { it > initialDensity },
            )

        assertThat(initialDensity).isLessThan(currentDensity)
    }

    @Test
    fun decreaseDensity() {
        val connectedDisplayId = connectedDisplayRule.setupTestDisplay()
        val displayName = displayManager.getDisplay(connectedDisplayId).name
        device.waitForIdle()
        wm.clearForcedDisplayDensityForUser(connectedDisplayId, UserHandle.myUserId())
        val initialDensity = wm.getBaseDisplayDensity(connectedDisplayId)

        instrumentation.context.startActivity(
            Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                .addCategory(Intent.CATEGORY_DEFAULT)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        waitFindObject(text(externalDisplaySettings)).click()
        waitFindObject(text(displayName)).click()
        waitFindObject(desc(decreaseDensityDescription)).click()
        val currentDensity =
            waitForDensityCondition(
                connectedDisplayId,
                "Density Decreased",
                predicate = { it < initialDensity },
            )

        assertThat(initialDensity).isGreaterThan(currentDensity)
    }

    @Test
    fun restoreDensityAfterReconnection() {
        var connectedDisplayId = connectedDisplayRule.setupTestDisplay()
        val displayName = displayManager.getDisplay(connectedDisplayId).name
        device.waitForIdle()
        wm.clearForcedDisplayDensityForUser(connectedDisplayId, UserHandle.myUserId())
        val initialDensity = wm.getBaseDisplayDensity(connectedDisplayId)

        instrumentation.context.startActivity(
            Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                .addCategory(Intent.CATEGORY_DEFAULT)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )

        waitFindObject(text(externalDisplaySettings)).click()
        waitFindObject(text(displayName)).click()
        waitFindObject(desc(increaseDensityDescription)).click()

        val lastDensity =
            waitForDensityCondition(
                connectedDisplayId,
                "Density Increased before disconnect",
                predicate = { it > initialDensity },
            )

        var idAfterReconnection = connectedDisplayRule.setupTestDisplay()
        device.waitForIdle()
        val densityAfterReconnection = wm.getBaseDisplayDensity(idAfterReconnection)

        assertThat(lastDensity).isEqualTo(densityAfterReconnection)
    }

    @After
    fun teardown() {
        activityManager?.forceStopPackage(SETTINGS_PACKAGE_NAME)
    }

    private fun getSettingsString(resName: String): String {
        val identifier = settingsResources.getIdentifier(resName, "string", SETTINGS_PACKAGE_NAME)
        return settingsResources.getString(identifier)
    }

    /**
     * Waits for the base display density of the given displayId to meet a predicate.
     *
     * @param displayId The ID of the display to check.
     * @param conditionName A descriptive name for the condition being waited for.
     * @param timeoutMs The maximum time to wait in milliseconds.
     * @param predicate A function that takes the current density and returns true if the condition
     *   is met.
     * @return The density value that met the condition.
     */
    private fun waitForDensityCondition(
        displayId: Int,
        conditionName: String,
        timeoutMs: Long = WAIT_FOR_DENSITY_TIMEOUT,
        predicate: (currentDensity: Int) -> Boolean,
    ): Int {
        var lastDensity = -1
        val densityCondition =
            object : Condition<UiDevice, Boolean> {
                override fun apply(device: UiDevice): Boolean {
                    try {
                        val currentDensity = wm.getBaseDisplayDensity(displayId)
                        lastDensity = currentDensity
                        return predicate(currentDensity)
                    } catch (e: Exception) {
                        return false
                    }
                }
            }

        val fulfilled = device.wait(densityCondition, timeoutMs)
        if (!fulfilled) {
            fail(
                "Condition '$conditionName' not met within $timeoutMs ms for display $displayId. " +
                    "Last density: $lastDensity"
            )
        }
        return lastDensity
    }

    private companion object {
        const val SETTINGS_PACKAGE_NAME = "com.android.settings"
        const val EXTERNAL_DISPLAY_SETTING_RES = "external_display_settings_title"
        const val INCREASE_DENSITY_DESCRIPTION_RES = "screen_zoom_make_larger_desc"
        const val DECREASE_DENSITY_DESCRIPTION_RES = "screen_zoom_make_smaller_desc"
        const val WAIT_FOR_DENSITY_TIMEOUT: Long = 3000
    }
}
