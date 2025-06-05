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

package com.android.settingslib.spa.restricted

import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.settingslib.spa.framework.common.SpaEnvironmentFactory
import com.android.settingslib.spa.widget.preference.SwitchPreference
import com.android.settingslib.spa.widget.preference.SwitchPreferenceModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn

/**
 * Restricted version [SwitchPreference].
 *
 * @param ifBlockedOverrideCheckedTo if this is not null and the current [RestrictedMode] is
 *   [Blocked] and [Blocked.canOverrideSwitchChecked] is set to true, the switch's checked status
 *   will be overridden to this value.
 */
@Composable
fun RestrictedSwitchPreference(
    model: SwitchPreferenceModel,
    restrictions: Restrictions,
    ifBlockedOverrideCheckedTo: Boolean? = null,
) {
    val context = LocalContext.current
    val repository = remember {
        checkNotNull(SpaEnvironmentFactory.instance.getRestrictedRepository(context)) {
            "RestrictedRepository not set"
        }
    }
    RestrictedSwitchPreference(model, restrictions, repository, ifBlockedOverrideCheckedTo)
}

@VisibleForTesting
@Composable
internal fun RestrictedSwitchPreference(
    model: SwitchPreferenceModel,
    restrictions: Restrictions,
    repository: RestrictedRepository,
    ifBlockedOverrideCheckedTo: Boolean? = null,
) {
    if (restrictions.isEmpty()) {
        SwitchPreference(model)
        return
    }
    val restrictedModeFlow =
        remember(restrictions) {
            repository.restrictedModeFlow(restrictions).flowOn(Dispatchers.Default)
        }
    val restrictedMode by
        restrictedModeFlow.collectAsStateWithLifecycle(initialValue = NoRestricted)
    val restrictedSwitchPreferenceModel =
        remember(restrictedMode, model, ifBlockedOverrideCheckedTo) {
            RestrictedSwitchPreferenceModel(
                model = model,
                restrictedMode = restrictedMode,
                ifBlockedOverrideCheckedTo = ifBlockedOverrideCheckedTo,
            )
        }

    restrictedSwitchPreferenceModel.RestrictionWrapper { SwitchPreference(it) }
}
