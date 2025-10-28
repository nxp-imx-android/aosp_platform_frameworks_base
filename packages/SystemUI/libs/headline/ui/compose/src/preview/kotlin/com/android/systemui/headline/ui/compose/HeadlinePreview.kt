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

package com.android.systemui.headline.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.android.systemui.headline.ui.viewmodel.FakeHeadlineItem
import com.android.systemui.headline.ui.viewmodel.FakeHeadlineViewModel
import com.android.systemui.headline.ui.viewmodel.HeadlineItem
import com.android.systemui.headline.ui.viewmodel.HeadlineItemContent
import com.android.systemui.headline.ui.viewmodel.fakeHeadlineItems

@Composable
@Preview
private fun HeadlinePreview() {
    // TODO(b/449675581): Use PlatformTheme {} once it works with previews or provide a new
    // PlatformThemeForPreviews {} composable.
    MaterialTheme { HeadlineScreen() }
}

@Composable
fun HeadlineScreen(items: List<HeadlineItem> = remember { fakeHeadlineItems() }) {
    @Composable
    fun rememberViewModel(
        currentItemIndex: Int? = 0,
        list: List<HeadlineItem> = items,
    ): FakeHeadlineViewModel = rememberViewModel(list, currentItemIndex)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // No item selected.
        FakeHeadline(rememberViewModel(currentItemIndex = null))

        // First item selected.
        FakeHeadline(rememberViewModel())

        // First item selected (RTL).
        WithReversedLayoutDirection { FakeHeadline(rememberViewModel()) }

        // Second item selected.
        FakeHeadline(rememberViewModel(currentItemIndex = 1))

        // Last item selected.
        FakeHeadline(rememberViewModel(currentItemIndex = items.lastIndex))

        // Icons and text.
        FakeHeadline(rememberViewModel(list = rememberListWithIconAndTextItem()))

        // Icons and text (RTL).
        WithReversedLayoutDirection {
            FakeHeadline(rememberViewModel(list = rememberListWithIconAndTextItem()))
        }

        // Only icons.
        FakeHeadline(rememberViewModel(list = rememberListWithIconOnlyItem()))

        // Only text.
        FakeHeadline(rememberViewModel(list = rememberListWithTextOnlyItem()))

        // Long item (has correct max size).
        FakeHeadline(rememberViewModel(list = rememberListWithLongItem()))

        // Empty item (has correct minimum size).
        val listWithEmptyItems = List(2) { FakeHeadlineItem("empty$it", emptyList(), emptyList()) }
        FakeHeadline(rememberViewModel(list = remember { listWithEmptyItems }))

        // Small item at 100% swipe distance, to make sure that the min content width is big enough
        // that we don't have a weird overlap with the camera cutout.
        HeadlineWithTransitionAtProgress(
            items = listWithEmptyItems,
            fromIndex = 0,
            toIndex = 1,
            progress = 0f,
            previewProgress = 1f,
            isInitiatedByUserInput = true,
        )
    }
}

@Composable
internal fun rememberViewModel(
    list: List<HeadlineItem>,
    currentItemIndex: Int? = 0,
): FakeHeadlineViewModel {
    return remember(list) { FakeHeadlineViewModel(list, currentItemIndex?.let { list[it] }) }
}

@Composable
fun FakeHeadline(viewModel: FakeHeadlineViewModel, modifier: Modifier = Modifier) {
    WithFakeCameraCutout(modifier) { Headline(viewModel, Modifier.height(36.dp)) }
}

@Composable
private fun WithFakeCameraCutout(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier.drawWithContent {
            drawContent()
            drawCircle(Color.White, radius = size.minDimension / 3f)
        },
        content = content,
    )
}

@Composable
private fun WithReversedLayoutDirection(content: @Composable () -> Unit) {
    val layoutDirection =
        when (LocalLayoutDirection.current) {
            LayoutDirection.Ltr -> LayoutDirection.Rtl
            LayoutDirection.Rtl -> LayoutDirection.Ltr
        }
    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection, content = content)
}

@Composable
private fun rememberListWithIconAndTextItem(): List<HeadlineItem> {
    return remember {
        buildList {
            add(
                FakeHeadlineItem(
                    "icons",
                    listOf(
                        HeadlineItemContent.Icon(Icons.Default.Timer, contentDescription = null),
                        HeadlineItemContent.Text("4:42"),
                    ),
                    listOf(
                        HeadlineItemContent.Text("3 min"),
                        HeadlineItemContent.Icon(
                            Icons.Default.DirectionsCar,
                            contentDescription = null,
                        ),
                    ),
                )
            )
        }
    }
}

@Composable
private fun rememberListWithIconOnlyItem(): List<HeadlineItem> {
    return remember {
        buildList {
            val icons =
                listOf(
                        Icons.Default.Timer,
                        Icons.Default.MusicNote,
                        Icons.Default.Phone,
                        Icons.Default.DirectionsCar,
                    )
                    .map { HeadlineItemContent.Icon(it, contentDescription = null) }
            add(FakeHeadlineItem("icons", icons.take(2), icons.takeLast(2)))
        }
    }
}

@Composable
private fun rememberListWithTextOnlyItem(): List<FakeHeadlineItem> {
    return remember {
        listOf(
            FakeHeadlineItem(
                "texts",
                listOf("1", "2", "3").map { HeadlineItemContent.Text(it) },
                listOf("c", "b", "a").map { HeadlineItemContent.Text(it) },
            )
        )
    }
}

@Composable
private fun rememberListWithLongItem(): List<HeadlineItem> {
    return remember {
        buildList {
            add(
                FakeHeadlineItem(
                    key = "long",
                    startContents =
                        listOf(HeadlineItemContent.Text("A very very very loooooong text")),
                    endContents = emptyList(),
                )
            )

            add(FakeHeadlineItem("empty", emptyList(), emptyList()))
        }
    }
}
