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

package com.android.systemui.dreams.data.repository

import android.content.pm.UserInfo
import android.content.res.Resources
import android.provider.Settings
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.dreams.shared.model.WhenToDream
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

interface DreamSettingsRepository {
    /** Returns when dreams are enabled. */
    fun getDreamsEnabled(user: UserInfo): Flow<Boolean>

    /**
     * Returns a [WhenToDream] for the specified user, indicating what state the device should be in
     * to trigger dreams.
     */
    fun getWhenToDreamState(user: UserInfo): Flow<WhenToDream>
}

@SysUISingleton
class DreamSettingsRepositoryImpl
@Inject
constructor(
    @Background private val bgDispatcher: CoroutineDispatcher,
    @Main private val resources: Resources,
    private val secureSettings: SecureSettings,
) : DreamSettingsRepository {
    private val dreamsEnabledByDefault by lazy {
        resources.getBoolean(com.android.internal.R.bool.config_dreamsEnabledByDefault)
    }

    private val dreamsActivatedOnSleepByDefault by lazy {
        resources.getBoolean(com.android.internal.R.bool.config_dreamsActivatedOnSleepByDefault)
    }

    private val dreamsActivatedOnDockByDefault by lazy {
        resources.getBoolean(com.android.internal.R.bool.config_dreamsActivatedOnDockByDefault)
    }

    private val dreamsActivatedOnPosturedByDefault by lazy {
        resources.getBoolean(com.android.internal.R.bool.config_dreamsActivatedOnPosturedByDefault)
    }

    override fun getDreamsEnabled(user: UserInfo): Flow<Boolean> =
        secureSettings
            .observerFlow(userId = user.id, names = arrayOf(Settings.Secure.SCREENSAVER_ENABLED))
            .emitOnStart()
            .map {
                secureSettings.getBoolForUser(
                    Settings.Secure.SCREENSAVER_ENABLED,
                    dreamsEnabledByDefault,
                    user.id,
                )
            }
            .flowOn(bgDispatcher)

    private fun getWhenToDreamSetting(user: UserInfo): Flow<WhenToDream> =
        secureSettings
            .observerFlow(
                userId = user.id,
                names =
                    arrayOf(
                        Settings.Secure.SCREENSAVER_ACTIVATE_ON_SLEEP,
                        Settings.Secure.SCREENSAVER_ACTIVATE_ON_DOCK,
                        Settings.Secure.SCREENSAVER_ACTIVATE_ON_POSTURED,
                    ),
            )
            .emitOnStart()
            .map {
                if (
                    secureSettings.getBoolForUser(
                        Settings.Secure.SCREENSAVER_ACTIVATE_ON_SLEEP,
                        dreamsActivatedOnSleepByDefault,
                        user.id,
                    )
                ) {
                    WhenToDream.WHILE_CHARGING
                } else if (
                    secureSettings.getBoolForUser(
                        Settings.Secure.SCREENSAVER_ACTIVATE_ON_DOCK,
                        dreamsActivatedOnDockByDefault,
                        user.id,
                    )
                ) {
                    WhenToDream.WHILE_DOCKED
                } else if (
                    secureSettings.getBoolForUser(
                        Settings.Secure.SCREENSAVER_ACTIVATE_ON_POSTURED,
                        dreamsActivatedOnPosturedByDefault,
                        user.id,
                    )
                ) {
                    WhenToDream.WHILE_POSTURED
                } else {
                    WhenToDream.NEVER
                }
            }
            .flowOn(bgDispatcher)

    override fun getWhenToDreamState(user: UserInfo): Flow<WhenToDream> =
        getDreamsEnabled(user)
            .flatMapLatestConflated { enabled ->
                if (enabled) {
                    getWhenToDreamSetting(user)
                } else {
                    flowOf(WhenToDream.NEVER)
                }
            }
            .flowOn(bgDispatcher)
}
