/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.qs.ui.composable

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.PlatformSliderDefaults
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.UserAction
import com.android.compose.animation.scene.UserActionResult
import com.android.compose.animation.scene.content.state.TransitionState
import com.android.compose.modifiers.thenIf
import com.android.systemui.brightness.ui.compose.BrightnessSliderContainer
import com.android.systemui.brightness.ui.compose.ContainerColors
import com.android.systemui.compose.modifiers.sysuiResTag
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.media.controls.ui.composable.MediaCarousel
import com.android.systemui.media.controls.ui.view.MediaHostState.Companion.COLLAPSED
import com.android.systemui.notifications.ui.composable.SnoozeableHeadsUpNotificationSpace
import com.android.systemui.qs.composefragment.ui.GridAnchor
import com.android.systemui.qs.flags.QsDetailedView
import com.android.systemui.qs.panels.ui.compose.EditMode
import com.android.systemui.qs.panels.ui.compose.TileDetails
import com.android.systemui.qs.panels.ui.compose.TileGrid
import com.android.systemui.qs.panels.ui.compose.toolbar.Toolbar
import com.android.systemui.qs.ui.composable.QuickSettingsShade.systemGestureExclusionInShade
import com.android.systemui.qs.ui.viewmodel.QuickSettingsContainerViewModel
import com.android.systemui.qs.ui.viewmodel.QuickSettingsShadeOverlayActionsViewModel
import com.android.systemui.qs.ui.viewmodel.QuickSettingsShadeOverlayContentViewModel
import com.android.systemui.res.R
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.ui.composable.Overlay
import com.android.systemui.shade.ui.composable.OverlayShade
import com.android.systemui.shade.ui.composable.OverlayShadeHeader
import com.android.systemui.shade.ui.composable.QuickSettingsOverlayHeader
import com.android.systemui.shade.ui.composable.ShadeHeader
import com.android.systemui.shade.ui.composable.isFullWidthShade
import com.android.systemui.statusbar.notification.stack.shared.model.ShadeScrimBounds
import com.android.systemui.statusbar.notification.stack.shared.model.ShadeScrimShape
import com.android.systemui.statusbar.notification.stack.ui.view.NotificationScrollView
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.NotificationsPlaceholderViewModel
import com.android.systemui.volume.panel.component.volume.ui.composable.VolumeSlider
import dagger.Lazy
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow

@SysUISingleton
class QuickSettingsShadeOverlay
@Inject
constructor(
    private val actionsViewModelFactory: QuickSettingsShadeOverlayActionsViewModel.Factory,
    private val contentViewModelFactory: QuickSettingsShadeOverlayContentViewModel.Factory,
    private val quickSettingsContainerViewModelFactory: QuickSettingsContainerViewModel.Factory,
    private val notificationStackScrollView: Lazy<NotificationScrollView>,
    private val notificationsPlaceholderViewModelFactory: NotificationsPlaceholderViewModel.Factory,
) : Overlay {

    override val key = Overlays.QuickSettingsShade

    private val actionsViewModel: QuickSettingsShadeOverlayActionsViewModel by lazy {
        actionsViewModelFactory.create()
    }

    override val userActions: Flow<Map<UserAction, UserActionResult>> = actionsViewModel.actions

    override suspend fun activate(): Nothing {
        actionsViewModel.activate()
    }

    @Composable
    override fun ContentScope.Content(modifier: Modifier) {
        val coroutineScope = rememberCoroutineScope()
        val contentViewModel =
            rememberViewModel("QuickSettingsShadeOverlayContent") {
                contentViewModelFactory.create()
            }
        val quickSettingsContainerViewModel =
            rememberViewModel("QuickSettingsShadeOverlayContainer") {
                quickSettingsContainerViewModelFactory.create(
                    supportsBrightnessMirroring = true,
                    expansion = COLLAPSED,
                    volumeSliderCoroutineScope = coroutineScope,
                )
            }
        val hunPlaceholderViewModel =
            rememberViewModel("QuickSettingsShadeOverlayPlaceholder") {
                notificationsPlaceholderViewModelFactory.create()
            }

        val showBrightnessMirror =
            quickSettingsContainerViewModel.brightnessSliderViewModel.showMirror
        val contentAlphaFromBrightnessMirror by
            animateFloatAsState(if (showBrightnessMirror) 0f else 1f)

        // Set the bounds to null when the QuickSettings overlay disappears.
        DisposableEffect(Unit) { onDispose { contentViewModel.onPanelShapeChanged(null) } }

        Box(modifier = modifier.graphicsLayer { alpha = contentAlphaFromBrightnessMirror }) {
            OverlayShade(
                panelElement = QuickSettingsShade.Elements.Panel,
                alignmentOnWideScreens = Alignment.TopEnd,
                enableTransparency = quickSettingsContainerViewModel.isTransparencyEnabled,
                onScrimClicked = contentViewModel::onScrimClicked,
                onBackgroundPlaced = { bounds, topCornerRadius, bottomCornerRadius ->
                    contentViewModel.onPanelShapeChanged(
                        ShadeScrimShape(
                            bounds = ShadeScrimBounds(bounds),
                            topRadius = topCornerRadius.roundToInt(),
                            bottomRadius = bottomCornerRadius.roundToInt(),
                        )
                    )
                },
                header = {
                    OverlayShadeHeader(
                        viewModel = quickSettingsContainerViewModel.shadeHeaderViewModel,
                        notificationsHighlight = ShadeHeader.ChipHighlight.Weak,
                        quickSettingsHighlight = ShadeHeader.ChipHighlight.Strong,
                        showClock = true,
                        modifier = Modifier.element(QuickSettingsShade.Elements.StatusBar),
                    )
                },
            ) {
                QuickSettingsContainer(viewModel = quickSettingsContainerViewModel)
            }
            SnoozeableHeadsUpNotificationSpace(
                stackScrollView = notificationStackScrollView.get(),
                viewModel = hunPlaceholderViewModel,
            )
        }
    }
}

