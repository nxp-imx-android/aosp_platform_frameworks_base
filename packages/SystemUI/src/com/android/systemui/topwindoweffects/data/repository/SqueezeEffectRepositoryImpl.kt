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
import android.util.DisplayUtils
import android.view.DisplayInfo
import androidx.annotation.ArrayRes
import androidx.annotation.DrawableRes
import com.android.systemui.common.coroutine.ChannelExt.trySendWithFailureLogging
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.res.R
import com.android.systemui.shared.Flags
import com.android.systemui.topwindoweffects.data.entity.SqueezeEffectCornerResourceId
import com.android.systemui.util.settings.GlobalSettings
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn

@SysUISingleton
class SqueezeEffectRepositoryImpl
@Inject
constructor(
    @Application private val context: Context,
    @Background private val bgHandler: Handler?,
    @Background private val bgCoroutineContext: CoroutineContext,
    private val globalSettings: GlobalSettings,
) : SqueezeEffectRepository, InvocationEffectSetUiHintsHandler {

    override val isSqueezeEffectEnabled: Flow<Boolean> =
        conflatedCallbackFlow {
                val observer =
                    object : ContentObserver(bgHandler) {
                        override fun onChange(selfChange: Boolean) {
                            trySendWithFailureLogging(
                                squeezeEffectEnabled,
                                TAG,
                                "updated isSqueezeEffectEnabled",
                            )
                        }
                    }
                trySendWithFailureLogging(squeezeEffectEnabled, TAG, "init isSqueezeEffectEnabled")
                globalSettings.registerContentObserverAsync(POWER_BUTTON_LONG_PRESS, observer)
                awaitClose { globalSettings.unregisterContentObserverAsync(observer) }
            }
            .flowOn(bgCoroutineContext)

    private val squeezeEffectEnabled
        get() =
            Flags.enableLppAssistInvocationEffect() &&
                globalSettings.getInt(
                    POWER_BUTTON_LONG_PRESS,
                    com.android.internal.R.integer.config_longPressOnPowerBehavior,
                ) == 5 // 5 corresponds to launch assistant in config_longPressOnPowerBehavior

    override suspend fun getRoundedCornersResourceId(): SqueezeEffectCornerResourceId {
        val displayInfo = DisplayInfo()
        context.display.getDisplayInfo(displayInfo)
        val displayIndex =
            DisplayUtils.getDisplayUniqueIdConfigIndex(context.resources, displayInfo.uniqueId)
        return SqueezeEffectCornerResourceId(
            top =
                getDrawableResource(
                    displayIndex = displayIndex,
                    arrayResId = R.array.config_roundedCornerTopDrawableArray,
                    backupDrawableId = R.drawable.rounded_corner_top,
                ),
            bottom =
                getDrawableResource(
                    displayIndex = displayIndex,
                    arrayResId = R.array.config_roundedCornerBottomDrawableArray,
                    backupDrawableId = R.drawable.rounded_corner_bottom,
                ),
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

    override fun tryHandleSetUiHints(hints: Bundle) = false

    companion object {
        private const val TAG = "SqueezeEffectRepository"
    }
}
