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

package com.android.systemui.accessibility.shortcutchooser.ui.composable

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.compose.PlatformOutlinedButton
import com.android.compose.theme.PlatformTheme
import com.android.compose.ui.graphics.painter.rememberDrawablePainter
import com.android.internal.accessibility.common.ShortcutConstants.UserShortcutType
import com.android.internal.accessibility.util.ShortcutUtils
import com.android.systemui.accessibility.shortcutchooser.shared.model.AccessibilityTargetModel
import com.android.systemui.dialog.ui.composable.AlertDialogContent
import com.android.systemui.res.R

@Composable
fun ShortcutEditorDialogContent(
    @UserShortcutType shortcutType: Int,
    infoList: List<AccessibilityTargetModel>,
    onDoneClick: () -> Unit,
    onTargetToggled: (targetName: String, isEnabled: Boolean) -> Unit,
) {
    PlatformTheme {
        AlertDialogContent(
            title = {
                Text(
                    text =
                        stringResource(
                            R.string.accessibility_shortcutchooser_editor_dialog_title,
                            stringResource(ShortcutUtils.typeToString(shortcutType)),
                        ),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start,
                )
            },
            content = {
                // Column layout to arrange LazyColumn and horizontalDivider
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Top),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        items(items = infoList) { ShortcutEditorRow(it, onTargetToggled) }
                    }

                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                }
            },
            positiveButton = {
                PlatformOutlinedButton(onClick = onDoneClick) {
                    Text(stringResource(R.string.accessibility_shortcutchooser_done_button))
                }
            },
            contentBottomPadding = 16.dp,
        )
    }
}

@Composable
private fun ShortcutEditorRow(
    info: AccessibilityTargetModel,
    onToggle: (targetName: String, isEnabled: Boolean) -> Unit,
) {
    val selectedState = remember { mutableStateOf(info.isAssigned) }
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(),
                ) {
                    val newValue = !selectedState.value
                    selectedState.value = newValue
                    onToggle(info.targetName, newValue)
                }
                .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.Start),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = selectedState.value,
            onCheckedChange = null,
            modifier = Modifier.size(18.dp),
        )

        Image(
            painter = rememberDrawablePainter(info.icon),
            contentDescription = null,
            modifier = Modifier.size(40.dp),
        )

        Text(text = info.featureName, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
    }
}
