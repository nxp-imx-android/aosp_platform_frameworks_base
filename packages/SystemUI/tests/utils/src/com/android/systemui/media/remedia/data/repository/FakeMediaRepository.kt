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

package com.android.systemui.media.remedia.data.repository

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.android.internal.logging.InstanceId
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.media.remedia.data.model.MediaDataModel
import com.android.systemui.util.settings.FakeSettings
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

@SysUISingleton
@SuppressLint("VisibleForTests")
class FakeMediaRepository
@Inject
constructor(
    private val applicationContext: Context,
    private val applicationScope: CoroutineScope,
    val backgroundDispatcher: CoroutineDispatcher,
    private val fakeUserSettings: FakeSettings,
) :
    MediaRepository,
    MediaPipelineRepository(
        applicationContext,
        applicationScope,
        backgroundDispatcher,
        fakeUserSettings,
    ) {
    override var currentMedia: SnapshotStateList<MediaDataModel> = mutableStateListOf()
    override val keysNeedRemoval = mutableListOf(InstanceId.fakeInstanceId(1))
    override var currentCarouselIndex by mutableIntStateOf(FIRST_INDEX_OF_CAROUSEL)
    override var shouldScrollToFirst = false
    override var isSwipedAway = false
    override var isUserInitiatedRemovalQueued = true
    override var isGutsVisible by mutableStateOf(false)
    override val allowMediaOnLockscreen = true
    override val visualStabilityListenerFlow: Flow<Unit> = emptyFlow()
    override val isReorderingAllowed = true

    override fun seek(sessionKey: InstanceId, to: Long) = Unit

    override fun reorderMedia() {
        currentCarouselIndex = FIRST_INDEX_OF_CAROUSEL
        isGutsVisible = false
        isUserInitiatedRemovalQueued = false
    }

    override fun storeCarouselIndex(index: Int) {
        currentCarouselIndex = index
    }

    override fun resetScrollToFirst() {
        shouldScrollToFirst = false
    }

    override fun storeIsGutsVisible(isGutsVisible: Boolean) {
        this.isGutsVisible = isGutsVisible
    }

    override fun setSwipedAwayState() {
        isSwipedAway = true
    }

    override fun cleanKeysNeedRemoval() {
        keysNeedRemoval.clear()
    }

    override fun clearCurrentUserMedia() = Unit

    fun setFakeCurrentMedia(mediaList: List<MediaDataModel>) {
        currentMedia.clear()
        currentMedia.addAll(mediaList)
    }

    private companion object {
        const val FIRST_INDEX_OF_CAROUSEL = 0
    }
}
