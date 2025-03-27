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

package com.android.systemui.underlay.ui.view

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import com.android.systemui.compose.ComposeInitializer

/** A root view of the Underlay SysUI window. */
class UnderlayWindowRootView(context: Context) : FrameLayout(context) {

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
