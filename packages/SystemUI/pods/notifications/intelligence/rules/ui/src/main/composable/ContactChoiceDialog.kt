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

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.android.systemui.notifications.intelligence.rules.shared.model.ContactModel
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.NotificationRuleEditViewModel
import kotlinx.coroutines.launch

/** Renders a menu to select 1 or more contacts matching a search string. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ContactChoiceDialog(
    initialSearchQuery: String,
    initialSelection: List<ContactModel>,
    onContactsSaved: (List<ContactModel>) -> Unit,
    viewModel: NotificationRuleEditViewModel,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val currentSelectedContacts = remember { mutableStateSetOf(*initialSelection.toTypedArray()) }
    var currentSearchResults by remember { mutableStateOf(emptyList<ContactModel>()) }
    var currentQuery by remember { mutableStateOf(initialSearchQuery) }
    var expanded by rememberSaveable { mutableStateOf(true) }

    val onQueryChange: (String) -> Unit = { query: String ->
        scope.launch {
            currentSearchResults = viewModel.fetchContacts(query, context.contentResolver)
        }
    }

    // When the dialog first opens, fetch all contacts.
    LaunchedEffect(Unit) { onQueryChange.invoke(currentQuery) }

    val onContactSelectionToggled: (ContactModel, Boolean) -> Unit =
        { contactModel: ContactModel, isSelectedNew: Boolean ->
            if (isSelectedNew) {
                currentSelectedContacts.add(contactModel)
            } else {
                currentSelectedContacts.remove(contactModel)
            }
        }

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
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            item(key = "Selected") {
                Text("Selected", style = MaterialTheme.typography.titleLargeEmphasized)
            }
            currentSelectedContacts.forEach {
                item(key = it.lookupUri) {
                    Contact(
                        contactModel = it,
                        isSelected = true,
                        setContactSelection = onContactSelectionToggled,
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item(key = "Search results") {
                Text("Search results", style = MaterialTheme.typography.titleLargeEmphasized)
            }
            currentSearchResults.forEach {
                if (it !in currentSelectedContacts) {
                    item(key = it.lookupUri) {
                        Contact(
                            contactModel = it,
                            isSelected = false,
                            setContactSelection = onContactSelectionToggled,
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            item(key = "Save") {
                Button(onClick = { onContactsSaved(currentSelectedContacts.toList()) }) {
                    Text("Save")
                }
            }
        }
    }
}

/**
 * Renders a single contact.
 *
 * @param isSelected true if the contact is currently selected to be part of the rule
 */
@Composable
private fun Contact(
    contactModel: ContactModel,
    isSelected: Boolean,
    setContactSelection: (ContactModel, Boolean) -> Unit,
    viewModel: NotificationRuleEditViewModel,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        AsyncUriImage(
            uri = contactModel.photoUri,
            contentDescription = contactModel.name,
            size = 40.dp,
            viewModel = viewModel,
            modifier = Modifier.padding(start = 8.dp),
        )

        Text(
            text = contactModel.name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.height(24.dp).fillMaxWidth(0.8f).padding(horizontal = 8.dp),
        )
        Button(onClick = { setContactSelection(contactModel, !isSelected) }) {
            val iconModifier = Modifier.size(18.dp)
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.Clear,
                    contentDescription = "Remove contact",
                    modifier = iconModifier,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add contact",
                    modifier = iconModifier,
                )
            }
        }
    }
}
