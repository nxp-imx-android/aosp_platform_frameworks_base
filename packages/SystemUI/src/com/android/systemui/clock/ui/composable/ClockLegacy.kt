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

package com.android.systemui.clock.ui.composable

import android.view.ContextThemeWrapper
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import android.util.TypedValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.viewinterop.AndroidView
import com.android.compose.modifiers.thenIf
import com.android.systemui.res.R

/**
 * Composable wrapper around the legacy [com.android.systemui.statusbar.policy.Clock] view. Will be
 * replaced by the fully composable [Clock].
 */
@Composable
fun ClockLegacy(
    textColor: Color,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    textStyle: TextStyle? = null,
) {
    val intColor = remember(textColor) { textColor.toArgb() }
    val density = LocalDensity.current
    val textPx = remember(textStyle, density) {
        textStyle?.let { with(density) { it.fontSize.toPx() } }
    }

    AndroidView(
        factory = { context ->
            val clock = com.android.systemui.statusbar.policy.Clock(
                ContextThemeWrapper(context, R.style.Theme_SystemUI_DesktopStatusBar),
                null,
            )
            // Desktop status bar handles its own padding.
            clock.setShouldApplyPadding(false)
            clock
        },
        update = { view ->
            view.setTextColor(intColor)
            textPx?.let {
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX, it)
            }
        },
        modifier = modifier.thenIf(onClick != null) { Modifier.clickable { onClick?.invoke() } },
    )
}
