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

package com.android.systemui.media.remedia.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults.colors
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import com.android.compose.PlatformButton
import com.android.compose.PlatformIconButton
import com.android.compose.PlatformOutlinedButton
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.SceneKey
import com.android.compose.animation.scene.SceneTransitionLayout
import com.android.compose.animation.scene.rememberMutableSceneTransitionLayoutState
import com.android.compose.animation.scene.transitions
import com.android.compose.theme.LocalAndroidColorScheme
import com.android.compose.ui.graphics.painter.rememberDrawablePainter
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.common.ui.compose.Icon
import com.android.systemui.common.ui.compose.PagerDots
import com.android.systemui.common.ui.compose.load
import com.android.systemui.communal.ui.compose.extensions.detectLongPressGesture
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.media.remedia.shared.model.MediaCardActionButtonLayout
import com.android.systemui.media.remedia.shared.model.MediaSessionState
import com.android.systemui.media.remedia.ui.viewmodel.MediaCardGutsViewModel
import com.android.systemui.media.remedia.ui.viewmodel.MediaCardViewModel
import com.android.systemui.media.remedia.ui.viewmodel.MediaCarouselVisibility
import com.android.systemui.media.remedia.ui.viewmodel.MediaNavigationViewModel
import com.android.systemui.media.remedia.ui.viewmodel.MediaOutputSwitcherChipViewModel
import com.android.systemui.media.remedia.ui.viewmodel.MediaPlayPauseActionViewModel
import com.android.systemui.media.remedia.ui.viewmodel.MediaSecondaryActionViewModel
import com.android.systemui.media.remedia.ui.viewmodel.MediaViewModel
import kotlin.math.max

/**
 * Renders a media controls UI element.
 *
 * This composable supports a multitude of presentation styles/layouts controlled by the
 * [presentationStyle] parameter. If the card carousel can be swiped away to dismiss by the user,
 * the [onDismissed] callback will be invoked when/if that happens.
 */
@Composable
fun Media(
    viewModelFactory: MediaViewModel.Factory,
    presentationStyle: MediaPresentationStyle,
    behavior: MediaUiBehavior,
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: MediaViewModel =
        rememberViewModel("Media.viewModel") {
            viewModelFactory.create(
                context = context,
                carouselVisibility = behavior.carouselVisibility,
            )
        }

    CardCarousel(
        viewModel = viewModel,
        presentationStyle = presentationStyle,
        behavior = behavior,
        onDismissed = onDismissed,
        modifier = modifier,
    )
}

/**
 * Renders a media controls carousel of cards.
 *
 * This composable supports a multitude of presentation styles/layouts controlled by the
 * [presentationStyle] parameter. The behavior is controlled by [behavior]. If
 * [MediaUiBehavior.isCarouselDismissible] is `true`, the [onDismissed] callback will be invoked
 * when/if that happens.
 */
@Composable
private fun CardCarousel(
    viewModel: MediaViewModel,
    presentationStyle: MediaPresentationStyle,
    behavior: MediaUiBehavior,
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(visible = viewModel.isCarouselVisible, modifier = modifier) {
        CardCarouselContent(
            viewModel = viewModel,
            presentationStyle = presentationStyle,
            behavior = behavior,
            onDismissed = onDismissed,
        )
    }
}

@Composable
private fun CardCarouselContent(
    viewModel: MediaViewModel,
    presentationStyle: MediaPresentationStyle,
    behavior: MediaUiBehavior,
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState =
        rememberDismissibleHorizontalPagerState(
            isDismissible = behavior.isCarouselDismissible,
            isScrollingEnabled = behavior.isCarouselScrollingEnabled,
        ) {
            viewModel.cards.size
        }

    val roundedCornerShape = RoundedCornerShape(32.dp)

    LaunchedEffect(pagerState.pagerState.currentPage) {
        viewModel.onCardSelected(pagerState.pagerState.currentPage)
    }

    DismissibleHorizontalPager(
        state = pagerState,
        onDismissed = onDismissed,
        pageSpacing = 8.dp,
        key = { index -> viewModel.cards[index].key },
        indicator = {
            if (pagerState.pagerState.pageCount > 1) {
                PagerDots(
                    pagerState = pagerState.pagerState,
                    activeColor = Color(0xffdee0ff),
                    nonActiveColor = Color(0xffa7a9ca),
                    dotSize = 6.dp,
                    spaceSize = 6.dp,
                    modifier =
                        Modifier.align(Alignment.BottomCenter).padding(8.dp).graphicsLayer {
                            translationX = pagerState.offset.value
                        },
                )
            }
        },
        modifier = modifier.padding(8.dp).clip(roundedCornerShape),
    ) { index ->
        Card(
            viewModel = viewModel.cards[index],
            presentationStyle = presentationStyle,
            modifier = Modifier.clip(roundedCornerShape),
        )
    }
}

