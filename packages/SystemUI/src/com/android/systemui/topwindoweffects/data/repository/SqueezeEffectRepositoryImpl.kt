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

import android.annotation.SuppressLint
import android.app.role.OnRoleHoldersChangedListener
import android.app.role.RoleManager
import android.content.Context
import android.content.SharedPreferences
import android.hardware.input.InputManager
import android.hardware.input.KeyGestureEvent
import android.os.Bundle
import android.os.UserHandle
import android.provider.Settings.Global.POWER_BUTTON_LONG_PRESS_DURATION_MS
import androidx.core.content.edit
import com.android.internal.annotations.VisibleForTesting
import com.android.systemui.assist.AssistManager
import com.android.systemui.common.coroutine.ChannelExt.trySendWithFailureLogging
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.shared.Flags
import com.android.systemui.user.data.repository.UserRepository
import com.android.systemui.util.settings.GlobalSettings
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import com.android.systemui.utils.coroutines.flow.mapLatestConflated
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@SysUISingleton
class SqueezeEffectRepositoryImpl
@Inject
constructor(
    @Application private val context: Context,
    @Background private val coroutineScope: CoroutineScope,
    private val globalSettings: GlobalSettings,
    private val userRepository: UserRepository,
    private val inputManager: InputManager,
    @Background coroutineContext: CoroutineContext,
    roleManager: RoleManager,
    @Background executor: Executor,
) : SqueezeEffectRepository, InvocationEffectSetUiHintsHandler {

    private val sharedPreferences by lazy {
        context.getSharedPreferences(SHARED_PREFERENCES_FILE_NAME, Context.MODE_PRIVATE)
    }

    private val selectedAssistantName: StateFlow<String> =
        conflatedCallbackFlow {
                val listener = OnRoleHoldersChangedListener { roleName, _ ->
                    if (roleName == RoleManager.ROLE_ASSISTANT) {
                        trySendWithFailureLogging(
                            roleManager.getCurrentAssistantFor(userRepository.selectedUserHandle),
                            TAG,
                            "updated currentlyActiveAssistantName due to role change",
                        )
                    }
                }
                roleManager.addOnRoleHoldersChangedListenerAsUser(
                    executor,
                    listener,
                    UserHandle.ALL,
                )

                launch {
                    userRepository.selectedUser.collect {
                        trySendWithFailureLogging(
                            roleManager.getCurrentAssistantFor(userRepository.selectedUserHandle),
                            TAG,
                            "updated currentlyActiveAssistantName due to user change",
                        )
                    }
                }

                awaitClose {
                    roleManager.removeOnRoleHoldersChangedListenerAsUser(listener, UserHandle.ALL)
                }
            }
            .flowOn(coroutineContext)
            .stateIn(
                scope = coroutineScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = roleManager.getCurrentAssistantFor(userRepository.selectedUserHandle),
            )

    private val isInvocationEffectEnabledByAssistantFlow: Flow<Boolean> =
        conflatedCallbackFlow {
                val listener =
                    SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                        if (key == IS_INVOCATION_EFFECT_ENABLED_BY_ASSISTANT_PREFERENCE) {
                            trySendWithFailureLogging(
                                loadIsInvocationEffectEnabledByAssistant(),
                                TAG,
                                "updated isInvocationEffectEnabledByAssistantFlow due to enabled status change",
                            )
                        }
                    }
                sharedPreferences.registerOnSharedPreferenceChangeListener(listener)

                coroutineScope.launch {
                    userRepository.selectedUser.collect {
                        trySendWithFailureLogging(
                            loadIsInvocationEffectEnabledByAssistant(),
                            TAG,
                            "updated isInvocationEffectEnabledByAssistantFlow due to user change",
                        )
                    }
                }

                coroutineScope.launch {
                    selectedAssistantName.collect {
                        trySendWithFailureLogging(
                            loadIsInvocationEffectEnabledByAssistant(),
                            TAG,
                            "updated isInvocationEffectEnabledByAssistantFlow due to assistant change",
                        )
                    }
                }

                awaitClose {
                    sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
                }
            }
            .flowOn(coroutineContext)

    override suspend fun getInvocationEffectInitialDelayMs(): Long {
        val duration = getLongPressPowerDurationFromSettings()
        return if (duration > DEFAULT_LONG_PRESS_POWER_DURATION_MILLIS) {
            DEFAULT_INITIAL_DELAY_MILLIS + (duration - DEFAULT_LONG_PRESS_POWER_DURATION_MILLIS)
        } else {
            DEFAULT_INITIAL_DELAY_MILLIS
        }
    }

    override suspend fun getInvocationEffectInwardsAnimationDurationMs(): Long {
        val duration = getLongPressPowerDurationFromSettings()
        return if (duration < DEFAULT_LONG_PRESS_POWER_DURATION_MILLIS) {
            DEFAULT_INWARD_EFFECT_DURATION - (DEFAULT_LONG_PRESS_POWER_DURATION_MILLIS - duration)
        } else {
            DEFAULT_INWARD_EFFECT_DURATION.toLong()
        }
    }

    private fun loadIsInvocationEffectEnabledByAssistant(): Boolean {
        val persistedForUser =
            sharedPreferences.getInt(
                PERSISTED_FOR_USER_PREFERENCE,
                PERSISTED_FOR_USER_DEFAULT_VALUE,
            )

        val persistedForAssistant =
            sharedPreferences.getString(
                PERSISTED_FOR_ASSISTANT_PREFERENCE,
                PERSISTED_FOR_ASSISTANT_DEFAULT_VALUE,
            )

        return if (
            persistedForUser == userRepository.selectedUserHandle.identifier &&
                persistedForAssistant == selectedAssistantName.value
        ) {
            sharedPreferences.getBoolean(
                IS_INVOCATION_EFFECT_ENABLED_BY_ASSISTANT_PREFERENCE,
                IS_INVOCATION_EFFECT_ENABLED_BY_ASSISTANT_DEFAULT_VALUE,
            )
        } else {
            IS_INVOCATION_EFFECT_ENABLED_BY_ASSISTANT_DEFAULT_VALUE
        }
    }

    private val isSqueezeEffectEnabled: Flow<Boolean> =
        isInvocationEffectEnabledByAssistantFlow.mapLatestConflated {
            Flags.enableLppAssistInvocationEffect() && it
        }

    override val isSqueezeEffectHapticEnabled = Flags.enableLppAssistInvocationHapticEffect()

    private fun getLongPressPowerDurationFromSettings() =
        globalSettings
            .getInt(
                POWER_BUTTON_LONG_PRESS_DURATION_MS,
                context.resources.getInteger(
                    com.android.internal.R.integer.config_longPressOnPowerDurationMs
                ),
            )
            .toLong()

    override fun tryHandleSetUiHints(hints: Bundle): Boolean {
        return when (hints.getString(AssistManager.ACTION_KEY)) {
            SET_INVOCATION_EFFECT_PARAMETERS_ACTION -> {
                if (hints.containsKey(IS_INVOCATION_EFFECT_ENABLED_KEY)) {
                    setIsInvocationEffectEnabledByAssistant(
                        hints.getBoolean(IS_INVOCATION_EFFECT_ENABLED_KEY)
                    )
                }
                true
            }
            else -> false
        }
    }

    private fun setIsInvocationEffectEnabledByAssistant(enabled: Boolean) {
        coroutineScope.launch {
            sharedPreferences.edit {
                putBoolean(IS_INVOCATION_EFFECT_ENABLED_BY_ASSISTANT_PREFERENCE, enabled)
                putString(PERSISTED_FOR_ASSISTANT_PREFERENCE, selectedAssistantName.value)
                putInt(PERSISTED_FOR_USER_PREFERENCE, userRepository.selectedUserHandle.identifier)
            }
        }
    }

    private val _isPowerButtonLongPressed = MutableStateFlow(false)
    override val isPowerButtonLongPressed = _isPowerButtonLongPressed.asStateFlow()

    private var isPowerButtonDownAndPowerKeySingleGestureActive = false

    @SuppressLint("MissingPermission")
    override val isEffectEnabledAndPowerButtonPressedAsSingleGesture: Flow<Boolean> =
        conflatedCallbackFlow {
                val listener =
                    InputManager.KeyGestureEventListener { event ->
                        updateIsPowerButtonDownAndSingleGestureActive(event)
                        trySendWithFailureLogging(
                            isPowerButtonDownAndPowerKeySingleGestureActive,
                            TAG,
                            "updated showInvocationEffect",
                        )
                    }
                trySendWithFailureLogging(false, TAG, "init showInvocationEffect")
                inputManager.registerKeyGestureEventListener(executor, listener)
                awaitClose { inputManager.unregisterKeyGestureEventListener(listener) }
            }
            .combine(isSqueezeEffectEnabled) { downAndActive, enabled -> downAndActive && enabled }
            .flowOn(coroutineContext)
            .distinctUntilChanged()

    private fun updateIsPowerButtonDownAndSingleGestureActive(event: KeyGestureEvent) {
        _isPowerButtonLongPressed.value =
            if (event.isGestureTypeAssistant()) {
                event.isGestureComplete()
            } else {
                _isPowerButtonLongPressed.value && isPowerButtonDownAndPowerKeySingleGestureActive
            }

        isPowerButtonDownAndPowerKeySingleGestureActive =
            if (isPowerButtonDownAndPowerKeySingleGestureActive) {
                !(event.isGestureTypeAssistant() &&
                    (event.isGestureCancelled() || event.isGestureComplete()))
            } else {
                event.isGestureTypeAssistant() && event.isGestureStart()
            }
    }

    companion object {
        private const val TAG = "SqueezeEffectRepository"

        /**
         * Current default timeout for detecting key combination is 150ms (as mentioned in
         * [KeyCombinationManager.COMBINE_KEY_DELAY_MILLIS]). Power key combinations don't have any
         * specific value defined yet for this timeout and they use this default timeout 150ms.
         * We're keeping this value of initial delay as 150ms because:
         * 1. Invocation effect doesn't show up in screenshots
         * 2. [TopLevelWindowEffects] window isn't created if power key combination is detected
         */
        @VisibleForTesting const val DEFAULT_INITIAL_DELAY_MILLIS = 150L
        @VisibleForTesting const val DEFAULT_LONG_PRESS_POWER_DURATION_MILLIS = 500L
        @VisibleForTesting const val DEFAULT_INWARD_EFFECT_DURATION = 800 // in milliseconds
        const val DEFAULT_OUTWARD_EFFECT_DURATION = 333 // in milliseconds

        @VisibleForTesting
        const val SET_INVOCATION_EFFECT_PARAMETERS_ACTION = "set_invocation_effect_parameters"
        @VisibleForTesting
        const val IS_INVOCATION_EFFECT_ENABLED_KEY = "is_invocation_effect_enabled"

        @VisibleForTesting const val IS_INVOCATION_EFFECT_ENABLED_BY_ASSISTANT_DEFAULT_VALUE = true
        private const val PERSISTED_FOR_USER_DEFAULT_VALUE = Integer.MIN_VALUE
        private const val PERSISTED_FOR_ASSISTANT_DEFAULT_VALUE = ""

        @VisibleForTesting
        const val SHARED_PREFERENCES_FILE_NAME = "assistant_invocation_effect_preferences"
        @VisibleForTesting
        const val IS_INVOCATION_EFFECT_ENABLED_BY_ASSISTANT_PREFERENCE =
            "is_invocation_effect_enabled"
        private const val PERSISTED_FOR_ASSISTANT_PREFERENCE = "persisted_for_assistant"
        private const val PERSISTED_FOR_USER_PREFERENCE = "persisted_for_user"
    }
}

private val UserRepository.selectedUserHandle
    get() = selectedUser.value.userInfo.userHandle

private fun RoleManager.getCurrentAssistantFor(userHandle: UserHandle) =
    getRoleHoldersAsUser(RoleManager.ROLE_ASSISTANT, userHandle)?.firstOrNull() ?: ""

private fun KeyGestureEvent.isGestureTypeAssistant() =
    this.keyGestureType == KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_ASSISTANT

private fun KeyGestureEvent.isGestureComplete() =
    this.action == KeyGestureEvent.ACTION_GESTURE_COMPLETE && !this.isCancelled

private fun KeyGestureEvent.isGestureCancelled() =
    this.action == KeyGestureEvent.ACTION_GESTURE_COMPLETE && this.isCancelled

private fun KeyGestureEvent.isGestureStart() = this.action == KeyGestureEvent.ACTION_GESTURE_START
