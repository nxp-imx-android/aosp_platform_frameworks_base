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

package com.android.systemui.underlay.ui.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import com.android.compose.ui.graphics.painter.rememberDrawablePainter
import com.android.systemui.underlay.ui.viewmodel.ActionViewModel

@Composable
fun Chip(action: ActionViewModel, modifier: Modifier = Modifier) {
    val outlineColor = MaterialTheme.colorScheme.onBackground
    val backgroundColor = MaterialTheme.colorScheme.background

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .clip(RoundedCornerShape(24.dp))
                .background(backgroundColor)
                .clickable { action.onClick() }
                .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        val painter = rememberDrawablePainter(action.icon)
        Image(
            painter = painter,
            colorFilter = if (action.attribution != null) ColorFilter.tint(outlineColor) else null,
            contentDescription = action.label,
            modifier = Modifier.size(24.dp).clip(CircleShape),
        )

        Text(action.label, style = MaterialTheme.typography.labelLarge, color = outlineColor)
        if (action.attribution != null) {
            Text(
                action.attribution,
                style = MaterialTheme.typography.labelLarge,
                color = outlineColor,
                modifier = Modifier.padding(start = 4.dp).alpha(0.4f),
            )
        }
    }
}