/** The possible states of the `ShadeBody`. */
private sealed interface ShadeBodyState {
    data object Editing : ShadeBodyState

    data object TileDetails : ShadeBodyState

    data object Default : ShadeBodyState
}

@Composable
fun ContentScope.QuickSettingsContainer(
    viewModel: QuickSettingsContainerViewModel,
    modifier: Modifier = Modifier,
) {
    val isEditing by viewModel.editModeViewModel.isEditing.collectAsStateWithLifecycle()
    val tileDetails =
        if (QsDetailedView.isEnabled) viewModel.detailsViewModel.activeTileDetails else null

    AnimatedContent(
        modifier = Modifier.sysuiResTag("quick_settings_container"),
        targetState =
            when {
                isEditing -> ShadeBodyState.Editing
                tileDetails != null -> ShadeBodyState.TileDetails
                else -> ShadeBodyState.Default
            },
        transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(500)) },
    ) { state ->
        when (state) {
            ShadeBodyState.Editing -> {
                EditMode(
                    viewModel = viewModel.editModeViewModel,
                    modifier =
                        modifier.fillMaxWidth().padding(QuickSettingsShade.Dimensions.Padding),
                )
            }

            ShadeBodyState.TileDetails -> {
                TileDetails(modifier = modifier, viewModel.detailsViewModel)
            }

            ShadeBodyState.Default -> {
                QuickSettingsLayout(
                    viewModel = viewModel,
                    modifier = modifier.sysuiResTag("quick_settings_panel"),
                )
            }
        }
    }
}

