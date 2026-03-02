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

package com.android.systemui.notifications.intelligence.rules.ui.composable

import android.content.res.Resources
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.android.systemui.notifications.intelligence.rules.shared.model.ActionModel
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.RulesScreenViewState
import com.android.systemui.res.R

/** Renders a fullscreen page to choose one rule action. */
@Composable
fun ActionChoiceScreen(
    viewState: RulesScreenViewState.EditField.Action,
    onDismissRequest: () -> Unit,
) {
    DropdownMenu(expanded = true, onDismissRequest = onDismissRequest) {
        for (action in ActionModel.entries) {
            DropdownMenuItem(
                text = { Text(text = action.toText(LocalResources.current)) },
                onClick = {
                    viewState.onActionSaved(action)
                    onDismissRequest.invoke()
                },
            )
        }
    }
}

fun ActionModel.toText(resources: Resources): String {
    val resourceId =
        when (this) {
            ActionModel.HighlightAndAlert -> R.string.notification_rules_action_highlight_and_alert
            ActionModel.Highlight -> R.string.notification_rules_action_highlight
            ActionModel.Silence -> R.string.notification_rules_action_silence
            ActionModel.Bundle -> R.string.notification_rules_action_bundle
            ActionModel.Block -> R.string.notification_rules_action_block
        }
    return resources.getString(resourceId)
}
