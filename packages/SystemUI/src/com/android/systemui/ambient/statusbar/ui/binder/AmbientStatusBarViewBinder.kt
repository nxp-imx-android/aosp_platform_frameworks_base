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

package com.android.systemui.ambient.statusbar.ui.binder

import android.content.Context
import android.widget.LinearLayout
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.android.systemui.ambient.statusbar.ui.AmbientStatusBarViewModel
import com.android.systemui.compose.modifiers.sysUiResTagContainer
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.lifecycle.repeatWhenAttached
import com.android.systemui.statusbar.chips.ui.compose.OngoingActivityChips
import com.android.systemui.statusbar.core.StatusBarConnectedDisplays
import com.android.systemui.statusbar.notification.icon.ui.viewbinder.ConnectedDisplaysStatusBarNotificationIconViewStore

object AmbientStatusBarViewBinder {

    /**
     * Binds the ongoing activity chips view to the view model, and sets content for the compose
     * view.
     */
    @JvmStatic
    fun bindOngoingActivityChipsView(
        context: Context,
        ongoingActivityChipsView: ComposeView,
        ambientStatusBarViewModelFactory: AmbientStatusBarViewModel.Factory,
        iconViewStoreFactory: ConnectedDisplaysStatusBarNotificationIconViewStore.Factory,
    ) {
        ongoingActivityChipsView.repeatWhenAttached {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                with(ongoingActivityChipsView) {
                    layoutParams =
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        )

                    setContent {
                        val viewModel =
                            rememberViewModel("DreamStatusBar.AmbientStatusBarViewModel") {
                                ambientStatusBarViewModelFactory.create()
                            }
                        val iconViewStore =
                            if (StatusBarConnectedDisplays.isEnabled) {
                                rememberViewModel("DreamStatusBar.IconViewStore") {
                                    iconViewStoreFactory.create(context.displayId)
                                }
                            } else {
                                null
                            }
                        val chips by viewModel.ongoingActivityChips.collectAsStateWithLifecycle()

                        OngoingActivityChips(
                            chips = chips,
                            iconViewStore = iconViewStore,
                            onChipBoundsChanged = viewModel::onChipBoundsChanged,
                            modifier = Modifier.sysUiResTagContainer(),
                        )
                    }
                }
            }
        }
    }
}
