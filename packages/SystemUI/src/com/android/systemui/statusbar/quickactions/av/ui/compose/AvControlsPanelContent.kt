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

package com.android.systemui.statusbar.quickactions.av.ui.compose

import android.graphics.drawable.Drawable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.compose.ui.graphics.painter.DrawablePainter
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.statusbar.quickactions.av.shared.model.Sensor
import com.android.systemui.statusbar.quickactions.av.shared.model.SensorAccess
import com.android.systemui.statusbar.quickactions.av.ui.viewmodel.AvControlsPanelContentViewModel
import com.android.systemui.statusbar.quickactions.av.ui.viewmodel.ButtonViewModel
import com.android.systemui.statusbar.quickactions.av.ui.viewmodel.PageType
import com.android.systemui.statusbar.quickactions.av.ui.viewmodel.SensorActivityViewModel
import kotlinx.coroutines.launch

/**
 * The main content of the AV Controls Panel, displaying sensor access, and options for Blur, Studio
 * Look, Camera Framing, Studio Mic, and Live Captions.
 */
@Composable
fun AvControlsPanelContent(
    viewModelFactory: AvControlsPanelContentViewModel.Factory,
    setCurrentPage: (PageType) -> Unit,
    modifier: Modifier = Modifier,
) {

    val viewModel =
        rememberViewModel("MainPage.viewModel", key = setCurrentPage) {
            viewModelFactory.create(setCurrentPage = setCurrentPage)
        }
    Column(modifier = modifier) {
        Row(modifier = Modifier.padding(10.dp).fillMaxWidth()) {
            SensorAccessButton(
                viewModelFactory = viewModel.sensorActivityViewModelFactory,
                setCurrentPage = setCurrentPage,
            )
        }
        if (viewModel.showBlurControls || viewModel.showStudioLookControls) {
            Row(modifier = Modifier.padding(4.dp).fillMaxWidth()) {
                if (viewModel.showStudioLookControls) {
                    Box(modifier = Modifier.weight(0.5f)) {
                        SectionButton(
                            buttonViewModelFactory = {
                                viewModel.studioLookButtonViewModelFactory.create(
                                    setCurrentPage = setCurrentPage
                                )
                            },
                            traceName = "StudioLookSectionButton",
                        )
                    }
                }
                Box(modifier = Modifier.weight(0.5f)) {
                    if (viewModel.showBlurControls) {
                        SectionButton(
                            buttonViewModelFactory = {
                                viewModel.blurButtonViewModelFactory.create(
                                    setCurrentPage = setCurrentPage
                                )
                            },
                            traceName = "BlurSectionButton",
                        )
                    }
                }
            }
        }
        if (
            viewModel.showLiveCaptionsButton ||
                viewModel.showStudioMicButton ||
                viewModel.showCameraFramingButton
        ) {
            Row(modifier = Modifier.padding(4.dp).fillMaxWidth()) {
                if (viewModel.showCameraFramingButton) {
                    Box(modifier = Modifier.weight(1f)) {
                        SimpleButton(
                            buttonViewModelFactory = {
                                viewModel.cameraFramingViewModelFactory.create()
                            },
                            traceName = "CameraFramingButton",
                        )
                    }
                }
                if (viewModel.showStudioMicButton) {
                    Box(modifier = Modifier.weight(1f)) {
                        SimpleButton(
                            buttonViewModelFactory = {
                                viewModel.studioMicViewModelFactory.create()
                            },
                            traceName = "StudioMicButton",
                        )
                    }
                }
                if (viewModel.showLiveCaptionsButton) {
                    Box(modifier = Modifier.weight(1f)) {
                        SimpleButton(
                            buttonViewModelFactory = {
                                viewModel.liveCaptionsViewModelFactory.create()
                            },
                            traceName = "LiveCaptionsButton",
                        )
                    }
                }
            }
        }
    }
}

