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

import android.widget.ImageView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateSet
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.util.fastAny
import com.android.compose.animation.scene.HoistedSceneTransitionLayoutState
import com.android.compose.animation.scene.SceneKey
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.DisplayAware
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.DisplayId
import com.android.systemui.headline.ui.viewmodel.HeadlineItem
import com.android.systemui.headline.ui.viewmodel.HeadlineItemKey
import com.android.systemui.headline.ui.viewmodel.HeadlineItemsAdapter
import com.android.systemui.headline.ui.viewmodel.HeadlineViewModel
import com.android.systemui.headline.ui.viewmodel.HeadlineViewModel.Companion.GoneScene
import com.android.systemui.headline.ui.viewmodel.toHeadlineItemKey
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.statusbar.notification.icon.ui.viewbinder.ConnectedDisplaysStatusBarNotificationIconViewStore
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class HeadlineViewModelImpl
@AssistedInject
constructor(
    @field:DisplayId @DisplayAware thisDisplayId: Int,
    @DisplayAware private val adapter: HeadlineItemsAdapter,
    iconViewStoreFactory: ConnectedDisplaysStatusBarNotificationIconViewStore.Factory,
) : HeadlineViewModel, HydratedActivatable() {

    private val iconViewStore: ConnectedDisplaysStatusBarNotificationIconViewStore =
        iconViewStoreFactory.create(thisDisplayId)

    override val state: HoistedSceneTransitionLayoutState =
        HoistedSceneTransitionLayoutState(
            initialScene = GoneScene,
            canChangeScene = { scene -> !keysToPrune.contains(scene.toHeadlineItemKey()) },
            onTransitionEnd = { pruneItems() },
            onSnap = { pruneItems() },
        )

    private val _items: SnapshotStateList<HeadlineItem> = mutableStateListOf()
    override val items: List<HeadlineItem> = _items

    private val keysToPrune: SnapshotStateSet<HeadlineItemKey> = mutableStateSetOf()

    private fun updateItems(items: List<HeadlineItem>) {
        val newKeys = items.map { it.key }.toSet()

        // Find the items that are deleted but still composed
        val composedDeletedItems =
            _items.filter { !newKeys.contains(it.key) && willItemSceneBeComposed(it) }

        _items.apply {
            clear()

            // Add all new items, which are sorted by priority
            addAll(items)

            // Add the deleted items while waiting for them to be pruned when safe to do so
            addAll(composedDeletedItems)
        }
        keysToPrune.addAll(composedDeletedItems.map { it.key })

        // Transition to the first new item
        animateOrSnapTo(items.firstOrNull()?.key?.toSceneKey() ?: GoneScene)
    }

    private fun animateOrSnapTo(scene: SceneKey?) {
        val targetScene = scene ?: GoneScene

        val uiBoundState = state.uiBoundState
        if (uiBoundState != null) {
            uiBoundState.setTargetScene(targetScene)
        } else {
            state.snapTo(targetScene)
        }
    }

    private fun pruneItems() {
        // Clear items that are safe to remove
        val keysSafeToPrune =
            items
                .filter { keysToPrune.contains(it.key) && !willItemSceneBeComposed(it) }
                .map { it.key }
                .toSet()

        _items.removeAll { keysSafeToPrune.contains(it.key) }
        keysToPrune.removeAll(keysSafeToPrune)
    }

    private fun willItemSceneBeComposed(item: HeadlineItem): Boolean {
        val scene = item.key.toSceneKey()
        return scene == state.currentScene ||
            state.currentTransitions.fastAny { it.fromContent == scene || it.toContent == scene }
    }

    override fun onItemClicked(item: HeadlineItem) {
        animateOrSnapTo(item.key.toSceneKey())
    }

    override fun iconView(key: String): ImageView? {
        return iconViewStore.iconView(key)
    }

    override var uiBounds: Rect by mutableStateOf(Rect.Zero)

    override suspend fun onActivated() {
        coroutineScope {
            launch { iconViewStore.activate() }
            launch { adapter.headlineItems.collect { updateItems(it) } }
        }
    }

    @AssistedFactory
    interface Factory : HeadlineViewModel.Factory {
        override fun create(): HeadlineViewModelImpl
    }
}
