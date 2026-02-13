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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.compose.ui.graphics.painter.rememberDrawablePainter
import com.android.systemui.notifications.intelligence.rules.shared.model.AppModel
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.NotificationRuleEditViewModel
import kotlinx.coroutines.launch

/** Renders a menu to select 1 or more apps matching a search string. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppChoiceDialog(viewModel: NotificationRuleEditViewModel) {
    val scope = rememberCoroutineScope()

    // All apps on the device. Null while the apps are being fetched.
    var allApps by remember { mutableStateOf<List<AppModel>?>(null) }
    // When the dialog first opens, fetch all apps.
    LaunchedEffect(Unit) { scope.launch { allApps = viewModel.fetchInstalledApps() } }

    var currentQuery by remember { mutableStateOf("") }
    var isExpanded by rememberSaveable { mutableStateOf(true) }

    val currentSearchResults =
        remember(allApps, currentQuery) {
            derivedStateOf { getSearchResults(allApps, currentQuery) }
        }

    SearchBar(
        inputField = {
            SearchBarDefaults.InputField(
                query = currentQuery,
                onQueryChange = { newQuery: String -> currentQuery = newQuery },
                onSearch = {},
                placeholder = { Text(text = "Search") },
                expanded = isExpanded,
                onExpandedChange = { isExpanded = it },
            )
        },
        expanded = isExpanded,
        onExpandedChange = { isExpanded = it },
    ) {
        val searchResults = currentSearchResults.value
        val modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        if (searchResults != null) {
            AppSearchResults(searchResults, modifier = modifier)
        } else {
            LoadingIcon(modifier = modifier)
        }
    }
}

@Composable
private fun AppSearchResults(currentSearchResults: List<AppModel>, modifier: Modifier = Modifier) {
    // TODO: b/478225883 - Allow users to select apps and save them.
    // TODO: b/478225883 - Show apps for other profiles in separate tabs.
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        // TODO: b/478225883 - Use package name as item key.
        items(currentSearchResults) { AppIcon(it) }
    }
}

@Composable
private fun AppIcon(appModel: AppModel) {
    Column {
        Image(
            rememberDrawablePainter(appModel.icon),
            contentDescription = null,
            modifier = Modifier.size(32.dp),
        )
        Text(appModel.label)
    }
}

@Composable
private fun LoadingIcon(modifier: Modifier = Modifier) {
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(32.dp),
        )
    }
}

/** Returns the subset of [allApps] that match [query]. */
private fun getSearchResults(allApps: List<AppModel>?, query: String): List<AppModel>? {
    return allApps?.filter { it.label.contains(query, ignoreCase = true) }
}
