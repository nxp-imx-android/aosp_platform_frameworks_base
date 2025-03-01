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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.compose.PlatformButton
import com.android.compose.PlatformIconButton
import com.android.compose.PlatformOutlinedButton
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.ElementKey
import com.android.compose.theme.LocalAndroidColorScheme
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.common.ui.compose.Icon
import com.android.systemui.common.ui.compose.load
import com.android.systemui.communal.ui.compose.extensions.detectLongPressGesture
import com.android.systemui.media.remedia.shared.model.MediaSessionState
import com.android.systemui.media.remedia.ui.viewmodel.MediaCardGutsViewModel
import com.android.systemui.media.remedia.ui.viewmodel.MediaOutputSwitcherChipViewModel
import com.android.systemui.media.remedia.ui.viewmodel.MediaPlayPauseActionViewModel
import com.android.systemui.media.remedia.ui.viewmodel.MediaSecondaryActionViewModel
import com.android.systemui.media.remedia.ui.viewmodel.MediaSeekBarViewModel
import kotlin.math.max

/** Renders the background of a card, loading the artwork and showing an overlay on top of it. */
@Composable
private fun CardBackground(imageLoader: suspend () -> ImageBitmap, modifier: Modifier = Modifier) {
    var image: ImageBitmap? by remember { mutableStateOf(null) }
    LaunchedEffect(imageLoader) {
        image = null
        image = imageLoader()
    }

    val gradientBaseColor = MaterialTheme.colorScheme.onSurface
    Box(
        modifier =
            modifier.drawWithContent {
                // Draw the content of the box (loaded art or placeholder).
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
            }
    ) {
        image?.let { loadedImage ->
            // Loaded art.
            Image(
                bitmap = loadedImage,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
            ?: run {
                // Placeholder.
                Box(Modifier.background(MaterialTheme.colorScheme.onSurface).matchParentSize())
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
 */
@Composable
private fun ContentScope.Navigation(
    viewModel: MediaSeekBarViewModel,
    isSeekBarVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    when (viewModel) {
        is MediaSeekBarViewModel.Showing -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = modifier,
            ) {
                viewModel.previous?.let {
                    SecondaryAction(viewModel = it, element = Media.Elements.PrevButton)
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

                viewModel.next?.let {
                    SecondaryAction(viewModel = it, element = Media.Elements.NextButton)
                }
            }
        }

        is MediaSeekBarViewModel.Hidden -> Unit
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
            onClick = viewModel.onClick,
            colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
            shape = RoundedCornerShape(cornerRadius),
            modifier = Modifier.size(width = buttonWidth, height = 48.dp),
        ) {
            when (viewModel.state) {
                is MediaSessionState.Playing,
                is MediaSessionState.Paused -> {
                    // TODO(b/399860531): load this expensive-to-load animated vector drawable off
                    //  the main thread.
                    val iconResource = checkNotNull(viewModel.icon)
                    Icon(
                        painter =
                            rememberAnimatedVectorPainter(
                                animatedImageVector =
                                    AnimatedImageVector.animatedVectorResource(
                                        id = iconResource.res
                                    ),
                                atEnd = viewModel.state == MediaSessionState.Playing,
                            ),
                        contentDescription = iconResource.contentDescription?.load(),
                        tint = iconColor,
                        modifier = Modifier.size(24.dp),
                    )
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
    if (element != null) {
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
    PlatformIconButton(
        onClick = viewModel.onClick,
        iconResource = (viewModel.icon as Icon.Resource).res,
        contentDescription = viewModel.icon.contentDescription?.load(),
        colors = IconButtonDefaults.iconButtonColors(contentColor = iconColor),
        enabled = viewModel.isEnabled,
        modifier = modifier.size(48.dp).padding(13.dp),
    )
}

private object Media {

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
    }
}
