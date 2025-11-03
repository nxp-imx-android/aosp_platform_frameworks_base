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

package com.android.server.personalcontext.component;

import android.service.personalcontext.insight.ContextInsight;

import androidx.annotation.NonNull;

/**
 * Interface to abstract away in-process / service-based refiners.
 *
 * @hide
 */
public interface Renderer extends Component {
    /** Gets the insights this renderer is interested in. */
    boolean isInterestedInInsight(ContextInsight insight);

    /** Renders an insight. */
    void render(@NonNull ContextInsight insight);
}
