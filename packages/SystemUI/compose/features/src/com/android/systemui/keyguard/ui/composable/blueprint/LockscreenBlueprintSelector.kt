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

package com.android.systemui.keyguard.ui.composable.blueprint

import androidx.compose.runtime.getValue
import com.android.systemui.keyguard.domain.interactor.KeyguardBlueprintInteractor
import com.android.systemui.lifecycle.HydratedActivatable
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.mapNotNull

/**
 * Selects the appropriate blueprint to use in the lockscreen.
 *
 * TODO(b/445751559): If we decide to keep this, it doesn't need to behave like a viewmodel
 */
class LockscreenBlueprintSelector
@AssistedInject
constructor(
    defaultBlueprint: DefaultBlueprint,
    blueprintInteractor: KeyguardBlueprintInteractor,
    blueprints: Set<@JvmSuppressWildcards ComposableLockscreenSceneBlueprint>,
) : HydratedActivatable() {
    /** Blueprints keyed by their ids */
    private val blueprintsById = blueprints.associateBy { it.id }

    /** Current Blueprint to use, if any */
    val blueprint: ComposableLockscreenSceneBlueprint by
        blueprintInteractor.blueprintId
            .mapNotNull { blueprintsById[it] }
            .hydratedStateOf(traceName = "blueprint", initialValue = defaultBlueprint)

    @AssistedFactory
    interface Factory {
        fun create(): LockscreenBlueprintSelector
    }
}
