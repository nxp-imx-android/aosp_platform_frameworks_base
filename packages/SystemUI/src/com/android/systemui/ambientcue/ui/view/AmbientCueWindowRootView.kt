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

package com.android.systemui.ambientcue.ui.view

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import com.android.compose.theme.PlatformTheme
import com.android.systemui.ambientcue.ui.compose.AmbientCueContainer
import com.android.systemui.ambientcue.ui.viewmodel.AmbientCueViewModel
import com.android.systemui.compose.ComposeInitializer
import com.android.systemui.dagger.qualifiers.Application
import javax.inject.Inject

/** A root view of the AmbientCue SysUI window. */
class AmbientCueWindowRootView
@Inject
constructor(
    @Application applicationContext: Context,
    ambientCueViewModelFactory: AmbientCueViewModel.Factory,
) : FrameLayout(applicationContext) {
    init {
        layoutParams =
            ViewGroup.LayoutParams(
                /* width = */ LayoutParams.MATCH_PARENT,
                /* height = */ LayoutParams.WRAP_CONTENT,
            )

        val composeView =
            ComposeView(context).apply {
                layoutParams =
                    LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                        gravity = Gravity.CENTER_VERTICAL
                        isClickable = true
                        isFocusable = true
                        isEnabled = true
                        defaultFocusHighlightEnabled = false
                        fitsSystemWindows = false
                    }
                setContent {
                    PlatformTheme {
                        AmbientCueContainer(
                            Modifier.fillMaxWidth().wrapContentHeight(),
                            ambientCueViewModelFactory,
                        )
                    }
                }
            }

        addView(composeView)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ComposeInitializer.onAttachedToWindow(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        ComposeInitializer.onDetachedFromWindow(this)
    }
}
