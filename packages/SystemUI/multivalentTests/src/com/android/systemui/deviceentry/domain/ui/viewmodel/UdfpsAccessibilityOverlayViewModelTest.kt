/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.deviceentry.domain.ui.viewmodel

import android.graphics.Point
import android.platform.test.flag.junit.FlagsParameterization
import android.view.MotionEvent
import android.view.View
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.accessibility.data.repository.fakeAccessibilityRepository
import com.android.systemui.biometrics.data.repository.fingerprintPropertyRepository
import com.android.systemui.biometrics.udfpsUtils
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.deviceentry.data.ui.viewmodel.alternateBouncerUdfpsAccessibilityOverlayViewModel
import com.android.systemui.deviceentry.data.ui.viewmodel.deviceEntryUdfpsAccessibilityOverlayViewModel
import com.android.systemui.deviceentry.ui.viewmodel.DeviceEntryUdfpsAccessibilityOverlayViewModel
import com.android.systemui.flags.Flags.FULL_SCREEN_USER_SWITCHER
import com.android.systemui.flags.andSceneContainer
import com.android.systemui.flags.fakeFeatureFlagsClassic
import com.android.systemui.keyguard.data.repository.deviceEntryFingerprintAuthRepository
import com.android.systemui.keyguard.data.repository.fakeBiometricSettingsRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepository
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.shared.model.TransitionState
import com.android.systemui.keyguard.shared.model.TransitionStep
import com.android.systemui.keyguard.ui.viewmodel.accessibilityActionsViewModelKosmos
import com.android.systemui.keyguard.ui.viewmodel.fakeDeviceEntryIconViewModelTransition
import com.android.systemui.kosmos.testScope
import com.android.systemui.res.R
import com.android.systemui.shade.shadeTestUtil
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
class UdfpsAccessibilityOverlayViewModelTest(flags: FlagsParameterization) : SysuiTestCase() {
    private val kosmos =
        testKosmos().apply {
            fakeFeatureFlagsClassic.apply { set(FULL_SCREEN_USER_SWITCHER, false) }
        }
    private val deviceEntryIconTransition = kosmos.fakeDeviceEntryIconViewModelTransition
    private val testScope = kosmos.testScope
    private val biometricSettingsRepository = kosmos.fakeBiometricSettingsRepository
    private val accessibilityRepository = kosmos.fakeAccessibilityRepository
    private val keyguardTransitionRepository = kosmos.fakeKeyguardTransitionRepository
    private val fingerprintPropertyRepository = kosmos.fingerprintPropertyRepository
    private val deviceEntryFingerprintAuthRepository = kosmos.deviceEntryFingerprintAuthRepository

    private val shadeTestUtil by lazy { kosmos.shadeTestUtil }

