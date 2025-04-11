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

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.compose.PlatformIconButton
import com.android.systemui.res.R

@Composable
fun UnderlayContainer(
    modifier: Modifier = Modifier,
    content: UnderlayComposableProvider,
) {
    Box(modifier = modifier) {
        // TODO: b/407634988 - Add rounded horns

        content.Content(modifier = Modifier)

        // Close Button.
        PlatformIconButton(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
            onClick = {
                // TODO: b/406978499 - Implement the hide logic.
                Log.d(TAG, "Close")
            },
            iconResource = R.drawable.ic_close,
            contentDescription =
                stringResource(id = R.string.underlay_close_button_content_description),
        )
    }
}

private const val TAG = "UnderlayContainer"
