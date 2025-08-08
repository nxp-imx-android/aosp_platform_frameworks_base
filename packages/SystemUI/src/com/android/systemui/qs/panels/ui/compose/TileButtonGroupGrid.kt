/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.systemui.qs.panels.ui.compose

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastSumBy
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.ElementKey
import com.android.systemui.qs.panels.shared.model.SizedTile
import com.android.systemui.qs.panels.shared.model.splitInRowsSequence
import com.android.systemui.res.R

/**
 * A grid of [SizedTile] implemented using a column of [ButtonGroup].
 *
 * @param sizedTiles The list of [SizedTile] to display.
 * @param columns The number of columns in the grid.
 * @param horizontalPadding The horizontal padding of the grid.
 * @param keys A factory of stable and unique keys representing the item.
 * @param elementKey A factory for [ElementKey] for each tile.
 * @param modifier The [Modifier] to be applied to the grid.
 * @param tileContent The content of each tile. It receives the [SizedTile] to display and the
 *   [MutableInteractionSource] to use for animating the tile's width. Do not use this interaction
 *   source if the tile should not bounce.
 */
@Composable
fun <T> ContentScope.ButtonGroupGrid(
    sizedTiles: List<SizedTile<T>>,
    columns: Int,
    horizontalPadding: Dp,
    keys: (T) -> Any,
    elementKey: (T) -> ElementKey,
    modifier: Modifier = Modifier,
    tileContent:
        @Composable
        ContentScope.(tile: SizedTile<T>, interactionSource: MutableInteractionSource) -> Unit,
) {
    val rows = remember(sizedTiles, columns) { splitInRowsSequence(sizedTiles, columns).toList() }
    Column(
        verticalArrangement = spacedBy(dimensionResource(R.dimen.qs_tile_margin_vertical)),
        modifier = modifier,
    ) {
        for (row in rows) {
            key(row.fastMap { keys(it.tile) }) {
                ButtonGroupRow(
                    row,
                    columns,
                    horizontalPadding,
                    keys,
                    elementKey,
                    tileContent = tileContent,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> ContentScope.ButtonGroupRow(
    row: List<SizedTile<T>>,
    columns: Int,
    horizontalPadding: Dp,
    keys: (T) -> Any,
    elementKey: (T) -> ElementKey,
    modifier: Modifier = Modifier,
    tileContent:
        @Composable
        ContentScope.(tile: SizedTile<T>, interactionSource: MutableInteractionSource) -> Unit,
) {
    val halfPadding = horizontalPadding / 2

    // Avoid setting the horizontal padding with the ButtonGroup to ensure that weight distribution
    // works properly for all rows.
    ButtonGroup(
        overflowIndicator = {},
        horizontalArrangement = spacedBy(0.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        for ((indexInRow, sizedTile) in row.withIndex()) {
            val column = row.subList(0, indexInRow).fastSumBy { it.width }
            val onLastColumn = column == columns - sizedTile.width
            val isFirst = indexInRow == 0
            customItem(
                buttonGroupContent = {
                    key(keys(sizedTile.tile)) {
                        // This interaction source may not be used by tiles that shouldn't bounce.
                        val interactionSource = remember { MutableInteractionSource() }
                        Element(
                            elementKey(sizedTile.tile),
                            Modifier.animateWidth(interactionSource)
                                .weight(sizedTile.width.toFloat())
                                .rowPadding(isFirst, onLastColumn, halfPadding),
                        ) {
                            tileContent(sizedTile, interactionSource)
                        }
                    }
                },
                menuContent = {},
            )
        }

        // If the row isn't filled, add a spacer
        val columnsLeft = columns - row.fastSumBy { it.width }
        if (columnsLeft > 0) {
            customItem(
                buttonGroupContent = {
                    Spacer(
                        Modifier.weight(columnsLeft.toFloat())
                            .rowPadding(isFirst = false, onLastColumn = true, halfPadding)
                    )
                },
                menuContent = {},
            )
        }
    }
}

/**
 * Adds padding to a Composable within a row, adjusting based on its position within that row.
 *
 * This modifier is useful for creating consistent spacing between items in a row, especially when
 * you want to avoid extra padding at the beginning or end of the row.
 *
 * @param isFirst True if this is the first item in the row. If true, no start padding is applied.
 * @param onLastColumn True if this item is in the last column of the row. The last element of a row
 *   may not be on the last column. If true, no end padding is applied.
 * @param horizontalPadding The amount of padding to apply to the start (if not first) and end (if
 *   not last) of the item.
 * @return A [Modifier] that applies the calculated padding.
 */
private fun Modifier.rowPadding(
    isFirst: Boolean,
    onLastColumn: Boolean,
    horizontalPadding: Dp,
): Modifier {
    return padding(
        start = if (isFirst) 0.dp else horizontalPadding,
        end = if (onLastColumn) 0.dp else horizontalPadding,
    )
}
