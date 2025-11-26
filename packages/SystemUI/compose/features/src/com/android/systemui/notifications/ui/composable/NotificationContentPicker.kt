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

package com.android.systemui.notifications.ui.composable

import com.android.compose.animation.scene.ContentKey
import com.android.systemui.scene.shared.model.SceneContainerConfig
import javax.inject.Inject

interface NotificationContentPicker {
    /** Returns a content from the provided [keys] or null. */
    fun pickContentFrom(keys: Set<ContentKey>): ContentKey?
}

/**
 * A content picker for [StackPlaceholderStateStorage] that selects the [ContentKey] with the
 * highest z index defined in [SceneContainerConfig].
 */
class StackPlaceholderContentPicker @Inject constructor(config: SceneContainerConfig) :
    NotificationContentPicker {
    /**
     * The keys of contents sorted by z-order such that the last one renders on top of all previous
     * ones.
     */
    private val contentKeysByZOrder by lazy { config.sceneKeys + config.overlayKeys }

    override fun pickContentFrom(keys: Set<ContentKey>): ContentKey? {
        // Select the content drawn on top.
        return contentKeysByZOrder.lastOrNull { it in keys }
    }
}

/**
 * A content picker for [HeadsUpPlaceholderStateStorage] that selects the [ContentKey] with the
 * lowest z index defined in [SceneContainerConfig].
 */
class HeadsUpPlaceholderContentPicker @Inject constructor(config: SceneContainerConfig) :
    NotificationContentPicker {
    /**
     * The keys of contents sorted by z-order such that the last one renders on top of all previous
     * ones.
     */
    private val contentKeysByZOrder by lazy { (config.sceneKeys + config.overlayKeys) }

    override fun pickContentFrom(keys: Set<ContentKey>): ContentKey? {
        // Select the content drawn at the bottom.
        return contentKeysByZOrder.firstOrNull { it in keys }
    }
}