/** A simple toggle button with an icon and optional subtext. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SimpleButton(
    buttonViewModelFactory: () -> ButtonViewModel,
    traceName: String,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val viewModel: ButtonViewModel =
        rememberViewModel("MainPage.$traceName") { buttonViewModelFactory() }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row {
            ToggleButton(
                modifier = Modifier.padding(4.dp).fillMaxWidth(),
                checked = viewModel.state.isEnabled,
                onCheckedChange = { scope.launch { viewModel.onClick() } },
            ) {
                viewModel.state.image?.let {
                    Icon(painter = painterResource(id = it), contentDescription = null)
                }
            }
        }
        viewModel.state.subText?.let {
            Row { Text(text = stringResource(it), style = MaterialTheme.typography.labelSmall) }
        }
    }
}

/** A button used for navigating to a specific section (e.g., Blur, Studio Look). */
@Composable
private fun SectionButton(
    buttonViewModelFactory: () -> ButtonViewModel,
    traceName: String,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val viewModel: ButtonViewModel =
        rememberViewModel("MainPage.$traceName") { buttonViewModelFactory() }
    ListItem(
        modifier =
            Modifier.padding(4.dp)
                .clip(shape = RoundedCornerShape(16.dp))
                .fillMaxWidth()
                .clickable(onClick = { scope.launch { viewModel.onClick() } }),
        leadingContent = {
            viewModel.state.image?.let {
                Icon(painter = painterResource(id = it), contentDescription = null)
            }
        },
        headlineContent = { viewModel.state.mainTitle?.let { Text(text = stringResource(it)) } },
        supportingContent = {
            viewModel.state.subTitle?.let {
                val text =
                    if (viewModel.state.subTitleArg != null) {
                        stringResource(it, viewModel.state.subTitleArg!!)
                    } else {
                        stringResource(it)
                    }
                Text(text = text)
            }
        },
        colors = itemColors(viewModel.state.isEnabled),
    )
}

/**
 * A button that displays active sensor usage (camera, microphone) and navigates to the sensor
 * activity page.
 */
@Composable
private fun SensorAccessButton(
    viewModelFactory: SensorActivityViewModel.Factory,
    setCurrentPage: (PageType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel =
        rememberViewModel("SensorAccessButton.viewModel", key = setCurrentPage) {
            viewModelFactory.create(setCurrentPage = setCurrentPage)
        }
    val sensorAccessList = viewModel.sensorAccessList
    if (sensorAccessList.isNotEmpty()) {
        val (leadingContent, headlineContentText, supportingContentText) =
            remember(sensorAccessList) {
                Triple(
                    activeAppsIconComposable(sensorAccessList),
                    activeAppsHeaderText(sensorAccessList),
                    activeAppsSupportText(sensorAccessList),
                )
            }
        ListItem(
            modifier =
                modifier
                    .padding(4.dp)
                    .clip(shape = RoundedCornerShape(22.dp))
                    .clickable(onClick = { viewModel.enterDedicatedPage() }),
            leadingContent = leadingContent,
            headlineContent = { Text(text = headlineContentText) },
            supportingContent = { Text(text = supportingContentText) },
        )
    }
}

private fun activeAppsIconComposable(
    sensorAccessList: List<SensorAccess>
): @Composable (() -> Unit)? {
    // TODO(469044323): Move the logic into the view model to make it testable.
    require(sensorAccessList.isNotEmpty())
    val firstIcon: Drawable? = sensorAccessList.first().icon
    val otherIcon: Drawable? = sensorAccessList.find { it.icon != firstIcon }?.icon
    // Show icon only if there is one icon.
    if (otherIcon == null) {
        return firstIcon?.let {
            { Icon(painter = DrawablePainter(drawable = it), contentDescription = null) }
        }
    }
    return null
}

private fun activeAppsHeaderText(sensorAccessList: List<SensorAccess>): String {
    // TODO(469044323): Move the logic into the view model to make it testable.
    require(sensorAccessList.isNotEmpty())
    val apps = sensorAccessList.map { it.appName }.distinct()
    val firstAppName = apps.first()
    return when (val tailCount = apps.size - 1) {
        0 -> firstAppName
        1 -> "$firstAppName, ${apps[1]}"
        else -> "$firstAppName +$tailCount more" // TODO(467631255): Use string resources.
    }
}

private fun activeAppsSupportText(sensorAccessList: List<SensorAccess>): String {
    // TODO(469044323): Move the logic into the view model to make it testable.
    require(sensorAccessList.isNotEmpty())
    val sensors = sensorAccessList.map { it.sensor }.distinct()
    if (sensors.size == 1) {
        return when (sensors.first()) {
            Sensor.CAMERA -> "Camera in use" // TODO(467631255): Use string resources.
            Sensor.MICROPHONE -> "Mic in use" // TODO(467631255): Use string resources.
        }
    }
    return "Camera, mic in use" // TODO(467631255): Use string resources.
}
