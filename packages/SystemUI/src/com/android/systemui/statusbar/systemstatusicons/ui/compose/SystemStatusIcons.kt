/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.systemui.statusbar.systemstatusicons.ui.compose

import android.graphics.Rect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onLayoutRectChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.android.systemui.common.ui.compose.Icon
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.statusbar.phone.domain.interactor.IsAreaDark
import com.android.systemui.statusbar.pipeline.mobile.ui.compose.MobileIcons
import com.android.systemui.statusbar.systemstatusicons.ui.viewmodel.SystemStatusIconViewModel
import com.android.systemui.statusbar.systemstatusicons.ui.viewmodel.SystemStatusIconsViewModel

/**
 * Composable that displays the system status icons. This does not handle any spacing or alignment.
 * That is expected to be done in a container composable like a Row.
 */
@Composable
fun SystemStatusIcons(
    viewModelFactory: SystemStatusIconsViewModel.Factory,
    isDark: IsAreaDark,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var bounds by remember { mutableStateOf(Rect()) }
    val color = if (isDark.isDark(bounds)) Color.White else Color.Black
    CompositionLocalProvider(LocalContentColor provides color) {
        val viewModel =
            rememberViewModel(traceName = "SystemStatusIcons") { viewModelFactory.create(context) }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier =
                modifier.onLayoutRectChanged { relativeLayoutBounds ->
                    bounds =
                        with(relativeLayoutBounds.boundsInScreen) { Rect(left, top, right, bottom) }
                },
        ) {
            viewModel.iconViewModels
                .filter { it.visible }
                .forEach { iconViewModel ->
                    // TODO(414653733): Make sure icons are sized uniformly.
                    when (iconViewModel) {
                        is SystemStatusIconViewModel.Default ->
                            iconViewModel.icon?.let {
                                Icon(
                                    icon = it,
                                    modifier = Modifier.size(20.dp).padding(1.dp),
                                    tint = LocalContentColor.current,
                                )
                            }
                        is SystemStatusIconViewModel.MobileIcons -> {
                            MobileIcons(
                                iconViewModel.mobileIconsViewModel,
                                iconViewModel.stackedMobileIconViewModel,
                            )
                        }
                    }
                }
        }
    }
}