/** Renders the UI of a single media card. */
@Composable
private fun Card(
    viewModel: MediaCardViewModel,
    presentationStyle: MediaPresentationStyle,
    modifier: Modifier = Modifier,
) {
    val stlState =
        rememberMutableSceneTransitionLayoutState(
            initialScene = presentationStyle.toScene(),
            transitions = Media.Transitions,
        )

    // Each time the presentation style changes, animate to the corresponding scene.
    LaunchedEffect(presentationStyle) {
        stlState.setTargetScene(targetScene = presentationStyle.toScene(), animationScope = this)
    }

    Box(modifier) {
        if (stlState.currentScene != Media.Scenes.Compact) {
            CardBackground(image = viewModel.background, modifier = Modifier.matchParentSize())
        }

        key(stlState) {
            SceneTransitionLayout(state = stlState) {
                scene(Media.Scenes.Default) {
                    CardForeground(viewModel = viewModel, threeRows = true, fillHeight = false)
                }

                scene(Media.Scenes.Compressed) {
                    CardForeground(viewModel = viewModel, threeRows = false, fillHeight = false)
                }

                scene(Media.Scenes.Compact) { CompactCardForeground(viewModel = viewModel) }
            }
        }
    }
}

/**
 * Renders the foreground of a card, including all UI content and the internal "guts".
 *
 * If [threeRows] is `true`, the layout will be organized as three horizontal rows; if `false`, two
 * rows will be used, resulting in a more compact layout.
 *
 * If [fillHeight] is `true`, the card will grow vertically to fill all available space in its
 * parent. If not, it'll only be as tall as needed to show its UI.
 */
@Composable
private fun ContentScope.CardForeground(
    viewModel: MediaCardViewModel,
    threeRows: Boolean,
    fillHeight: Boolean,
    modifier: Modifier = Modifier,
) {
    // Can't use a Crossfade composable because of the custom layout logic below. Animate the alpha
    // of the guts (and, indirectly, of the content) from here.
    val gutsAlphaAnimatable = remember { Animatable(0f) }
    val isGutsVisible = viewModel.guts.isVisible
    LaunchedEffect(isGutsVisible) { gutsAlphaAnimatable.animateTo(if (isGutsVisible) 1f else 0f) }

    // Use a custom layout to measure the content even if the content is being hidden because the
    // internal guts are showing. This is needed because only the content knows the size the of the
    // card and the guts are set to be the same size of the content.
    Layout(
        content = {
            CardForegroundContent(
                viewModel = viewModel,
                threeRows = threeRows,
                fillHeight = fillHeight,
                modifier =
                    Modifier.graphicsLayer {
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                        alpha = 1f - gutsAlphaAnimatable.value
                    },
            )

            CardGuts(
                viewModel = viewModel.guts,
                modifier =
                    Modifier.graphicsLayer {
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                        alpha = gutsAlphaAnimatable.value
                    },
            )
        },
        modifier = modifier,
    ) { measurables, constraints ->
        check(measurables.size == 2)
        val contentPlaceable = measurables[0].measure(constraints)
        // Guts should always have the exact dimensions as the content, even if we don't show the
        // content.
        val gutsPlaceable =
            measurables[1].measure(
                Constraints.fixed(contentPlaceable.width, contentPlaceable.height)
            )

        layout(contentPlaceable.measuredWidth, contentPlaceable.measuredHeight) {
            if (!viewModel.guts.isVisible || gutsAlphaAnimatable.isRunning) {
                contentPlaceable.place(0, 0)
            }
            if (viewModel.guts.isVisible || gutsAlphaAnimatable.isRunning) {
                gutsPlaceable.place(0, 0)
            }
        }
    }
}

