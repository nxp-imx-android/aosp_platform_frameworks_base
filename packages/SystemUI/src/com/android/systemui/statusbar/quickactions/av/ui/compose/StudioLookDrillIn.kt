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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.statusbar.quickactions.av.ui.viewmodel.ButtonViewModel
import com.android.systemui.statusbar.quickactions.av.ui.viewmodel.StudioLookDrillInViewModel
import kotlinx.coroutines.launch

/** A drill-in page for Studio Look controls (e.g., Portrait Relight, Face Retouch). */
@Composable
fun StudioLookDrillIn(
    viewModelFactory: StudioLookDrillInViewModel.Factory,
    returnToMainPage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: StudioLookDrillInViewModel =
        rememberViewModel("StudioLookDrillIn.viewModel", key = returnToMainPage) {
            viewModelFactory.create(returnToMainPage)
        }
    DrillIn(
        modifier = modifier,
        drillInTitle = stringResource(viewModel.drillInTitle),
        returnToMainPage = { viewModel.returnToMainPage() },
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            StudioLookSwitch(
                traceName = "PortraitRelightButtonViewModel",
                viewModelFactory = { viewModel.portraitRelightButtonViewModelFactory.create() },
            )
            StudioLookSwitch(
                traceName = "FaceRetouchButtonViewModel",
                viewModelFactory = { viewModel.faceRetouchButtonViewModelFactory.create() },
            )
        }
    }
}

/** A toggle switch with an icon and label, used for enabling/disabling a Studio Look feature. */
@Composable
fun StudioLookSwitch(traceName: String, viewModelFactory: () -> ButtonViewModel) {
    val scope = rememberCoroutineScope()
    val viewModel: ButtonViewModel = rememberViewModel(traceName) { viewModelFactory() }
    ListItem(
        leadingContent = {
            viewModel.state.image?.let {
                Icon(painter = painterResource(id = it), contentDescription = null)
            }
        },
        headlineContent = { viewModel.state.mainTitle?.let { Text(text = stringResource(it)) } },
        trailingContent = {
            Switch(
                checked = viewModel.state.isEnabled,
                onCheckedChange = { scope.launch { viewModel.onClick() } },
            )
        },
        colors = colors(viewModel.state.isEnabled),
        modifier =
            Modifier.clip(shape = RoundedCornerShape(8.dp))
                .clickable(onClick = { scope.launch { viewModel.onClick() } }),
    )
}

/** Colors for the StudioLookSwitch based on its enabled state. */
@Composable
private fun colors(isEnabled: Boolean): ListItemColors =
    if (isEnabled) {
        ListItemDefaults.colors()
            .copy(
                containerColor = MaterialTheme.colorScheme.primary,
                headlineColor = MaterialTheme.colorScheme.onPrimary,
                leadingIconColor = MaterialTheme.colorScheme.onPrimary,
                supportingTextColor = MaterialTheme.colorScheme.onPrimary,
            )
    } else {
        ListItemDefaults.colors()
            .copy(
                containerColor = MaterialTheme.colorScheme.secondary,
                headlineColor = MaterialTheme.colorScheme.onSecondary,
                leadingIconColor = MaterialTheme.colorScheme.onSecondary,
                supportingTextColor = MaterialTheme.colorScheme.onSecondary,
            )
    }
