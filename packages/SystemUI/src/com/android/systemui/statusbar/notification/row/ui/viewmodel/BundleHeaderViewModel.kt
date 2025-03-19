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

package com.android.systemui.statusbar.notification.row.ui.viewmodel

import android.graphics.drawable.Drawable
import android.view.View
import androidx.compose.animation.core.tween
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.android.compose.animation.scene.MutableSceneTransitionLayoutState
import com.android.compose.animation.scene.transitions
import com.android.systemui.notifications.ui.composable.row.BundleHeader
import com.android.systemui.statusbar.notification.row.domain.interactor.BundleInteractor
import kotlinx.coroutines.CoroutineScope

class BundleHeaderViewModel(val interactor: BundleInteractor) {
    val titleText
        get() = interactor.titleText

    val numberOfChildren
        get() = interactor.numberOfChildren

    val hasUnreadMessages
        get() = interactor.hasUnreadMessages

    val bundleIcon
        get() = interactor.bundleIcon

    val previewIcons
        get() = interactor.previewIcons

    var backgroundDrawable by mutableStateOf<Drawable?>(null)

    var onExpandClickListener: View.OnClickListener? = null

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    val state: MutableSceneTransitionLayoutState =
        MutableSceneTransitionLayoutState(
            BundleHeader.Scenes.Collapsed,
            MotionScheme.standard(),
            transitions {
                from(BundleHeader.Scenes.Collapsed, to = BundleHeader.Scenes.Expanded) {
                    spec = tween(500)
                    translate(BundleHeader.Elements.PreviewIcon3, x = 32.dp)
                    translate(BundleHeader.Elements.PreviewIcon2, x = 16.dp)
                    fade(BundleHeader.Elements.PreviewIcon1)
                    fade(BundleHeader.Elements.PreviewIcon2)
                    fade(BundleHeader.Elements.PreviewIcon3)
                }
            },
        )

    fun onHeaderClicked(scope: CoroutineScope) {
        val targetScene =
            when (state.currentScene) {
                BundleHeader.Scenes.Collapsed -> {
                    interactor.rowExpanded()
                    BundleHeader.Scenes.Expanded
                }
                BundleHeader.Scenes.Expanded -> BundleHeader.Scenes.Collapsed
                else -> error("Unknown Scene")
            }
        state.setTargetScene(targetScene, scope)

        onExpandClickListener?.onClick(null)
    }
}
