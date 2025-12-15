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

package com.android.systemui.screencapture.record.smallscreen.ui.compose

import android.graphics.Region
import android.view.ViewTreeObserver.InternalInsetsInfo
import android.view.Window
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.compose.PlatformIconButton
import com.android.compose.modifiers.thenIf
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.ScreenCaptureUi
import com.android.systemui.screencapture.common.ScreenCaptureUiScope
import com.android.systemui.screencapture.common.ui.compose.LoadingIcon
import com.android.systemui.screencapture.common.ui.compose.PrimaryButton
import com.android.systemui.screencapture.common.ui.compose.ScreenCaptureContent
import com.android.systemui.screencapture.common.ui.compose.loadIcon
import com.android.systemui.screencapture.common.ui.viewmodel.DrawableLoaderViewModel
import com.android.systemui.screencapture.record.smallscreen.shared.model.RecordDetailsPopupType
import com.android.systemui.screencapture.record.smallscreen.ui.viewmodel.SmallScreenCaptureRecordViewModel
import com.android.systemui.util.view.listenToComputeInternalInsets
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@ScreenCaptureUiScope
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class SmallScreenCaptureRecordContent
@Inject
constructor(
    @ScreenCaptureUi private val window: Window?,
    private val viewModelFactory: SmallScreenCaptureRecordViewModel.Factory,
) : ScreenCaptureContent {

    @Composable
    override fun Content() {
        val viewModel =
            rememberViewModel("SmallScreenCaptureRecordContent#viewModel") {
                viewModelFactory.create()
            }

        val viewTreeObserver = window?.decorView?.viewTreeObserver
        val toolbarBounds = remember { Region.obtain() }
        val settingsBounds = remember { Region.obtain() }
        LaunchedEffect(viewTreeObserver) {
            viewTreeObserver?.listenToComputeInternalInsets {
                setTouchableInsets(InternalInsetsInfo.TOUCHABLE_INSETS_REGION)
                touchableRegion.op(toolbarBounds, Region.Op.UNION)
                touchableRegion.op(settingsBounds, Region.Op.UNION)
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier.fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.safeContent.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                        )
                    )
                    .padding(horizontal = 30.dp),
        ) {
            // TODO(b/428686600) use Toolbar shared with the large screen
            Surface(
                shape = FloatingToolbarDefaults.ContainerShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp,
                modifier =
                    Modifier.fillBoundsInWindowIf(
                        region = toolbarBounds,
                        condition = viewTreeObserver != null,
                    ),
            ) {
                Row(
                    modifier = Modifier.height(64.dp).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlatformIconButton(
                        onClick = { viewModel.dismiss() },
                        contentDescription =
                            stringResource(id = R.string.underlay_close_button_content_description),
                        colors =
                            IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            ),
                        iconResource = R.drawable.ic_close,
                    )
                    AnimatedVisibility(visible = viewModel.shouldShowSettingsButton) {
                        ToggleToolbarButton(
                            checked = viewModel.detailsPopup == RecordDetailsPopupType.Settings,
                            onCheckedChanged = {
                                if (it) {
                                    viewModel.showSettings()
                                } else {
                                    viewModel.resetDetailsPopup()
                                }
                            },
                            icon = {
                                LoadingIcon(
                                    icon =
                                        loadIcon(
                                                viewModel = viewModel,
                                                resId = R.drawable.ic_settings,
                                                contentDescription = null,
                                            )
                                            .value,
                                    modifier = Modifier.size(24.dp),
                                )
                            },
                        )
                    }
                    AnimatedVisibility(visible = viewModel.shouldShowMarkupButton) {
                        ToggleToolbarButton(
                            checked = viewModel.markupEnabled == true,
                            onCheckedChanged = { viewModel.setMarkupEnabled(it) },
                            icon = {
                                LoadingIcon(
                                    icon =
                                        loadIcon(
                                                viewModel = viewModel,
                                                resId = R.drawable.ic_markup,
                                                contentDescription = null,
                                            )
                                            .value,
                                    modifier = Modifier.size(24.dp),
                                )
                            },
                        )
                    }
                    AnimatedVisibility(
                        visible =
                            viewModel.shouldShowMarkupButton && viewModel.markupEnabled == true
                    ) {
                        ToggleToolbarButton(
                            checked =
                                viewModel.detailsPopup ==
                                    RecordDetailsPopupType.MarkupColorSelector,
                            onCheckedChanged = {
                                if (it) {
                                    viewModel.showMarkupColorSelector()
                                } else {
                                    viewModel.resetDetailsPopup()
                                }
                            },
                            icon = {
                                val colorInt =
                                    viewModel.recordDetailsMarkupColorPickerViewModel.color
                                        ?: return@ToggleToolbarButton
                                MarkupColorItem(
                                    color = Color(colorInt),
                                    selected = false,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    val coroutineScope = rememberCoroutineScope()
                    var buttonJob: Job? by remember { mutableStateOf(null) }
                    ToolbarPrimaryButton(
                        recording = viewModel.isRecording,
                        onClick = {
                            if (buttonJob == null) {
                                buttonJob =
                                    coroutineScope.launch {
                                        viewModel.onPrimaryButtonTapped()
                                        buttonJob = null
                                    }
                            }
                        },
                        viewModel = viewModel,
                        modifier = Modifier.height(40.dp),
                    )
                }
            }

            AnimatedVisibility(
                visible = viewModel.detailsPopup != RecordDetailsPopupType.Invisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier =
                    Modifier.fillBoundsInWindowIf(
                        region = settingsBounds,
                        condition = viewTreeObserver != null,
                    ),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(28.dp),
                    shadowElevation = 2.dp,
                    modifier = Modifier.animateContentSize(),
                ) {
                    AnimatedContent(
                        targetState = viewModel.detailsPopup,
                        contentAlignment = Alignment.Center,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        modifier = Modifier.widthIn(max = 352.dp),
                    ) { currentPopup ->
                        val contentModifier = Modifier.fillMaxWidth()
                        when (currentPopup) {
                            RecordDetailsPopupType.Settings ->
                                RecordDetailsSettings(
                                    parametersViewModel =
                                        viewModel.recordDetailsParametersViewModel,
                                    targetViewModel = viewModel.recordDetailsTargetViewModel,
                                    drawableLoaderViewModel = viewModel,
                                    onAppSelectorClicked = { viewModel.showAppSelector() },
                                    modifier = contentModifier,
                                )
                            RecordDetailsPopupType.AppSelector ->
                                RecordDetailsAppSelector(
                                    viewModel = viewModel.recordDetailsAppSelectorViewModel,
                                    onBackPressed = { viewModel.showSettings() },
                                    onTaskSelected = {
                                        viewModel.recordDetailsTargetViewModel.selectTask(it)
                                        viewModel.showSettings()
                                    },
                                    modifier = contentModifier,
                                )
                            RecordDetailsPopupType.MarkupColorSelector ->
                                RecordDetailsMarkupColorPicker(
                                    viewModel = viewModel.recordDetailsMarkupColorPickerViewModel,
                                    modifier = contentModifier,
                                )
                            RecordDetailsPopupType.Invisible -> {
                                /* do nothing */
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Modifier.fillBoundsInWindowIf(region: Region, condition: Boolean): Modifier =
        thenIf(condition) {
            Modifier.onGloballyPositioned { layoutCoordinates ->
                with(layoutCoordinates.boundsInWindow()) {
                    region.set(
                        left.roundToInt(),
                        top.roundToInt(),
                        right.roundToInt(),
                        bottom.roundToInt(),
                    )
                }
            }
        }
}

@Composable
private fun ToggleToolbarButton(
    checked: Boolean,
    onCheckedChanged: (Boolean) -> Unit,
    icon: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val shape = RoundedCornerShape(12.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(48.dp)
                .padding(6.dp)
                .clip(shape)
                .thenIf(checked) { Modifier.background(color = secondaryColor, shape = shape) }
                .clickable(onClick = { onCheckedChanged(!checked) }),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides
                if (checked) {
                    MaterialTheme.colorScheme.onSecondary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
        ) {
            icon()
        }
    }
}

@Composable
private fun ToolbarPrimaryButton(
    recording: Boolean,
    viewModel: DrawableLoaderViewModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(targetState = recording) { isRecording ->
        if (isRecording) {
            PrimaryButton(
                onClick = onClick,
                text = stringResource(R.string.screenrecord_stop_label),
                icon =
                    loadIcon(
                            viewModel = viewModel,
                            resId = R.drawable.ic_stop,
                            contentDescription = null,
                        )
                        .value,
                contentPadding = PaddingValues(horizontal = 14.dp),
                iconPadding = 4.dp,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                modifier = modifier,
            )
        } else {
            PrimaryButton(
                onClick = onClick,
                text = stringResource(R.string.screenrecord_continue),
                icon =
                    loadIcon(
                            viewModel = viewModel,
                            resId = R.drawable.ic_screenrecord,
                            contentDescription = null,
                        )
                        .value,
                contentPadding = PaddingValues(horizontal = 14.dp),
                iconPadding = 4.dp,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                modifier = modifier,
            )
        }
    }
}
