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
package com.android.systemui.communal.domain.definition

import android.content.ComponentName
import com.android.systemui.Dumpable
import kotlinx.coroutines.flow.Flow

interface ContextualSetupDefinition : Dumpable {
    /** Unique stable ID for persistence. */
    val id: String

    /** The component to launch. */
    val target: SetupTarget

    /**
     * Unified flow: Emits true when the Setup Activity should be launched. The implementation
     * handles all logic (Trigger + Preconditions + Power Optimization).
     */
    val isReady: Flow<Boolean>
}

sealed interface SetupTarget {
    val componentName: ComponentName

    @JvmInline value class Activity(override val componentName: ComponentName) : SetupTarget

    @JvmInline value class Service(override val componentName: ComponentName) : SetupTarget
}
