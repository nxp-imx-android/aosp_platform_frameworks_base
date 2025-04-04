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

import android.app.role.OnRoleHoldersChangedListener
import android.app.role.RoleManager
import android.content.pm.UserInfo
import android.hardware.input.InputManager
import android.os.Bundle
import android.os.Handler
import android.os.UserHandle
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
import com.android.systemui.topwindoweffects.data.repository.SqueezeEffectRepositoryImpl.Companion.IS_INVOCATION_EFFECT_ENABLED_BY_ASSISTANT_DEFAULT_VALUE
import com.android.systemui.topwindoweffects.data.repository.SqueezeEffectRepositoryImpl.Companion.IS_INVOCATION_EFFECT_ENABLED_KEY
import com.android.systemui.topwindoweffects.data.repository.SqueezeEffectRepositoryImpl.Companion.SET_INVOCATION_EFFECT_PARAMETERS_ACTION
import com.android.systemui.user.data.repository.FakeUserRepository
import com.android.systemui.util.settings.FakeGlobalSettings
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.Executor
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.eq
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
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
    private val mainExecutor = Executor(Runnable::run)
    private val userRepository = FakeUserRepository()

    @Mock private lateinit var handler: Handler
    @Mock private lateinit var inputManager: InputManager
    @Mock private lateinit var roleManager: RoleManager

    private val onRoleHoldersChangedListener =
        ArgumentCaptor.forClass(OnRoleHoldersChangedListener::class.java)

    private val Kosmos.underTest by
        Kosmos.Fixture {
            SqueezeEffectRepositoryImpl(
                context = context,
                coroutineScope = testScope.backgroundScope,
                globalSettings = globalSettings,
                userRepository = userRepository,
                inputManager = inputManager,
                handler = handler,
                coroutineContext = testScope.testScheduler,
                roleManager = roleManager,
                executor = mainExecutor,
            )
        }

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
    }

    @DisableFlags(Flags.FLAG_ENABLE_LPP_ASSIST_INVOCATION_EFFECT)
    @Test
    fun testSqueezeEffectDisabled_FlagDisabled() =
        kosmos.runTest {
            globalSettings.putInt(POWER_BUTTON_LONG_PRESS, 5)
            underTest.tryHandleSetUiHints(createAssistantSettingBundle(true))

            val isSqueezeEffectEnabled by collectLastValue(underTest.isSqueezeEffectEnabled)
            assertThat(isSqueezeEffectEnabled).isFalse()
        }

    @EnableFlags(Flags.FLAG_ENABLE_LPP_ASSIST_INVOCATION_EFFECT)
    @Test
    fun testSqueezeEffectDisabled_GlobalSettingDisabled() =
        kosmos.runTest {
            underTest.tryHandleSetUiHints(createAssistantSettingBundle(true))
            globalSettings.putInt(POWER_BUTTON_LONG_PRESS, 0)

            val isSqueezeEffectEnabled by collectLastValue(underTest.isSqueezeEffectEnabled)
            assertThat(isSqueezeEffectEnabled).isFalse()
        }

    @EnableFlags(Flags.FLAG_ENABLE_LPP_ASSIST_INVOCATION_EFFECT)
    @Test
    fun testSqueezeEffectDisabled_AssistantSettingDisabled() =
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

    private suspend fun Kosmos.initUserAndAssistant(
        userInfos: List<UserInfo>,
        userIndex: Int,
        assistantName: String,
    ) {
        underTest // "poke" class to ensure it's initialized
        userRepository.setUserInfos(userInfos)
        userRepository.setSelectedUserInfo(userInfos[userIndex])
        verify(roleManager)
            .addOnRoleHoldersChangedListenerAsUser(
                eq(mainExecutor),
                onRoleHoldersChangedListener.capture(),
                eq(UserHandle.ALL),
            )
        `when`(
                roleManager.getRoleHoldersAsUser(
                    eq(RoleManager.ROLE_ASSISTANT),
                    eq(userInfos[userIndex].userHandle),
                )
            )
            .thenReturn(listOf(assistantName))
        onRoleHoldersChangedListener.value.onRoleHoldersChanged(
            RoleManager.ROLE_ASSISTANT,
            userInfos[userIndex].userHandle,
        )
    }

    @EnableFlags(Flags.FLAG_ENABLE_LPP_ASSIST_INVOCATION_EFFECT)
    @Test
    fun testAssistantEnabledStatusIsDefault_AssistantSwitched() =
        kosmos.runTest {
            initUserAndAssistant(userInfos, 0, "a")
            globalSettings.putInt(POWER_BUTTON_LONG_PRESS, 5)
            underTest.tryHandleSetUiHints(
                createAssistantSettingBundle(
                    !IS_INVOCATION_EFFECT_ENABLED_BY_ASSISTANT_DEFAULT_VALUE
                )
            )

            `when`(
                    roleManager.getRoleHoldersAsUser(
                        eq(RoleManager.ROLE_ASSISTANT),
                        eq(userInfos[0].userHandle),
                    )
                )
                .thenReturn(listOf("b"))
            onRoleHoldersChangedListener.value.onRoleHoldersChanged(
                RoleManager.ROLE_ASSISTANT,
                userInfos[0].userHandle,
            )

            val isSqueezeEffectEnabled by collectLastValue(underTest.isSqueezeEffectEnabled)
            assertThat(isSqueezeEffectEnabled)
                .isEqualTo(IS_INVOCATION_EFFECT_ENABLED_BY_ASSISTANT_DEFAULT_VALUE)
        }

    @EnableFlags(Flags.FLAG_ENABLE_LPP_ASSIST_INVOCATION_EFFECT)
    @Test
    fun testAssistantEnabledStatusIsDefault_UserSwitched() =
        kosmos.runTest {
            initUserAndAssistant(userInfos, 0, "a")
            globalSettings.putInt(POWER_BUTTON_LONG_PRESS, 5)
            underTest.tryHandleSetUiHints(
                createAssistantSettingBundle(
                    !IS_INVOCATION_EFFECT_ENABLED_BY_ASSISTANT_DEFAULT_VALUE
                )
            )

            userRepository.setSelectedUserInfo(userInfos[1])

            val isSqueezeEffectEnabled by collectLastValue(underTest.isSqueezeEffectEnabled)
            assertThat(isSqueezeEffectEnabled)
                .isEqualTo(IS_INVOCATION_EFFECT_ENABLED_BY_ASSISTANT_DEFAULT_VALUE)
        }

    @EnableFlags(Flags.FLAG_ENABLE_LPP_ASSIST_INVOCATION_EFFECT)
    @Test
    fun testAssistantEnabledStatusIsRetained_AssistantSwitchedBackAndForth() =
        kosmos.runTest {
            initUserAndAssistant(userInfos, 0, "a")
            globalSettings.putInt(POWER_BUTTON_LONG_PRESS, 5)
            underTest.tryHandleSetUiHints(
                createAssistantSettingBundle(
                    !IS_INVOCATION_EFFECT_ENABLED_BY_ASSISTANT_DEFAULT_VALUE
                )
            )

            `when`(
                    roleManager.getRoleHoldersAsUser(
                        eq(RoleManager.ROLE_ASSISTANT),
                        eq(UserHandle.CURRENT),
                    )
                )
                .thenReturn(listOf("b"))
            onRoleHoldersChangedListener.value.onRoleHoldersChanged(
                RoleManager.ROLE_ASSISTANT,
                UserHandle.CURRENT,
            )
            `when`(
                    roleManager.getRoleHoldersAsUser(
                        eq(RoleManager.ROLE_ASSISTANT),
                        eq(UserHandle.CURRENT),
                    )
                )
                .thenReturn(listOf("a"))
            onRoleHoldersChangedListener.value.onRoleHoldersChanged(
                RoleManager.ROLE_ASSISTANT,
                UserHandle.CURRENT,
            )

            val isSqueezeEffectEnabled by collectLastValue(underTest.isSqueezeEffectEnabled)
            assertThat(isSqueezeEffectEnabled)
                .isEqualTo(!IS_INVOCATION_EFFECT_ENABLED_BY_ASSISTANT_DEFAULT_VALUE)
        }

    @EnableFlags(Flags.FLAG_ENABLE_LPP_ASSIST_INVOCATION_EFFECT)
    @Test
    fun testAssistantEnabledStatusIsRetained_UserSwitchedBackAndForth() =
        kosmos.runTest {
            initUserAndAssistant(userInfos, 0, "a")
            globalSettings.putInt(POWER_BUTTON_LONG_PRESS, 5)
            underTest.tryHandleSetUiHints(
                createAssistantSettingBundle(
                    !IS_INVOCATION_EFFECT_ENABLED_BY_ASSISTANT_DEFAULT_VALUE
                )
            )

            userRepository.setSelectedUserInfo(userInfos[1])
            userRepository.setSelectedUserInfo(userInfos[0])

            val isSqueezeEffectEnabled by collectLastValue(underTest.isSqueezeEffectEnabled)
            assertThat(isSqueezeEffectEnabled)
                .isEqualTo(!IS_INVOCATION_EFFECT_ENABLED_BY_ASSISTANT_DEFAULT_VALUE)
        }

    companion object {
        private val userInfos =
            listOf(
                UserInfo().apply {
                    id = 0
                    name = "User 0"
                },
                UserInfo().apply {
                    id = 1
                    name = "User 1"
                },
            )
    }
}
