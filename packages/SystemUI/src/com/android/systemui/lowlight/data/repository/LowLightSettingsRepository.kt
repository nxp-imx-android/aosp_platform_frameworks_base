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

package com.android.systemui.lowlight.data.repository

import android.content.pm.UserInfo
import android.content.res.Resources
import android.provider.Settings
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.lowlight.shared.model.LowLightDisplayBehavior
import com.android.systemui.util.kotlin.emitOnStart
import com.android.systemui.util.settings.SecureSettings
import com.android.systemui.util.settings.SettingsProxyExt.observerFlow
import com.android.systemui.utils.coroutines.flow.flatMapLatestConflated
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * The {@link LowLightSettingsRepository} provides access to settings associated with low-light
 * behavior, included whether it is enabled and the user chosen behavior. It also allows for setting
 * these values.
 */
interface LowLightSettingsRepository {
    /**
     * Returns a flow for tracking whether low-light behavior is enabled for the user. is a separate
     * setting as the user might have a preferred behavior, but temporarily disabled the
     * functionality.
     */
    fun getLowLightDisplayBehaviorEnabled(user: UserInfo): Flow<Boolean>

    /** Updates whether the user has low-light behavior enabled. */
    fun setLowLightDisplayBehaviorEnabled(user: UserInfo, enabled: Boolean)

    /**
     * Returns the chosen {@link LowLightDisplayBehavior} for the user. Note that enabled state is
     * tracked in {@link #getLowLightDisplayBehaviorEnabled}.
     */
    fun getLowLightDisplayBehavior(user: UserInfo): Flow<LowLightDisplayBehavior>

    /** Sets the {@link LowLightDisplayBehavior} for the given user. */
    fun setLowLightDisplayBehavior(user: UserInfo, behavior: LowLightDisplayBehavior)
}

class LowLightSettingsRepositoryImpl
@Inject
constructor(
    @Background private val bgDispatcher: CoroutineDispatcher,
    private val secureSettings: SecureSettings,
    @Main private val resources: Resources,
) : LowLightSettingsRepository {
    private val lowLightDisplayBehaviorEnabledDefault by lazy {
        resources.getBoolean(
            com.android.internal.R.bool.config_lowLightDisplayBehaviorEnabledDefault
        )
    }

    private val lowLightDisplayBehaviorDefault by lazy {
        resources.getInteger(com.android.internal.R.integer.config_lowLightDisplayBehaviorDefault)
    }

    override fun getLowLightDisplayBehaviorEnabled(user: UserInfo): Flow<Boolean> =
        secureSettings
            .observerFlow(
                userId = user.id,
                names = arrayOf(Settings.Secure.LOW_LIGHT_DISPLAY_BEHAVIOR_ENABLED),
            )
            .emitOnStart()
            .map {
                secureSettings.getBoolForUser(
                    Settings.Secure.LOW_LIGHT_DISPLAY_BEHAVIOR_ENABLED,
                    lowLightDisplayBehaviorEnabledDefault,
                    user.id,
                )
            }
            .flowOn(bgDispatcher)

    override fun setLowLightDisplayBehaviorEnabled(user: UserInfo, enabled: Boolean) {
        secureSettings.putBoolForUser(
            Settings.Secure.LOW_LIGHT_DISPLAY_BEHAVIOR_ENABLED,
            enabled,
            user.id,
        )
    }

    override fun getLowLightDisplayBehavior(user: UserInfo): Flow<LowLightDisplayBehavior> =
        getLowLightDisplayBehaviorEnabled(user)
            .flatMapLatestConflated { enabled ->
                if (enabled) {
                    secureSettings
                        .observerFlow(
                            userId = user.id,
                            names = arrayOf(Settings.Secure.LOW_LIGHT_DISPLAY_BEHAVIOR),
                        )
                        .emitOnStart()
                        .map {
                            secureSettings
                                .getIntForUser(
                                    Settings.Secure.LOW_LIGHT_DISPLAY_BEHAVIOR,
                                    lowLightDisplayBehaviorDefault,
                                    user.id,
                                )
                                .toLowLightDisplayBehavior()
                        }
                } else {
                    flowOf(LowLightDisplayBehavior.NONE)
                }
            }
            .flowOn(bgDispatcher)

    override fun setLowLightDisplayBehavior(user: UserInfo, behavior: LowLightDisplayBehavior) {
        secureSettings.putIntForUser(
            Settings.Secure.LOW_LIGHT_DISPLAY_BEHAVIOR,
            behavior.toSettingsInt(),
            user.id,
        )
    }

    private fun Int.toLowLightDisplayBehavior(): LowLightDisplayBehavior {
        return when (this) {
            Settings.Secure.LOW_LIGHT_DISPLAY_BEHAVIOR_NO_DREAM -> LowLightDisplayBehavior.NO_DREAM
            Settings.Secure.LOW_LIGHT_DISPLAY_BEHAVIOR_SCREEN_OFF ->
                LowLightDisplayBehavior.SCREEN_OFF
            Settings.Secure.LOW_LIGHT_DISPLAY_BEHAVIOR_LOW_LIGHT_CLOCK_DREAM ->
                LowLightDisplayBehavior.LOW_LIGHT_DREAM
            else -> LowLightDisplayBehavior.UNKNOWN
        }
    }

    private fun LowLightDisplayBehavior.toSettingsInt(): Int {
        return when (this) {
            LowLightDisplayBehavior.NO_DREAM -> Settings.Secure.LOW_LIGHT_DISPLAY_BEHAVIOR_NO_DREAM
            LowLightDisplayBehavior.SCREEN_OFF ->
                Settings.Secure.LOW_LIGHT_DISPLAY_BEHAVIOR_SCREEN_OFF
            LowLightDisplayBehavior.LOW_LIGHT_DREAM ->
                Settings.Secure.LOW_LIGHT_DISPLAY_BEHAVIOR_LOW_LIGHT_CLOCK_DREAM
            else -> lowLightDisplayBehaviorDefault
        }
    }
}
