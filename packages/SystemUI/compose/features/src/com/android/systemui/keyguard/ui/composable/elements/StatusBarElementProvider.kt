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

package com.android.systemui.keyguard.ui.composable.elements

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.android.compose.animation.scene.ElementContentScope
import com.android.compose.modifiers.height
import com.android.keyguard.dagger.KeyguardStatusBarViewComponent
import com.android.systemui.common.ui.compose.windowinsets.LocalDisplayCutout
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.plugins.keyguard.ui.composable.elements.BaseLockscreenElement.ElementSource
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElement
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementProvider
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenScope
import com.android.systemui.res.R
import com.android.systemui.shade.NotificationPanelView
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.shade.ShadeViewStateProvider
import com.android.systemui.statusbar.phone.KeyguardStatusBarView
import com.android.systemui.statusbar.ui.binder.KeyguardStatusBarViewBinder
import com.android.systemui.statusbar.ui.viewmodel.KeyguardStatusBarViewModel
import com.android.systemui.util.Utils
import dagger.Lazy
import javax.inject.Inject
import kotlin.collections.List

@SysUISingleton
class StatusBarElementProvider
@Inject
constructor(
    @ShadeDisplayAware private val context: Context,
    private val componentFactory: KeyguardStatusBarViewComponent.Factory,
    private val notificationPanelView: Lazy<NotificationPanelView>,
    private val viewModelFactory: KeyguardStatusBarViewModel.Factory,
) : LockscreenElementProvider {
    override val elements: List<LockscreenElement> by lazy { listOf(StatusBarElement()) }

    private inner class StatusBarElement : LockscreenElement {
        override val key = LockscreenElementKeys.StatusBar
        override val context = this@StatusBarElementProvider.context
        override val source = ElementSource.STANDARD

        @Composable
        override fun LockscreenScope<ElementContentScope>.LockscreenElement() {
            StatusBar(viewModelFactory = viewModelFactory, modifier = Modifier.fillMaxWidth())
        }
    }

    @Composable
    fun StatusBar(
        viewModelFactory: KeyguardStatusBarViewModel.Factory,
        modifier: Modifier = Modifier,
    ) {
        val context = LocalContext.current
        val displayCutout = LocalDisplayCutout.current

        @SuppressLint("InflateParams")
        val view =
            remember(context) {
                (LayoutInflater.from(context).inflate(R.layout.keyguard_status_bar, null, false)
                        as KeyguardStatusBarView)
                    .also {
                        it.layoutParams =
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                            )
                    }
            }
        val viewController =
            remember(context) {
                val provider =
                    object : ShadeViewStateProvider {
                        override val lockscreenShadeDragProgress: Float = 0f
                        override val panelViewExpandedHeight: Float = 0f
                    }

                componentFactory.build(view, provider).keyguardStatusBarViewController
            }

        val viewModel =
            rememberViewModel("KeyguardStatusBarViewModel") { viewModelFactory.create() }
        AndroidView(
            factory = {
                notificationPanelView.get().findViewById<View>(R.id.keyguard_header)?.let {
                    (it.parent as ViewGroup).removeView(it)
                }
                KeyguardStatusBarViewBinder.bind(view, viewModel)
                viewController.init()
                view
            },
            modifier =
                modifier.fillMaxWidth().height { Utils.getStatusBarHeaderHeightKeyguard(context) },
            update = {
                viewController.setDisplayCutout(
                    displayCutout().viewDisplayCutoutKeyguardStatusBarView
                )
            },
        )
    }
}
