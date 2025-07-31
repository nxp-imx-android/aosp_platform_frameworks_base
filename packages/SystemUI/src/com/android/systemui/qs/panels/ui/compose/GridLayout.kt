/*
 * Copyright (C) 2024 The Android Open Source Project
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

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.ElementKey
import com.android.systemui.qs.panels.ui.viewmodel.EditTileViewModel
import com.android.systemui.qs.panels.ui.viewmodel.PaginatableViewModel
import com.android.systemui.qs.panels.ui.viewmodel.TileViewModel
import com.android.systemui.qs.pipeline.shared.TileSpec

/** A layout of tiles, indicating how they should be composed when showing in QS or in edit mode. */
interface GridLayout {

    /**
     * Displays a grid of tiles with an optional reveal animation.
     *
     * @param listening can be used to compose the grid but limit when tiles should be listening. It
     *   should be a function tracking a snapshot state.
     * @param revealEffectContainer The [ElementKey] of the container driving the reveal animation.
     *   During expansion, tiles use this container's height to compute their own, creating a
     *   synchronized reveal effect. When `null`, the effect is disabled.
     */
    @Composable
    fun ContentScope.TileGrid(
        tiles: List<TileViewModel>,
        modifier: Modifier,
        listening: () -> Boolean,
        revealEffectContainer: ElementKey?,
    )

    @Composable
    fun EditTileGrid(
        tiles: List<EditTileViewModel>,
        modifier: Modifier,
        onAddTile: (TileSpec, Int) -> Unit,
        onRemoveTile: (TileSpec) -> Unit,
        onSetTiles: (List<TileSpec>) -> Unit,
        onStopEditing: () -> Unit,
    )
}

/** A type of [GridLayout] that can be paginated, to use together with [PaginatedGridLayout]. */
interface PaginatableGridLayout : GridLayout {

    /** The factory to use when creating the grid layout view model. */
    val viewModelFactory: PaginatableViewModel.Factory
}
