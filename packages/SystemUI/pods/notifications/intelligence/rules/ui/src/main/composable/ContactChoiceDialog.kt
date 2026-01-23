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

import android.content.Context
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.NotificationRuleEditViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Renders a menu to select 1 or more contacts matching a search string. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactChoiceDialog(
    viewModel: NotificationRuleEditViewModel,
    scope: CoroutineScope,
    context: Context,
) {
    var currentSearchResults by remember { mutableStateOf(emptyList<String>()) }
    var currentQuery by remember { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(false) }

    val onQueryChange: (String) -> Unit = { query: String ->
        scope.launch {
            currentSearchResults =
                viewModel.fetchContacts(query, context.contentResolver).map { it.name }
        }
    }
    // When the dialog first opens, fetch all contacts.
    LaunchedEffect(Unit) { onQueryChange.invoke(currentQuery) }

    SearchBar(
        inputField = {
            SearchBarDefaults.InputField(
                query = currentQuery,
                onQueryChange = { newQuery: String ->
                    currentQuery = newQuery
                    onQueryChange(newQuery)
                },
                onSearch = { onQueryChange(it) },
                placeholder = { Text(text = "Search") },
                expanded = expanded,
                onExpandedChange = { expanded = it },
            )
        },
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        // TODO: b/478225883 - Allow users to select contacts and save them.
        currentSearchResults.forEach { Row { Text(it) } }
    }
}
