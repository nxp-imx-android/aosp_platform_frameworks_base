/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.statusbar.pipeline.shared.ui.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.android.compose.theme.colorAttr
import com.android.systemui.clock.ui.composable.ClockLegacy
import com.android.systemui.clock.ui.viewmodel.AmPmStyle
import com.android.systemui.clock.ui.viewmodel.ClockViewModel
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.res.R
import com.android.systemui.shade.ui.composable.VariableDayDate
import com.android.systemui.statusbar.pipeline.shared.ui.viewmodel.HomeStatusBarViewModel

/** Top level composable responsible for all UI shown for the Status Bar for DesktopMode. */
@Composable
fun DesktopStatusBar(
    viewModel: HomeStatusBarViewModel,
    clockViewModelFactory: ClockViewModel.Factory,
    modifier: Modifier = Modifier,
) {
    // TODO(433589833): Update padding values to match UX specs.
    Row(modifier = modifier.fillMaxWidth().padding(top = 8.dp, start = 12.dp, end = 12.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
            modifier = Modifier.padding(vertical = 4.dp),
        ) {
            // TODO(433589833): Add support for color themes.
            ClockLegacy(textColor = Color.White, onClick = null)

            val clockViewModel =
                rememberViewModel("HomeStatusBar.Clock") {
                    clockViewModelFactory.create(AmPmStyle.Gone)
                }
            VariableDayDate(
                longerDateText = clockViewModel.longerDateText,
                shorterDateText = clockViewModel.shorterDateText,
                textColor = colorAttr(R.attr.wallpaperTextColor),
            )
        }
    }
}
