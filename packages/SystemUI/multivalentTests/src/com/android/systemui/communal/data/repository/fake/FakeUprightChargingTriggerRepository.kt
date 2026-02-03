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

package com.android.systemui.communal.data.repository.fake

import com.android.systemui.communal.data.repository.UprightChargingTriggerRepository
import kotlinx.coroutines.flow.MutableStateFlow

class FakeUprightChargingTriggerRepository : UprightChargingTriggerRepository {
    private val _isTriggered = MutableStateFlow(false)
    override val isTriggered = _isTriggered

    fun setTriggered(value: Boolean) {
        _isTriggered.value = value
    }
}
