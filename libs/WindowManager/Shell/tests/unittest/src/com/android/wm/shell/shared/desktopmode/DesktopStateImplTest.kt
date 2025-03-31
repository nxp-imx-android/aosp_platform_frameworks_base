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

package com.android.wm.shell.shared.desktopmode

import android.os.SystemProperties
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.annotations.Presubmit
import android.window.DesktopModeFlags
import androidx.test.filters.SmallTest
import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn
import com.android.dx.mockito.inline.extended.StaticMockitoSession
import com.android.internal.R
import com.android.window.flags.Flags
import com.android.wm.shell.ShellTestCase
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.quality.Strictness

/**
 * Test class for [DesktopStateImpl].
 */
@SmallTest
@Presubmit
class DesktopStateImplTest : ShellTestCase() {

    private lateinit var mockitoSession: StaticMockitoSession

    @Before
    fun setUp() {
        mockitoSession =
            ExtendedMockito.mockitoSession()
                .strictness(Strictness.LENIENT)
                .mockStatic(SystemProperties::class.java)
                .startMocking()

        val resources = mContext.getOrCreateTestableResources()
        resources.apply {
            addOverride(R.bool.config_isDesktopModeSupported, false)
            addOverride(R.bool.config_isDesktopModeDevOptionSupported, false)
            addOverride(R.bool.config_canInternalDisplayHostDesktops, false)
        }
        setEnforceDeviceRestriction(true)
        setEnterDesktopByDefaultOnFreeformDisplay(false)

        resetDesktopModeFlagsCache()
        enableEnforceDeviceRestriction()
    }

    @After
    fun tearDown() {
        mockitoSession.finishMocking()
    }

