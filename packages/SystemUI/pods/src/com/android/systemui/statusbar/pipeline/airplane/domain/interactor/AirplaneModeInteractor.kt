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

package com.android.systemui.statusbar.pipeline.airplane.domain.interactor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** The business logic layer for airplane mode. */
public interface AirplaneModeInteractor {
    /** True if the device is currently in airplane mode. */
    public val isAirplaneMode: StateFlow<Boolean>

    /** True if we're configured to force-hide the airplane mode icon and false otherwise. */
    public val isForceHidden: Flow<Boolean>

    /** Sets airplane mode state returning the result of the operation. */
    public suspend fun setIsAirplaneMode(isInAirplaneMode: Boolean): SetResult

    public enum class SetResult {
        /** Airplane mode was set successfully. */
        SUCCESS,
        /**
         * Airplane mode couldn't be set because we're in ECM mode. See
         * [com.android.systemui.statusbar.pipeline.mobile.data.repository.MobileConnectionsRepository.isInEcmMode].
         */
        BLOCKED_BY_ECM,
    }
}
