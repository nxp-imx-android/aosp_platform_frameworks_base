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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.systemui.notifications.intelligence.rules.shared.model.ActionModel
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.RulesScreenViewState
import com.android.systemui.res.R

/** Renders the action on [viewModel.rule] as a clickable item. */
@Composable
fun EditableAction(
    action: ActionModel,
    onEnterEditField: (RulesScreenViewState.EditField) -> Unit,
    onActionSaved: (ActionModel) -> Unit,
) {
    Action(
        action,
        onClick = { onEnterEditField(RulesScreenViewState.EditField.Action(onActionSaved)) },
    )
}

/** Renders the given action as a non-clickable item. */
@Composable
fun ReadOnlyAction(action: ActionModel) {
    Action(action, onClick = null)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Action(action: ActionModel, onClick: (() -> Unit)?) {
    val actionColor = action.toColor()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(
            imageVector = action.toIcon(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier =
                Modifier.size(24.dp)
                    .background(actionColor, MaterialTheme.shapes.small)
                    .padding(2.dp),
        )
        Text(
            text = action.toText(),
            color = actionColor,
            style = MaterialTheme.typography.titleMediumEmphasized,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        onClick?.let {
            Button(onClick = onClick) {
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = actionColor)
            }
        }
    }
}

@Composable
fun ActionModel.toText(): String {
    val resourceId =
        when (this) {
            ActionModel.HighlightAndAlert -> R.string.notification_rules_action_highlight_and_alert
            ActionModel.Highlight -> R.string.notification_rules_action_highlight
            ActionModel.Silence -> R.string.notification_rules_action_silence
            ActionModel.Bundle -> R.string.notification_rules_action_bundle
            ActionModel.Block -> R.string.notification_rules_action_block
        }
    return stringResource(resourceId)
}

// TODO: b/307607958 - Load the real icons.
@Composable
fun ActionModel.toIcon(): ImageVector {
    return when (this) {
        ActionModel.HighlightAndAlert -> Icons.Filled.Star
        ActionModel.Highlight -> Icons.Filled.Star
        ActionModel.Silence -> Icons.Filled.Star
        ActionModel.Bundle -> Icons.Filled.Star
        ActionModel.Block -> Icons.Filled.Star
    }
}

@Composable
fun ActionModel.toColor(): Color {
    return when (this) {
        ActionModel.HighlightAndAlert -> Color.Cyan
        ActionModel.Highlight -> Color.Green
        ActionModel.Silence -> Color.Yellow
        ActionModel.Bundle -> Color.Magenta
        ActionModel.Block -> Color.Red
    }
}
