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

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.android.systemui.notifications.intelligence.rules.shared.model.RuleValue
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.RulesScreenViewState

/** Renders a fullscreen page to select 1 or more contacts matching a search string. */
@Composable
fun ContactChoiceScreen(
    viewState: RulesScreenViewState.EditField.Contacts,
    onDismissRequest: () -> Unit,
) {
    val viewModel = viewState.viewModel
    val contentResolver = LocalContext.current.contentResolver

    val initialSelection =
        when (val contacts = viewModel.rule.contacts) {
            is RuleValue.Specified -> contacts.value.contacts
            is RuleValue.Ambiguous -> emptyList()
            null -> emptyList()
        }

    EditScreen(
        title = "People [TK]",
        initialSelection = initialSelection,
        onSelectionSaved = { viewState.onContactsSaved(it) },
        onDismissRequest = onDismissRequest,
        fetchSearchResults = { query -> viewModel.fetchContacts(query, contentResolver) },
        sortKey = { it.name },
        uniqueId = { it.lookupUri },
        image = {
            AsyncUriImage(
                uri = it.photoUri,
                contentDescription = it.name,
                size = EditScreenDimens.imageSize,
                viewModel = viewModel,
            )
        },
        text = { it.name },
    )
}
