/*
 * Copyright (C) 2023 The Android Open Source Project
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

@file:OptIn(ExperimentalComposeUiApi::class)

package com.android.systemui.bouncer.ui.composable

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onInterceptKeyBeforeSoftKeyboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.PlatformIconButton
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.content.state.TransitionState
import com.android.systemui.bouncer.ui.viewmodel.PasswordBouncerViewModel
import com.android.systemui.common.ui.compose.SelectedUserAwareInputConnection
import com.android.systemui.common.ui.compose.SelectedUserAwareLocalContext
import com.android.systemui.compose.modifiers.sysuiResTag
import com.android.systemui.res.R

/** UI for the input part of a password-requiring version of the bouncer. */
@Composable
internal fun ContentScope.PasswordBouncer(
    viewModel: PasswordBouncerViewModel,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val isTextFieldFocusRequested by
        viewModel.isTextFieldFocusRequested.collectAsStateWithLifecycle()
    LaunchedEffect(isTextFieldFocusRequested) {
        if (isTextFieldFocusRequested) {
            focusRequester.requestFocus()
        }
    }

    val isInputEnabled: Boolean by viewModel.isInputEnabled.collectAsStateWithLifecycle()
    val animateFailure: Boolean by viewModel.animateFailure.collectAsStateWithLifecycle()
    val isImeSwitcherButtonVisible by
        viewModel.isImeSwitcherButtonVisible.collectAsStateWithLifecycle()
    val isPasswordRevealed by viewModel.isPasswordRevealed.collectAsStateWithLifecycle()
    val selectedUserId by viewModel.selectedUserId.collectAsStateWithLifecycle()

    DisposableEffect(Unit) { onDispose { viewModel.onHidden() } }

    LaunchedEffect(animateFailure) {
        if (animateFailure) {
            // We don't currently have a failure animation for password, just consume it:
            viewModel.onFailureAnimationShown()
        }
    }

    val color = MaterialTheme.colorScheme.onSurfaceVariant

    ResetFocusIfNeeded(layoutState.transitionState, viewModel)

    SelectedUserAwareInputConnection(selectedUserId) {
        SelectedUserAwareLocalContext(selectedUserId) {
            OutlinedSecureTextField(
                state = viewModel.textFieldState,
                enabled = isInputEnabled,
                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                keyboardOptions =
                    KeyboardOptions(
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                onKeyboardAction = { viewModel.onAuthenticateKeyPressed() },
                textObfuscationMode =
                    if (isPasswordRevealed) TextObfuscationMode.Visible
                    else TextObfuscationMode.Hidden,
                modifier =
                    modifier
                        .width(dimensionResource(id = R.dimen.keyguard_password_field_width))
                        .sysuiResTag("bouncer_text_entry")
                        .focusRequester(focusRequester)
                        .onFocusChanged { viewModel.onTextFieldFocusChanged(it.isFocused) }
                        .onInterceptKeyBeforeSoftKeyboard { keyEvent ->
                            if (keyEvent.key == Key.Back) {
                                viewModel.onImeDismissed()
                                true
                            } else {
                                false
                            }
                        },
                trailingIcon = {
                    trailingIcons(viewModel, color, isImeSwitcherButtonVisible, isPasswordRevealed)
                },
                shape = RoundedCornerShape(28.dp),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = color,
                        unfocusedBorderColor = color,
                    ),
            )
        }
    }
}

@Composable
private fun trailingIcons(
    viewModel: PasswordBouncerViewModel,
    color: Color,
    isImeSwitcherButtonVisible: Boolean,
    isPasswordRevealed: Boolean,
) {
    if (!viewModel.isMoreIndicatorsAndButtonsEnabled) {
        if (isImeSwitcherButtonVisible) {
            ImeSwitcherButton(viewModel, color)
        }
        return
    }

    Row() {
        if (isImeSwitcherButtonVisible) {
            ImeSwitcherButton(viewModel, color)
        }
        IconButton(
            onClick = {
                if (isPasswordRevealed) {
                    viewModel.onHidePasswordButtonClicked()
                } else {
                    viewModel.onRevealPasswordButtonClicked()
                }
            },
            colors =
                IconButtonDefaults.filledIconButtonColors(
                    contentColor = color,
                    containerColor = Color.Transparent,
                ),
        ) {
            Icon(
                imageVector =
                    if (isPasswordRevealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                contentDescription = null,
                // 24p matches the size of ImeSwitcherButton's icon
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** Button for changing the password input method (IME). */
@Composable
private fun ImeSwitcherButton(viewModel: PasswordBouncerViewModel, color: Color) {
    val context = LocalContext.current
    PlatformIconButton(
        onClick = { viewModel.onImeSwitcherButtonClicked(context.displayId) },
        iconResource = R.drawable.ic_lockscreen_ime,
        contentDescription = stringResource(R.string.accessibility_ime_switch_button),
        colors =
            IconButtonDefaults.filledIconButtonColors(
                contentColor = color,
                containerColor = Color.Transparent,
            ),
    )
}

@Composable
private fun ResetFocusIfNeeded(
    transitionState: TransitionState,
    viewModel: PasswordBouncerViewModel,
) {
    val focusManager = LocalFocusManager.current
    SideEffect {
        if (viewModel.shouldResetFocus(transitionState)) {
            focusManager.clearFocus()
        }
    }
}
