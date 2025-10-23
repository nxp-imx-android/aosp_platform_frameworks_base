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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import com.android.compose.ui.graphics.painter.rememberDrawablePainter
import com.android.systemui.accessibility.shortcutchooser.shared.model.AccessibilityTargetModel

private object Constants {
    // Desired switch width divided by the default switch width in material3.
    // See: https://m3.material.io/components/switch/specs for track width.
    // Hardcoding because the default width is not exposed by the library.
    const val ICON_SWITCH_SCALE = 40 / 52f
}

// TODO: https://issuetracker.google.com/454128374 - Refactor this when top row key dialog lands.
@Composable
fun ShortcutPickerRow(
    target: AccessibilityTargetModel,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(52.dp)
                // Add interactable before padding so the entire row is clickable.
                .interactable(target, onClick)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = rememberDrawablePainter(target.icon),
            contentDescription = null,
            modifier = Modifier.size(40.dp),
        )

        Text(
            target.featureName,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
        )

        if (target.isToggleable) {
            PickerSwitch(checked = target.isToggleOn)
        }
    }
}

private fun Modifier.interactable(target: AccessibilityTargetModel, onClick: () -> Unit): Modifier =
    if (target.isToggleable) {
        toggleable(value = target.isToggleOn, role = Role.Switch, onValueChange = { onClick() })
    } else {
        clickable { onClick() }
    }

@Composable
private fun PickerSwitch(checked: Boolean) {
    Switch(
        checked = checked,
        onCheckedChange = null,
        // Scaling instead of setting width because Switch is a composite composable so setting
        // the width is ignored.
        modifier = Modifier.scale(Constants.ICON_SWITCH_SCALE),
        thumbContent = {
            if (checked) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                )
            }
        },
    )
}
