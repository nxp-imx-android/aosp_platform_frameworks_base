/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.quickactions.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.ElementKey
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.ui.composable.Overlay
import javax.inject.Inject
import kotlinx.coroutines.awaitCancellation

@SysUISingleton
class QuickActionsOverlay @Inject constructor() : Overlay {

    override val key = Overlays.QuickActions
    override val alwaysCompose: Boolean = false

    override suspend fun activate(): Nothing {
        awaitCancellation()
    }

    @Composable override fun ContentScope.Content(modifier: Modifier) {}
}

object QuickActions {
    object Elements {
        val Panel = ElementKey("QuickActionsOverlayPanel")
    }
}
