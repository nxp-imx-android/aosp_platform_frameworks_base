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

package com.android.systemui.screencapture.common.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyledTooltip(tooltipText: String, content: @Composable () -> Unit) {
    if (tooltipText.isNotBlank()) {
        val tooltipState = rememberTooltipState(isPersistent = true)

        val tertiaryColor = MaterialTheme.colorScheme.tertiaryFixed
        val onTertiaryColor = MaterialTheme.colorScheme.onTertiaryFixed

        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
            tooltip = {
                PlainTooltip(
                    caretShape = TooltipDefaults.caretShape(),
                    shape = RoundedCornerShape(size = 360.dp),
                    containerColor = tertiaryColor,
                    contentColor = onTertiaryColor,
                ) {
                    Text(
                        text = tooltipText,
                        style = MaterialTheme.typography.labelLarge,
                        color = onTertiaryColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                    )
                }
            },
            state = tooltipState,
            focusable = false,
            enableUserInput = true,
            content = {
                // Manually show/hide the tooltip to support trackpad pointer hover.
                // TODO(b/452756010) Remove this custom logic once tooltips support trackpad pointer
                Box(
                    modifier =
                        Modifier.pointerInput(tooltipState) {
                            coroutineScope {
                                awaitPointerEventScope {
                                    while (isActive) {
                                        val event = awaitPointerEvent()
                                        when (event.type) {
                                            PointerEventType.Enter -> launch { tooltipState.show() }
                                            PointerEventType.Exit ->
                                                launch { tooltipState.dismiss() }
                                        }
                                    }
                                }
                            }
                        }
                ) {
                    content()
                }
            },
        )
    } else {
        content()
    }
}