@Composable
private fun ContentScope.CardForegroundContent(
    viewModel: MediaCardViewModel,
    threeRows: Boolean,
    fillHeight: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .combinedClickable(
                    onClick = viewModel.onClick,
                    onLongClick = viewModel.onLongClick,
                    onClickLabel = viewModel.onClickLabel,
                )
                .padding(16.dp)
    ) {
        // Always add the first/top row, regardless of presentation style.
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Icon.
            Icon(
                icon = viewModel.icon,
                tint = LocalAndroidColorScheme.current.primaryFixed,
                modifier = Modifier.size(24.dp).clip(CircleShape),
            )
            Spacer(modifier = Modifier.weight(1f))
            viewModel.outputSwitcherChips.fastForEach { chip ->
                OutputSwitcherChip(viewModel = chip, modifier = Modifier.padding(start = 8.dp))
            }
        }

        // If the card is taller than necessary to show all the rows, this adds spacing
        // between the top row and the rows below, anchoring the next rows to the bottom
        // of the card.
        if (fillHeight) {
            Spacer(Modifier.weight(1f))
        }

        if (threeRows) {
            // Three row presentation style.
            //
            // Second row.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Metadata(
                    title = viewModel.title,
                    subtitle = viewModel.subtitle,
                    color = Color.White,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                )

                if (viewModel.actionButtonLayout == MediaCardActionButtonLayout.WithPlayPause) {
                    AnimatedVisibility(visible = viewModel.playPauseAction != null) {
                        PlayPauseAction(
                            viewModel = checkNotNull(viewModel.playPauseAction),
                            buttonWidth = 48.dp,
                            buttonColor = LocalAndroidColorScheme.current.primaryFixed,
                            iconColor = LocalAndroidColorScheme.current.onPrimaryFixed,
                            buttonCornerRadius = { isPlaying -> if (isPlaying) 16.dp else 48.dp },
                        )
                    }
                }
            }

            // Third row.
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 24.dp),
            ) {
                Navigation(
                    viewModel = viewModel.navigation,
                    isSeekBarVisible = true,
                    areActionsVisible =
                        viewModel.actionButtonLayout == MediaCardActionButtonLayout.WithPlayPause,
                    modifier = Modifier.weight(1f),
                )

                viewModel.additionalActions.fastForEachIndexed { index, action ->
                    SecondaryAction(
                        viewModel = action,
                        element = Media.Elements.additionalActionButton(index),
                    )
                }
            }
        } else {
            // Two row presentation style.
            //
            // Bottom row.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 36.dp),
            ) {
                Metadata(
                    title = viewModel.title,
                    subtitle = viewModel.subtitle,
                    color = Color.White,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                )

                Navigation(
                    viewModel = viewModel.navigation,
                    isSeekBarVisible = false,
                    areActionsVisible =
                        viewModel.actionButtonLayout == MediaCardActionButtonLayout.WithPlayPause,
                    modifier = Modifier.padding(end = 8.dp),
                )

                if (
                    viewModel.actionButtonLayout == MediaCardActionButtonLayout.SecondaryActionsOnly
                ) {
                    viewModel.additionalActions.fastForEachIndexed { index, action ->
                        SecondaryAction(
                            viewModel = action,
                            element = Media.Elements.additionalActionButton(index),
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }

                AnimatedVisibility(visible = viewModel.playPauseAction != null) {
                    PlayPauseAction(
                        viewModel = checkNotNull(viewModel.playPauseAction),
                        buttonWidth = 48.dp,
                        buttonColor = LocalAndroidColorScheme.current.primaryFixed,
                        iconColor = LocalAndroidColorScheme.current.onPrimaryFixed,
                        buttonCornerRadius = { isPlaying -> if (isPlaying) 16.dp else 48.dp },
                    )
                }
            }
        }
    }
}

/**
 * Renders a simplified version of [CardForeground] that puts everything on a single row and doesn't
 * support the guts.
 */
