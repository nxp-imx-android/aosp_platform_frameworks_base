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

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.qs.flags.QsSplitInternetTile
import com.android.systemui.qs.pipeline.shared.logging.QSPipelineLogger.InternetTileMigrationScenario
import com.android.systemui.statusbar.connectivity.ConnectivityModule.Companion.INTERNET_TILE_SPEC
import com.android.systemui.statusbar.connectivity.ConnectivityModule.Companion.MOBILE_DATA_TILE_SPEC
import com.android.systemui.statusbar.connectivity.ConnectivityModule.Companion.WIFI_TILE_SPEC
import com.android.systemui.statusbar.pipeline.shared.connectivityConstants
import com.android.systemui.statusbar.pipeline.shared.fake
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class InternetTileMigrationTest : SysuiTestCase() {

    private val kosmos =
        testKosmos().apply { connectivityConstants.fake.hasDataCapabilities = true }

    private var scenario: InternetTileMigrationScenario? = null

    private val Kosmos.underTest by Kosmos.Fixture { internetTileMigration }

    @Test
    @EnableFlags(QsSplitInternetTile.FLAG_NAME)
    @DisableFlags(QsSplitInternetTile.SUPPRESSION_FLAG_NAME)
    fun largeInternetTile_toSmallWifiAndCellNextToEachOther() =
        kosmos.runTest {
            val currentTiles =
                listOf(
                    TileSpec.create("a"),
                    TileSpec.create("b"),
                    internetSpec,
                    TileSpec.create("c"),
                )
            val largeTiles = setOf(internetSpec, TileSpec.create("b"))
            val (migratedCurrent, migratedLarge) =
                underTest.migrateInternetTile(currentTiles, largeTiles, isMainUser = true) {
                    scenario = it
                }

            assertThat(scenario)
                .isEqualTo(InternetTileMigrationScenario.LARGE_INTERNET_TO_SMALL_WIFI_CELL)

            assertThat(migratedCurrent)
                .isEqualTo(
                    listOf(
                        TileSpec.create("a"),
                        TileSpec.create("b"),
                        wifiSpec,
                        cellSpec,
                        TileSpec.create("c"),
                    )
                )
            assertThat(migratedLarge).isEqualTo(setOf(TileSpec.create("b")))
        }

    @Test
    @EnableFlags(QsSplitInternetTile.FLAG_NAME)
    @DisableFlags(QsSplitInternetTile.SUPPRESSION_FLAG_NAME)
    fun smallInternetTile_toSmallWifi() =
        kosmos.runTest {
            val currentTiles =
                listOf(
                    TileSpec.create("a"),
                    TileSpec.create("b"),
                    internetSpec,
                    TileSpec.create("c"),
                )
            val largeTiles = setOf(TileSpec.create("b"))
            val (migratedCurrent, migratedLarge) =
                underTest.migrateInternetTile(currentTiles, largeTiles, isMainUser = true) {
                    scenario = it
                }

            assertThat(scenario)
                .isEqualTo(InternetTileMigrationScenario.SMALL_INTERNET_TO_SMALL_WIFI)

            assertThat(migratedCurrent)
                .isEqualTo(
                    listOf(
                        TileSpec.create("a"),
                        TileSpec.create("b"),
                        wifiSpec,
                        TileSpec.create("c"),
                    )
                )
            assertThat(migratedLarge).isEqualTo(largeTiles)
        }

    @Test
    @EnableFlags(QsSplitInternetTile.FLAG_NAME)
    @DisableFlags(QsSplitInternetTile.SUPPRESSION_FLAG_NAME)
    fun noInternetTile_noChanges() =
        kosmos.runTest {
            val currentTiles =
                listOf(TileSpec.create("a"), TileSpec.create("b"), TileSpec.create("c"))
            val largeTiles = setOf(TileSpec.create("b"))
            val (migratedCurrent, migratedLarge) =
                underTest.migrateInternetTile(currentTiles, largeTiles, isMainUser = true) {
                    scenario = it
                }
            assertThat(scenario).isNull()

            assertThat(migratedCurrent).isEqualTo(currentTiles)
            assertThat(migratedLarge).isEqualTo(largeTiles)
        }

    @Test
    @EnableFlags(QsSplitInternetTile.FLAG_NAME)
    @DisableFlags(QsSplitInternetTile.SUPPRESSION_FLAG_NAME)
    fun largeInternet_noMobileDataCapability_largeWifi() =
        kosmos.runTest {
            connectivityConstants.fake.hasDataCapabilities = false

            val currentTiles =
                listOf(TileSpec.create("a"), TileSpec.create("internet"), TileSpec.create("b"))
            val largeTiles = setOf(TileSpec.create("internet"))

            val (migratedCurrent, migratedLarge) =
                underTest.migrateInternetTile(currentTiles, largeTiles, isMainUser = true) {
                    scenario = it
                }
            assertThat(scenario)
                .isEqualTo(InternetTileMigrationScenario.LARGE_INTERNET_TO_LARGE_WIFI)

            assertThat(migratedCurrent)
                .isEqualTo(
                    listOf(TileSpec.create("a"), TileSpec.create("wifi"), TileSpec.create("b"))
                )
            assertThat(migratedLarge).isEqualTo(setOf(TileSpec.create("wifi")))
        }

    @Test
    @EnableFlags(QsSplitInternetTile.FLAG_NAME)
    @DisableFlags(QsSplitInternetTile.SUPPRESSION_FLAG_NAME)
    fun largeInternet_noMainUser_largeWifi() =
        kosmos.runTest {
            val currentTiles =
                listOf(TileSpec.create("a"), TileSpec.create("internet"), TileSpec.create("b"))
            val largeTiles = setOf(TileSpec.create("internet"))

            val (migratedCurrent, migratedLarge) =
                underTest.migrateInternetTile(currentTiles, largeTiles, isMainUser = false) {
                    scenario = it
                }
            assertThat(scenario)
                .isEqualTo(InternetTileMigrationScenario.LARGE_INTERNET_TO_LARGE_WIFI)

            assertThat(migratedCurrent)
                .isEqualTo(
                    listOf(TileSpec.create("a"), TileSpec.create("wifi"), TileSpec.create("b"))
                )
            assertThat(migratedLarge).isEqualTo(setOf(TileSpec.create("wifi")))
        }

    @Test
    @EnableFlags(QsSplitInternetTile.FLAG_NAME)
    @DisableFlags(QsSplitInternetTile.SUPPRESSION_FLAG_NAME)
    fun smallInternet_noMobileDataCapability_smallWifi() =
        kosmos.runTest {
            connectivityConstants.fake.hasDataCapabilities = false

            val currentTiles =
                listOf(TileSpec.create("a"), TileSpec.create("internet"), TileSpec.create("b"))
            val largeTiles = emptySet<TileSpec>()

            val (migratedCurrent, migratedLarge) =
                underTest.migrateInternetTile(currentTiles, largeTiles, isMainUser = true) {
                    scenario = it
                }
            assertThat(scenario)
                .isEqualTo(InternetTileMigrationScenario.SMALL_INTERNET_TO_SMALL_WIFI)

            assertThat(migratedCurrent)
                .isEqualTo(
                    listOf(TileSpec.create("a"), TileSpec.create("wifi"), TileSpec.create("b"))
                )
            assertThat(migratedLarge).isEmpty()
        }

    @Test
    @EnableFlags(QsSplitInternetTile.FLAG_NAME)
    @DisableFlags(QsSplitInternetTile.SUPPRESSION_FLAG_NAME)
    fun smallInternet_noMainUser_smallWifi() =
        kosmos.runTest {
            connectivityConstants.fake.hasDataCapabilities = false

            val currentTiles =
                listOf(TileSpec.create("a"), TileSpec.create("internet"), TileSpec.create("b"))
            val largeTiles = emptySet<TileSpec>()

            val (migratedCurrent, migratedLarge) =
                underTest.migrateInternetTile(currentTiles, largeTiles, isMainUser = false) {
                    scenario = it
                }
            assertThat(scenario)
                .isEqualTo(InternetTileMigrationScenario.SMALL_INTERNET_TO_SMALL_WIFI)

            assertThat(migratedCurrent)
                .isEqualTo(
                    listOf(TileSpec.create("a"), TileSpec.create("wifi"), TileSpec.create("b"))
                )
            assertThat(migratedLarge).isEmpty()
        }

    @Test
    @DisableFlags(QsSplitInternetTile.FLAG_NAME)
    fun wifiCellTogether_toInternetLarge() =
        kosmos.runTest {
            val currentTiles =
                listOf(
                    TileSpec.create("a"),
                    TileSpec.create("b"),
                    wifiSpec,
                    cellSpec,
                    TileSpec.create("c"),
                )
            val largeTiles = setOf(TileSpec.create("b"))
            val (migratedCurrent, migratedLarge) =
                underTest.migrateInternetTile(currentTiles, largeTiles, isMainUser = true) {
                    scenario = it
                }

            assertThat(scenario)
                .isEqualTo(InternetTileMigrationScenario.WIFI_CELL_ADJACENT_TO_LARGE_INTERNET)

            assertThat(migratedCurrent)
                .isEqualTo(
                    listOf(
                        TileSpec.create("a"),
                        TileSpec.create("b"),
                        internetSpec,
                        TileSpec.create("c"),
                    )
                )
            assertThat(migratedLarge).isEqualTo(setOf(TileSpec.create("b"), internetSpec))
        }

    @Test
    @DisableFlags(QsSplitInternetTile.FLAG_NAME)
    fun cellWifiTogether_toInternetLarge() =
        kosmos.runTest {
            val currentTiles =
                listOf(
                    TileSpec.create("a"),
                    TileSpec.create("b"),
                    cellSpec,
                    wifiSpec,
                    TileSpec.create("c"),
                )
            val largeTiles = setOf(TileSpec.create("b"))
            val (migratedCurrent, migratedLarge) =
                underTest.migrateInternetTile(currentTiles, largeTiles, isMainUser = true) {
                    scenario = it
                }

            assertThat(scenario)
                .isEqualTo(InternetTileMigrationScenario.WIFI_CELL_ADJACENT_TO_LARGE_INTERNET)

            assertThat(migratedCurrent)
                .isEqualTo(
                    listOf(
                        TileSpec.create("a"),
                        TileSpec.create("b"),
                        internetSpec,
                        TileSpec.create("c"),
                    )
                )
            assertThat(migratedLarge).isEqualTo(setOf(TileSpec.create("b"), internetSpec))
        }

    @Test
    @DisableFlags(QsSplitInternetTile.FLAG_NAME)
    fun wifiCellTogether_wifiLarge_toInternetLarge() =
        kosmos.runTest {
            val currentTiles =
                listOf(
                    TileSpec.create("a"),
                    TileSpec.create("b"),
                    wifiSpec,
                    cellSpec,
                    TileSpec.create("c"),
                )
            val largeTiles = setOf(wifiSpec, TileSpec.create("b"))
            val (migratedCurrent, migratedLarge) =
                underTest.migrateInternetTile(currentTiles, largeTiles, isMainUser = true) {
                    scenario = it
                }

            assertThat(scenario)
                .isEqualTo(InternetTileMigrationScenario.WIFI_CELL_ADJACENT_TO_LARGE_INTERNET)

            assertThat(migratedCurrent)
                .isEqualTo(
                    listOf(
                        TileSpec.create("a"),
                        TileSpec.create("b"),
                        internetSpec,
                        TileSpec.create("c"),
                    )
                )
            assertThat(migratedLarge).isEqualTo(setOf(TileSpec.create("b"), internetSpec))
        }

    @Test
    @DisableFlags(QsSplitInternetTile.FLAG_NAME)
    fun wifiCellTogether_cellLarge_toInternetLarge() =
        kosmos.runTest {
            val currentTiles =
                listOf(
                    TileSpec.create("a"),
                    TileSpec.create("b"),
                    wifiSpec,
                    cellSpec,
                    TileSpec.create("c"),
                )
            val largeTiles = setOf(cellSpec, TileSpec.create("b"))
            val (migratedCurrent, migratedLarge) =
                underTest.migrateInternetTile(currentTiles, largeTiles, isMainUser = true) {
                    scenario = it
                }

            assertThat(scenario)
                .isEqualTo(InternetTileMigrationScenario.WIFI_CELL_ADJACENT_TO_LARGE_INTERNET)

            assertThat(migratedCurrent)
                .isEqualTo(
                    listOf(
                        TileSpec.create("a"),
                        TileSpec.create("b"),
                        internetSpec,
                        TileSpec.create("c"),
                    )
                )
            assertThat(migratedLarge).isEqualTo(setOf(TileSpec.create("b"), internetSpec))
        }

    @Test
    @DisableFlags(QsSplitInternetTile.FLAG_NAME)
    fun wifiCellTogether_bothLarge_toInternetLarge() =
        kosmos.runTest {
            val currentTiles =
                listOf(
                    TileSpec.create("a"),
                    TileSpec.create("b"),
                    wifiSpec,
                    cellSpec,
                    TileSpec.create("c"),
                )
            val largeTiles = setOf(wifiSpec, cellSpec, TileSpec.create("b"))
            val (migratedCurrent, migratedLarge) =
                underTest.migrateInternetTile(currentTiles, largeTiles, isMainUser = true) {
                    scenario = it
                }

            assertThat(scenario)
                .isEqualTo(InternetTileMigrationScenario.WIFI_CELL_ADJACENT_TO_LARGE_INTERNET)

            assertThat(migratedCurrent)
                .isEqualTo(
                    listOf(
                        TileSpec.create("a"),
                        TileSpec.create("b"),
                        internetSpec,
                        TileSpec.create("c"),
                    )
                )
            assertThat(migratedLarge).isEqualTo(setOf(TileSpec.create("b"), internetSpec))
        }

    @Test
    @DisableFlags(QsSplitInternetTile.FLAG_NAME)
    fun wifiCellSeparate_wifiLarge_toInternetLarge_inWifiPosition() =
        kosmos.runTest {
            val currentTiles =
                listOf(
                    TileSpec.create("a"),
                    wifiSpec,
                    TileSpec.create("b"),
                    cellSpec,
                    TileSpec.create("c"),
                )
            val largeTiles = setOf(wifiSpec, TileSpec.create("b"))
            val (migratedCurrent, migratedLarge) =
                underTest.migrateInternetTile(currentTiles, largeTiles, isMainUser = true) {
                    scenario = it
                }

            assertThat(scenario)
                .isEqualTo(
                    InternetTileMigrationScenario.LARGE_WIFI_CELL_NOT_ADJACENT_TO_LARGE_INTERNET
                )

            assertThat(migratedCurrent)
                .isEqualTo(
                    listOf(
                        TileSpec.create("a"),
                        internetSpec,
                        TileSpec.create("b"),
                        TileSpec.create("c"),
                    )
                )
            assertThat(migratedLarge).isEqualTo(setOf(TileSpec.create("b"), internetSpec))
        }

    @Test
    @DisableFlags(QsSplitInternetTile.FLAG_NAME)
    fun wifiCellSeparate_cellLargeWifiSmall_toInternetSmall_inWifiPosition() =
        kosmos.runTest {
            val currentTiles =
                listOf(
                    TileSpec.create("a"),
                    wifiSpec,
                    TileSpec.create("b"),
                    cellSpec,
                    TileSpec.create("c"),
                )
            val largeTiles = setOf(cellSpec, TileSpec.create("b"))
            val (migratedCurrent, migratedLarge) =
                underTest.migrateInternetTile(currentTiles, largeTiles, isMainUser = true) {
                    scenario = it
                }

            assertThat(scenario)
                .isEqualTo(
                    InternetTileMigrationScenario.SMALL_WIFI_CELL_NOT_ADJACENT_TO_SMALL_INTERNET
                )

            assertThat(migratedCurrent)
                .isEqualTo(
                    listOf(
                        TileSpec.create("a"),
                        internetSpec,
                        TileSpec.create("b"),
                        TileSpec.create("c"),
                    )
                )
            assertThat(migratedLarge).isEqualTo(setOf(TileSpec.create("b")))
        }

    @Test
    @DisableFlags(QsSplitInternetTile.FLAG_NAME)
    fun wifiCellNotAdjacent_cellFirst_toInternet_inWifiPosition() =
        kosmos.runTest {
            val currentTiles =
                listOf(
                    TileSpec.create("a"),
                    cellSpec,
                    TileSpec.create("b"),
                    wifiSpec,
                    TileSpec.create("c"),
                )
            val largeTiles = setOf(cellSpec, TileSpec.create("b"))
            val (migratedCurrent, migratedLarge) =
                underTest.migrateInternetTile(currentTiles, largeTiles, isMainUser = true) {
                    scenario = it
                }

            assertThat(scenario)
                .isEqualTo(
                    InternetTileMigrationScenario.SMALL_WIFI_CELL_NOT_ADJACENT_TO_SMALL_INTERNET
                )

            assertThat(migratedCurrent)
                .isEqualTo(
                    listOf(
                        TileSpec.create("a"),
                        TileSpec.create("b"),
                        internetSpec,
                        TileSpec.create("c"),
                    )
                )
            assertThat(migratedLarge).isEqualTo(setOf(TileSpec.create("b")))
        }

    @Test
    @DisableFlags(QsSplitInternetTile.FLAG_NAME)
    fun smallWifiOnly_toSmallInternetInSamePosition() =
        kosmos.runTest {
            val currentTiles =
                listOf(TileSpec.create("a"), TileSpec.create("b"), wifiSpec, TileSpec.create("c"))
            val largeTiles = setOf(TileSpec.create("b"))
            val (migratedCurrent, migratedLarge) =
                underTest.migrateInternetTile(currentTiles, largeTiles, isMainUser = true) {
                    scenario = it
                }

            assertThat(scenario)
                .isEqualTo(InternetTileMigrationScenario.SMALL_WIFI_TO_SMALL_INTERNET_NO_CELL)

            assertThat(migratedCurrent)
                .isEqualTo(
                    listOf(
                        TileSpec.create("a"),
                        TileSpec.create("b"),
                        internetSpec,
                        TileSpec.create("c"),
                    )
                )
            assertThat(migratedLarge).isEqualTo(largeTiles)
        }

    @Test
    @DisableFlags(QsSplitInternetTile.FLAG_NAME)
    fun largeWifiOnly_toLargeInternetInSamePosition() =
        kosmos.runTest {
            val currentTiles =
                listOf(TileSpec.create("a"), TileSpec.create("b"), wifiSpec, TileSpec.create("c"))
            val largeTiles = setOf(TileSpec.create("b"), wifiSpec)
            val (migratedCurrent, migratedLarge) =
                underTest.migrateInternetTile(currentTiles, largeTiles, isMainUser = true) {
                    scenario = it
                }

            assertThat(scenario)
                .isEqualTo(InternetTileMigrationScenario.LARGE_WIFI_TO_LARGE_INTERNET_NO_CELL)

            assertThat(migratedCurrent)
                .isEqualTo(
                    listOf(
                        TileSpec.create("a"),
                        TileSpec.create("b"),
                        internetSpec,
                        TileSpec.create("c"),
                    )
                )
            assertThat(migratedLarge).isEqualTo(setOf(TileSpec.create("b"), internetSpec))
        }

    @Test
    @DisableFlags(QsSplitInternetTile.FLAG_NAME)
    fun smallCellOnly_toSmallInternetInSamePosition() =
        kosmos.runTest {
            val currentTiles =
                listOf(TileSpec.create("a"), TileSpec.create("b"), cellSpec, TileSpec.create("c"))
            val largeTiles = setOf(TileSpec.create("b"))
            val (migratedCurrent, migratedLarge) =
                underTest.migrateInternetTile(currentTiles, largeTiles, isMainUser = true) {
                    scenario = it
                }

            assertThat(scenario)
                .isEqualTo(InternetTileMigrationScenario.SMALL_CELL_TO_SMALL_INTERNET_NO_WIFI)

            assertThat(migratedCurrent)
                .isEqualTo(
                    listOf(
                        TileSpec.create("a"),
                        TileSpec.create("b"),
                        internetSpec,
                        TileSpec.create("c"),
                    )
                )
            assertThat(migratedLarge).isEqualTo(largeTiles)
        }

    @Test
    @DisableFlags(QsSplitInternetTile.FLAG_NAME)
    fun largeCellOnly_toLargeInternetInSamePosition() =
        kosmos.runTest {
            val currentTiles =
                listOf(TileSpec.create("a"), TileSpec.create("b"), cellSpec, TileSpec.create("c"))
            val largeTiles = setOf(TileSpec.create("b"), cellSpec)
            val (migratedCurrent, migratedLarge) =
                underTest.migrateInternetTile(currentTiles, largeTiles, isMainUser = true) {
                    scenario = it
                }

            assertThat(scenario)
                .isEqualTo(InternetTileMigrationScenario.LARGE_CELL_TO_LARGE_INTERNET_NO_WIFI)

            assertThat(migratedCurrent)
                .isEqualTo(
                    listOf(
                        TileSpec.create("a"),
                        TileSpec.create("b"),
                        internetSpec,
                        TileSpec.create("c"),
                    )
                )
            assertThat(migratedLarge).isEqualTo(setOf(TileSpec.create("b"), internetSpec))
        }

    @Test
    @DisableFlags(QsSplitInternetTile.FLAG_NAME)
    fun noWifiOrCellTile_noChanges() =
        kosmos.runTest {
            val currentTiles =
                listOf(TileSpec.create("a"), TileSpec.create("b"), TileSpec.create("c"))
            val largeTiles = setOf(TileSpec.create("b"))
            val (migratedCurrent, migratedLarge) =
                underTest.migrateInternetTile(currentTiles, largeTiles, isMainUser = true) {
                    scenario = it
                }

            assertThat(scenario).isNull()

            assertThat(migratedCurrent).isEqualTo(currentTiles)
            assertThat(migratedLarge).isEqualTo(largeTiles)
        }

    private companion object {
        val internetSpec = TileSpec.create(INTERNET_TILE_SPEC)
        val wifiSpec = TileSpec.create(WIFI_TILE_SPEC)
        val cellSpec = TileSpec.create(MOBILE_DATA_TILE_SPEC)
    }
}
