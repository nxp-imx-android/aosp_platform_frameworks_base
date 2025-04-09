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

import android.content.Context
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.provider.Settings.Global.POWER_BUTTON_LONG_PRESS
import android.provider.Settings.Global.POWER_BUTTON_LONG_PRESS_DURATION_MS
import android.util.DisplayUtils
import android.view.DisplayInfo
import androidx.annotation.ArrayRes
import androidx.annotation.DrawableRes
import com.android.internal.annotations.VisibleForTesting
import com.android.systemui.assist.AssistManager
import com.android.systemui.common.coroutine.ChannelExt.trySendWithFailureLogging
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.res.R
import com.android.systemui.shared.Flags
import com.android.systemui.topwindoweffects.data.entity.SqueezeEffectCornersInfo
import com.android.systemui.util.settings.GlobalSettings
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

@VisibleForTesting
const val SET_INVOCATION_EFFECT_PARAMETERS_ACTION = "set_invocation_effect_parameters"
@VisibleForTesting const val IS_INVOCATION_EFFECT_ENABLED_KEY = "is_invocation_effect_enabled"
@VisibleForTesting const val DEFAULT_INITIAL_DELAY_MILLIS = 100L
@VisibleForTesting const val DEFAULT_LONG_PRESS_POWER_DURATION_MILLIS = 500L

@SysUISingleton
class SqueezeEffectRepositoryImpl
@Inject
constructor(
    @Application private val context: Context,
    @Background private val bgHandler: Handler?,
    @Background private val bgCoroutineContext: CoroutineContext,
    private val globalSettings: GlobalSettings,
) : SqueezeEffectRepository, InvocationEffectSetUiHintsHandler {

    private val isPowerButtonLongPressConfiguredToLaunchAssistantFlow: Flow<Boolean> =
        conflatedCallbackFlow {
                val observer =
                    object : ContentObserver(bgHandler) {
                        override fun onChange(selfChange: Boolean) {
                            trySendWithFailureLogging(
                                getIsPowerButtonLongPressConfiguredToLaunchAssistant(),
                                TAG,
                                "updated isPowerButtonLongPressConfiguredToLaunchAssistantFlow",
                            )
                        }
                    }
                trySendWithFailureLogging(
                    getIsPowerButtonLongPressConfiguredToLaunchAssistant(),
                    TAG,
                    "init isPowerButtonLongPressConfiguredToLaunchAssistantFlow",
                )
                globalSettings.registerContentObserverAsync(POWER_BUTTON_LONG_PRESS, observer)
                awaitClose { globalSettings.unregisterContentObserverAsync(observer) }
            }
            .flowOn(bgCoroutineContext)

    override suspend fun getInvocationEffectInitialDelayMs(): Long {
        val duration = getLongPressPowerDurationFromSettings()
        // TODO(b/408363187): adjust this difference for values lower than 500ms
        return if (duration > DEFAULT_LONG_PRESS_POWER_DURATION_MILLIS) {
            DEFAULT_INITIAL_DELAY_MILLIS + (duration - DEFAULT_LONG_PRESS_POWER_DURATION_MILLIS)
        } else {
            DEFAULT_INITIAL_DELAY_MILLIS
        }
    }

    override suspend fun getRoundedCornersInfo(): SqueezeEffectCornersInfo {
        val displayInfo = DisplayInfo()
        context.display.getDisplayInfo(displayInfo)
        val displayIndex =
            DisplayUtils.getDisplayUniqueIdConfigIndex(context.resources, displayInfo.uniqueId)
        val maxResDisplayMode =
            DisplayUtils.getMaximumResolutionDisplayMode(displayInfo.supportedModes)
        val ratio =
            if (maxResDisplayMode == null) {
                1f
            } else {
                DisplayUtils.getPhysicalPixelDisplaySizeRatio(
                    /*physicalWidth = */ maxResDisplayMode.physicalWidth,
                    /*physicalHeight = */ maxResDisplayMode.physicalHeight,
                    /*currentWidth = */ displayInfo.naturalWidth,
                    /*currentHeight = */ displayInfo.naturalHeight,
                )
            }
        return SqueezeEffectCornersInfo(
            topResourceId =
                getDrawableResource(
                    displayIndex = displayIndex,
                    arrayResId = R.array.config_roundedCornerTopDrawableArray,
                    backupDrawableId = R.drawable.rounded_corner_top,
                ),
            bottomResourceId =
                getDrawableResource(
                    displayIndex = displayIndex,
                    arrayResId = R.array.config_roundedCornerBottomDrawableArray,
                    backupDrawableId = R.drawable.rounded_corner_bottom,
                ),
            physicalPixelDisplaySizeRatio = ratio,
        )
    }

    @DrawableRes
    private fun getDrawableResource(
        displayIndex: Int,
        @ArrayRes arrayResId: Int,
        @DrawableRes backupDrawableId: Int,
    ): Int {
        val drawableResource: Int
        context.resources.obtainTypedArray(arrayResId).let { array ->
            drawableResource =
                if (displayIndex >= 0 && displayIndex < array.length()) {
                    array.getResourceId(displayIndex, backupDrawableId)
                } else {
                    backupDrawableId
                }
            array.recycle()
        }
        return drawableResource
    }

    private val isInvocationEffectEnabledForCurrentAssistantFlow = MutableStateFlow(true)

    override val isSqueezeEffectEnabled: Flow<Boolean> =
        combine(
            isPowerButtonLongPressConfiguredToLaunchAssistantFlow,
            isInvocationEffectEnabledForCurrentAssistantFlow,
        ) { prerequisites ->
            prerequisites.all { it } && Flags.enableLppAssistInvocationEffect()
        }

    private fun getIsPowerButtonLongPressConfiguredToLaunchAssistant() =
        globalSettings.getInt(
            POWER_BUTTON_LONG_PRESS,
            context.resources.getInteger(
                com.android.internal.R.integer.config_longPressOnPowerBehavior
            ),
        ) == 5 // 5 corresponds to launch assistant in PhoneWindowManager.java

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
                    isInvocationEffectEnabledForCurrentAssistantFlow.value =
                        hints.getBoolean(IS_INVOCATION_EFFECT_ENABLED_KEY)
                }
                true
            }
            else -> false
        }
    }

    companion object {
        private const val TAG = "SqueezeEffectRepository"
    }
}
