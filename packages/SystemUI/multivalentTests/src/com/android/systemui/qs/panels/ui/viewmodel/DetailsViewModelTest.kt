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

package com.android.systemui.qs.panels.ui.viewmodel

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags.FLAG_DUAL_SHADE
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.qs.FakeQSTile
import com.android.systemui.qs.pipeline.data.repository.tileSpecRepository
import com.android.systemui.qs.pipeline.domain.interactor.currentTilesInteractor
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.tiles.dialog.audioDetailsViewModelFactory
import com.android.systemui.shade.domain.interactor.disableDualShade
import com.android.systemui.shade.domain.interactor.enableDualShade
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
@EnableSceneContainer
class DetailsViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val spec = TileSpec.create("internet")
    private val specNoDetails = TileSpec.create("NoDetailsTile")

    private val Kosmos.underTest: DetailsViewModel by Kosmos.Fixture { detailsViewModel }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun changeTileDetailsViewModelWithDualShadeEnabled() =
        kosmos.runTest {
            enableDualShade()
            val specs = listOf(spec, specNoDetails)
            tileSpecRepository.setTiles(userId = 0, specs)

            val tiles by collectLastValue(currentTilesInteractor.currentTiles)

            assertThat(currentTilesInteractor.currentTilesSpecs).hasSize(2)

            val secondTile = checkNotNull(tiles)[1]
            assertThat(secondTile.spec).isEqualTo(specNoDetails)
            (secondTile.tile as FakeQSTile).hasDetailsViewModel = false

            assertThat(underTest.activeTileDetails).isNull()

            // Click on the tile that has the `spec`.
            assertThat(underTest.onTileClicked(spec)).isTrue()
            assertThat(underTest.activeTileDetails).isNotNull()
            assertThat(underTest.activeTileDetails?.title).isEqualTo("internet")

            // Click on a tile that doesn't have a valid spec.
            assertThat(underTest.onTileClicked(null)).isFalse()
            assertThat(underTest.activeTileDetails).isNull()

            // Click again on the tile that has the `spec`.
            assertThat(underTest.onTileClicked(spec)).isTrue()
            assertThat(underTest.activeTileDetails).isNotNull()
            assertThat(underTest.activeTileDetails?.title).isEqualTo("internet")

            // Click on a tile that doesn't have a detailed view.
            assertThat(underTest.onTileClicked(specNoDetails)).isFalse()
            assertThat(underTest.activeTileDetails).isNull()

            // Click on the volume settings button.
            underTest.onVolumeSettingsButtonClicked(audioDetailsViewModelFactory.create())
            assertThat(underTest.activeTileDetails).isNotNull()
            assertThat(underTest.activeTileDetails?.title).isEqualTo("Volume")

            underTest.closeDetailedView()
            assertThat(underTest.activeTileDetails).isNull()

            assertThat(underTest.onTileClicked(null)).isFalse()
        }

    @Test
    @DisableFlags(FLAG_DUAL_SHADE)
    fun ignoreChangingTileDetailsViewModelWithDualShadeDisabled() =
        kosmos.runTest {
            disableDualShade()
            val specs = listOf(spec, specNoDetails)
            tileSpecRepository.setTiles(userId = 0, specs)

            val tiles by collectLastValue(currentTilesInteractor.currentTiles)

            assertThat(currentTilesInteractor.currentTilesSpecs).hasSize(2)

            val secondTile = checkNotNull(tiles)[1]
            assertThat(secondTile.spec).isEqualTo(specNoDetails)
            (secondTile.tile as FakeQSTile).hasDetailsViewModel = false

            assertThat(underTest.activeTileDetails).isNull()

            // Click on the tile that has the `spec`.
            assertThat(underTest.onTileClicked(spec)).isFalse()
            assertThat(underTest.activeTileDetails).isNull()

            // Click on a tile that doesn't have a valid spec.
            assertThat(underTest.onTileClicked(null)).isFalse()
            assertThat(underTest.activeTileDetails).isNull()

            // Click on a tile that doesn't have a detailed view.
            assertThat(underTest.onTileClicked(specNoDetails)).isFalse()
            assertThat(underTest.activeTileDetails).isNull()
        }
}
