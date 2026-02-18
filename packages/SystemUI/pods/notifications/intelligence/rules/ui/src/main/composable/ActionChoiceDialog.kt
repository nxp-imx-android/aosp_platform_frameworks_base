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

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.android.systemui.notifications.intelligence.rules.shared.model.ActionModel

/**
 * Renders a dropdown menu to choose one rule action.
 *
 * @param onActionSelected invoked when the user selects an action in the menu.
 */
@Composable
fun ActionChoiceDialog(onDismissRequest: () -> Unit, onActionSelected: (ActionModel) -> Unit) {
    DropdownMenu(expanded = true, onDismissRequest = onDismissRequest) {
        for (action in ActionModel.entries) {
            DropdownMenuItem(
                // TODO: b/478225883 - Add translated strings describing the actions.
                text = { Text(text = action.name) },
                onClick = {
                    onActionSelected.invoke(action)
                    onDismissRequest.invoke()
                },
            )
        }
    }
}
