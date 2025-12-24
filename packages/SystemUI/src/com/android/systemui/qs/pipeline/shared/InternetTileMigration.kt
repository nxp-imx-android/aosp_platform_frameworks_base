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

package com.android.systemui.qs.pipeline.shared

import com.android.systemui.log.core.Logger
import com.android.systemui.qs.flags.QsSplitInternetTile
import com.android.systemui.qs.pipeline.shared.logging.QSPipelineLogger.InternetTileMigrationScenario
import com.android.systemui.statusbar.connectivity.ConnectivityModule.Companion.INTERNET_TILE_SPEC
import com.android.systemui.statusbar.connectivity.ConnectivityModule.Companion.MOBILE_DATA_TILE_SPEC
import com.android.systemui.statusbar.connectivity.ConnectivityModule.Companion.WIFI_TILE_SPEC
import kotlin.math.abs

object InternetTileMigration {
    private val internetTileSpec = TileSpec.create(INTERNET_TILE_SPEC)
    private val wifiTileSpec = TileSpec.create(WIFI_TILE_SPEC)
    private val cellTileSpec = TileSpec.create(MOBILE_DATA_TILE_SPEC)

    /**
     * Migrates a list of tiles from `internet` to `wifi` or viceversa depending on the value of
     * [QsSplitInternetTile.isEnabled].
     *
     * Use this for simple migrations
     *
     * If [QsSplitInternetTile.isEnabled] is `true`, instances of `internet` will be converted to
     * `wifi`.
     *
     * If [QsSplitInternetTile.isEnabled] is `false`, instances of `wifi` will be converted to
     * `internet`.
     */
    fun List<TileSpec>.migrateInternetTile(): List<TileSpec> {
        return map(flagCheckedMap)
    }

    /**
     * Migration in place of the internet tile to be used by [TileSpecRepository].
     *
     * This migration takes into account the size of the tiles and will migrate `internet` into
     * `wifi`, and possibly add `cell`. For details see [migrateInternetTileToWifiAndCell], and
     * [migrateWifiOrCellToInternet], which provide the forward and backward migrations
     * respectively.
     *
     * As the migration removes the incompatible tiles, repeated applications of this are NO-OP.
     *
     * @param currentTiles the list of current tiles to migrate
     * @param largeTiles the set of current large tiles
     * @return a pair containing the list of tiles and set of large tiles to be used post migration.
     */
    fun migrateInternetTile(
        currentTiles: List<TileSpec>,
        largeTiles: Set<TileSpec>,
        logScenario: (InternetTileMigrationScenario) -> Unit = {},
    ): Pair<List<TileSpec>, Set<TileSpec>> {
        if (QsSplitInternetTile.isEnabled) {
            return migrateInternetTileToWifiAndCell(currentTiles, largeTiles, logScenario)
        } else {
            return migrateWifiOrCellToInternet(currentTiles, largeTiles, logScenario)
        }
    }

    /**
     * Migration for [QsSplitInternetTile.isEnabled] going from `false` to `true`.
     * * If the `internet` tile is large, it's replaced with `wifi` and `cell` next to each other,
     *   in the current position of `internet`, both small. This effectively replaces one large tile
     *   with two small tiles, visually.
     * * If the `internet` tile is small, it's replaced with `wifi`, in the same position, as a
     *   small tile.
     * * If there's no `internet` tile, nothing changes.
     *
     * This migration effectively results in no changes to the layout (assuming no XL tiles).
     */
    private fun migrateInternetTileToWifiAndCell(
        currentTiles: List<TileSpec>,
        largeTiles: Set<TileSpec>,
        logScenario: (InternetTileMigrationScenario) -> Unit = {},
    ): Pair<List<TileSpec>, Set<TileSpec>> {
        if (internetTileSpec !in currentTiles) return currentTiles to largeTiles
        val mutableCurrentTiles = currentTiles.toMutableList()
        val internetPosition = mutableCurrentTiles.indexOf(internetTileSpec)
        mutableCurrentTiles.remove(internetTileSpec)
        if (internetTileSpec in largeTiles) {
            // Add cell first, so we can add wifi right before it at the same index
            mutableCurrentTiles.add(internetPosition, cellTileSpec)
            logScenario(InternetTileMigrationScenario.LARGE_INTERNET_TO_SMALL_WIFI_CELL)
        } else {
            logScenario(InternetTileMigrationScenario.SMALL_INTERNET_TO_SMALL_WIFI)
        }
        mutableCurrentTiles.add(internetPosition, wifiTileSpec)
        return mutableCurrentTiles to largeTiles - internetTileSpec
    }

