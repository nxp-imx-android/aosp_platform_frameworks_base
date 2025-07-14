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

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.android.systemui.notifications.ui.composable.component

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow.Companion.Ellipsis

@Composable
internal fun Title(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier, style = MaterialTheme.typography.titleSmallEmphasized)
}

@Composable
internal fun CollapsedText(content: String, modifier: Modifier = Modifier) {
    Text(
        content,
        modifier,
        style = MaterialTheme.typography.bodyMediumEmphasized,
        maxLines = 1,
        overflow = Ellipsis,
    )
}

@Composable
internal fun ExpandedText(content: String, modifier: Modifier = Modifier) {
    Text(
        content,
        modifier,
        style = MaterialTheme.typography.bodyMediumEmphasized,
        // TODO: b/431222735 - This should support longer text (à la BigTextStyle)
        maxLines = 2,
        overflow = Ellipsis,
    )
}
