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

package com.android.systemui.inputmethod.ui.composable

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import com.android.compose.PlatformOutlinedButton
import com.android.compose.theme.PlatformTheme
import com.android.internal.R
import com.android.systemui.Flags
import com.android.systemui.inputmethod.ui.viewmodel.ImeSwitcherMenuViewModel
import com.android.systemui.lifecycle.rememberActivated

/**
 * The UI for the content of the IME Switcher Menu Dialog.
 *
 * @param viewModelFactory the factory to create a view model.
 * @param dismissAction the action to invoke when the UI should be dismissed.
 */
@Composable
fun ImeSwitcherMenuContent(
    viewModelFactory: (context: Context) -> ImeSwitcherMenuViewModel,
    /** Callback when the UI should be dismissed. */
    dismissAction: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel =
        rememberActivated(
            traceName = "imeSwitcherMenuViewModelFactory",
            key = Pair(viewModelFactory, context),
        ) {
            viewModelFactory.invoke(context)
        }

    // TODO(b/369376884): The composable does correctly update when the theme changes
    //  while the dialog is open, but the background (which we don't control here)
    //  doesn't, which causes us to show things like white text on a white background.
    //  as a workaround, we remember the original theme and keep it on recomposition.
    val isCurrentlyInDarkTheme = isSystemInDarkTheme()
    val cachedDarkTheme = remember { isCurrentlyInDarkTheme }
    // TODO(b/474600479): Remove remember val and PlatformTheme wrapper once flag is advanced.
    val isDarkTheme =
        if (Flags.dialogBackgroundRefresh()) isCurrentlyInDarkTheme else cachedDarkTheme
    val paneTitleDescription = stringResource(R.string.select_input_method)

    PlatformTheme(isDarkTheme = isDarkTheme) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .semantics {
                        paneTitle = paneTitleDescription
                        testTagsAsResourceId = true
                    }
                    .testTag("container"),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(modifier = Modifier.weight(weight = 1f, fill = false)) {
                ImeSwitcherMenuList(
                    viewModel.menuItems.toList(),
                    viewModel,
                    dismissAction,
                    useLargeScreenLayout = false
                )
            }
            viewModel.settingsButtonAction.value?.let { action ->
                SettingsFooter(
                    settingsButtonAction = {
                        action.invoke()
                        dismissAction.invoke()
                    }
                )
            }
        }
    }
}

/**
 * The footer of the IME Switcher Menu Dialog, which contains the settings button.
 *
 * @param settingsButtonAction the action to invoke when the settings button is clicked. This
 *   action should also dismiss the UI.
 */
@Composable
private fun SettingsFooter(settingsButtonAction: () -> Unit) {
    val settingsButtonDescription = stringResource(R.string.input_method_language_settings)
    Box(
        contentAlignment = Alignment.CenterEnd,
        modifier =
            Modifier.fillMaxWidth()
                .padding(top = 8.dp, end = 16.dp, bottom = 16.dp)
                .testTag("settings_footer"),
    ) {
        PlatformOutlinedButton(
            modifier = Modifier.testTag("button1"),
            onClick = settingsButtonAction,
        ) {
            Text(
                text = stringResource(R.string.input_method_switcher_settings_button),
                modifier =
                    Modifier.padding(vertical = 3.dp).semantics {
                        contentDescription = settingsButtonDescription
                    },
            )
        }
    }
}
