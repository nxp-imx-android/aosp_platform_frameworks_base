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

package com.android.server.wm;


import android.annotation.NonNull;
import android.annotation.Nullable;

/**
 * Centralizes visibility-related logic for window containers. This helper determines visibility
 * states, occlusion, and whether content is filling its container.
 */
interface WindowContainerVisibilityHelper {

    /**
     * Returns the visibility state of the given {@link TaskFragment}.
     *
     * @param current the {@link TaskFragment} to check visibility for.
     * @param starting the currently starting activity or {@code null} if there is none.
     */
    @TaskFragment.TaskFragmentVisibility
    int getTaskFragmentVisibility(@NonNull TaskFragment current, @Nullable ActivityRecord starting);
}