@Composable
private fun ContentScope.CompactCardForeground(
    viewModel: MediaCardViewModel,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .clickable(onClick = viewModel.onClick, onClickLabel = viewModel.onClickLabel)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(16.dp),
    ) {
        Icon(
            icon = viewModel.icon,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp),
        )

        Metadata(
            title = viewModel.title,
            subtitle = viewModel.subtitle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )

        SecondaryAction(
            viewModel = viewModel.outputSwitcherChipButton,
            element = Media.Elements.OutputSwitcherButton,
            iconColor = MaterialTheme.colorScheme.onSurface,
        )

        val rightAction = (viewModel.navigation as? MediaNavigationViewModel.Showing)?.right
        if (rightAction != null) {
            SecondaryAction(
                viewModel = rightAction,
                element = Media.Elements.NextButton,
                iconColor = MaterialTheme.colorScheme.onSurface,
            )
        }

        AnimatedVisibility(visible = viewModel.playPauseAction != null) {
            PlayPauseAction(
                viewModel = checkNotNull(viewModel.playPauseAction),
                buttonWidth = 72.dp,
                buttonColor = MaterialTheme.colorScheme.primaryContainer,
                iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                buttonCornerRadius = { isPlaying -> if (isPlaying) 16.dp else 24.dp },
            )
        }
    }
}

/** Renders the background of a card, loading the artwork and showing an overlay on top of it. */
@Composable
private fun CardBackground(image: ImageBitmap?, modifier: Modifier = Modifier) {
    Crossfade(targetState = image, modifier = modifier) { imageOrNull ->
        if (imageOrNull != null) {
            // Loaded art.
            val gradientBaseColor = MaterialTheme.colorScheme.onSurface
            Image(
                bitmap = imageOrNull,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier.fillMaxSize().drawWithContent {
                        // Draw the content (loaded art).
                        drawContent()

                        if (image != null) {
                            // Then draw the overlay.
                            drawRect(
                                brush =
                                    Brush.radialGradient(
                                        0f to gradientBaseColor.copy(alpha = 0.65f),
                                        1f to gradientBaseColor.copy(alpha = 0.75f),
                                        center = size.center,
                                        radius = max(size.width, size.height) / 2,
                                    )
                            )
                        }
                    },
            )
        } else {
            // Placeholder.
            Box(Modifier.background(MaterialTheme.colorScheme.onSurface).fillMaxSize())
        }
    }
}

/**
 * Renders the navigation UI (seek bar and/or previous/next buttons).
 *
 * If [isSeekBarVisible] is `false`, the seek bar will not be included in the layout, even if it
 * would otherwise be showing based on the view-model alone. This is meant for callers to decide
 * whether they'd like to show the seek bar in addition to the prev/next buttons or just show the
 * buttons.
 *
 * If [areActionsVisible] is `false`, the left/right buttons to the left and right of the seek bar
 * will not be included in the layout.
 */
@Composable
private fun ContentScope.Navigation(
    viewModel: MediaNavigationViewModel,
    isSeekBarVisible: Boolean,
    areActionsVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    when (viewModel) {
        is MediaNavigationViewModel.Showing -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = modifier,
            ) {
                if (areActionsVisible) {
                    SecondaryAction(viewModel = viewModel.left, element = Media.Elements.PrevButton)
                }

                val interactionSource = remember { MutableInteractionSource() }
                val colors =
                    colors(
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                        thumbColor = Color.White,
                    )
                if (isSeekBarVisible) {
                    // To allow the seek bar slider to fade in and out, it's tagged as an element.
                    Element(key = Media.Elements.SeekBarSlider, modifier = Modifier.weight(1f)) {
                        Slider(
                            interactionSource = interactionSource,
                            value = viewModel.progress,
                            onValueChange = { progress -> viewModel.onScrubChange(progress) },
                            onValueChangeFinished = { viewModel.onScrubFinished() },
                            colors = colors,
                            thumb = {
                                SeekBarThumb(interactionSource = interactionSource, colors = colors)
                            },
                            track = { sliderState ->
                                SeekBarTrack(
                                    sliderState = sliderState,
                                    isSquiggly = viewModel.isSquiggly,
                                    colors = colors,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                if (areActionsVisible) {
                    SecondaryAction(
                        viewModel = viewModel.right,
                        element = Media.Elements.NextButton,
                    )
                }
            }
        }

        is MediaNavigationViewModel.Hidden -> Unit
    }
}

/** Renders the thumb of the seek bar. */
@Composable
private fun SeekBarThumb(
    interactionSource: MutableInteractionSource,
    colors: SliderColors,
    modifier: Modifier = Modifier,
) {
    val interactions = remember { mutableStateListOf<Interaction>() }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> interactions.add(interaction)
                is PressInteraction.Release -> interactions.remove(interaction.press)
                is PressInteraction.Cancel -> interactions.remove(interaction.press)
                is DragInteraction.Start -> interactions.add(interaction)
                is DragInteraction.Stop -> interactions.remove(interaction.start)
                is DragInteraction.Cancel -> interactions.remove(interaction.start)
            }
        }
    }

    Spacer(
        modifier
            .size(width = 4.dp, height = 16.dp)
            .hoverable(interactionSource = interactionSource)
            .background(color = colors.thumbColor, shape = RoundedCornerShape(16.dp))
    )
}

