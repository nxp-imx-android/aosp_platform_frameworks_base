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

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.systemui.common.shared.model.Icon as IconModel
import com.android.systemui.common.ui.compose.Icon

private val ICON_SIZE = 24.dp

/** Data class to represent a single item in the ActionButtonGroup. */
data class ActionButtonGroupItem(val icon: IconModel?, val label: String?, val onClick: () -> Unit)

/** A group of N icon buttons that each perform an action. */
@Composable
fun ActionButtonGroup(items: List<ActionButtonGroupItem>, modifier: Modifier = Modifier) {
    Row(modifier = modifier) {
        items.forEach { item ->
            Button(onClick = item.onClick, enabled = item.icon != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.icon != null) {
                        Icon(icon = item.icon, modifier = modifier.size(ICON_SIZE))
                    }

                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))

                    if (item.label != null) {
                        Text(item.label)
                    }
                }
            }
        }
    }
}
