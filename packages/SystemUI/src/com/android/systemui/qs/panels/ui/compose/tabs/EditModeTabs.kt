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

package com.android.systemui.qs.panels.ui.compose.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.compose.modifiers.padding
import com.android.compose.theme.LocalAndroidColorScheme
import com.android.systemui.qs.panels.ui.viewmodel.EditModeTabViewModel

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EditModeTabs(
    viewModel: EditModeTabViewModel,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onTabChanged: () -> Unit = {},
) {
    val containerColor = LocalAndroidColorScheme.current.surfaceEffect1
    val selectedButtonColor = MaterialTheme.colorScheme.secondary
    val selectedTextColor = MaterialTheme.colorScheme.onSecondary
    val unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    HorizontalFloatingToolbar(
        modifier = modifier.height(60.dp),
        expanded = false,
        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 8.dp),
        colors =
            FloatingToolbarDefaults.standardFloatingToolbarColors(
                toolbarContainerColor = containerColor
            ),
    ) {
        EditModeTabViewModel.tabs.forEach { tab ->
            val isSelected = updateTransition(viewModel.selectedTab == tab)
            val selectionBackgroundAlpha by isSelected.animateFloat { if (it) 1f else 0f }
            val textColor by
                isSelected.animateColor {
                    if (it) {
                        selectedTextColor
                    } else {
                        unselectedTextColor
                    }
                }
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier.fillMaxHeight()
                        .clickable(enabled = enabled) {
                            if (!isSelected.currentState) {
                                onTabChanged()
                            }
                            viewModel.selectTab(tab)
                        }
                        .padding(horizontal = 5.dp)
                        .drawBehind {
                            drawRoundRect(
                                color = selectedButtonColor,
                                alpha = selectionBackgroundAlpha,
                                cornerRadius = CornerRadius(size.height / 2),
                            )
                        }
                        .padding(horizontal = 16.dp),
            ) {
                isSelected.AnimatedVisibility(
                    visible = { it },
                    enter = (fadeIn() + expandIn(expandFrom = Alignment.Center)),
                    exit = (fadeOut() + shrinkOut(shrinkTowards = Alignment.Center)),
                ) {
                    Icon(
                        imageVector = tab.titleIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                BasicText(stringResource(tab.titleResId), color = { textColor })
            }
        }
    }
}
