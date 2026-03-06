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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.android.systemui.notifications.intelligence.rules.shared.model.AppModel
import com.android.systemui.notifications.intelligence.rules.shared.model.ContactModel
import com.android.systemui.notifications.intelligence.rules.shared.model.ContactsModel
import com.android.systemui.notifications.intelligence.rules.shared.model.IncludedAppsModel
import com.android.systemui.notifications.intelligence.rules.shared.model.RuleValue
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.NotificationRuleEditViewModel
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.RuleDisplayModel
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.RulesScreenViewState
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.TextStyles
import com.android.systemui.res.R

/**
 * A composable rendering a page to edit a specific notification rule.
 *
 * This is still a work-in-progress.
 *
 * @param onDismissRuleEditScreen invoked when the user dismisses this current screen.
 * @param onEnterEditField invoked when the user starts editing a particular field of the rule.
 * @param onExitEditField invoked when the user finishes editing a particular field of the rule.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NotificationRuleEdit(
    viewModel: NotificationRuleEditViewModel,
    onDismissRuleEditScreen: () -> Unit,
    onEnterEditField: (RulesScreenViewState.EditField) -> Unit,
    onExitEditField: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current

    val addFieldOptions: List<RulesScreenViewState.EditField> =
        buildAddFieldOptions(viewModel, onExitEditField = onExitEditField)
    var isAddFieldDialogShowing by remember { mutableStateOf(false) }

    val textStyles = rememberTextStyles()
    val ruleDisplay: RuleDisplayModel =
        remember(
            viewModel,
            viewModel.rule,
            onEnterEditField,
            onExitEditField,
            textStyles,
            resources,
        ) {
            viewModel.buildRuleText(
                onEnterEditField = onEnterEditField,
                onExitEditField = onExitEditField,
                resources = resources,
            )
        }
    val text =
        remember(ruleDisplay.textChunks, textStyles) {
            buildAnnotatedString(ruleDisplay.textChunks, textStyles)
        }

    BackHandler(enabled = true, onBack = onDismissRuleEditScreen)
    Column(modifier = modifier) {
        Header(
            title =
                if (viewModel.rule.isNew) {
                    stringResource(R.string.notification_rules_create_new_title)
                } else {
                    stringResource(R.string.notification_rules_edit)
                },
            onDismissRequest = onDismissRuleEditScreen,
        )
        EditableAction(viewModel, onEnterEditField = onEnterEditField)
        Text(text = text, style = MaterialTheme.typography.titleLargeEmphasized)

        AddButton(
            addFieldOptions = addFieldOptions,
            toggleAddFieldDialogShowing = { isAddFieldDialogShowing = !isAddFieldDialogShowing },
        )
        if (isAddFieldDialogShowing) {
            AddFieldDialog(
                options = addFieldOptions,
                onDismissRequest = { isAddFieldDialogShowing = false },
                onOptionSelected = { editField -> onEnterEditField(editField) },
            )
        }
    }
}

/** Renders a '+' button letting users add additional fields to the rule. */
@Composable
private fun AddButton(
    addFieldOptions: List<RulesScreenViewState.EditField>,
    toggleAddFieldDialogShowing: () -> Unit,
) {
    if (addFieldOptions.isEmpty()) {
        return
    }

    Button(onClick = { toggleAddFieldDialogShowing() }) { Text(stringResource(R.string.add)) }
}

/**
 * Builds a list of filter and condition fields that can be added to the rule. Only includes types
 * that *aren't* present in the rule yet. (Types that *are* present can be edited by clicking their
 * text.)
 */
private fun buildAddFieldOptions(
    viewModel: NotificationRuleEditViewModel,
    onExitEditField: () -> Unit,
): List<RulesScreenViewState.EditField> {
    return mutableListOf<RulesScreenViewState.EditField>().apply {
        if (viewModel.rule.contacts == null) {
            add(
                RulesScreenViewState.EditField.Contacts(
                    onContactsSaved = { newContacts ->
                        onContactsSaved(newContacts, viewModel, onExitEditField)
                    },
                    viewModel = viewModel,
                )
            )
        }
        if (viewModel.rule.includedApps == null) {
            add(
                RulesScreenViewState.EditField.Apps(
                    viewModel = viewModel,
                    onAppsSaved = { newApps -> onAppsSaved(newApps, viewModel, onExitEditField) },
                )
            )
        }
    }
}

private fun onContactsSaved(
    newContacts: List<ContactModel>,
    viewModel: NotificationRuleEditViewModel,
    onExitEditField: () -> Unit,
) {
    viewModel.rule =
        viewModel.rule.copy(
            contacts =
                if (newContacts.isNotEmpty()) {
                    RuleValue.Specified(ContactsModel(newContacts))
                } else {
                    // Saving with no selected contacts is effectively removing contacts from the
                    // filter.
                    null
                }
        )
    onExitEditField()
}

private fun onAppsSaved(
    newApps: List<AppModel>,
    viewModel: NotificationRuleEditViewModel,
    onExitEditField: () -> Unit,
) {
    viewModel.rule =
        viewModel.rule.copy(
            includedApps =
                if (newApps.isNotEmpty()) {
                    RuleValue.Specified(IncludedAppsModel(newApps))
                } else {
                    // Saving with no selected apps is effectively removing apps from the filter.
                    null
                }
        )
    onExitEditField()
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun rememberTextStyles(): TextStyles {
    val defaultStyle = MaterialTheme.typography.titleLargeEmphasized
    val baseValueSpanStyle =
        SpanStyle(textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Bold)
    val specified = baseValueSpanStyle.copy(color = MaterialTheme.colorScheme.primary)
    val ambiguous = baseValueSpanStyle.copy(color = MaterialTheme.colorScheme.error)

    return remember(defaultStyle, specified, ambiguous) {
        TextStyles(
            defaultStyle = defaultStyle,
            specifiedValueSpanStyle = specified,
            ambiguousValueSpanStyle = ambiguous,
        )
    }
}
