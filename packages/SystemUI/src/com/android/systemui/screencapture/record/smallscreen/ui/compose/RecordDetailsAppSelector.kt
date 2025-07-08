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

@file:OptIn(ExperimentalMaterial3Api::class)

package com.android.systemui.screencapture.record.smallscreen.ui.compose

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.CarouselItemScope
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.compose.PlatformIconButton
import com.android.systemui.res.R
import com.android.systemui.screencapture.record.smallscreen.ui.viewmodel.RecordDetailsAppSelectorViewModel
import com.android.systemui.screencapture.record.smallscreen.ui.viewmodel.RecordDetailsAppViewModel

@Composable
fun RecordDetailsAppSelector(
    viewModel: RecordDetailsAppSelectorViewModel,
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.padding(bottom = 32.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
        ) {
            PlatformIconButton(
                onClick = onBackPressed,
                iconResource = R.drawable.ic_arrow_back,
                contentDescription = stringResource(R.string.accessibility_back),
                colors =
                    IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                modifier = Modifier.size(48.dp),
            )
            Text(
                text = stringResource(R.string.screen_record_capture_target_choose_app),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        val carouselState = rememberCarouselState { viewModel.apps.size }
        HorizontalUncontainedCarousel(
            state = carouselState,
            itemWidth = 168.dp,
            itemSpacing = 24.dp,
            contentPadding = PaddingValues(horizontal = 32.dp),
            flingBehavior = CarouselDefaults.singleAdvanceFlingBehavior(carouselState),
            modifier = Modifier,
        ) { index ->
            val appViewModel = viewModel.apps[index]
            AppPreview(viewModel = appViewModel, modifier = Modifier)
        }
    }
}

@Composable
private fun CarouselItemScope.AppPreview(
    viewModel: RecordDetailsAppViewModel,
    modifier: Modifier = Modifier,
    cornersRadius: Dp = 16.dp,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            modifier
                .maskClip(RoundedCornerShape(cornersRadius))
                .clickable(onClick = viewModel.onSelect),
    ) {
        Icon(
            bitmap = viewModel.icon.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )

        AnimatedContent(
            targetState = viewModel.thumbnail,
            contentAlignment = Alignment.Center,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier =
                Modifier.clip(RoundedCornerShape(cornersRadius)).aspectRatio(9 / 16f).fillMaxSize(),
        ) { thumbnail ->
            if (thumbnail == null) {
                Spacer(
                    modifier =
                        Modifier.background(color = MaterialTheme.colorScheme.surfaceContainerHigh)
                )
            } else {
                Image(bitmap = thumbnail.asImageBitmap(), contentDescription = null)
            }
        }
    }
}