    /**
     * Migration for [QsSplitInternetTile.isEnabled] going from `true` to `false`.
     * * If the `wifi`, and `cell` tile are next to each other, in any size, they are replaced by a
     *   large `internet` tile, in the same position as the earliest tile of those.
     * * If the `wifi`, and `cell` tile are both present but not next to each other, the `cell` tile
     *   is removed, and the `wifi` tile is replaced with the `internet` tile in the same position
     *   and with the same size.
     * * If either `wifi`, or `cell` tiles are present (but not both), that tile is replaced with
     *   the `internet` tile in the same position and with the same size.
     * * If neither `wifi` or `cell` tiles are present, nothing changes.
     *
     * Note that this backward migration could cause layout changes, however it's only expected to
     * be used in case of a flag rollback, and therefore not likely.
     */
    private fun migrateWifiOrCellToInternet(
        currentTiles: List<TileSpec>,
        largeTiles: Set<TileSpec>,
        logScenario: (InternetTileMigrationScenario) -> Unit = {},
    ): Pair<List<TileSpec>, Set<TileSpec>> {
        if (!(wifiTileSpec in currentTiles || cellTileSpec in currentTiles)) {
            return currentTiles to largeTiles
        }
        val mutableCurrentTiles = currentTiles.toMutableList()
        var wifiPosition = mutableCurrentTiles.indexOf(wifiTileSpec)
        val cellPosition = mutableCurrentTiles.indexOf(cellTileSpec)
        return if (wifiPosition > -1 && cellPosition > -1) {
            // Both tiles are present, replacing wifi with internet. Internet will be large if the
            // tiles are next to each other, or wifi is large.
            val adjacent = abs(wifiPosition - cellPosition) == 1
            val replaceToLarge = adjacent || wifiTileSpec in largeTiles
            mutableCurrentTiles.remove(cellTileSpec)
            // Get wifi position again as it may have changed
            wifiPosition = mutableCurrentTiles.indexOf(wifiTileSpec)
            mutableCurrentTiles.remove(wifiTileSpec)
            mutableCurrentTiles.add(wifiPosition, internetTileSpec)
            val newLargeSet =
                if (replaceToLarge) {
                    largeTiles - wifiTileSpec - cellTileSpec + internetTileSpec
                } else {
                    largeTiles - wifiTileSpec - cellTileSpec
                }
            if (adjacent) {
                logScenario(InternetTileMigrationScenario.WIFI_CELL_ADJACENT_TO_LARGE_INTERNET)
            } else if (replaceToLarge) {
                logScenario(
                    InternetTileMigrationScenario.LARGE_WIFI_CELL_NOT_ADJACENT_TO_LARGE_INTERNET
                )
            } else {
                logScenario(
                    InternetTileMigrationScenario.SMALL_WIFI_CELL_NOT_ADJACENT_TO_SMALL_INTERNET
                )
            }
            mutableCurrentTiles to newLargeSet
        } else if (wifiPosition > -1) {
            mutableCurrentTiles.remove(wifiTileSpec)
            mutableCurrentTiles.add(wifiPosition, internetTileSpec)
            val newLargeSet =
                if (wifiTileSpec in largeTiles) {
                    logScenario(InternetTileMigrationScenario.LARGE_WIFI_TO_LARGE_INTERNET_NO_CELL)
                    largeTiles - wifiTileSpec + internetTileSpec
                } else {
                    logScenario(InternetTileMigrationScenario.SMALL_WIFI_TO_SMALL_INTERNET_NO_CELL)
                    largeTiles
                }
            mutableCurrentTiles to newLargeSet
        } else if (cellPosition > -1) {
            mutableCurrentTiles.remove(cellTileSpec)
            mutableCurrentTiles.add(cellPosition, internetTileSpec)
            val newLargeSet =
                if (cellTileSpec in largeTiles) {
                    logScenario(InternetTileMigrationScenario.LARGE_CELL_TO_LARGE_INTERNET_NO_WIFI)
                    largeTiles - cellTileSpec + internetTileSpec
                } else {
                    logScenario(InternetTileMigrationScenario.SMALL_CELL_TO_SMALL_INTERNET_NO_WIFI)
                    largeTiles
                }
            mutableCurrentTiles to newLargeSet
        } else {
            currentTiles to largeTiles
        }
    }

    private inline val flagCheckedMap: (TileSpec) -> TileSpec
        get() =
            if (QsSplitInternetTile.isEnabled) {
                ::fromInternetToWifi
            } else {
                ::fromWifiToInternet
            }

    private fun fromInternetToWifi(tile: TileSpec): TileSpec {
        return if (tile == internetTileSpec) wifiTileSpec else tile
    }

    private fun fromWifiToInternet(tile: TileSpec): TileSpec {
        return if (tile == wifiTileSpec) internetTileSpec else tile
    }

    private const val migrationInternetToWifi = "internet -> wifi"
    private const val migrationWifiToInternet = "wifi -> internet"

    /** String to be used by loggers indicating the migration that is performed. */
    @JvmStatic
    val migrationString =
        if (QsSplitInternetTile.isEnabled) {
            migrationInternetToWifi
        } else {
            migrationWifiToInternet
        }

    fun Logger.logMigration() {
        i("Migration performed: $migrationString")
    }
}
