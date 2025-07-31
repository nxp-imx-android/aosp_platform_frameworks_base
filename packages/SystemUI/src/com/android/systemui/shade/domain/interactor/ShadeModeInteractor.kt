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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/**
 * Defines interface for classes that can provide state and business logic related to the mode of
 * the shade.
 */
interface ShadeModeInteractor {

    /** The version of the shade layout to use. */
    val shadeMode: StateFlow<ShadeMode>

    /**
     * Whether the shade layout should be wide (true) or narrow (false).
     *
     * In a wide layout, notifications and quick settings each take up only half the screen width
     * (whether they are shown at the same time or not). In a narrow layout, they can each be as
     * wide as the entire screen.
     *
     * Note: When scene container is disabled, this returns `false` in some exceptional cases when
     * the screen would otherwise be considered wide. This is defined by the
     * `config_use_split_notification_shade` config value. In scene container such overrides are
     * deprecated, and this flow returns the same values as [DisplayStateInteractor.isWideScreen].
     */
    val isShadeLayoutWide: StateFlow<Boolean>

    /** Convenience shortcut for querying whether the current [shadeMode] is [ShadeMode.Dual]. */
    val isDualShade: Boolean
        get() = shadeMode.value is ShadeMode.Dual

    /** Convenience shortcut for querying whether the current [shadeMode] is [ShadeMode.Split]. */
    val isSplitShade: Boolean
        get() = shadeMode.value is ShadeMode.Split

    /** Whether the user has enabled the Dual Shade setting. */
    val isDualShadeSettingEnabled: Flow<Boolean>
}

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

    override val isDualShadeSettingEnabled: Flow<Boolean> =
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

    private val isLargeScreen: StateFlow<Boolean> = repository.isLargeScreen

    override val isShadeLayoutWide: StateFlow<Boolean> = repository.isShadeLayoutWide

    private val shadeModeInitialValue: ShadeMode
        get() =
            determineShadeMode(
                isDualShadeEnabled = DUAL_SHADE_ENABLED_DEFAULT,
                isShadeLayoutWide = isShadeLayoutWide.value,
                isLargeScreen = isLargeScreen.value,
            )

    override val shadeMode: StateFlow<ShadeMode> =
        combine(isDualShadeSettingEnabled, isShadeLayoutWide, isLargeScreen, ::determineShadeMode)
            .logDiffsForTable(tableLogBuffer = tableLogBuffer, initialValue = shadeModeInitialValue)
            .stateIn(applicationScope, SharingStarted.Eagerly, initialValue = shadeModeInitialValue)

    private fun determineShadeMode(
        isDualShadeEnabled: Boolean,
        isShadeLayoutWide: Boolean,
        isLargeScreen: Boolean,
    ): ShadeMode {
        return when {
            // Case 1: Legacy shade (pre-scene container).
            !SceneContainerFlag.isEnabled ->
                if (isShadeLayoutWide) ShadeMode.Split else ShadeMode.Single

            // Case 2: The Dual Shade setting has been enabled by the user.
            isDualShadeEnabled -> ShadeMode.Dual

            // Case 3: Large screen in landscape orientation, with Dual Shade setting disabled.
            isLargeScreen && isShadeLayoutWide ->
                if (isSplitShadeEnabled) ShadeMode.Split else ShadeMode.Dual

            // Case 4: Phone (in any orientation) or large screen in portrait, with Dual Shade
            // setting disabled.
            else -> ShadeMode.Single
        }
    }

    companion object {
        /* Whether the Dual Shade setting is enabled by default. */
        private const val DUAL_SHADE_ENABLED_DEFAULT = false
    }
}

class ShadeModeInteractorEmptyImpl @Inject constructor() : ShadeModeInteractor {

    override val shadeMode: StateFlow<ShadeMode> = MutableStateFlow(ShadeMode.Single)

    override val isShadeLayoutWide: StateFlow<Boolean> = MutableStateFlow(false)

    override val isDualShadeSettingEnabled: Flow<Boolean> = flowOf(false)
}
