/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.notifications.intelligence.rules.ui.composable

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt

/**
 * A composable that loads an image from a URI and displays it. Shows a default placeholder if the
 * [uri] is null or can't be loaded.
 *
 * @param uri the URI of the image
 * @param contentDescription A descriptive text for accessibility
 * @param size the size to render the image at.
 */
@Composable
fun AsyncUriImage(
    uri: Uri?,
    contentDescription: String?,
    size: Dp,
    loadBitmap: suspend (Uri, Context, Int) -> Bitmap?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val sizePx = with(density) { size.toPx().roundToInt() }
    val bitmap: Bitmap? by
        produceState<Bitmap?>(initialValue = null, uri, loadBitmap, context, sizePx) {
            uri?.let { value = loadBitmap(it, context, sizePx) }
        }
    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .then(
                    if (bitmap == null) {
                        // TODO: b/478225883 - Use a better default placeholder, like the first
                        // letter of the contact.
                        Modifier.background(MaterialTheme.colorScheme.secondary)
                    } else {
                        Modifier
                    }
                )
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
