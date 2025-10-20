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

package com.android.systemui.scene.ui.composable

import com.android.systemui.scene.shared.model.Scenes

/** The navigation distances of all scenes. */
val SceneNavigationDistances =
    mapOf(
        Scenes.Gone to 0,
        Scenes.Lockscreen to 0,
        Scenes.Communal to 1,
        Scenes.Dream to 2,
        Scenes.Occluded to 3,
        Scenes.Shade to 4,
        Scenes.QuickSettings to 5,
    )
