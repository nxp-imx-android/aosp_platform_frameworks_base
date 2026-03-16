/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.biometrics.domain.interactor

import android.graphics.PointF
import android.graphics.Rect
import android.util.Log
import com.android.systemui.Flags
import com.android.systemui.common.ui.domain.interactor.ConfigurationInteractor
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.shade.ShadeDisplayAware
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

@SysUISingleton
class PeripheralFpsInteractor
@Inject
constructor(
    @ShadeDisplayAware val configurationInteractor: ConfigurationInteractor,
    val fingerprintPropertyInteractor: FingerprintPropertyInteractor,
) {
    /** Whether fingerprint sensor location is peripheral, i.e. does not have display location. */
    val isSupported: Flow<Boolean>
        get() =
            if (Flags.standaloneFingerprintLockScreenUxFix()) {
                fingerprintPropertyInteractor.isPeripheralFps
            } else {
                flowOf(false)
            }

    /** Represents the sensor's location for the purpose of ripple effect origin. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val locationForRippleEffect: Flow<PointF>
        get() =
            if (Flags.standaloneFingerprintLockScreenUxFix()) {
                // TODO b/481645959: Return more specific locations for known peripheral locations.
                combine(
                    configurationInteractor.maxBounds,
                    configurationInteractor.scaleForResolution,
                ) { bounds, scale ->
                    calculateCenterPoint(bounds, scale)
                }
            } else {
                Log.w(
                    TAG,
                    "locationForRippleEffect should not be used when standalone_fingerprint_lock_screen_ux_fix is disabled",
                )
                flowOf(PointF(0f, 0f))
            }

    private fun calculateCenterPoint(bounds: Rect, scale: Float) =
        PointF(bounds.width() * 0.5f * scale, bounds.height() * 0.5f * scale)

    companion object {
        private const val TAG = "PeripheralFpsInteractor"
    }
}