    private lateinit var underTest: DeviceEntryUdfpsAccessibilityOverlayViewModel

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<FlagsParameterization> {
            return FlagsParameterization.allCombinationsOf().andSceneContainer()
        }
    }

    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    @Before
    fun setup() {
        whenever(kosmos.udfpsUtils.isWithinSensorArea(any(), any(), any())).thenReturn(false)
        whenever(
                kosmos.udfpsUtils.getTouchInNativeCoordinates(anyInt(), any(), any(), anyBoolean())
            )
            .thenReturn(Point(0, 0))
        whenever(
                kosmos.udfpsUtils.onTouchOutsideOfSensorArea(
                    anyBoolean(),
                    eq(null),
                    anyInt(),
                    anyInt(),
                    any(),
                    anyBoolean(),
                )
            )
            .thenReturn("Move left")
        underTest = kosmos.deviceEntryUdfpsAccessibilityOverlayViewModel
        overrideResource(R.integer.udfps_padding_debounce_duration, 0)
    }

    @After
    fun teardown() {
        mContext.orCreateTestableResources.removeOverride(R.integer.udfps_padding_debounce_duration)
    }

    @Test
    fun visible() =
        testScope.runTest {
            val visible by collectLastValue(underTest.visible)
            setupVisibleStateOnLockscreen()
            assertThat(visible).isTrue()
        }

    @Test
    fun contentDescription_setOnUdfpsTouchOutsideSensorArea() =
        testScope.runTest {
            val contentDescription by collectLastValue(underTest.contentDescription)
            setupVisibleStateOnLockscreen()
            underTest.onHoverEvent(mock<View>(), mock<MotionEvent>())
            runCurrent()
            assertThat(contentDescription).isEqualTo("Move left")
        }

    @Test
    fun clearAccessibilityOverlayMessageReason_updatesWhenFocusChangesFromUdfpsOverlayToLockscreen() =
        testScope.runTest {
            val clearAccessibilityOverlayMessageReason by
                collectLastValue(underTest.clearAccessibilityOverlayMessageReason)
            val contentDescription by collectLastValue(underTest.contentDescription)
            setupVisibleStateOnLockscreen()
            kosmos.accessibilityActionsViewModelKosmos.clearUdfpsAccessibilityOverlayMessage("test")
            runCurrent()
            assertThat(clearAccessibilityOverlayMessageReason).isEqualTo("test")

            // UdfpsAccessibilityOverlayViewBinder collects clearAccessibilityOverlayMessageReason
            // and calls
            // viewModel.setContentDescription(null) - mock this here
            underTest.setContentDescription(null)
            runCurrent()
            assertThat(contentDescription).isNull()
        }

    @Test
    fun clearAccessibilityOverlayMessageReason_updatesAfterUdfpsOverlayFocusOnAlternateBouncer() =
        testScope.runTest {
            val clearAccessibilityOverlayMessageReason by
                collectLastValue(underTest.clearAccessibilityOverlayMessageReason)
            val contentDescription by collectLastValue(underTest.contentDescription)
            setupVisibleStateOnLockscreen()
            kosmos.alternateBouncerUdfpsAccessibilityOverlayViewModel
                .clearUdfpsAccessibilityOverlayMessage("test")
            runCurrent()
            assertThat(clearAccessibilityOverlayMessageReason).isEqualTo("test")

            // UdfpsAccessibilityOverlayViewBinder collects clearAccessibilityOverlayMessageReason
            // and calls
            // viewModel.setContentDescription(null) - mock this here
            underTest.setContentDescription(null)
            runCurrent()
            assertThat(contentDescription).isNull()
        }

    @Test
    fun touchExplorationNotEnabled_overlayNotVisible() =
        testScope.runTest {
            val visible by collectLastValue(underTest.visible)
            setupVisibleStateOnLockscreen()
            accessibilityRepository.isTouchExplorationEnabled.value = false
            assertThat(visible).isFalse()
        }

    @Test
    fun deviceEntryFgIconViewModelAod_overlayNotVisible() =
        testScope.runTest {
            val visible by collectLastValue(underTest.visible)
            setupVisibleStateOnLockscreen()

            // AOD
            keyguardTransitionRepository.sendTransitionSteps(
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.AOD,
                this,
            )
            runCurrent()
            assertThat(visible).isFalse()
        }

    fun fpNotRunning_overlayNotVisible() =
        testScope.runTest {
            val visible by collectLastValue(underTest.visible)
            setupVisibleStateOnLockscreen()
            deviceEntryFingerprintAuthRepository.setIsRunning(false)
            assertThat(visible).isFalse()
        }

    @Test
    fun deviceEntryViewAlpha0_overlayNotVisible() =
        testScope.runTest {
            val visible by collectLastValue(underTest.visible)
            setupVisibleStateOnLockscreen()
            deviceEntryIconTransition.setDeviceEntryParentViewAlpha(0f)
            assertThat(visible).isFalse()
        }

    private suspend fun setupVisibleStateOnLockscreen() {
        // A11y enabled
        accessibilityRepository.isTouchExplorationEnabled.value = true

        // Transition alpha is 1f
        deviceEntryIconTransition.setDeviceEntryParentViewAlpha(1f)

        // Listening for UDFPS
        fingerprintPropertyRepository.supportsUdfps()
        biometricSettingsRepository.setIsFingerprintAuthEnrolledAndEnabled(true)
        deviceEntryFingerprintAuthRepository.setIsRunning(true)

        // Lockscreen
        keyguardTransitionRepository.sendTransitionStep(
            TransitionStep(
                from = KeyguardState.AOD,
                to = KeyguardState.LOCKSCREEN,
                value = 0f,
                transitionState = TransitionState.STARTED,
            )
        )
        keyguardTransitionRepository.sendTransitionStep(
            TransitionStep(
                from = KeyguardState.AOD,
                to = KeyguardState.LOCKSCREEN,
                value = 1f,
                transitionState = TransitionState.FINISHED,
            )
        )

        // Shade not expanded
        shadeTestUtil.setQsExpansion(0f)
        shadeTestUtil.setLockscreenShadeExpansion(0f)
    }
}
