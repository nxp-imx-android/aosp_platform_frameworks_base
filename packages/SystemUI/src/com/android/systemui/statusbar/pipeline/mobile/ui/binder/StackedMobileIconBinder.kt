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

package com.android.systemui.statusbar.pipeline.mobile.ui.binder

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.lifecycle.repeatWhenAttached
import com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.MobileIconsViewModel
import com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.StackedMobileIconViewModel
import com.android.systemui.statusbar.pipeline.shared.ui.binder.ModernStatusBarViewBinding
import com.android.systemui.statusbar.pipeline.shared.ui.composable.StackedMobileIcon
import com.android.systemui.statusbar.pipeline.shared.ui.view.SingleBindableStatusBarComposeIconView

object StackedMobileIconBinder {
    fun bind(
        view: SingleBindableStatusBarComposeIconView,
        mobileIconsViewModel: MobileIconsViewModel,
        viewModelFactory: StackedMobileIconViewModel.Factory,
    ): ModernStatusBarViewBinding {
        return SingleBindableStatusBarComposeIconView.withDefaultBinding(
            view = view,
            shouldBeVisible = { mobileIconsViewModel.isStackable.value },
        ) { _, tint ->
            view.repeatWhenAttached {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    view.composeView.apply {
                        setViewCompositionStrategy(
                            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
                        )
                        setContent {
                            val viewModel =
                                rememberViewModel("StackedMobileIconBinder") {
                                    viewModelFactory.create()
                                }
                            if (viewModel.isIconVisible) {
                                CompositionLocalProvider(LocalContentColor provides Color(tint())) {
                                    StackedMobileIcon(viewModel)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
