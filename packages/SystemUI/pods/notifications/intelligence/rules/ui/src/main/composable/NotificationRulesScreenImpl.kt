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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.notifications.intelligence.rules.shared.model.DraftRuleModel
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.NotificationRuleEditViewModel
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.NotificationRuleFreeformTextCreationViewModel
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.NotificationRulesScreenViewModel
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.RulesScreenViewState
import javax.inject.Inject

class NotificationRulesScreenImpl @Inject constructor() : NotificationRulesScreen {
    @Composable
    override fun Content(
        viewModelFactory: NotificationRulesScreenViewModel.Factory,
        editViewModelFactory: NotificationRuleEditViewModel.Factory,
        freeformTextViewModelFactory: NotificationRuleFreeformTextCreationViewModel.Factory,
        dismissRulesScreen: () -> Unit,
        startingBackStack: List<RulesScreenViewState>,
        modifier: Modifier,
    ) {
        val scope = rememberCoroutineScope()

        // TODO: b/486844997 - When the new platform drop for androidx.compose.animation is in, this
        // can be replaced by the navigation3 library, which will also support back gestures.
        val backStack = remember {
            mutableStateListOf<RulesScreenViewState>().apply { addAll(startingBackStack) }
        }
        val screenViewModel =
            rememberViewModel("NotificationRulesScreen") { viewModelFactory.create(backStack) }

        val onNavigateToEditScreen: (DraftRuleModel) -> Unit = { draftRule ->
            val newState = RulesScreenViewState.EditRule(editViewModelFactory.create(draftRule))
            backStack.add(newState)
        }
        val onEnterEditField: (RulesScreenViewState.EditField) -> Unit = { backStack.add(it) }
        val onDismissRequest: () -> Unit = { backStack.removeLast() }

        Box(modifier = modifier) {
            when (val viewState = screenViewModel.currentScreen) {
                is RulesScreenViewState.CurrentRules -> {
                    CurrentRulesScreen(
                        viewModel = screenViewModel,
                        onDismissCurrentRulesScreen = dismissRulesScreen,
                        onNavigateToEditScreen = onNavigateToEditScreen,
                        onNavigateToFreeformRuleCreationScreen = {
                            backStack.add(
                                RulesScreenViewState.FreeformTextRuleCreation(
                                    freeformTextViewModelFactory.create()
                                )
                            )
                        },
                    )
                }

                is RulesScreenViewState.FreeformTextRuleCreation -> {
                    FreeformTextRuleCreationScreen(
                        viewModel = viewState.viewModel,
                        onDismissRequest = onDismissRequest,
                        onEnterEditField = onEnterEditField,
                        onNavigateToEditScreen = onNavigateToEditScreen,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                is RulesScreenViewState.EditRule -> {
                    NotificationRuleEdit(
                        viewModel = viewState.viewModel,
                        onDismissRuleEditScreen = onDismissRequest,
                        onEnterEditField = onEnterEditField,
                        onExitEditField = onDismissRequest,
                    )
                }
                is RulesScreenViewState.EditField.Action -> {
                    ActionChoiceScreen(viewState, onDismissRequest = onDismissRequest)
                }
                is RulesScreenViewState.EditField.Contacts -> {
                    ContactChoiceScreen(viewState, onDismissRequest = onDismissRequest)
                }
                is RulesScreenViewState.EditField.Apps -> {
                    AppChoiceScreen(viewState, onDismissRequest = onDismissRequest)
                }
            }
        }
    }
}
