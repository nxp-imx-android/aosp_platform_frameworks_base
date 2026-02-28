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

package com.android.systemui.statusbar.pipeline.shared.ui.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.android.compose.animation.scene.HoistedSceneTransitionLayoutState
import com.android.compose.animation.scene.SceneKey
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.DisplayAware
import com.android.systemui.headline.ui.viewmodel.HeadlineItem
import com.android.systemui.headline.ui.viewmodel.HeadlineItemsAdapter
import com.android.systemui.headline.ui.viewmodel.HeadlineViewModel
import com.android.systemui.headline.ui.viewmodel.HeadlineViewModel.Companion.GoneScene
import com.android.systemui.lifecycle.HydratedActivatable
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

class HeadlineViewModelImpl
@AssistedInject
constructor(@DisplayAware private val adapter: HeadlineItemsAdapter) :
    HeadlineViewModel, HydratedActivatable() {

    override val state: HoistedSceneTransitionLayoutState =
        HoistedSceneTransitionLayoutState(initialScene = GoneScene)

    private val _items: SnapshotStateList<HeadlineItem> = mutableStateListOf()
    override val items: List<HeadlineItem> = _items

    private fun updateItems(items: List<HeadlineItem>) {
        _items.apply {
            clear()

            // Add all new items, which are sorted by priority
            addAll(items)
        }

        // Transition to the first new item
        snapTo(items.firstOrNull()?.key?.toSceneKey() ?: GoneScene)
    }

    private fun snapTo(scene: SceneKey?) {
        val targetScene = scene ?: GoneScene

        // TODO(b/486143387): Use the compose coroutine context to animate scene change
        state.snapTo(targetScene)
    }

    override fun onItemClicked(item: HeadlineItem) {
        snapTo(item.key.toSceneKey())
    }

    override suspend fun onActivated() {
        adapter.headlineItems.collect { updateItems(it) }
    }

    @AssistedFactory
    interface Factory : HeadlineViewModel.Factory {
        override fun create(): HeadlineViewModelImpl
    }
}