    @DisableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE,
        Flags.FLAG_ENABLE_DESKTOP_MODE_THROUGH_DEV_OPTION,
    )
    @Test
    fun canEnterDesktopMode_DWFlagDisabled_deviceNotEligible_returnsFalse() {
        val desktopState = DesktopStateImpl(context)

        assertThat(desktopState.canEnterDesktopMode).isFalse()
    }

    @DisableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE,
        Flags.FLAG_ENABLE_DESKTOP_MODE_THROUGH_DEV_OPTION,
    )
    @Test
    fun canEnterDesktopMode_DWFlagDisabled_deviceEligible_configDevOptionOn_returnsFalse() {
        setDeviceEligibleForDesktopMode(true)
        setCanInternalDisplayHostDesktops(true)
        mContext
            .getOrCreateTestableResources()
            .addOverride(R.bool.config_isDesktopModeDevOptionSupported, true)
        disableEnforceDeviceRestriction()
        val desktopState = DesktopStateImpl(context)

        assertThat(desktopState.canEnterDesktopMode).isFalse()
    }

    @DisableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE,
        Flags.FLAG_ENABLE_DESKTOP_MODE_THROUGH_DEV_OPTION,
    )
    @Test
    fun canEnterDesktopMode_DWFlagDisabled_deviceNotEligible_configDevOptionOn_returnsFalse() {
        mContext
            .getOrCreateTestableResources()
            .addOverride(R.bool.config_isDesktopModeDevOptionSupported, true)
        val desktopState = DesktopStateImpl(context)

        assertThat(desktopState.canEnterDesktopMode).isFalse()
    }

    @DisableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE,
        Flags.FLAG_ENABLE_DESKTOP_MODE_THROUGH_DEV_OPTION,
    )
    @Test
    fun canEnterDesktopMode_DWFlagDisabled_deviceNotEligible_forceUsingDevOption_returnsTrue() {
        mContext
            .getOrCreateTestableResources()
            .addOverride(R.bool.config_isDesktopModeDevOptionSupported, true)
        setFlagOverride(DesktopModeFlags.ToggleOverride.OVERRIDE_ON)
        val desktopState = DesktopStateImpl(context)

        assertThat(desktopState.canEnterDesktopMode).isTrue()
    }

    @DisableFlags(Flags.FLAG_ENABLE_DESKTOP_MODE_THROUGH_DEV_OPTION)
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    @Test
    fun canEnterDesktopMode_DWFlagEnabled_deviceNotEligible_returnsFalse() {
        mContext
            .getOrCreateTestableResources()
            .addOverride(R.bool.config_isDesktopModeDevOptionSupported, true)
        val desktopState = DesktopStateImpl(context)

        assertThat(desktopState.canEnterDesktopMode).isFalse()
    }

    @DisableFlags(Flags.FLAG_ENABLE_DESKTOP_MODE_THROUGH_DEV_OPTION)
    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE,
        Flags.FLAG_ENABLE_PROJECTED_DISPLAY_DESKTOP_MODE,
    )
    @Test
    fun canEnterDesktopMode_DWFlagEnabled_deviceEligibleWithProjected_returnsTrue() {
        setDeviceEligibleForDesktopMode(true)
        setCanInternalDisplayHostDesktops(false)
        val desktopState = DesktopStateImpl(context)

        assertThat(desktopState.canEnterDesktopMode).isTrue()
    }

    @DisableFlags(
        Flags.FLAG_ENABLE_DESKTOP_MODE_THROUGH_DEV_OPTION,
        Flags.FLAG_ENABLE_PROJECTED_DISPLAY_DESKTOP_MODE,
    )
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    @Test
    fun canEnterDesktopMode_DWFlagEnabled_deviceEligibleWithInternalDisplay_returnsTrue() {
        setDeviceEligibleForDesktopMode(true)
        setCanInternalDisplayHostDesktops(true)
        val desktopState = DesktopStateImpl(context)

        assertThat(desktopState.canEnterDesktopMode).isTrue()
    }

    @DisableFlags(
        Flags.FLAG_ENABLE_DESKTOP_MODE_THROUGH_DEV_OPTION,
        Flags.FLAG_ENABLE_PROJECTED_DISPLAY_DESKTOP_MODE,
    )
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    @Test
    fun canEnterDesktopMode_DWFlagEnabled_deviceEligibleWithoutInternalDisplay_returnsFalse() {
        setDeviceEligibleForDesktopMode(true)
        setCanInternalDisplayHostDesktops(false)
        val desktopState = DesktopStateImpl(context)

        assertThat(desktopState.canEnterDesktopMode).isFalse()
    }

    @DisableFlags(Flags.FLAG_ENABLE_DESKTOP_MODE_THROUGH_DEV_OPTION)
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    @Test
    fun canEnterDesktopMode_DWFlagEnabled_deviceNotEligible_forceUsingDevOption_returnsTrue() {
        mContext
            .getOrCreateTestableResources()
            .addOverride(R.bool.config_isDesktopModeDevOptionSupported, true)
        setFlagOverride(DesktopModeFlags.ToggleOverride.OVERRIDE_ON)
        val desktopState = DesktopStateImpl(context)

        assertThat(desktopState.canEnterDesktopMode).isTrue()
    }

    @Test
    fun isDeviceEligibleForDesktopMode_configDEModeOnAndIntDispHostsDesktop_returnsTrue() {
        val resources = mContext.getOrCreateTestableResources()
        resources.addOverride(R.bool.config_isDesktopModeSupported, true)
        resources.addOverride(R.bool.config_canInternalDisplayHostDesktops, true)
        val desktopState = DesktopStateImpl(context)

        assertThat(desktopState.isDeviceEligibleForDesktopMode).isTrue()
    }

    @Test
    fun isDeviceEligibleForDesktopMode_configDEModeOffAndIntDispHostsDesktop_returnsFalse() {
        val resources = mContext.getOrCreateTestableResources()
        resources.addOverride(R.bool.config_isDesktopModeSupported, false)
        resources.addOverride(R.bool.config_canInternalDisplayHostDesktops, true)
        val desktopState = DesktopStateImpl(context)

        assertThat(desktopState.isDeviceEligibleForDesktopMode).isFalse()
    }

    @DisableFlags(Flags.FLAG_ENABLE_PROJECTED_DISPLAY_DESKTOP_MODE)
    @Test
    fun isDeviceEligibleForDesktopMode_configDEModeOnAndIntDispHostsDesktopOff_returnsFalse() {
        val resources = mContext.getOrCreateTestableResources()
        resources.addOverride(R.bool.config_isDesktopModeSupported, true)
        resources.addOverride(R.bool.config_canInternalDisplayHostDesktops, false)
        val desktopState = DesktopStateImpl(context)

        assertThat(desktopState.isDeviceEligibleForDesktopMode).isFalse()
    }

    @EnableFlags(Flags.FLAG_ENABLE_PROJECTED_DISPLAY_DESKTOP_MODE)
    @Test
    fun isPDDeviceEligibleForDesktopMode_configDEModeOnAndIntDispHostsDesktopOff_returnsTrue() {
        val resources = mContext.getOrCreateTestableResources()
        resources.addOverride(R.bool.config_isDesktopModeSupported, true)
        resources.addOverride(R.bool.config_canInternalDisplayHostDesktops, false)
        val desktopState = DesktopStateImpl(context)

        assertThat(desktopState.isDeviceEligibleForDesktopMode).isTrue()
    }

    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    @DisableFlags(Flags.FLAG_ENABLE_DESKTOP_MODE_THROUGH_DEV_OPTION)
    @Test
    fun isInternalDisplayEligibleToHostDesktops_supportFlagOff_returnsFalse() {
        val desktopState = DesktopStateImpl(context)

        assertThat(desktopState.isDeviceEligibleForDesktopMode).isFalse()
    }

    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_MODE_THROUGH_DEV_OPTION)
    @Test
    fun isInternalDisplayEligibleToHostDesktops_supportFlagOn_returnsFalse() {
        val desktopState = DesktopStateImpl(context)

        assertThat(desktopState.isDeviceEligibleForDesktopMode).isFalse()
    }

    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_MODE_THROUGH_DEV_OPTION)
    @Test
    fun isInternalDisplayEligibleToHostDesktops_supportFlagOn_configDevOptModeOn_returnsTrue() {
        mContext
            .getOrCreateTestableResources()
            .addOverride(R.bool.config_isDesktopModeDevOptionSupported, true)
        val desktopState = DesktopStateImpl(context)

        assertThat(desktopState.isDeviceEligibleForDesktopMode).isTrue()
    }

    @DisableFlags(Flags.FLAG_SHOW_DESKTOP_EXPERIENCE_DEV_OPTION)
    @Test
    fun canShowDesktopExperienceDevOption_flagDisabled_returnsFalse() {
        setDeviceEligibleForDesktopMode(true)
        val desktopState = DesktopStateImpl(context)

        assertThat(desktopState.canShowDesktopExperienceDevOption).isFalse()
    }

    @EnableFlags(Flags.FLAG_SHOW_DESKTOP_EXPERIENCE_DEV_OPTION)
    @Test
    fun canShowDesktopExperienceDevOption_flagEnabled_deviceNotEligible_returnsFalse() {
        val desktopState = DesktopStateImpl(context)

        assertThat(desktopState.canShowDesktopExperienceDevOption).isFalse()
    }

    @EnableFlags(
        Flags.FLAG_SHOW_DESKTOP_EXPERIENCE_DEV_OPTION,
        Flags.FLAG_ENABLE_PROJECTED_DISPLAY_DESKTOP_MODE,
    )
    @Test
    fun canShowDesktopExperienceDevOption_flagEnabled_deviceAndInternalDisplayEligible_returnsTrue() {
        setDeviceEligibleForDesktopMode(true)
        setCanInternalDisplayHostDesktops(true)
        val desktopState = DesktopStateImpl(context)

        assertThat(desktopState.canShowDesktopExperienceDevOption).isTrue()
    }

    @EnableFlags(Flags.FLAG_SHOW_DESKTOP_EXPERIENCE_DEV_OPTION)
    @DisableFlags(Flags.FLAG_ENABLE_PROJECTED_DISPLAY_DESKTOP_MODE)
    @Test
    fun canShowDesktopExperienceDevOption_flagEnabled_deviceEligibleNotInternalDisplay_returnsFalse() {
        setDeviceEligibleForDesktopMode(true)
        setCanInternalDisplayHostDesktops(false)
        val desktopState = DesktopStateImpl(context)

        assertThat(desktopState.canShowDesktopExperienceDevOption).isFalse()
    }

    @EnableFlags(
        Flags.FLAG_SHOW_DESKTOP_EXPERIENCE_DEV_OPTION,
        Flags.FLAG_ENABLE_PROJECTED_DISPLAY_DESKTOP_MODE,
    )
    @Test
    fun canShowDesktopExperienceDevOption_flagEnabled_deviceEligibleWithProjected_returnsTrue() {
        setDeviceEligibleForDesktopMode(true)
        setCanInternalDisplayHostDesktops(false)
        val desktopState = DesktopStateImpl(context)

        assertThat(desktopState.canShowDesktopExperienceDevOption).isTrue()
    }

    private fun enableEnforceDeviceRestriction() {
        setEnforceDeviceRestriction(true)
    }

    private fun disableEnforceDeviceRestriction() {
        setEnforceDeviceRestriction(false)
    }

    private fun setEnforceDeviceRestriction(value: Boolean) {
        doReturn(value).`when` {
            SystemProperties.getBoolean(
                eq(DesktopStateImpl.ENFORCE_DEVICE_RESTRICTIONS_SYS_PROP),
                any(),
            )
        }
    }

    private fun setEnterDesktopByDefaultOnFreeformDisplay(value: Boolean) {
        doReturn(value).`when` {
            SystemProperties.getBoolean(
                eq(DesktopStateImpl.ENTER_DESKTOP_BY_DEFAULT_ON_FREEFORM_DISPLAY_SYS_PROP),
                any(),
            )
        }
    }

    private fun resetDesktopModeFlagsCache() {
        val cachedToggleOverride =
            DesktopModeFlags::class.java.getDeclaredField("sCachedToggleOverride")
        cachedToggleOverride.isAccessible = true
        cachedToggleOverride.set(null, null)
    }

    private fun setFlagOverride(override: DesktopModeFlags.ToggleOverride) {
        val cachedToggleOverride =
            DesktopModeFlags::class.java.getDeclaredField("sCachedToggleOverride")
        cachedToggleOverride.isAccessible = true
        cachedToggleOverride.set(null, override)
    }

    private fun setDeviceEligibleForDesktopMode(eligible: Boolean) {
        mContext
            .getOrCreateTestableResources()
            .addOverride(R.bool.config_isDesktopModeSupported, eligible)
    }

    private fun setCanInternalDisplayHostDesktops(eligible: Boolean) {
        mContext
            .getOrCreateTestableResources()
            .addOverride(R.bool.config_canInternalDisplayHostDesktops, eligible)
    }
}
