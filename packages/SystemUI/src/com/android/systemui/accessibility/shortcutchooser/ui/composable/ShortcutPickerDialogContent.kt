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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.compose.PlatformOutlinedButton
import com.android.compose.theme.PlatformTheme
import com.android.compose.ui.graphics.painter.rememberDrawablePainter
import com.android.systemui.accessibility.shortcutchooser.shared.model.AccessibilityTargetModel
import com.android.systemui.dialog.ui.composable.AlertDialogContent
import com.android.systemui.res.R

/**
 * The content used by [SystemUIDialogFactory.create] to create toggle dialogs for different
 * shortcut type.
 *
 * @param infoList The list of targets enabled for a specific shortcut type
 * @param onEditClick The callback for the negative button
 * @param onDoneClick The callback for the positive button
 * @param onTargetClick The callback when clicking on the [ToggleTargetRow]
 */
@Composable
fun ShortcutPickerDialogContent(
    infoList: List<AccessibilityTargetModel>,
    onEditClick: () -> Unit,
    onDoneClick: () -> Unit,
    onTargetClick: (AccessibilityTargetModel) -> Unit,
) {
    PlatformTheme {
        AlertDialogContent(
            title = {
                Text(
                    stringResource(R.string.accessibility_shortcutchooser_picker_dialog_title),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start,
                )
            },
            content = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(items = infoList) { info ->
                        ShortcutPickerRow(info) { onTargetClick(info) }
                    }
                }
            },
            negativeButton = {
                PlatformOutlinedButton(
                    onClick = onEditClick,
                    modifier = Modifier.testTag("edit_button"),
                ) {
                    Text(stringResource(R.string.accessibility_shortcutchooser_edit_button))
                }
            },
            positiveButton = {
                PlatformOutlinedButton(
                    onClick = onDoneClick,
                    modifier = Modifier.testTag("done_button"),
                ) {
                    Text(stringResource(R.string.accessibility_shortcutchooser_done_button))
                }
            },
        )
    }
}

@Composable
private fun ShortcutPickerRow(info: AccessibilityTargetModel, onClick: () -> Unit) {
    var checkedState by remember { mutableStateOf(info.isToggleOn) }

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .testTag(info.targetName)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(),
                ) {
                    if (info.isToggleable) {
                        // Only toggle the local visual state if a switch is present.
                        // The onClick action is the same regardless.
                        checkedState = !checkedState
                    }
                    onClick()
                }
                .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.Start),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = rememberDrawablePainter(info.icon),
            contentDescription = null,
            modifier = Modifier.size(40.dp),
        )

        Text(text = info.featureName, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)

        if (info.isToggleable) {
            // Use the checkedState that is now managed by the Row's click handler
            Switch(
                checked = checkedState,
                onCheckedChange = null, // The Row's clickable modifier handles the logic
                thumbContent =
                    if (checkedState) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize),
                            )
                        }
                    } else {
                        {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize),
                            )
                        }
                    },
            )
        }
    }
}
