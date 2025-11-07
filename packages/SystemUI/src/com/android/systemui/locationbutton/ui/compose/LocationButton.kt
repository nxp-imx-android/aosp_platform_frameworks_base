/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.systemui.locationbutton.ui.compose

import android.view.View
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.locationbutton.ui.viewmodel.LocationButtonViewModel
import com.android.systemui.res.R

@Composable
fun LocationButton(
    viewModelFactory: LocationButtonViewModel.Factory,
    sessionId: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val buttonViewModel = rememberViewModel("LocationButton") { viewModelFactory.create(sessionId) }
    val viewModel = buttonViewModel.getButtonViewModel() ?: return

    val context = LocalContext.current
    val updatedContext =
        remember(viewModel.configuration) {
            context.createConfigurationContext(viewModel.configuration)
        }

    val density =
        remember(updatedContext) {
            Density(
                density = updatedContext.resources.displayMetrics.density,
                fontScale = updatedContext.resources.configuration.fontScale,
            )
        }

    val layoutDirection =
        if (updatedContext.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
            LayoutDirection.Rtl
        } else {
            LayoutDirection.Ltr
        }

    CompositionLocalProvider(
        LocalContext provides updatedContext,
        LocalLayoutDirection provides layoutDirection,
        LocalDensity provides density,
    ) {
        val buttonWidth = with(LocalDensity.current) { viewModel.width.toDp() }
        val buttonHeight = with(LocalDensity.current) { viewModel.height.toDp() }
        val strokeWidth = with(LocalDensity.current) { viewModel.strokeWidth.toDp() }
        val cornerRadius = with(density) { viewModel.cornerRadius.toDp() }

        Button(
            onClick = onClick,
            modifier = modifier.size(width = buttonWidth, height = buttonHeight),
            shape = RoundedCornerShape(cornerRadius),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = viewModel.backgroundColor,
                    contentColor = viewModel.textColor,
                ),
            border = BorderStroke(strokeWidth, viewModel.strokeColor),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_my_location),
                contentDescription = null,
                tint = viewModel.iconTint,
            )
            viewModel.textResId?.let { textResId ->
                Text(
                    text = stringResource(textResId),
                    modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                )
            }
        }
    }
}
