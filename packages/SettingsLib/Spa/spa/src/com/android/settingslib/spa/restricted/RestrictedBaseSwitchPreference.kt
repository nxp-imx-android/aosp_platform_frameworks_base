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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.settingslib.spa.framework.common.SpaEnvironmentFactory
import com.android.settingslib.spa.widget.preference.SwitchPreferenceModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn

@Composable
internal fun rememberRestrictedRepository(): RestrictedRepository {
    val context = LocalContext.current
    val repository = remember {
        checkNotNull(SpaEnvironmentFactory.instance.getRestrictedRepository(context)) {
            "RestrictedRepository not set"
        }
    }
    return repository
}

@Composable
internal fun RestrictedBaseSwitchPreference(
    model: SwitchPreferenceModel,
    restrictions: Restrictions,
    repository: RestrictedRepository,
    ifBlockedOverrideCheckedTo: Boolean? = null,
    content: @Composable (SwitchPreferenceModel, Modifier) -> Unit,
) {
    if (restrictions.isEmpty()) {
        content(model, Modifier)
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

    restrictedSwitchPreferenceModel.RestrictionWrapper(content)
}
