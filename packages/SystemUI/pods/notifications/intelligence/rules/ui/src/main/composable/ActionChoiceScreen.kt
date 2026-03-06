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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    Column(Modifier.fillMaxSize()) {
        Header(
            stringResource(R.string.notification_rules_choose_action_title),
            onDismissRequest = onDismissRequest,
        )
        DropdownMenu(expanded = true, onDismissRequest = onDismissRequest) {
            for (action in ActionModel.entries) {
                DropdownMenuItem(
                    text = { Text(text = action.toText()) },
                    onClick = {
                        viewState.onActionSaved(action)
                        onDismissRequest.invoke()
                    },
                )
            }
        }
    }
}
