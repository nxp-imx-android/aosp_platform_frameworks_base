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

package com.android.systemui.topwindoweffects.data.repository

import android.hardware.input.InputManager
import android.os.Bundle
import android.os.Handler
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.provider.Settings.Global.POWER_BUTTON_LONG_PRESS
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.assist.AssistManager
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.shared.Flags
import com.android.systemui.testKosmos
import com.android.systemui.topwindoweffects.data.repository.SqueezeEffectRepositoryImpl.Companion.IS_INVOCATION_EFFECT_ENABLED_KEY
import com.android.systemui.topwindoweffects.data.repository.SqueezeEffectRepositoryImpl.Companion.SET_INVOCATION_EFFECT_PARAMETERS_ACTION
import com.android.systemui.util.settings.FakeGlobalSettings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations

private fun createAssistantSettingBundle(enableAssistantSetting: Boolean) =
    Bundle().apply {
        putString(AssistManager.ACTION_KEY, SET_INVOCATION_EFFECT_PARAMETERS_ACTION)
        putBoolean(IS_INVOCATION_EFFECT_ENABLED_KEY, enableAssistantSetting)
    }

@SmallTest
@RunWith(AndroidJUnit4::class)
class SqueezeEffectRepositoryTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val globalSettings = FakeGlobalSettings(StandardTestDispatcher())

    @Mock private lateinit var bgHandler: Handler
    @Mock private lateinit var inputManager: InputManager

    private val Kosmos.underTest by
        Kosmos.Fixture {
            SqueezeEffectRepositoryImpl(
                context = mContext,
                handler = bgHandler,
                coroutineContext = testScope.testScheduler,
                executor = Runnable::run,
                inputManager = inputManager,
                globalSettings = globalSettings,
            )
        }

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
    }

    @DisableFlags(Flags.FLAG_ENABLE_LPP_ASSIST_INVOCATION_EFFECT)
    @Test
    fun testSqueezeEffectDisabled_WhenOtherwiseEnabled_FlagDisabled() =
        kosmos.runTest {
            globalSettings.putInt(POWER_BUTTON_LONG_PRESS, 5)
            underTest.tryHandleSetUiHints(createAssistantSettingBundle(true))

            val isSqueezeEffectEnabled by collectLastValue(underTest.isSqueezeEffectEnabled)

            assertThat(isSqueezeEffectEnabled).isFalse()
        }

    @EnableFlags(Flags.FLAG_ENABLE_LPP_ASSIST_INVOCATION_EFFECT)
    @Test
    fun testSqueezeEffectDisabled_WhenOtherwiseEnabled_GlobalSettingDisabled() =
        kosmos.runTest {
            underTest.tryHandleSetUiHints(createAssistantSettingBundle(true))
            globalSettings.putInt(POWER_BUTTON_LONG_PRESS, 0)

            val isSqueezeEffectEnabled by collectLastValue(underTest.isSqueezeEffectEnabled)

            assertThat(isSqueezeEffectEnabled).isFalse()
        }

    @EnableFlags(Flags.FLAG_ENABLE_LPP_ASSIST_INVOCATION_EFFECT)
    @Test
    fun testSqueezeEffectDisabled_WhenOtherwiseEnabled_AssistantSettingDisabled() =
        kosmos.runTest {
            globalSettings.putInt(POWER_BUTTON_LONG_PRESS, 5)
            underTest.tryHandleSetUiHints(createAssistantSettingBundle(false))

            val isSqueezeEffectEnabled by collectLastValue(underTest.isSqueezeEffectEnabled)

            assertThat(isSqueezeEffectEnabled).isFalse()
        }

    @EnableFlags(Flags.FLAG_ENABLE_LPP_ASSIST_INVOCATION_EFFECT)
    @Test
    fun testSqueezeEffectEnabled_AllSettingsEnabled() =
        kosmos.runTest {
            globalSettings.putInt(POWER_BUTTON_LONG_PRESS, 5)
            underTest.tryHandleSetUiHints(createAssistantSettingBundle(true))

            val isSqueezeEffectEnabled by collectLastValue(underTest.isSqueezeEffectEnabled)

            assertThat(isSqueezeEffectEnabled).isTrue()
        }
}
