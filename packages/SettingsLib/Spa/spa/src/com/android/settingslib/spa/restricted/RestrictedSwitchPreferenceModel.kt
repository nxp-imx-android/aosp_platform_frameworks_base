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

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import com.android.settingslib.spa.widget.preference.SwitchPreferenceModel

internal class RestrictedSwitchPreferenceModel(
    model: SwitchPreferenceModel,
    private val restrictedMode: RestrictedMode?,
    ifBlockedOverrideCheckedTo: Boolean?,
) : SwitchPreferenceModel {
    override val title = model.title

    override val checked =
        when {
            restrictedMode == null -> ({ null })
            restrictedMode is Blocked &&
                restrictedMode.canOverrideSwitchChecked &&
                ifBlockedOverrideCheckedTo != null -> ({ ifBlockedOverrideCheckedTo })
            else -> model.checked
        }

    override val summary = model.summary

    override val icon = model.icon

    override val changeable =
        when (restrictedMode) {
            NoRestricted -> model.changeable
            else -> ({ false })
        }

    override val onCheckedChange =
        when (restrictedMode) {
            NoRestricted -> model.onCheckedChange
            else -> null
        }

    @Composable
    fun RestrictionWrapper(content: @Composable (SwitchPreferenceModel, Modifier) -> Unit) {
        val modifier =
            when (restrictedMode) {
                is BlockedWithDetails -> {
                    Modifier.clickable(
                            role = Role.Switch,
                            onClick = { restrictedMode.showDetails() },
                        )
                        .semantics { this.toggleableState = toggleableState(checked()) }
                }

                else -> Modifier
            }
        content(this, modifier)
    }

    companion object {
        private fun toggleableState(value: Boolean?) =
            when (value) {
                true -> ToggleableState.On
                false -> ToggleableState.Off
                null -> ToggleableState.Indeterminate
            }
    }
}