/** Column containing Brightness and QS tiles. */
@Composable
fun ContentScope.QuickSettingsLayout(
    viewModel: QuickSettingsContainerViewModel,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            modifier.padding(
                start = QuickSettingsShade.Dimensions.Padding,
                end = QuickSettingsShade.Dimensions.Padding,
            ),
    ) {
        if (isFullWidthShade()) {
            VerticalSeparator()
            QuickSettingsOverlayHeader(
                viewModel = viewModel.shadeHeaderViewModel,
                modifier = Modifier.element(QuickSettingsShade.Elements.Header),
            )

            VerticalSeparator()
        }

        Toolbar(
            modifier =
                Modifier.fillMaxWidth().requiredHeight(QuickSettingsShade.Dimensions.ToolbarHeight),
            viewModel = viewModel.toolbarViewModel,
        )

        // TODO(b/428805936): Double check this padding.
        VerticalSeparator()

        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        ) {
            MediaCarousel(
                isVisible = viewModel.showMedia,
                mediaHost = viewModel.mediaHost,
                carouselController = viewModel.mediaCarouselController,
                usingCollapsedLandscapeMedia = true,
                modifier = Modifier.padding(horizontal = QuickSettingsShade.Dimensions.Padding),
            )

            if (viewModel.showMedia) {
                VerticalSeparator()
            }

            Box(
                Modifier.systemGestureExclusionInShade(
                    enabled = { layoutState.transitionState is TransitionState.Idle }
                )
            ) {
                BrightnessSliderContainer(
                    viewModel = viewModel.brightnessSliderViewModel,
                    containerColors =
                        ContainerColors(
                            idleColor = Color.Transparent,
                            mirrorColor =
                                OverlayShade.Colors.panelBackground(viewModel.isTransparencyEnabled),
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            VerticalSeparator()

            val volumeSliderViewModel = viewModel.volumeSliderViewModel
            if (volumeSliderViewModel != null) {
                val volumeSliderState by volumeSliderViewModel.slider.collectAsStateWithLifecycle()

                Box(
                    Modifier.systemGestureExclusionInShade(
                        enabled = { layoutState.transitionState is TransitionState.Idle }
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        VolumeSlider(
                            modifier = Modifier.weight(1f),
                            showLabel = false,
                            state = volumeSliderState,
                            onValueChange = { newValue: Float ->
                                volumeSliderViewModel.onValueChanged(volumeSliderState, newValue)
                            },
                            onValueChangeFinished = {
                                volumeSliderViewModel.onValueChangeFinished()
                            },
                            onIconTapped = { volumeSliderViewModel.toggleMuted(volumeSliderState) },
                            sliderColors = PlatformSliderDefaults.defaultPlatformSliderColors(),
                            hapticsViewModelFactory =
                                volumeSliderViewModel.getSliderHapticsViewModelFactory(),
                        )
                        IconButton(
                            colors =
                                IconButtonDefaults.iconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            onClick = {
                                viewModel.detailsViewModel.onVolumeSettingsButtonClicked(
                                    viewModel.audioDetailsViewModelFactory.create()
                                )
                            },
                        ) {
                            Icon(
                                painterResource(R.drawable.horizontal_ellipsis),
                                // TODO(b/378513663): Update the placeholder content description
                                contentDescription = "Volume settings",
                            )
                        }
                    }
                }

                VerticalSeparator()
            }

            GridAnchor()

            // TODO(b/428805936): Double check this padding.
            VerticalSeparator(QuickSettingsShade.Dimensions.Padding)

            TileGrid(viewModel = viewModel.tileGridViewModel, modifier = Modifier.fillMaxWidth())

            // TODO(b/428805936): Double check this padding.
            VerticalSeparator(QuickSettingsShade.Dimensions.Padding * 2)
        }
    }
}

@Composable
private fun VerticalSeparator(height: Dp = QuickSettingsShade.Dimensions.Padding) {
    Spacer(Modifier.height(height = height))
}

object QuickSettingsShade {
    object Elements {
        val StatusBar = ElementKey("QuickSettingsShadeOverlayStatusBar")
        val Panel = ElementKey("QuickSettingsShadeOverlayPanel")
        val Header = ElementKey("QuickSettingsShadeOverlayHeader")
    }

    object Dimensions {
        val Padding = 16.dp
        val ToolbarHeight = 48.dp
    }

    /**
     * Applies system gesture exclusion to a component adding [Dimensions.Padding] to left and
     * right.
     */
    @Composable
    fun Modifier.systemGestureExclusionInShade(enabled: () -> Boolean): Modifier {
        val density = LocalDensity.current
        return thenIf(enabled()) {
            Modifier.systemGestureExclusion { layoutCoordinates ->
                val sidePadding = with(density) { Dimensions.Padding.toPx() }
                Rect(
                    offset = Offset(x = -sidePadding, y = 0f),
                    size =
                        Size(
                            width = layoutCoordinates.size.width.toFloat() + 2 * sidePadding,
                            height = layoutCoordinates.size.height.toFloat(),
                        ),
                )
            }
        }
    }
}
