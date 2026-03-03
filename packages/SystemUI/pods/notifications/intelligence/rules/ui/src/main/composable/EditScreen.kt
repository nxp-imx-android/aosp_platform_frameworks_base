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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarColors
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarDefaults.ScreenOffset
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.android.compose.PlatformButton
import com.android.compose.PlatformOutlinedButton
import com.android.systemui.res.R
import kotlinx.coroutines.launch

/**
 * A generic composable supporting an edit screen for a particular type [T]. The screen shows the
 * currently-selected set of items and lets the user search for items to add or remove them.
 *
 * @param title a string shown in the header explaining the type of item being edited.
 * @param initialSelection the currently selected items when the screen first loads.
 * @param onSelectionSaved invoked when the user saves their selected items.
 * @param onDismissRequest invoked when the user leaves the page without saving.
 * @param allSearchResults an list of full search results, shown when the user first opens the
 *   search box but hasn't typed a query yet. If null, no default results will be shown.
 * @param fetchSearchResults a function that fetches the relevant search results based on the given
 *   [query]. [allSearchResults] is provided as the second parameter.
 * @param sortKey a function used to sort items as they're added to the list.
 * @param uniqueId a function that should return a unique identifier for an item.
 * @param image a composable rendering an image for a particular item.
 * @param text a function that should return the main text for a particular item.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> EditScreen(
    title: String,
    initialSelection: List<T>,
    onSelectionSaved: (List<T>) -> Unit,
    onDismissRequest: () -> Unit,
    allSearchResults: List<T>? = null,
    fetchSearchResults: suspend (query: String) -> List<T>?,
    sortKey: (T) -> String,
    uniqueId: (T) -> Any,
    image: (@Composable (T) -> Unit),
    text: (T) -> String,
) {
    val scope = rememberCoroutineScope()

    val currentSelection: MutableList<T> = remember {
        mutableStateListOf<T>().apply { addAll(initialSelection.sortedBy { sortKey(it) }) }
    }

    val searchBarState = rememberSearchBarState()
    val textFieldState = rememberTextFieldState()
    val searchResults: List<T>? by
        // allSearchResults might change from null to non-null when results are loaded, so
        // `searchResults` should be re-calculated when that value changes.
        produceState<List<T>?>(emptyList(), textFieldState.text, allSearchResults) {
            value = fetchSearchResults(textFieldState.text.toString())
        }
    val inputField =
        @Composable {
            SearchBarDefaults.InputField(
                textFieldState = textFieldState,
                searchBarState = searchBarState,
                onSearch = { scope.launch { searchBarState.animateToCollapsed() } },
                placeholder = {
                    Text(
                        text = stringResource(R.string.notification_rules_search),
                        // Use `clearAndSetSemantics` because `ExpandedFullScreenSearchBar` will
                        // handle accessibility for us.
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                },
            )
        }

    val onSelectionToggled: (T, Boolean) -> Unit = { model: T, isSelectedNew: Boolean ->
        if (isSelectedNew) {
            currentSelection.add(model)
        } else {
            currentSelection.remove(model)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Header(title = title, onDismissRequest = onDismissRequest)

            SearchBar(state = searchBarState, inputField = inputField)
            ExpandedFullScreenSearchBar(state = searchBarState, inputField = inputField) {
                Box {
                    val currentSearchResults = searchResults
                    if (currentSearchResults != null) {
                        SearchResults(
                            searchResults = currentSearchResults,
                            onSelectionToggled = onSelectionToggled,
                            currentSelection = currentSelection,
                            uniqueId = uniqueId,
                            image = image,
                            text = text,
                        )
                    } else {
                        LoadingIcon(Modifier.fillMaxSize())
                    }

                    // The floating save button has to be included both in the search results and
                    // outside the search results because the search results do a fullscreen
                    // takeover.
                    FloatingSaveButton(
                        currentSelection = currentSelection,
                        onSelectionSaved = onSelectionSaved,
                        sortKey = sortKey,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }

            if (currentSelection.isNotEmpty()) {
                SelectedItems(
                    currentSelection = currentSelection,
                    onSelectionToggled = onSelectionToggled,
                    onClearSelection = { currentSelection.clear() },
                    uniqueId = uniqueId,
                    image = image,
                    text = text,
                )
            } else {
                NoSelection()
            }
        }

        FloatingSaveButton(
            currentSelection = currentSelection,
            onSelectionSaved = onSelectionSaved,
            sortKey = sortKey,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Header(title: String, onDismissRequest: () -> Unit) {
    CenterAlignedTopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onDismissRequest) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.accessibility_back),
                )
            }
        },
    )
}

/**
 * Renders the current search results with affordances to add or remove items from the selection.
 */