/**
 * Renders the track of the seek bar.
 *
 * If [isSquiggly] is `true`, the part to the left of the thumb will animate a squiggly line that
 * oscillates up and down. The [waveLength] and [amplitude] control the geometry of the squiggle and
 * the [waveSpeedDpPerSec] controls the speed by which it seems to "move" horizontally.
 */
@Composable
private fun SeekBarTrack(
    sliderState: SliderState,
    isSquiggly: Boolean,
    colors: SliderColors,
    modifier: Modifier = Modifier,
    waveLength: Dp = 20.dp,
    amplitude: Dp = 3.dp,
    waveSpeedDpPerSec: Dp = 8.dp,
) {
    // Animating the amplitude allows the squiggle to gradually grow to its full height or shrink
    // back to a flat line as needed.
    val animatedAmplitude by
        animateDpAsState(
            targetValue = if (isSquiggly) amplitude else 0.dp,
            label = "SeekBarTrack.amplitude",
        )

    // This animates the horizontal movement of the squiggle.
    val animatedWaveOffset = remember { Animatable(0f) }

    LaunchedEffect(isSquiggly) {
        if (isSquiggly) {
            animatedWaveOffset.snapTo(0f)
            animatedWaveOffset.animateTo(
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        animation =
                            tween(
                                durationMillis = (1000 * (waveLength / waveSpeedDpPerSec)).toInt(),
                                easing = LinearEasing,
                            ),
                        repeatMode = RepeatMode.Restart,
                    ),
            )
        }
    }

    // Render the track.
    Canvas(modifier = modifier) {
        val thumbPositionPx = size.width * sliderState.value

        // The squiggly part before the thumb.
        if (thumbPositionPx > 0) {
            val amplitudePx = amplitude.toPx()
            val animatedAmplitudePx = animatedAmplitude.toPx()
            val waveLengthPx = waveLength.toPx()

            val path =
                Path().apply {
                    val halfWaveLengthPx = waveLengthPx / 2
                    val halfWaveCount = (thumbPositionPx / halfWaveLengthPx).toInt()

                    repeat(halfWaveCount + 3) { index ->
                        // Draw a half wave (either a hill or a valley shape starting and ending on
                        // the horizontal center).
                        relativeQuadraticTo(
                            // The control point for the bezier curve is on top of the peak of the
                            // hill or the very center bottom of the valley shape.
                            dx1 = halfWaveLengthPx / 2,
                            dy1 = if (index % 2 == 0) -animatedAmplitudePx else animatedAmplitudePx,
                            // Advance horizontally, half a wave length at a time.
                            dx2 = halfWaveLengthPx,
                            dy2 = 0f,
                        )
                    }
                }

            // Now that the squiggle is rendered a bit past the thumb, clip off the part that passed
            // the thumb. It's easier to clip the extra squiggle than to figure out the bezier curve
            // for part of a hill/valley.
            clipRect(
                left = 0f,
                top = -amplitudePx,
                right = thumbPositionPx,
                bottom = amplitudePx * 2,
            ) {
                translate(left = -waveLengthPx * animatedWaveOffset.value, top = 0f) {
                    // Actually render the squiggle.
                    drawPath(
                        path = path,
                        color = colors.activeTrackColor,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            }
        }

        // The flat line after the thumb.
        drawLine(
            color = colors.inactiveTrackColor,
            start = Offset(thumbPositionPx, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

/** Renders the internal "guts" of a card. */
@Composable
private fun CardGuts(viewModel: MediaCardGutsViewModel, modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier.pointerInput(Unit) { detectLongPressGesture { viewModel.onLongClick() } }
    ) {
        // Settings button.
        Icon(
            icon = checkNotNull(viewModel.settingsButton.icon),
            modifier =
                Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 16.dp).clickable {
                    viewModel.settingsButton.onClick()
                },
        )

        //  Content.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 32.dp, bottom = 40.dp),
        ) {
            Text(text = viewModel.text, color = Color.White)

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlatformButton(
                    onClick = viewModel.primaryAction.onClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                ) {
                    Text(
                        text = checkNotNull(viewModel.primaryAction.text),
                        color = LocalAndroidColorScheme.current.onPrimaryFixed,
                    )
                }

                viewModel.secondaryAction?.let { button ->
                    PlatformOutlinedButton(
                        onClick = button.onClick,
                        border = BorderStroke(width = 1.dp, color = Color.White),
                    ) {
                        Text(text = checkNotNull(button.text), color = Color.White)
                    }
                }
            }
        }
    }
}

/** Renders the metadata labels of a track. */
@Composable
private fun ContentScope.Metadata(
    title: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    // This element can be animated when switching between scenes inside a media card.
    Element(key = Media.Elements.Metadata, modifier = modifier) {
        // When the title and/or subtitle change, crossfade between the old and the new.
        Crossfade(targetState = title to subtitle, label = "Labels.crossfade") { (title, subtitle)
            ->
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Renders a small chip showing the current output device and providing a way to switch to a
 * different output device.
 */
@Composable
private fun OutputSwitcherChip(
    viewModel: MediaOutputSwitcherChipViewModel,
    modifier: Modifier = Modifier,
) {
    PlatformButton(
        onClick = viewModel.onClick,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = LocalAndroidColorScheme.current.primaryFixed
            ),
        contentPadding = PaddingValues(start = 8.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        modifier = modifier.height(24.dp),
    ) {
        Icon(
            icon = viewModel.icon,
            tint = LocalAndroidColorScheme.current.onPrimaryFixed,
            modifier = Modifier.size(16.dp),
        )
        viewModel.text?.let {
            Spacer(Modifier.size(4.dp))
            Text(
                text = viewModel.text,
                style = MaterialTheme.typography.bodySmall,
                color = LocalAndroidColorScheme.current.onPrimaryFixed,
            )
        }
    }
}

/** Renders the primary action of media controls: the play/pause button. */
@Composable
private fun ContentScope.PlayPauseAction(
    viewModel: MediaPlayPauseActionViewModel,
    buttonWidth: Dp,
    buttonColor: Color,
    iconColor: Color,
    buttonCornerRadius: (isPlaying: Boolean) -> Dp,
    modifier: Modifier = Modifier,
) {
    val cornerRadius: Dp by
        animateDpAsState(
            targetValue = buttonCornerRadius(viewModel.state != MediaSessionState.Paused),
            label = "PlayPauseAction.cornerRadius",
        )
    // This element can be animated when switching between scenes inside a media card.
    Element(key = Media.Elements.PlayPauseButton, modifier = modifier) {
        PlatformButton(
            onClick = viewModel.onClick ?: {},
            enabled = viewModel.onClick != null,
            colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
            shape = RoundedCornerShape(cornerRadius),
            modifier = Modifier.size(width = buttonWidth, height = 48.dp),
        ) {
            when (viewModel.state) {
                is MediaSessionState.Playing,
                is MediaSessionState.Paused -> {
                    val painterOrNull =
                        when (viewModel.icon) {
                            // TODO(b/399860531): load this expensive-to-load animated vector
                            //  drawable off the main thread.
                            is Icon.Resource ->
                                rememberAnimatedVectorPainter(
                                    animatedImageVector =
                                        AnimatedImageVector.animatedVectorResource(
                                            id = viewModel.icon.res
                                        ),
                                    atEnd = viewModel.state == MediaSessionState.Playing,
                                )
                            is Icon.Loaded -> rememberDrawablePainter(viewModel.icon.drawable)
                            null -> null
                        }
                    painterOrNull?.let { painter ->
                        Icon(
                            painter = painter,
                            contentDescription = viewModel.icon?.contentDescription?.load(),
                            tint = iconColor,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                is MediaSessionState.Buffering -> {
                    CircularProgressIndicator(color = iconColor, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

/**
 * Renders an icon button for an action that's not the play/pause action.
 *
 * If [element] is provided, the secondary action element will be able to animate when switching
 * between scenes inside a media card.
 */
@Composable
private fun ContentScope.SecondaryAction(
    viewModel: MediaSecondaryActionViewModel,
    modifier: Modifier = Modifier,
    element: ElementKey? = null,
    iconColor: Color = Color.White,
) {
    if (viewModel !is MediaSecondaryActionViewModel.None && element != null) {
        Element(key = element, modifier = modifier) {
            SecondaryActionContent(viewModel = viewModel, iconColor = iconColor)
        }
    } else {
        SecondaryActionContent(viewModel = viewModel, iconColor = iconColor, modifier = modifier)
    }
}

/** The content of a [SecondaryAction]. */
@Composable
private fun SecondaryActionContent(
    viewModel: MediaSecondaryActionViewModel,
    iconColor: Color,
    modifier: Modifier = Modifier,
) {
    val sharedModifier = modifier.size(48.dp).padding(13.dp)
    when (viewModel) {
        is MediaSecondaryActionViewModel.Action ->
            PlatformIconButton(
                onClick = viewModel.onClick ?: {},
                iconResource = (viewModel.icon as Icon.Resource).res,
                contentDescription = viewModel.icon.contentDescription?.load(),
                colors = IconButtonDefaults.iconButtonColors(contentColor = iconColor),
                enabled = viewModel.onClick != null,
                modifier = sharedModifier,
            )

        is MediaSecondaryActionViewModel.ReserveSpace -> Spacer(modifier = sharedModifier)

        is MediaSecondaryActionViewModel.None -> Unit
    }
}

/** Enumerates all supported media presentation styles. */
enum class MediaPresentationStyle {
    /** The "normal" 3-row carousel look. */
    Default,
    /** Similar to [Default] but not as tall (2-row carousel look). */
    Compressed,
    /** A special single-row treatment that fits nicely in quick settings. */
    Compact,
}

data class MediaUiBehavior(
    val isCarouselDismissible: Boolean = true,
    val isCarouselScrollingEnabled: Boolean = true,
    val carouselVisibility: MediaCarouselVisibility = MediaCarouselVisibility.WhenNotEmpty,
    val isFalsingProtectionNeeded: Boolean = false,
)

private object Media {

    /**
     * Scenes.
     *
     * The implementation uses a [SceneTransitionLayout] to smoothly animate transitions between
     * different card layouts. Each card layout is identified as its own "scene" and the STL
     * framework takes care of animating the layouts and their elements as the card morphs between
     * scenes.
     */
    object Scenes {
        /** The "normal" 3-row carousel look. */
        val Default = SceneKey("default")
        /** Similar to [Default] but not as tall (2-row carousel look). */
        val Compressed = SceneKey("compressed")
        /** A special single-row treatment that fits nicely in quick settings. */
        val Compact = SceneKey("compact")
    }

    /** Definitions of how scene changes are transition-animated. */
    val Transitions = transitions {
        from(Scenes.Default, to = Scenes.Compact) {}
        from(Scenes.Default, to = Scenes.Compressed) { fade(Elements.SeekBarSlider) }
        from(Scenes.Compact, to = Scenes.Compressed) { fade(Elements.SeekBarSlider) }
    }

    /**
     * Element keys.
     *
     * Composables that are wrapped in [ContentScope.Element] with one of these as their `key`
     * parameter will automatically be picked up by the STL transition animation framework and will
     * be animated from their bounds in the original scene to their bounds in the destination scene.
     *
     * In addition, tagging such elements with a key allows developers to customize the transition
     * animations even further.
     */
    object Elements {
        val PlayPauseButton = ElementKey("play_pause")
        val Metadata = ElementKey("metadata")
        val PrevButton = ElementKey("prev")
        val NextButton = ElementKey("next")
        val SeekBarSlider = ElementKey("seek_bar_slider")
        val OutputSwitcherButton = ElementKey("output_switcher")

        fun additionalActionButton(index: Int): ElementKey {
            return ElementKey("additional_action_$index")
        }
    }
}

private fun MediaPresentationStyle.toScene(): SceneKey {
    return when (this) {
        MediaPresentationStyle.Default -> Media.Scenes.Default
        MediaPresentationStyle.Compressed -> Media.Scenes.Compressed
        MediaPresentationStyle.Compact -> Media.Scenes.Compact
    }
}
