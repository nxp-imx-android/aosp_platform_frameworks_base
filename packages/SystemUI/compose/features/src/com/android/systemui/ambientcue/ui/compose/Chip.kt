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

package com.android.systemui.ambientcue.ui.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.compose.ui.graphics.painter.rememberDrawablePainter
import com.android.systemui.ambientcue.ui.viewmodel.ActionViewModel

@Composable
fun Chip(action: ActionViewModel, modifier: Modifier = Modifier) {
    val outlineColor = MaterialTheme.colorScheme.onBackground
    val backgroundColor = MaterialTheme.colorScheme.background

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .clip(RoundedCornerShape(24.dp))
                .background(backgroundColor)
                .defaultMinSize(minHeight = 48.dp)
                .combinedClickable(onClick = action.onClick, onLongClick = action.onLongClick)
                .padding(start = 12.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
    ) {
        val painter = rememberDrawablePainter(action.icon)
        Image(
            painter = painter,
            contentDescription = action.label,
            modifier =
                Modifier.size(24.dp)
                    .border(0.75.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .clip(CircleShape),
        )

        Column {
            val hasAttribution = action.attribution != null
            Text(
                action.label,
                style = MaterialTheme.typography.labelLarge,
                color = outlineColor,
                maxLines = if (hasAttribution) 1 else 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (hasAttribution) {
                Text(
                    action.attribution!!,
                    style = MaterialTheme.typography.labelLarge,
                    color = outlineColor,
                    maxLines = 1,
                    modifier = Modifier.alpha(0.8f),
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
