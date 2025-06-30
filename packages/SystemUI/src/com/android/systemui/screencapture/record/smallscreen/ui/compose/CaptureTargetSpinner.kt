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

package com.android.systemui.screencapture.record.smallscreen.ui.compose

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import com.android.systemui.res.R
import com.android.systemui.screencapture.record.shared.ui.viewmodel.SingleSelectViewModel
import com.android.systemui.screencapture.record.smallscreen.ui.viewmodel.CaptureTargetSpinnerItemViewModel

@Composable
fun CaptureTargetSpinner(
    viewModel: SingleSelectViewModel<CaptureTargetSpinnerItemViewModel>,
    modifier: Modifier = Modifier,
) {
    val itemHeight: Dp = 56.dp
    val cornerRadius: Dp = itemHeight / 2
    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier = modifier.animateContentSize().height(IntrinsicSize.Min).width(IntrinsicSize.Min)
    ) {
        AnimatedContent(
            targetState = expanded,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
        ) { currentExpanded ->
            if (currentExpanded) {
                Surface(
                    content = {},
                    shape = RoundedCornerShape(cornerRadius),
                    color = MaterialTheme.colorScheme.surfaceBright,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Surface(
                    content = {},
                    color = Color.Transparent,
                    shape = RoundedCornerShape(cornerRadius),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth().height(itemHeight),
                )
            }
        }
        Column(modifier = Modifier.clip(RoundedCornerShape(cornerRadius))) {
            viewModel.items.fastForEachIndexed { index, itemViewModel ->
                val isCurrentOption = index == viewModel.selectedItemIndex
                AnimatedVisibility(visible = expanded || isCurrentOption) {
                    ToolbarItem(
                        label = itemViewModel.label,
                        icon =
                            if (expanded) {
                                if (isCurrentOption) {
                                    R.drawable.ic_check_expressive
                                } else {
                                    null
                                }
                            } else {
                                R.drawable.ic_expressive_spinner_arrow
                            },
                        onClick = {
                            if (expanded) {
                                expanded = false
                                viewModel.onItemSelect(index)
                            } else {
                                expanded = true
                            }
                        },
                        active = !expanded && isCurrentOption,
                        modifier = Modifier.fillMaxWidth().height(itemHeight),
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolbarItem(
    label: String,
    onClick: () -> Unit,
    active: Boolean,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        colors =
            if (active) {
                ButtonDefaults.textButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            } else {
                ButtonDefaults.textButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )
            },
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        Text(text = label, modifier = Modifier.weight(1f))
        icon?.let {
            Icon(
                painter = painterResource(it),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
