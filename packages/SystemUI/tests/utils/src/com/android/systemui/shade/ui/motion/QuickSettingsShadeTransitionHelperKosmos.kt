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

package com.android.systemui.shade.ui.motion

import com.android.systemui.kosmos.Kosmos
import com.android.systemui.qs.composefragment.dagger.usingMediaInComposeFragment
import com.android.systemui.qs.pipeline.data.repository.tileSpecRepository
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.shade.domain.interactor.enableDualShade
import com.android.systemui.user.data.repository.userRepository

/**
 * Sets up the environment for testing Quick Settings shade transitions.
 *
 * This function is a test helper that prepares the system for tests involving the Quick Settings
 * shade. It enables dual shade, ensures that media notifications are handled in the compose
 * fragment, and populates the Quick Settings with a specified number of mock tiles.
 *
 * This is useful for creating a consistent and realistic starting state for motion and UI tests
 * that interact with the Quick Settings shade.
 *
 * @param numTiles The number of mock tiles to create and display in the Quick Settings grid.
 * @sample com.android.systemui.shade.ui.motion.samples.setupQuickSettingsShadeOverlayTransitionSample
 */
suspend fun Kosmos.setupQuickSettingsShadeOverlayTransition(numTiles: Int) {
    this.enableDualShade(wideLayout = false)
    this.usingMediaInComposeFragment = true
    val tileSpecs = getTilesList(numTiles).map { TileSpec.create(it) }

    this.tileSpecRepository.setTiles(this.userRepository.getSelectedUserInfo().id, tileSpecs)
}

private fun getTilesList(numTiles: Int): List<String> =
    (1..numTiles).map { number -> "Tile$number" }
