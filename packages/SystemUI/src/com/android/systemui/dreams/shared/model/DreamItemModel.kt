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

package com.android.systemui.dreams.shared.model

import android.content.ComponentName
import android.graphics.drawable.Icon
import android.service.dreams.DreamItem

/**
 * Immutable data model representing a single dream (screensaver). This class decouples SystemUI
 * from the framework's [android.service.dreams.DreamItem] class.
 */
data class DreamItemModel(
    val componentName: ComponentName,
    val title: CharSequence? = null,
    val description: CharSequence? = null,
    val icon: Icon? = null,
    val previewImage: Icon? = null,
    val settingsActivity: ComponentName? = null,
) {
    override fun toString(): String {
        return "$title (${componentName.flattenToShortString()})"
    }

    companion object {
        fun from(item: DreamItem): DreamItemModel {
            return DreamItemModel(
                componentName = item.componentName,
                title = item.title,
                description = item.description,
                icon = item.icon,
                previewImage = item.previewImage,
                settingsActivity = item.settingsActivity,
            )
        }
    }
}
