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

package com.android.systemui.screencapture.sharescreen.largescreen.ui.compose

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap

/**
 * A temporary data class representing a single item in the list. This will be replaced by a
 * ViewModel in the next step.
 */
private data class ContentItem(val icon: Bitmap?, val label: CharSequence?)

/**
 * A composable that displays a scrollable list of shareable content (e.g., recent apps).
 *
 * @param modifier The modifier to be applied to the composable.
 */
@Composable
fun ShareContentList(modifier: Modifier = Modifier) {
    // TODO(b/436886242): Remove dummy data and inject view model.
    val contentList =
        listOf(
            ContentItem(icon = null, label = "App 1"),
            ContentItem(icon = null, label = "App 2"),
            ContentItem(icon = null, label = "App 3"),
            ContentItem(icon = null, label = "App 4"),
        )

    LazyColumn(modifier = modifier.height(120.dp).width(148.dp)) {
        itemsIndexed(contentList) { index, contentItem ->
            SelectorItem(
                icon = contentItem.icon,
                label = contentItem.label,
                isSelected = index == 0,
                onItemSelected = {},
            )
        }
    }
}

/**
 * A composable that displays a single item in the share content list. It shows an icon and a label,
 * and its appearance changes based on whether it is selected.
 *
 * @param icon The icon to display for the item. A placeholder is used if null.
 * @param label The text label for the item. A placeholder is used if null.
 * @param isSelected Whether this item is currently selected.
 * @param onItemSelected The callback to be invoked when this item is clicked.
 */
@Composable
private fun SelectorItem(
    icon: Bitmap?,
    label: CharSequence?,
    isSelected: Boolean,
    onItemSelected: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color =
            if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.width(148.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp).clickable(onClick = onItemSelected),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                // TODO: Address the hardcoded placeholder color.
                bitmap = icon?.asImageBitmap() ?: createDefaultColorImageBitmap(20, 20, Color.BLUE),
                contentDescription = null,
                modifier = Modifier.size(16.dp).clip(CircleShape),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label?.toString() ?: "Title",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Creates an [ImageBitmap] of a given size, filled with a solid color. Used as a placeholder for
 * when an app icon is not available.
 *
 * @param width The width of the bitmap.
 * @param height The height of the bitmap.
 * @param color The color to fill the bitmap with.
 */
private fun createDefaultColorImageBitmap(width: Int, height: Int, color: Int): ImageBitmap {
    val bitmap = createBitmap(width, height)
    bitmap.eraseColor(color)
    return bitmap.asImageBitmap()
}