@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun <T> SearchResults(
    searchResults: List<T>,
    onSelectionToggled: (T, Boolean) -> Unit,
    currentSelection: List<T>,
    uniqueId: (T) -> Any,
    image: (@Composable (T) -> Unit),
    text: (T) -> String,
) {
    LazyColumn(
        horizontalAlignment = Alignment.CenterHorizontally,
        // fillMaxSize ensures that the FloatingSaveButton is always at the bottom.
        modifier = Modifier.fillMaxSize().padding(top = 8.dp),
    ) {
        item(key = "Search results") {
            Text(
                stringResource(R.string.notification_rules_search_results),
                style = MaterialTheme.typography.titleLargeEmphasized,
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
            )
        }
        items(searchResults, key = uniqueId) {
            Item(
                model = it,
                isSelected = it in currentSelection,
                onSelectionToggled = onSelectionToggled,
                image = image,
                text = text,
            )
        }
    }
}

/** Renders the list of items that are currently selected to be part of the rule filter. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> SelectedItems(
    currentSelection: List<T>,
    onSelectionToggled: (T, Boolean) -> Unit,
    onClearSelection: () -> Unit,
    uniqueId: (T) -> Any,
    image: (@Composable (T) -> Unit),
    text: (T) -> String,
) {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        item(key = "Selected header") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
            ) {
                Text(
                    stringResource(
                        R.string.notification_rules_number_selected,
                        currentSelection.size,
                    ),
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.notification_rules_deselect_all),
                    modifier = Modifier.clickable { onClearSelection() },
                )
            }
        }
        items(currentSelection, key = uniqueId) {
            Item(
                model = it,
                isSelected = true,
                onSelectionToggled = onSelectionToggled,
                image = image,
                text = text,
            )
        }
    }
}

/**
 * Renders a single item.
 *
 * @param isSelected true if the item is currently selected to be part of the rule
 */
@Composable
private fun <T> Item(
    model: T,
    isSelected: Boolean,
    onSelectionToggled: (T, Boolean) -> Unit,
    image: @Composable (T) -> Unit,
    text: (T) -> String,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.medium,
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(EditScreenDimens.imageSize)) { image(model) }

        Text(
            text = text(model),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.height(24.dp).fillMaxWidth(0.8f).padding(horizontal = 8.dp),
        )

        val buttonIconModifier = Modifier.size(18.dp)
        val onClick = { onSelectionToggled(model, !isSelected) }
        if (isSelected) {
            PlatformOutlinedButton(onClick = onClick) {
                Icon(
                    imageVector = Icons.Filled.Clear,
                    contentDescription = stringResource(R.string.remove),
                    modifier = buttonIconModifier,
                )
            }
        } else {
            PlatformButton(onClick = onClick) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.add),
                    modifier = buttonIconModifier,
                )
            }
        }
    }
}

@Composable
private fun NoSelection() {
    Text(
        stringResource(R.string.notification_rules_nothing_selected),
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun LoadingIcon(modifier: Modifier = Modifier) {
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 32.dp).size(32.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> BoxScope.FloatingSaveButton(
    currentSelection: List<T>,
    onSelectionSaved: (List<T>) -> Unit,
    sortKey: (T) -> String,
    modifier: Modifier = Modifier,
) {
    HorizontalFloatingToolbar(
        expanded = true,
        colors =
            FloatingToolbarColors(
                toolbarContainerColor = MaterialTheme.colorScheme.inverseSurface,
                toolbarContentColor = MaterialTheme.colorScheme.inverseOnSurface,
                fabContainerColor = MaterialTheme.colorScheme.inverseSurface,
                fabContentColor = MaterialTheme.colorScheme.inverseOnSurface,
            ),
        shape = FloatingToolbarDefaults.ContainerShape,
        modifier =
            modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .offset(y = -ScreenOffset)
                // Use zIndex to ensure it's on top of other content.
                .zIndex(1f)
                // Apply `clip` before `clickable` so the touch ripple is only within the rounded
                // rectangle area.
                .clip(FloatingToolbarDefaults.ContainerShape)
                .clickable { onSelectionSaved(currentSelection.sortedBy { sortKey(it) }) },
    ) {
        Text(
            stringResource(R.string.notification_rules_save_number_selected, currentSelection.size),
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

/** Common dimensions used by the edit screen. */
object EditScreenDimens {
    val imageSize = 40.dp
}
