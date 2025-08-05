/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.shade.domain.interactor

import android.content.Context
import android.provider.Settings
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.log.table.TableLogBuffer
import com.android.systemui.log.table.logDiffsForTable
import com.android.systemui.res.R
import com.android.systemui.scene.domain.SceneFrameworkTableLog
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.shade.data.repository.ShadeRepository
import com.android.systemui.shade.shared.model.ShadeMode
import com.android.systemui.shared.settings.data.repository.SecureSettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Defines interface for classes that can provide state and business logic related to the mode of
 * the shade.
 */
interface ShadeModeInteractor {

    /** The version of the shade layout to use. */
    val shadeMode: StateFlow<ShadeMode>

    /**
     * Whether the shade layout should be full width (true) or floating (false).
     *
     * In a floating (aka wide) layout, notifications and quick settings each take up only up to
     * half the screen width (whether they are shown at the same time or not).
     *
     * In a full width (aka narrow) layout, they can each be as wide as the entire screen.
     *
     * Note: In non-Dual-Shade modes, this value may be `true` even when the screen is wide.
     */
    val isFullWidthShade: StateFlow<Boolean>

    /** Convenience shortcut for querying whether the current [shadeMode] is [ShadeMode.Dual]. */
    val isDualShade: Boolean
        get() = shadeMode.value is ShadeMode.Dual

    /** Convenience shortcut for querying whether the current [shadeMode] is [ShadeMode.Split]. */
    val isSplitShade: Boolean
        get() = shadeMode.value is ShadeMode.Split
}

@OptIn(ExperimentalCoroutinesApi::class)
class ShadeModeInteractorImpl
@Inject
constructor(
    @Background applicationScope: CoroutineScope,
    @Background backgroundDispatcher: CoroutineDispatcher,
    @ShadeDisplayAware private val context: Context,
    repository: ShadeRepository,
    secureSettingsRepository: SecureSettingsRepository,
    @SceneFrameworkTableLog private val tableLogBuffer: TableLogBuffer,
) : ShadeModeInteractor {

    private val isDualShadeSettingEnabled: Flow<Boolean> =
        if (SceneContainerFlag.isEnabled) {
            secureSettingsRepository
                .boolSetting(Settings.Secure.DUAL_SHADE, defaultValue = DUAL_SHADE_ENABLED_DEFAULT)
                .flowOn(backgroundDispatcher)
        } else {
            flowOf(false)
        }

    private val isSplitShadeEnabled: Boolean =
        !SceneContainerFlag.isEnabled ||
            !context.resources.getBoolean(R.bool.config_disableSplitShade)

    override val isFullWidthShade: StateFlow<Boolean> =
        isDualShadeSettingEnabled
            .flatMapLatest { isDualShadeSettingEnabled ->
                if (isDualShadeSettingEnabled) {
                    repository.isWideScreen.map { !it }
                } else {
                    repository.legacyUseSplitShade.map { !it }
                }
            }
            .stateIn(
                applicationScope,
                SharingStarted.Eagerly,
                initialValue = !repository.isWideScreen.value,
            )

    private val shadeModeInitialValue: ShadeMode
        get() =
            determineShadeMode(
                isDualShadeSettingEnabled = DUAL_SHADE_ENABLED_DEFAULT,
                isFullWidthShade = isFullWidthShade.value,
            )

    override val shadeMode: StateFlow<ShadeMode> =
        combine(isDualShadeSettingEnabled, isFullWidthShade, ::determineShadeMode)
            .logDiffsForTable(tableLogBuffer = tableLogBuffer, initialValue = shadeModeInitialValue)
            .stateIn(applicationScope, SharingStarted.Eagerly, initialValue = shadeModeInitialValue)

    private fun determineShadeMode(
        isDualShadeSettingEnabled: Boolean,
        isFullWidthShade: Boolean,
    ): ShadeMode {
        return when {
            // Case 1: The Dual Shade setting has been enabled by the user.
            isDualShadeSettingEnabled -> ShadeMode.Dual

            // Case 2: Phone (in any orientation) or large screen in portrait, with Dual Shade
            // setting disabled.
            isFullWidthShade -> ShadeMode.Single

            // Case 3: Large screen in landscape orientation, with Dual Shade setting disabled.
            isSplitShadeEnabled -> ShadeMode.Split

            // Case 4: Large screen in landscape orientation, with both Dual Shade setting and Split
            // Shade disabled.
            else -> ShadeMode.Dual
        }
    }

    companion object {
        /* Whether the Dual Shade setting is enabled by default. */
        private const val DUAL_SHADE_ENABLED_DEFAULT = false
    }
}

class ShadeModeInteractorEmptyImpl @Inject constructor() : ShadeModeInteractor {

    override val shadeMode: StateFlow<ShadeMode> = MutableStateFlow(ShadeMode.Single)

    override val isFullWidthShade: StateFlow<Boolean> = MutableStateFlow(false)
}
