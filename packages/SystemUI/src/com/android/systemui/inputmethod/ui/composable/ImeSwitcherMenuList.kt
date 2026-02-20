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

package com.android.systemui.inputmethod.ui.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.systemui.common.ui.compose.Icon as SysuiIconComposable
import com.android.systemui.inputmethod.ui.viewmodel.ImeSwitcherMenuViewModel
import com.android.systemui.res.R

/**
 * The UI for the list of IMEs and IME subtypes.
 *
 * @param items the UI items for IMEs and IME subtypes.
 * @param viewModel the view model to get the data from.
 * @param dismissAction the action to invoke when the UI should be dismissed.
 * @param useLargeScreenLayout whether the UI should use the large screen layout.
 */
@Composable
fun ImeSwitcherMenuList(
    items: List<ImeSwitcherMenuViewModel.MenuItem>,
    viewModel: ImeSwitcherMenuViewModel,
    dismissAction: () -> Unit,
    useLargeScreenLayout: Boolean,
) {
    val listState = rememberLazyListState()
    // Start the UI scrolled to the selected item, and react to changes in the selected item,
    // scrolling to the new position.
    LaunchedEffect(viewModel.selectedIndex.intValue, items.size) {
        val index = viewModel.selectedIndex.intValue
        if (index != -1 && index < items.size) {
            listState.scrollToItem(index)
        }
    }

    // TODO(b/308488505): Add scroll indicators to the LazyColumn once implemented.
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(top = 8.dp),
        modifier = Modifier.heightIn(max = 373.dp),
    ) {
        itemsIndexed(items, key = { _, item -> "${item.imeId}:${item.subtypeIndex}" }) { index, item
            ->
            ImeSwitcherMenuListItem(
                item,
                index == viewModel.selectedIndex.intValue,
                viewModel,
                dismissAction,
                useLargeScreenLayout,
            )
        }
    }
}

/**
 * The UI for a single IME or IME subtype list item.
 *
 * @param item the IME or IME subtype to display.
 * @param isSelected whether this is the currently selected item in the list.
 * @param viewModel the view model to get the data from.
 * @param dismissAction the action to invoke when the UI should be dismissed.
 * @param useLargeScreenLayout whether the UI should use the large screen layout.
 */
@Composable
private fun ImeSwitcherMenuListItem(
    item: ImeSwitcherMenuViewModel.MenuItem,
    isSelected: Boolean,
    viewModel: ImeSwitcherMenuViewModel,
    dismissAction: () -> Unit,
    useLargeScreenLayout: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (item.hasDivider) {
            HorizontalDivider(
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(start = 20.dp, top = 8.dp, end = 24.dp, bottom = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }

        if (item.hasHeader) {
            Text(
                text = item.imeName.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .padding(top = 4.dp, bottom = 16.dp),
            )
        }

        val backgroundColor =
            if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
        val selectedColor =
            if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurface
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .heightIn(min = 72.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(backgroundColor)
                    .semantics { this.selected = isSelected }
                    .clickable(
                        onClickLabel =
                            if (isSelected) {
                                stringResource(R.string.input_method_switcher_dismiss)
                            } else {
                                stringResource(R.string.input_method_switcher_select_item)
                            },
                        onClick = {
                            if (!isSelected) {
                                viewModel.onImeAndSubtypeSelected(item.imeId, item.subtypeIndex)
                            }
                            dismissAction.invoke()
                        }
                    )
                    .padding(start = 20.dp, end = 24.dp)
                    .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (useLargeScreenLayout) {
                SubtypeIconOrShortLabel(item, color = selectedColor)
            }
            Column(modifier = Modifier.weight(1f)) {
                val text = if (item.subtypeName.isNullOrEmpty()) item.imeName else item.subtypeName
                Text(
                    text = text.toString(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = selectedColor,
                    maxLines = 1,
                    modifier = if (isSelected) Modifier.basicMarquee(iterations = 1) else Modifier,
                )
                if (!item.layoutName.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.layoutName.toString().uppercase(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier =
                            if (isSelected) Modifier.basicMarquee(iterations = 1) else Modifier,
                    )
                }
            }

            if (isSelected) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null, // decorative
                    tint = selectedColor,
                    modifier = Modifier.padding(start = 12.dp).size(24.dp),
                )
            }
        }
    }
}

/**
 * The UI which shows the subtype icon if available, otherwise the subtype short label. If neither
 * is available, shows an empty spacer.
 *
 * @param item the IME or IME subtype to display.
 * @param color the color of the icon or short label.
 */
@Composable
private fun SubtypeIconOrShortLabel(item: ImeSwitcherMenuViewModel.MenuItem, color: Color) {
    Row(modifier = Modifier.padding(end = 16.dp)) {
        val icon = item.subtypeIcon
        if (icon != null) {
            SysuiIconComposable(
                icon = icon,
                modifier = Modifier.size(ListItemDimensions.IconSize).testTag("SubtypeIcon"),
                tint = color,
            )
        } else if (!item.subtypeShortLabel.isNullOrEmpty()) {
            Box(
                modifier = Modifier.size(ListItemDimensions.IconSize),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = item.subtypeShortLabel.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = color,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            Spacer(modifier = Modifier.size(ListItemDimensions.IconSize))
        }
    }
}

private object ListItemDimensions {
    val IconSize = 24.dp
}
