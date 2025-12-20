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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.compose.ui.graphics.painter.DrawablePainter
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.statusbar.quickactions.av.shared.model.Sensor
import com.android.systemui.statusbar.quickactions.av.ui.viewmodel.PageType
import com.android.systemui.statusbar.quickactions.av.ui.viewmodel.SensorActivityViewModel

/** A drill-in page displaying detailed sensor usage (camera, microphone) by apps. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SensorActivityDrillIn(
    viewModelFactory: SensorActivityViewModel.Factory,
    setCurrentPage: (PageType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel =
        rememberViewModel("SensorActivityDrillIn.viewModel", key = setCurrentPage) {
            viewModelFactory.create(setCurrentPage = setCurrentPage)
        }
    val typography = MaterialTheme.typography
    val colorScheme = MaterialTheme.colorScheme

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = { viewModel.returnToMainPage() }) {
                // TODO(467631255): Use string resources.
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Privacy", // TODO(467631255): Use string resources.
                modifier =
                    Modifier.weight(1f) // Takes all available horizontal space.
                        .height(24.dp)
                        .fillMaxWidth(), // Ensures the composable fills it's weighted space.
                style = typography.titleMedium,
                textAlign = TextAlign.Center,
                color = colorScheme.onSurface,
            )
        }
        Text(
            text = "See apps that are actively or recently using these permissions.",
            modifier = Modifier.height(32.dp),
            style = typography.labelMedium,
            textAlign = TextAlign.Center,
            color = colorScheme.onSurfaceVariant,
        )
        // TODO(469056547): Move logic into ViewModel where it can be tested.
        val usagesPerApp = viewModel.sensorAccessList.groupBy { it.packageName }.entries.toList()
        usagesPerApp.forEachIndexed { index, (packageName, usages) ->
            val isFirstItem = (index == 0)
            val isLastItem = (index == usagesPerApp.size - 1)
            if (!isFirstItem) {
                Spacer(modifier = Modifier.height(2.dp))
            }
            AppDetailItem(
                viewModel = viewModel,
                appIcon = usages.first().icon,
                appName = usages.first().appName,
                packageName = packageName,
                sensorUsages = usages.map { it.sensor }.distinct(),
                isFirstItem = isFirstItem,
                isLastItem = isLastItem,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        ListItem(
            // TODO(467631255): Use string resources.
            headlineContent = { Text(text = "See all access", textAlign = TextAlign.Center) },
            modifier =
                Modifier.clip(shape = RoundedCornerShape(size = 26.dp))
                    .clickable(onClick = { viewModel.openPrivacyDashboard() }),
            colors =
                ListItemDefaults.colors()
                    .copy(
                        containerColor = colorScheme.surface,
                        headlineColor = colorScheme.onSurface,
                    ),
        )
    }
}

/** A list item displaying details for a specific app's sensor usage. */
@Composable
private fun AppDetailItem(
    viewModel: SensorActivityViewModel,
    appIcon: Drawable?,
    appName: String,
    packageName: String,
    sensorUsages: List<Sensor>,
    isFirstItem: Boolean,
    isLastItem: Boolean,
) {
    val colorScheme = MaterialTheme.colorScheme

    val bigRadius = 26.dp
    val smallRadius = 4.dp
    val topCornerRadius = if (isFirstItem) bigRadius else smallRadius
    val bottomCornerRadius = if (isLastItem) bigRadius else smallRadius
    val shape =
        RoundedCornerShape(
            topStart = topCornerRadius,
            topEnd = topCornerRadius,
            bottomStart = bottomCornerRadius,
            bottomEnd = bottomCornerRadius,
        )
    ListItem(
        leadingContent =
            appIcon?.let {
                { Icon(painter = DrawablePainter(drawable = it), contentDescription = null) }
            },
        headlineContent = { Text(text = appName) },
        supportingContent = {
            SupportingContent(
                viewModel = viewModel,
                packageName = packageName,
                sensors = sensorUsages,
            )
        },
        colors =
            ListItemDefaults.colors()
                .copy(
                    containerColor = colorScheme.surface,
                    headlineColor = colorScheme.onSurface,
                    supportingTextColor = colorScheme.primary,
                ),
        modifier = Modifier.height(92.dp).clip(shape = shape),
    )
}

/** The supporting content for an app detail item, showing used sensors and action buttons. */
@Composable
private fun SupportingContent(
    viewModel: SensorActivityViewModel,
    packageName: String,
    sensors: List<Sensor>,
) {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    Column() {
        Row(modifier = Modifier.height(20.dp).fillMaxWidth()) {
            val hasCamera = sensors.contains(Sensor.CAMERA)
            val hasMicrophone = sensors.contains(Sensor.MICROPHONE)
            if (hasCamera) {
                Icon(
                    imageVector = Icons.Filled.Videocam,
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant,
                )
                // TODO(467631255): Use string resources.
                Text(text = "Camera", style = typography.bodySmall)
            }
            if (hasCamera && hasMicrophone) {
                Spacer(modifier = Modifier.width(16.dp))
            }
            if (hasMicrophone) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant,
                )
                // TODO(467631255): Use string resources.
                Text(text = "Microphone", style = typography.bodySmall)
            }
        }
        Row(modifier = Modifier.height(24.dp).fillMaxWidth()) {
            Text(
                // TODO(467631255): Use string resources.
                text = "ManageAccess",
                style = typography.labelMedium,
                color = colorScheme.primary,
                modifier = Modifier.clickable(onClick = { viewModel.manageApp(packageName) }),
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                // TODO(467631255): Use string resources.
                text = "Close app",
                style = typography.labelMedium,
                color = colorScheme.primary,
                modifier = Modifier.clickable(onClick = { viewModel.closeApp(packageName) }),
            )
        }
    }
}
